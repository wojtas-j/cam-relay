import React, { useEffect, useState } from "react";
import { getCurrentUser, deleteAccount } from "../../api/auth";
import { useNavigate } from "react-router-dom";
import Footer from "../../components/Footer/Footer";
import ConfirmPopup from "../../components/Popup/ConfirmPopup";
import "./DashboardPage.css";

interface User {
    username: string;
    roles?: string[];
}

const DashboardPage: React.FC = () => {
    const [user, setUser] = useState<User | null>(null);
    const [showPopup, setShowPopup] = useState(false);
    const navigate = useNavigate();

    useEffect(() => {
        const fetchUser = async () => {
            try {
                const data = await getCurrentUser();
                setUser(data);
            } catch (err: any) {
                const status = err.response?.status;
                if (status === 401) navigate("/unauthorized");
                else if (status === 403) navigate("/forbidden");
                else navigate("/not-found");
            }
        };
        fetchUser();
    }, [navigate]);

    const handleDelete = async () => {
        try {
            await deleteAccount();
            window.location.href = "/login";
        } catch (err) {
            console.error("Error deleting account", err);
        }
    };

    if (!user) return <p>Loading user data...</p>;

    return (
        <div className="dashboard-container">
            <div className="dashboard-box">
                <h1>Dashboard</h1>

                <p className="username">USER: {user.username}</p>

                <button className="delete-btn" onClick={() => setShowPopup(true)}>
                    Delete Account
                </button>
            </div>

            {showPopup && (
                <ConfirmPopup
                    message="Are you sure you want to delete your account?"
                    onConfirm={handleDelete}
                    onCancel={() => setShowPopup(false)}
                />
            )}

            <Footer />
        </div>
    );
};

export default DashboardPage;
