import React, { useState } from "react";
import "./CreateUserForm.css";
import Popup from "../Popup/Popup";
import { createUser } from "../../api/auth";
import { Eye, EyeOff } from "lucide-react";

export default function CreateUserForm() {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [mainRole, setMainRole] = useState<string>("USER");
    const [receiver, setReceiver] = useState(false);
    const [popupMessage, setPopupMessage] = useState<string | null>(null);
    const [popupType, setPopupType] = useState<"success" | "error">("success");

    const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
        e.preventDefault();

        const roles = [mainRole, ...(receiver ? ["RECEIVER"] : [])];

        if (!username || !password) {
            setPopupMessage("Please fill in all fields.");
            setPopupType("error");
            return;
        }

        try {
            await createUser(username, password, roles);
            setPopupMessage(`User "${username}" created successfully.`);
            setPopupType("success");
            setUsername("");
            setPassword("");
            setReceiver(false);
            setMainRole("USER");
        } catch (err: any) {
            console.error("❌ Error caught in CreateUserForm:", err);

            let msg = err?.message || "Error creating user.";

            if (err?.data && typeof err.data === "object" && !err.data.detail) {
                msg = Object.values(err.data).flat().join("; ") || msg;
            }

            msg = msg
                .replace(/\/api\/\S+\s*/g, "")
                .replace(/\s*\/problems\/\S+.*/g, "")
                .replace(/Validation Error/i, "")
                .replace(/Authentication Failed/i, "")
                .split(/[;]+/)
                .map((part: string) => part.trim())
                .filter(Boolean)
                .map((part: string) => "• " + part)
                .join("\n");

            setPopupMessage(msg);
            setPopupType("error");
        }
    };

    return (
        <>
            {popupMessage && (
                <Popup
                    message={popupMessage}
                    onClose={() => setPopupMessage(null)}
                    type={popupType}
                />
            )}

            <div className="create-user-box">
                <h2 className="create-user-title">Create New User</h2>

                <form onSubmit={handleSubmit}>
                    <input
                        type="text"
                        placeholder="Username"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                    />

                    <div className="password-wrapper">
                        <input
                            type={showPassword ? "text" : "password"}
                            placeholder="Password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                        />
                        <span
                            className="password-toggle"
                            onClick={() => setShowPassword((prev) => !prev)}
                        >
                            {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                        </span>
                    </div>

                    <div className="role-selection">
                        <label>Main Role:</label>
                        <select
                            value={mainRole}
                            onChange={(e) => setMainRole(e.target.value)}
                        >
                            <option value="USER">USER</option>
                            <option value="ADMIN">ADMIN</option>
                        </select>

                        <label className="receiver-checkbox">
                            <input
                                type="checkbox"
                                checked={receiver}
                                onChange={(e) => setReceiver(e.target.checked)}
                            />
                            RECEIVER
                        </label>
                    </div>

                    <button type="submit">Create User</button>
                </form>
            </div>
        </>
    );
}
