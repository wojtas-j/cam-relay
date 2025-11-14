import customtkinter as ctk
from popup import Popup

class LoginWindow(ctk.CTkToplevel):
    def __init__(self, parent, auth_client, on_success):
        super().__init__(parent)

        self.auth = auth_client
        self.on_success = on_success
        self.parent = parent

        self.title("Login")
        self.geometry("420x320")
        self.resizable(False, False)

        self.center()

        # GUI
        ctk.CTkLabel(
            self,
            text="Sign in",
            font=ctk.CTkFont(size=22, weight="bold")
        ).pack(pady=20)

        self.username = ctk.CTkEntry(self, placeholder_text="Username", width=240)
        self.username.pack(pady=10)

        self.password = ctk.CTkEntry(self, placeholder_text="Password", show="*", width=240)
        self.password.pack(pady=10)

        self.show_btn = ctk.CTkButton(self, text="👁 Show", width=90, command=self.toggle_pass)
        self.show_btn.pack()

        ctk.CTkButton(self, text="Login", width=160, command=self.try_login).pack(pady=25)

        # Block background & focus
        self.grab_set()
        self.focus_force()

    def center(self):
        self.update_idletasks()
        w, h = 420, 320
        sw = self.winfo_screenwidth()
        sh = self.winfo_screenheight()
        x = (sw - w) // 2
        y = (sh - h) // 2
        self.geometry(f"{w}x{h}+{x}+{y}")

    def toggle_pass(self):
        if self.password.cget("show") == "*":
            self.password.configure(show="")
            self.show_btn.configure(text="🙈 Hide")
        else:
            self.password.configure(show="*")
            self.show_btn.configure(text="👁 Show")

    def try_login(self):
        try:
            self.auth.login(self.username.get(), self.password.get())
            user = self.auth.get_current_user()

            if "RECEIVER" not in user.get("roles", []):
                Popup.error(self, "Access denied (RECEIVER role required)")
                return

            # close window -> then run success callback
            self.destroy()
            self.on_success(user)

        except Exception as e:
            Popup.error(self, str(e))
