import "./Popup.css";
import * as React from "react";

interface PopupProps {
    message: string;
    onClose: () => void;
    type?: "success" | "error";
}

const Popup: React.FC<PopupProps> = ({ message, onClose, type }) => {
    const derivedType =
        type || (message.toLowerCase().includes("error") || message.toLowerCase().includes("invalid")
            ? "error"
            : "success");

    return (
        <div className="popup-overlay">
            <div className={`popup-box ${derivedType === "error" ? "popup-error" : "popup-success"}`}>
                <p>{message}</p>
                <button
                    className={derivedType === "error" ? "popup-btn popup-btn-error" : "popup-btn popup-btn-success"}
                    onClick={onClose}
                >
                    OK
                </button>
            </div>
        </div>
    );
};

export default Popup;
