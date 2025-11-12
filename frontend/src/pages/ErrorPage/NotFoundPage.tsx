import "../ErrorPage/ErrorPage.css";
import { Link } from "react-router-dom";

export default function NotFoundPage() {
    return (
        <div className="error-container">
            <h2>404 - Page Not Found</h2>
            <p>The page you are looking for does not exist.</p>

            <Link to="/dashboard" className="back-btn">
                Go back to Dashboard
            </Link>
        </div>
    );
}
