import { createBrowserRouter, Navigate } from "react-router-dom";
import App from "../App";
import LoginPage from "../pages/LoginPage/LoginPage";
import DashboardPage from "../pages/DashboardPage/DashboardPage";
import CreateUserPage from "../pages/CreateUserPage/CreateUserPage";
import { ProtectedRoute } from "../routes/ProtectedRoute";
import NotFoundPage from "../pages/NotFoundPage/NotFoundPage.tsx";
import StreamPage from "../pages/StreamPage/StreamPage.tsx";
import UsersPage from "../pages/UserPage/UserPage.tsx";

export const router = createBrowserRouter([
    {
        path: "/login",
        element: <LoginPage />,
    },
    {
        path: "/",
        element: <App />,
        children: [
            {
                index: true,
                element: <Navigate to="/dashboard" replace />,
            },
            {
                path: "dashboard",
                element: (
                    <ProtectedRoute>
                        <DashboardPage />
                    </ProtectedRoute>
                ),
            },
            {
                path: "stream",
                element: (
                    <ProtectedRoute roles={["USER"]}>
                        <StreamPage />
                    </ProtectedRoute>
                ),
            },
            {
                path: "create-user",
                element: (
                    <ProtectedRoute roles={["ADMIN"]}>
                        <CreateUserPage />
                    </ProtectedRoute>
                ),
            },
            {
                path: "users",
                element: (
                    <ProtectedRoute roles={["ADMIN"]}>
                        <UsersPage />
                    </ProtectedRoute>
                ),
            },
            {
                path: "*",
                element: <NotFoundPage />,
            },
        ],
    },
]);
