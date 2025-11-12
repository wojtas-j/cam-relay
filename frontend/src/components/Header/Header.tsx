import "./Header.css";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export default function Header() {
    const { user, logoutUser } = useAuth();
    const navigate = useNavigate();
    const [opacity, setOpacity] = useState(0.8);

    useEffect(() => {
        const handleScroll = () => setOpacity(window.scrollY > 20 ? 0.25 : 0.8);
        window.addEventListener("scroll", handleScroll);
        return () => window.removeEventListener("scroll", handleScroll);
    }, []);

    const handleLogout = async () => {
        await logoutUser();
        navigate("/login");
    };

    return (
        <header className="header" style={{ background: `rgba(20,20,20,${opacity})` }}>
            <div className="header-left">
                <button onClick={() => navigate("/dashboard")} className="header-btn">
                    Dashboard
                </button>
                {user?.roles.includes("ADMIN") && (
                    <button onClick={() => navigate("/create-user")} className="header-btn admin-btn">
                        Create User
                    </button>
                )}
            </div>

            <div className="header-right">
                {user && (
                    <button onClick={handleLogout} className="logout-btn">
                        Logout
                    </button>
                )}
            </div>
        </header>
    );
}
