import { useAuth } from "../../context/AuthContext";
import { useNavigate } from "react-router-dom";
import "./NotFoundPage.css";

export default function NotFoundPage() {
    const { user } = useAuth();
    const navigate = useNavigate();

    const handleRedirect = () => {
        if (user) navigate("/dashboard");
        else navigate("/login");
    };

    return (
        <div className="notfound-container">
            <h1>404 - Page Not Found</h1>
            <p>The page you’re looking for doesn’t exist or has been moved.</p>
            <button onClick={handleRedirect} className="notfound-btn">
                Go {user ? "to Dashboard" : "to Login"}
            </button>
        </div>
    );
}
