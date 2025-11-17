import React from "react";
import "./Popup.css";

interface LogsPopupProps {
    logs: string[];
    onClose: () => void;
    onClear: () => void;
}

const LogsPopup: React.FC<LogsPopupProps> = ({ logs, onClose, onClear }) => {
    return (
        <div className="popup-overlay">
            <div className="popup-box logs-popup">
                <h3 className="popup-title">Logs</h3>

                <div className="logs-scroll">
                    {logs.length === 0 ? (
                        <p className="logs-empty">No logs</p>
                    ) : (
                        logs.map((line, idx) => (
                            <div key={idx} className="logs-line">
                                {line}
                            </div>
                        ))
                    )}
                </div>

                <div className="popup-buttons">
                    <button className="cancel-btn" onClick={onClear}>Clear logs</button>
                    <button className="confirm-btn" onClick={onClose}>Close</button>
                </div>
            </div>
        </div>
    );
};

export default LogsPopup;
