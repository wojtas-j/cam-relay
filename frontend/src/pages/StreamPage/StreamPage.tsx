import React, { useEffect, useRef, useState } from "react";
import "./StreamPage.css";
import { getWebSocketUrl, getReceivers } from "../../api/auth";

type WSState = "idle" | "connecting" | "open" | "closed" | "error";

interface Receiver {
    username: string;
}

const StreamPage: React.FC = () => {
    const wsRef = useRef<WebSocket | null>(null);
    const logEndRef = useRef<HTMLDivElement | null>(null);

    const [state, setState] = useState<WSState>("idle");
    const [log, setLog] = useState<string[]>([]);
    const [knownReceivers, setKnownReceivers] = useState<string[]>([]);
    const [onlineReceiver, setOnlineReceiver] = useState<string | null>(null);

    const appendLog = (msg: string) => {
        setLog((prev) => [...prev, `${msg}`]);
    };

    useEffect(() => {
        logEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [log]);

    useEffect(() => {
        const load = async () => {
            try {
                const receivers: Receiver[] = await getReceivers();
                const usernames = receivers.map((r) => r.username);
                setKnownReceivers(usernames);
            } catch {}
        };

        load();
        return () => wsRef.current?.close();
    }, []);

    const connect = () => {
        if (knownReceivers.length === 0) {
            appendLog("No receivers found");
            return;
        }

        if (wsRef.current?.readyState === WebSocket.OPEN) return;

        setState("connecting");
        appendLog("Connecting…");

        const ws = new WebSocket(getWebSocketUrl());
        wsRef.current = ws;

        ws.onopen = () => {
            setState("open");
            appendLog("Connected");
            ws.send(JSON.stringify({ type: "ping" }));
        };

        ws.onmessage = (evt) => {
            try {
                const data = JSON.parse(evt.data);
                if (data.type === "user-list" && Array.isArray(data.payload)) {
                    const onlineList: string[] = data.payload;
                    const activeReceiver =
                        knownReceivers.find((r) => onlineList.includes(r)) || null;

                    setOnlineReceiver(activeReceiver);
                }
            } catch {}
        };

        ws.onclose = () => {
            setState("closed");
            appendLog("Closed");
            setOnlineReceiver(null);
        };

        ws.onerror = () => {
            setState("error");
            appendLog("Error");
        };
    };

    const disconnect = () => {
        wsRef.current?.close();
        wsRef.current = null;

        setState("closed");
        appendLog("Disconnected");
        setOnlineReceiver(null);
    };

    const clearLog = () => setLog([]);

    const startStream = () => {
        appendLog("Starting stream…");
    };

    const wsLabel = state === "idle" ? "Not connected" : state;

    return (
        <div className="stream-container">
            <header className="stream-header">
                <h1>Stream Panel</h1>

                <div className="ws-controls">
                    <button onClick={connect} disabled={state === "connecting" || state === "open"}>
                        Connect
                    </button>

                    <button onClick={disconnect} disabled={state !== "open"}>
                        Disconnect
                    </button>

                    <button onClick={clearLog}>Clear log</button>
                </div>
            </header>

            <section className="stream-main">
                <div className="stream-left">

                    <div className="status">
                        <strong>WebSocket:</strong>{" "}
                        <span className={`badge ${state}`}>{wsLabel}</span>
                    </div>

                    {state === "open" && (
                        <div className="receiver-box">
                            <h3>Receiver</h3>

                            {onlineReceiver ? (
                                <p className="receiver-online">
                                    {onlineReceiver} — Connected
                                </p>
                            ) : (
                                <p className="receiver-offline">
                                    None online
                                </p>
                            )}

                            <div className="receiver-start-wrapper">
                                <button
                                    className="start-btn"
                                    disabled={!onlineReceiver}
                                    onClick={startStream}
                                >
                                    START STREAM
                                </button>
                            </div>
                        </div>
                    )}

                    <div className="logger-box">
                        <h3>Logger</h3>

                        <div className="logger-scroll">
                            {log.map((line, i) => (
                                <div key={i} className="log-line">{line}</div>
                            ))}
                            <div ref={logEndRef} />
                        </div>
                    </div>
                </div>

                <div className="stream-right">
                    <div className="preview-box">
                        <h3>Preview</h3>
                        <div className="preview-content">
                        </div>
                    </div>
                </div>
            </section>
        </div>
    );
};

export default StreamPage;
