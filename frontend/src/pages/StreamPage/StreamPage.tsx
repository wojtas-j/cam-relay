import React, { useEffect, useRef, useState } from "react";
import "./StreamPage.css";
import { getWebSocketUrl } from "../../api/auth";

type WSState = "idle" | "connecting" | "open" | "closed" | "error";

const StreamPage: React.FC = () => {
    const wsRef = useRef<WebSocket | null>(null);
    const logEndRef = useRef<HTMLDivElement | null>(null);

    const [state, setState] = useState<WSState>("idle");
    const [log, setLog] = useState<string[]>([]);
    const [remoteUsers, setRemoteUsers] = useState<string[]>([]);

    const appendLog = (msg: string) => {
        setLog((prev) => [...prev, `${new Date().toISOString()} — ${msg}`]);
    };

    useEffect(() => {
        if (logEndRef.current) {
            logEndRef.current.scrollIntoView({ behavior: "smooth" });
        }
    }, [log]);

    useEffect(() => {
        return () => {
            if (wsRef.current) {
                wsRef.current.close();
                wsRef.current = null;
            }
        };
    }, []);

    const connect = () => {
        if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) return;

        setState("connecting");
        appendLog("Connecting to WebSocket...");

        const ws = new WebSocket(getWebSocketUrl());
        wsRef.current = ws;

        ws.onopen = () => {
            setState("open");
            appendLog("Connected.");
            ws.send(JSON.stringify({ type: "ping", payload: "hello" }));
        };

        ws.onmessage = (evt) => {
            try {
                const data = JSON.parse(evt.data);
                appendLog("Received: " + JSON.stringify(data));

                if (data.type === "user-list" && Array.isArray(data.payload)) {
                    setRemoteUsers(data.payload);
                }
            } catch (e) {
                appendLog("Received raw: " + evt.data);
            }
        };

        ws.onclose = (ev) => {
            setState("closed");
            appendLog(`Closed (code=${ev.code})`);
        };

        ws.onerror = () => {
            setState("error");
            appendLog("WebSocket error");
        };
    };

    const disconnect = () => {
        wsRef.current?.close();
        wsRef.current = null;
        setState("closed");
        appendLog("Disconnected manually");
    };

    const sendOffer = () => {
        const msg = {
            type: "offer",
            from: "me",
            to: "target",
            payload: "SDP_PLACEHOLDER"
        };

        wsRef.current?.send(JSON.stringify(msg));
        appendLog("Sent offer (test)");
    };

    return (
        <div className="stream-container">
            <header className="stream-header">
                <h1>Stream Page</h1>
                <div className="ws-controls">
                    <button onClick={connect} disabled={state === "connecting" || state === "open"}>
                        Connect
                    </button>
                    <button onClick={disconnect} disabled={state !== "open"}>
                        Disconnect
                    </button>
                    <button onClick={sendOffer} disabled={state !== "open"}>
                        Send offer (test)
                    </button>
                </div>
            </header>

            <section className="stream-main">
                <div className="stream-left">
                    <div className="status">
                        <strong>WebSocket:</strong>{" "}
                        <span className={`badge ${state}`}>{state}</span>
                    </div>

                    <div className="remote-list">
                        <h3>Remote users</h3>
                        {remoteUsers.length === 0 ? (
                            <p>No connected users</p>
                        ) : (
                            <ul>
                                {remoteUsers.map((u) => (
                                    <li key={u}>{u}</li>
                                ))}
                            </ul>
                        )}
                    </div>
                </div>

                <div className="stream-right">
                    <h3>Log</h3>
                    <div className="log">
                        {log.map((line, i) => (
                            <div key={i} className="log-line">
                                {line}
                            </div>
                        ))}
                        <div ref={logEndRef} />
                    </div>
                </div>
            </section>

            <footer className="stream-footer">
                <p>Streaming will be added later — currently testing WebSocket/signaling.</p>
            </footer>
        </div>
    );
};

export default StreamPage;
