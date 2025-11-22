import React, { createContext, useContext, useEffect, useState } from "react";
import { getCurrentUser, logout, refreshToken } from "../api/auth";

interface User {
    username: string;
    roles: string[];
}

interface AuthContextType {
    user: User | null;
    loading: boolean;
    refreshUser: () => Promise<void>;
    logoutUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [user, setUser] = useState<User | null>(null);
    const [loading, setLoading] = useState(true);

    const refreshUser = async () => {
        try {
            const data = await getCurrentUser();
            setUser(data);
        } catch {
            setUser(null);
        } finally {
            setLoading(false);
        }
    };

    const logoutUser = async () => {
        window.dispatchEvent(new Event("stop-stream"));

        await logout();

        setUser(null);
    };

    useEffect(() => {
        const initialize = async () => {
            try {
                await refreshToken();
            } catch {
            } finally {
                await refreshUser();
            }
        };

        initialize();

        const handleForceLogout = () => {
            window.dispatchEvent(new Event("stop-stream"));

            setUser(null);
            setLoading(false);
        };

        window.addEventListener("force-logout", handleForceLogout);
        return () => window.removeEventListener("force-logout", handleForceLogout);
    }, []);

    return (
        <AuthContext.Provider value={{ user, loading, refreshUser, logoutUser }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error("useAuth must be used within AuthProvider");
    return ctx;
};
