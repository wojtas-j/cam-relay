# app.py
import argparse
import customtkinter as ctk
from auth import AuthClient
from login_window import LoginWindow
from main_window import MainWindow
from logger import setup_logging
import logging
import sys


def start_app():
    # -----------------------------
    # Parse CLI arguments first
    # -----------------------------
    parser = argparse.ArgumentParser(description="Cam Relay GUI Application")
    parser.add_argument("--debug", action="store_true", help="Enable debug mode")
    args = parser.parse_args()

    # -----------------------------
    # Initialize logging
    # -----------------------------
    setup_logging(debug=args.debug)
    logging.info("Starting GUI app...")

    # -----------------------------
    # Configure CustomTkinter
    # -----------------------------
    ctk.set_appearance_mode("dark")
    ctk.set_default_color_theme("blue")

    # -----------------------------
    # Create root window
    # -----------------------------
    root = ctk.CTk()
    root.withdraw()  # login window opens first
    root.title("Cam Relay")
    root.geometry("600x400")

    # -----------------------------
    # Auth client initialization
    # -----------------------------
    try:
        auth = AuthClient()
        logging.info("AuthClient initialized")
    except Exception as e:
        logging.exception("Failed to initialize AuthClient")
        sys.exit(1)

    # -----------------------------
    # Login callback
    # -----------------------------
    def on_login_success(user):
        username = user.get("username", "unknown")
        logging.info("User logged in: %s", username)
        MainWindow(root, user, auth)

    # -----------------------------
    # Start login window
    # -----------------------------
    LoginWindow(root, auth, on_login_success)

    # -----------------------------
    # Handle app closing
    # -----------------------------
    def on_close():
        logging.info("App closed by user")
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", on_close)

    # -----------------------------
    # Start event loop
    # -----------------------------
    try:
        root.mainloop()
    except Exception:
        logging.exception("Unhandled error in GUI loop")
        raise


if __name__ == "__main__":
    start_app()
