import React, { useState } from "react";
import "./LoginForm.css";
import Popup from "../Popup/Popup";
import { Eye, EyeOff } from "lucide-react";

interface LoginFormProps {
    onSuccess: (u: string, p: string) => void;
}

export default function LoginForm({ onSuccess }: LoginFormProps) {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
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
            {error && <Popup message={error} onClose={() => setError(null)} type="error" />}

            <div className="login-box">
                <h2 className="login-title">Sign in</h2>

                <form onSubmit={handleSubmit}>
                    <input
                        type="text"
                        placeholder="Login"
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

                    <button type="submit">Login</button>
                </form>
            </div>
        </>
    );
}
