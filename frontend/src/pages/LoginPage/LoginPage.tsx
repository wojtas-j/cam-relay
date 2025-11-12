import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import LoginForm from "../../components/LoginForm/LoginForm";
import { login } from "../../api/auth";
import Popup from "../../components/Popup/Popup";
import Footer from "../../components/Footer/Footer";

const LoginPage: React.FC = () => {
    const navigate = useNavigate();
    const [error, setError] = useState<string | null>(null);

    const handleLogin = async (username: string, password: string) => {
        try {
            await login(username, password);
            navigate("/dashboard");
        } catch (err: any) {
            setError("Invalid username or password.");
        }
    };

    return (
        <>
            {error && <Popup message={error} onClose={() => setError(null)} />}

            <div className="login-page">
                <LoginForm onSuccess={handleLogin} />
            </div>

            <Footer />
        </>
    );
};

export default LoginPage;
