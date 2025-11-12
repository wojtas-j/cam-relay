import { Outlet } from "react-router-dom";
import Header from "../components/Header/Header";
import { useAuth } from "../context/AuthContext";

export default function AppLayout() {
    const { user } = useAuth();

    return (
        <>
            {user && <Header />}
    <main style={{ paddingTop: user ? "70px" : "0" }}>
    <Outlet />
    </main>
    </>
);
}
