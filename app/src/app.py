#app.py
import customtkinter as ctk
from auth import AuthClient
from login_window import LoginWindow
from main_window import MainWindow

def start_app():
    ctk.set_appearance_mode("dark")
    ctk.set_default_color_theme("blue")

    root = ctk.CTk()
    root.withdraw()
    root.title("Cam Relay")
    root.geometry("600x400")

    auth = AuthClient()

    def on_login_success(user):
        MainWindow(root, user, auth)

    # Open login window
    LoginWindow(root, auth, on_login_success)

    # Proper close handler
    def on_close():
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", on_close)
    root.mainloop()


if __name__ == "__main__":
    start_app()
