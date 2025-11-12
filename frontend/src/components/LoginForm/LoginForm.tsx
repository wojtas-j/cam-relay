import React, { useState } from "react";
import "./LoginForm.css";
import Popup from "../Popup/Popup";

interface LoginFormProps {
    onSuccess: (u: string, p: string) => void;
}

export default function LoginForm({ onSuccess }: LoginFormProps) {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState<string | null>(null);

    const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
        e.preventDefault();

        try {
            onSuccess(username, password);
        } catch {
            setError("Login failed");
        }
    };

    return (
        <>
            {error && <Popup message={error} onClose={() => setError(null)} />}

            <div className="login-box">
                <h2 className="login-title">Sign in</h2>

                <form onSubmit={handleSubmit}>
                    <input
                        type="text"
                        placeholder="Login"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                    />

                    <input
                        type="password"
                        placeholder="Password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                    />

                    <button type="submit">Login</button>
                </form>
            </div>
        </>
    );
}
