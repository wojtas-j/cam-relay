import "./Header.css";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export default function Header() {
    const { user, logoutUser } = useAuth();
    const navigate = useNavigate();
    const [opacity, setOpacity] = useState(0.8);
    const [menuOpen, setMenuOpen] = useState(false);

    useEffect(() => {
        const handleScroll = () => setOpacity(window.scrollY > 20 ? 0.25 : 0.8);
        window.addEventListener("scroll", handleScroll);
        return () => window.removeEventListener("scroll", handleScroll);
    }, []);

    const handleLogout = async () => {
        await logoutUser();
        navigate("/login");
    };

    const handleNavigate = (path: string) => {
        navigate(path);
        setMenuOpen(false);
    };

    return (
        <header className="header" style={{ background: `rgba(36, 52, 71,${opacity})` }}>
            <div className="header-title" onClick={() => navigate("/dashboard")}>
                CamRelay
            </div>

            {/* Desktop buttons */}
            <div className="header-buttons">
                <button onClick={() => navigate("/dashboard")} className="header-btn">
                    Dashboard
                </button>

                {user?.roles.includes("ADMIN") && (
                    <button onClick={() => navigate("/create-user")} className="header-btn">
                        Create User
                    </button>
                )}

                {user?.roles.includes("ADMIN") && (
                    <button onClick={() => navigate("/users")} className="header-btn">
                        Users
                    </button>
                )}

                {user?.roles.includes("USER") && (
                    <button onClick={() => navigate("/stream")} className="header-btn">
                        Stream
                    </button>
                )}

                {user && (
                    <button onClick={handleLogout} className="logout-btn">
                        Logout
                    </button>
                )}
            </div>


            {/* Mobile menu button */}
            <button
                className="menu-toggle"
                onClick={() => setMenuOpen((prev) => !prev)}
            >
                ☰
            </button>

            {/* Mobile dropdown */}
            {menuOpen && (
                <div className="mobile-menu">
                    <button onClick={() => handleNavigate("/dashboard")}>Dashboard</button>
                    {user?.roles.includes("ADMIN") && (
                        <button onClick={() => handleNavigate("/create-user")}>Create User</button>
                    )}
                    {user?.roles.includes("ADMIN") && (
                        <button onClick={() => handleNavigate("/users")}>Users</button>
                    )}
                    {user?.roles.includes("USER") && (
                        <button onClick={() => handleNavigate("/stream")}>Stream</button>
                    )}
                    {user && <button onClick={handleLogout}>Logout</button>}
                </div>
            )}

        </header>
    );
}
