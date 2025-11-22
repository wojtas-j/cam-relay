import { Outlet} from "react-router-dom";
import Header from "./components/Header/Header";
import Footer from "./components/Footer/Footer";

export default function App() {
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
