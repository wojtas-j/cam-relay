import React, { useEffect, useState } from "react";
import { getCurrentUser } from "../../api/auth";
import { useNavigate } from "react-router-dom";
import Footer from "../../components/Footer/Footer";
import "./DashboardPage.css";

interface User {
    username: string;
    email?: string;
    roles?: string[];
}

const DashboardPage: React.FC = () => {
    const [user, setUser] = useState<User | null>(null);
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

    return (
        <div className="dashboard-container">
            <div className="dashboard-box">
                <h1>Dashboard</h1>

                {user ? (
                    <div className="user-info">
                        <p><strong>Username:</strong> {user.username}</p>
                        {user.email && <p><strong>Email:</strong> {user.email}</p>}
                        {user.roles && (
                            <p><strong>Roles:</strong> {user.roles.join(", ")}</p>
                        )}
                    </div>
                ) : (
                    <p>Loading user data...</p>
                )}
            </div>

            <Footer />
        </div>
    );
};

export default DashboardPage;
