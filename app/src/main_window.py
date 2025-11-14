import customtkinter as ctk

class MainWindow(ctk.CTkToplevel):
    def __init__(self, parent, user_info):
        super().__init__(parent)

        self.title("Main Window")
        self.geometry("500x300")

        ctk.CTkLabel(self, text="Authenticated User:", font=ctk.CTkFont(size=20)).pack(pady=20)

        info_text = "\n".join([
            f"Username: {user_info.get('username')}",
            f"Roles: {', '.join(user_info.get('roles', []))}",
            f"Created At: {user_info.get('createdAt')}"
        ])

        ctk.CTkLabel(self, text=info_text, justify="left").pack(pady=10)

        self.grab_set()
        self.focus_force()
