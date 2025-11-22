# app.py
import argparse
import customtkinter as ctk
import sys
import logging
from auth import AuthClient
from login_window import LoginWindow
from main_window import MainWindow
from logger import setup_logging
from docker_manager import run_docker_compose_up, run_docker_compose_down, register_shutdown_hook
from popup import Popup


def start_app():
    # -----------------------------
    # Parse CLI arguments
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
    root.bind("<<APP_EXIT>>", lambda e: on_close_root())
    root.withdraw()
    root.title("Cam Relay")
    root.geometry("600x400")

    # -----------------------------
    # GLOBAL CLOSE HANDLER (docker down)
    # -----------------------------
    def on_close_root():
        logging.info("Application closing... Running docker-compose down.")
        try:
            run_docker_compose_down()
        except Exception as e:
            logging.error("Error while stopping Docker: %s", e)
        finally:
            root.destroy()

    root.protocol("WM_DELETE_WINDOW", on_close_root)

    # -----------------------------
    # Start Docker on app launch
    # -----------------------------
    try:
        ok = run_docker_compose_up()
        if not ok:
            Popup.error(root, "Failed to start Docker! Check docker.log.")
        else:
            logging.info("Docker started successfully.")
    except Exception as e:
        logging.error("Error starting Docker: %s", e)
        Popup.error(root, "Docker error: " + str(e))

    # -----------------------------
    # Initialize Auth
    # -----------------------------
    try:
        auth = AuthClient()
        logging.info("AuthClient initialized")
    except Exception as e:
        logging.error("Failed to initialize AuthClient: %s", e)
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
    # Start event loop
    # -----------------------------
    try:
        root.mainloop()
    except Exception as e:
        logging.error("Unhandled error in GUI loop: %s", e)
        raise


if __name__ == "__main__":
    start_app()
