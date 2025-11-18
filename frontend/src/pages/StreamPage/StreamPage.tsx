/* eslint-disable no-empty */
import React, { useEffect, useRef, useState } from "react";
import "./StreamPage.css";
import { getWebSocketUrl, getReceivers, getCurrentUser } from "../../api/auth";
import LogsPopup from "../../components/Popup/LogsPopup";

type WSState = "idle" | "connecting" | "open" | "closed" | "error";

interface Receiver { username: string; }

const ICE_CONFIG: RTCConfiguration = {
    iceServers: [
        {
            urls: "turn:87.205.113.203:9001?transport=udp",
            username: "webrtc",
            credential: "supersecretpassword"
        },
        {
            urls: "turn:87.205.113.203:9001?transport=tcp",
            username: "webrtc",
            credential: "supersecretpassword"
        },
        { urls: "stun:stun.l.google.com:19302" }
    ]
};

const StreamPage: React.FC = () => {
    const wsRef = useRef<WebSocket | null>(null);
    const pcRef = useRef<RTCPeerConnection | null>(null);
    const localStreamRef = useRef<MediaStream | null>(null);
    const previewRef = useRef<HTMLVideoElement | null>(null);

    const [state, setState] = useState<WSState>("idle");
    const [logs, setLogs] = useState<string[]>([]);
    const [receivers, setReceivers] = useState<string[]>([]);
    const [onlineReceiver, setOnlineReceiver] = useState<string | null>(null);
    const [showLogs, setShowLogs] = useState(false);
    const [username, setUsername] = useState<string | null>(null);

    const [hasStream, setHasStream] = useState(false);
    const [mutedStream, setMutedStream] = useState(false);
    const [mutedMic, setMutedMic] = useState(false);
    const [cameraOn, setCameraOn] = useState(true);
    const [volume, setVolume] = useState(100);

    useEffect(() => {
        (async () => {
            try {
                const me = await getCurrentUser();
                setUsername(me?.username ?? null);
            } catch { setUsername(null); }
        })();
        (async () => {
            try {
                const list: Receiver[] = await getReceivers();
                setReceivers(list.map(r => r.username));
            } catch {}
        })();
        const unload = () => {
            cleanupPeer();
            wsRef.current?.close();
        };
        window.addEventListener("beforeunload", unload);
        return () => {
            window.removeEventListener("beforeunload", unload);
            cleanupPeer();
            wsRef.current?.close();
        };
    }, []);

    const addLog = (m: string) => setLogs(prev => [...prev, `${new Date().toISOString()}  ${m}`]);

    const connectWS = () => {
        if (!username) { addLog("No current user"); return; }
        if (receivers.length === 0) { addLog("No receivers"); return; }
        if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) return;
        setState("connecting");
        addLog("Connecting to WebSocket...");
        const ws = new WebSocket(getWebSocketUrl());
        wsRef.current = ws;
        ws.onopen = () => {
            setState("open");
            addLog("WebSocket connected");
            ws.send(JSON.stringify({ type: "ping" }));
        };
        ws.onmessage = (evt) => {
            try {
                const data = JSON.parse(evt.data);
                if (data?.type === "user-list" && Array.isArray(data.payload)) {
                    const active = receivers.find(r => data.payload.includes(r)) ?? null;
                    setOnlineReceiver(active);
                }
                if (data?.type === "answer" && data.payload && pcRef.current) {
                    const answer = JSON.parse(data.payload);
                    pcRef.current.setRemoteDescription(new RTCSessionDescription(answer))
                        .then(() => addLog("Remote answer applied"))
                        .catch(e => addLog("Failed set remote answer: " + String(e)));
                }
                if (data?.type === "candidate" && data.payload && pcRef.current) {
                    const cand = JSON.parse(data.payload);
                    pcRef.current.addIceCandidate(new RTCIceCandidate(cand)).catch(() => addLog("addIceCandidate error"));
                }
            } catch {
                addLog("WS message parse error");
            }
        };
        ws.onclose = () => {
            setState("closed");
            addLog("WebSocket closed");
            setOnlineReceiver(null);
            stopStreamInternal();
        };
        ws.onerror = () => {
            setState("error");
            addLog("WebSocket error");
            stopStreamInternal();
        };
    };

    const disconnectWS = () => {
        wsRef.current?.close();
        wsRef.current = null;
        setState("closed");
        addLog("WebSocket disconnected");
        setOnlineReceiver(null);
        stopStreamInternal();
    };

    const sendSignal = (type: string, payload: any, to: string) => {
        if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) { addLog("WS not open"); return; }
        if (!username) { addLog("No username"); return; }
        const msg = { type, from: username, to, payload: typeof payload === "string" ? payload : JSON.stringify(payload) };
        wsRef.current.send(JSON.stringify(msg));
    };

    const ensureLocalStream = async (): Promise<MediaStream> => {
        if (localStreamRef.current) return localStreamRef.current;

        const s = await navigator.mediaDevices.getUserMedia({
            audio: {
                echoCancellation: true,
                noiseSuppression: true,
                sampleRate: 48000
            },
            video: {
                width: { ideal: 1280 },
                height: { ideal: 720 },
                frameRate: { ideal: 60, max: 60 }
            }
        });

        localStreamRef.current = s;

        s.getAudioTracks().forEach(t => t.enabled = false);
        setMutedStream(true);

        if (previewRef.current) {
            previewRef.current.srcObject = s;
            previewRef.current.muted = false;
            previewRef.current.volume = volume / 100;
        }

        return s;
    };


    const startStream = async () => {
        if (!onlineReceiver) { addLog("❌ No receiver online"); return; }
        if (!username) { addLog("❌ No username"); return; }
        try {
            addLog("Creating RTCPeerConnection...");
            const pc = new RTCPeerConnection(ICE_CONFIG);
            pcRef.current = pc;

            const local = await ensureLocalStream();

            for (const t of local.getTracks()) {
                addLog("Adding track: " + t.kind);
                pc.addTrack(t, local);
            }

            const videoTrack = local.getVideoTracks()[0];
            const sender = pc.getSenders().find(s => s.track === videoTrack);
            if (sender) {
                const params = sender.getParameters();
                if (!params.encodings) params.encodings = [{}];

                params.encodings[0].maxBitrate = 5_000_000;
                params.encodings[0].maxFramerate = 60;
                params.encodings[0].networkPriority = "high";
                params.encodings[0].scaleResolutionDownBy = 1.0;

                await sender.setParameters(params);
            }


            pc.onicecandidate = (e) => {
                if (e.candidate) {
                    sendSignal("candidate", e.candidate.toJSON(), onlineReceiver);
                }
            };

            pc.onconnectionstatechange = () =>
                addLog("PeerConnection state = " + pc.connectionState);

            const offer = await pc.createOffer();

            await pc.setLocalDescription(offer);

            sendSignal("offer", offer, onlineReceiver);

            setHasStream(true);

        } catch (e: any) {
            addLog("❌ Start stream failed: " + e.message);
            console.error(e);
            cleanupPeer();
        }
    };


    const stopStreamInternal = () => {
        cleanupPeer();
        setHasStream(false);
        setMutedMic(false);
        setMutedStream(false);
        setCameraOn(true);
        addLog("Local stream stopped");
    };

    const cleanupPeer = () => {
        try { pcRef.current?.close(); } catch {}
        pcRef.current = null;
        try {
            if (localStreamRef.current) {
                localStreamRef.current.getTracks().forEach(t => t.stop());
            }
        } catch {}
        localStreamRef.current = null;
        if (previewRef.current) previewRef.current.srcObject = null;
    };

    const toggleMic = () => {
        if (!localStreamRef.current) return;
        localStreamRef.current.getAudioTracks().forEach(t => t.enabled = !t.enabled);
        setMutedMic(p => !p);
    };

    const toggleCamera = () => {
        if (!localStreamRef.current) return;
        localStreamRef.current.getVideoTracks().forEach(t => t.enabled = !t.enabled);
        setCameraOn(p => !p);
    };

    const toggleStreamMute = () => {
        if (!localStreamRef.current) return;

        const newMuted = !mutedStream;
        localStreamRef.current.getAudioTracks().forEach(t => t.enabled = !newMuted);

        setMutedStream(newMuted);
    };

    return (
        <div className="stream-container">
            {showLogs && <LogsPopup logs={logs} onClose={() => setShowLogs(false)} onClear={() => setLogs([])} />}

            <header className="stream-header">
                <h1>Stream Panel</h1>
                <div className="ws-controls">
                    <button onClick={connectWS} disabled={state === "connecting" || state === "open"}>Connect</button>
                    <button onClick={disconnectWS} disabled={state !== "open"}>Disconnect</button>
                    <button onClick={() => setShowLogs(true)}>Show logs</button>
                </div>
            </header>

            <section className="stream-vertical">
                <div className="status">
                    <strong>WebSocket:</strong>{" "}
                    <span className={`badge ${state}`}>{state === "idle" ? "Not connected" : state}</span>
                </div>

                {state === "open" && (
                    <div className="receiver-box full-width-box">
                        <h3>Receiver</h3>
                        {onlineReceiver ? <p className="receiver-online">{onlineReceiver} — Connected</p> : <p className="receiver-offline">None online</p>}
                    </div>
                )}

                {onlineReceiver && (
                    <div className="preview-box full-width-box">
                        <h3>Preview</h3>

                        <div className="preview-stage">
                            <video ref={previewRef} className="preview-video" autoPlay muted playsInline />
                        </div>

                        <div className="controls-area">
                            <div className="start-area">
                                <button className={`ctrl-btn start ${hasStream ? "active" : ""}`} onClick={() => hasStream ? stopStreamInternal() : startStream()}>
                                    {hasStream ? "STOP STREAM" : "START STREAM"}
                                </button>
                            </div>

                            <div className="ctrl-group">
                                <button className={`ctrl-btn ${mutedStream ? "off" : "on"}`} disabled={!hasStream} onClick={toggleStreamMute}>
                                    STREAM: {mutedStream ? "MUTED" : "VOICE ENABLED"}
                                </button>

                                <button className={`ctrl-btn ${mutedMic ? "off" : "on"}`} disabled={!hasStream} onClick={toggleMic}>
                                    MIC: {mutedMic ? "MUTED" : "ON"}
                                </button>

                                <button className={`ctrl-btn ${cameraOn ? "on" : "off"}`} disabled={!hasStream} onClick={toggleCamera}>
                                    CAMERA: {cameraOn ? "ON" : "OFF"}
                                </button>
                            </div>

                            <div className="volume-area">
                                <label className="volume-label">Volume: {volume}%</label>
                                <input className="volume-slider" type="range" min={0} max={100} value={volume} disabled={!hasStream} onChange={(e) => {
                                    const vol = Number(e.target.value);
                                    setVolume(vol);

                                    if (previewRef.current) {
                                        previewRef.current.volume = vol / 100;
                                    }
                                }}
                                />
                            </div>
                        </div>
                    </div>
                )}
            </section>
        </div>
    );
};

export default StreamPage;
