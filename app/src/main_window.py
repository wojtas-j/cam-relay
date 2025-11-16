import json
import customtkinter as ctk
from popup import Popup
from websocket_client import WebSocketClient


class MainWindow(ctk.CTkToplevel):
    def __init__(self, parent, user_info, auth_client):
        super().__init__(parent)

        self.title("Main Window")
        self.geometry("900x600")
        self.auth = auth_client
        self.user_info = user_info
        self.ws_client = None

        self.status_var = ctk.StringVar(value="DISCONNECTED")
        self.connected_users_var = ctk.StringVar(value="No connected users")

        ctk.CTkLabel(self, text="WS Status:", font=ctk.CTkFont(size=16)).pack(pady=(20, 5))

        self.status_label = ctk.CTkLabel(
            self,
            textvariable=self.status_var,
            font=ctk.CTkFont(size=20, weight="bold")
        )
        self.status_label.pack(pady=5)

        ctk.CTkLabel(
            self,
            text="Connected Users:",
            font=ctk.CTkFont(size=18)
        ).pack(pady=(30, 5))

        self.connected_label = ctk.CTkLabel(
            self,
            textvariable=self.connected_users_var,
            font=ctk.CTkFont(size=24, weight="bold"),
            text_color="#00bfff"
        )
        self.connected_label.pack(pady=10)

        frame = ctk.CTkFrame(self)
        frame.pack(pady=25)

        self.connect_btn = ctk.CTkButton(frame, text="Connect", width=120, command=self.connect_ws)
        self.connect_btn.grid(row=0, column=0, padx=12, pady=8)

        self.disconnect_btn = ctk.CTkButton(frame, text="Disconnect", width=120, command=self.disconnect_ws)
        self.disconnect_btn.grid(row=0, column=1, padx=12, pady=8)

        self._update_buttons(False)

        self.user_list = []

        self.grab_set()
        self.focus_force()

    def _set_status(self, s: str):
        self.status_var.set(s)

    def _update_buttons(self, connected: bool):
        if connected:
            self.connect_btn.configure(state="disabled")
            self.disconnect_btn.configure(state="normal")
        else:
            self.connect_btn.configure(state="normal")
            self.disconnect_btn.configure(state="disabled")

    def connect_ws(self):
        try:
            token = self.auth.get_access_token()
            if not token:
                Popup.error(self, "No authorization token — log in again")
                return

            base = self.auth.base_url.replace("https://", "").replace("http://", "")
            if base.endswith("/api"):
                base = base[:-4]

            ws_url = f"wss://{base}/ws"
            cookie_header = self.auth.get_cookie_header()
            verify_tls = getattr(self.auth.session, "verify", True)

            self.ws_client = WebSocketClient(
                url=ws_url,
                cookie_header=cookie_header,
                on_message=self._on_ws_message,
                on_status=self._on_ws_status,
                verify_tls=verify_tls
            )

            self.ws_client.connect()
            self._update_buttons(True)

        except Exception as e:
            Popup.error(self, f"Connect error: {e}")
            self._update_buttons(False)

    def disconnect_ws(self):
        try:
            if self.ws_client:
                self.ws_client.disconnect()
                self.ws_client = None

            self._set_status("DISCONNECTED")
            self._update_buttons(False)
            self._clear_users()

        except Exception as e:
            Popup.error(self, f"Disconnect error: {e}")

    def _on_ws_message(self, message: str):
        try:
            data = json.loads(message)
        except:
            return

        if data.get("type") == "user-list":
            users = data.get("payload", [])
            self.after(0, lambda: self._update_user_list(users))

    def _on_ws_status(self, status: str):
        self.after(0, lambda: self._set_status(status))

        if status.upper() == "CONNECTED":
            self.after(0, lambda: self._update_buttons(True))
        else:
            self.after(0, lambda: self._update_buttons(False))
            self.after(0, self._clear_users)

    def _update_user_list(self, users: list[str]):
        self.user_list = users

        if not users:
            self.connected_users_var.set("No connected users")
        else:
            formatted = " • ".join(users)
            self.connected_users_var.set(formatted)

    def _clear_users(self):
        self.user_list = []
        self.connected_users_var.set("No connected users")
