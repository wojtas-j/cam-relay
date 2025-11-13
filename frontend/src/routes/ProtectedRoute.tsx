import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import type {JSX} from "react";

export const ProtectedRoute = ({
                                   children,
                                   roles,
                               }: {
    children: JSX.Element;
    roles?: string[];
}) => {
    const { user, loading } = useAuth();

    if (loading) return <div>Loading...</div>;
    if (!user) return <Navigate to="/login" replace />;

    if (roles && !roles.some((r) => user.roles.includes(r))) {
        return <Navigate to="/dashboard" replace />;
    }

    return children;
};
