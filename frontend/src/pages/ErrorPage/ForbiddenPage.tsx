import "../ErrorPage/ErrorPage.css";
import { Link } from "react-router-dom";

export default function ForbiddenPage() {
    return (
        <div className="error-container">
            <h1>403 – Forbidden</h1>
            <p>You do not have permission to access this page.</p>

            <Link to="/dashboard" className="back-btn">
                Go back to Dashboard
            </Link>
        </div>
    );
}
