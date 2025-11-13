import "./Footer.css";

export default function Footer() {
    return (
        <footer className="footer">
            <span>CamRelay © {new Date().getFullYear()} — Secure Messaging Platform</span>
        </footer>
    );
}
