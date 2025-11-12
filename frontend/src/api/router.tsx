import { createBrowserRouter } from "react-router-dom";
import LoginPage from "../pages/LoginPage/LoginPage";
import DashboardPage from "../pages/DashboardPage/DashboardPage";
import ForbiddenPage from "../pages/ErrorPage/ForbiddenPage";
import UnauthorizedPage from "../pages/ErrorPage/UnauthorizedPage";
import NotFoundPage from "../pages/ErrorPage/NotFoundPage";

export const router = createBrowserRouter([
    { path: "/", element: <LoginPage /> },
    { path: "/dashboard", element: <DashboardPage /> },
    { path: "/forbidden", element: <ForbiddenPage /> },
    { path: "/unauthorized", element: <UnauthorizedPage /> },
    { path: "*", element: <NotFoundPage /> },
]);
