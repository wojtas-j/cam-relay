import { Outlet, useNavigate } from "react-router-dom";
import Header from "./components/Header/Header";
import Footer from "./components/Footer/Footer";
import { useEffect } from "react";
import { useAuth } from "./context/AuthContext";

export default function App() {
    const { user, loading } = useAuth();
    const navigate = useNavigate();

    useEffect(() => {
        if (!loading) {
            if (user) navigate("/dashboard");
            else navigate("/login");
        }
    }, [user, loading, navigate]);

    return (
        <div className="app-container">
            <Header />
            <main className="app-content">
                <Outlet />
            </main>
            <Footer />
        </div>
    );
}
