# main_window.py
import customtkinter as ctk
import json
from popup import Popup
from websocket_client import WebSocketClient

class MainWindow(ctk.CTkToplevel):
    def __init__(self, parent, user_info, auth_client):
        super().__init__(parent)

        self.title("Main Window")
        self.geometry("500x360")
        self.auth = auth_client
        self.user_info = user_info
        self.ws_client = None

        ctk.CTkLabel(self, text="Authenticated User:", font=ctk.CTkFont(size=20)).pack(pady=12)

        info_text = "\n".join([
            f"Username: {user_info.get('username')}",
            f"Roles: {', '.join(user_info.get('roles', []))}",
            f"Created At: {user_info.get('createdAt')}"
        ])

        ctk.CTkLabel(self, text=info_text, justify="left").pack(pady=6)

        # Status
        self.status_var = ctk.StringVar(value="DISCONNECTED")
        ctk.CTkLabel(self, text="WS status:", anchor="w").pack(pady=(10,0))
        self.status_label = ctk.CTkLabel(self, textvariable=self.status_var, font=ctk.CTkFont(size=16, weight="bold"))
        self.status_label.pack(pady=4)

        # Buttons
        frame = ctk.CTkFrame(self)
        frame.pack(pady=12)

        self.connect_btn = ctk.CTkButton(frame, text="Connect", width=120, command=self.connect_ws)
        self.connect_btn.grid(row=0, column=0, padx=8, pady=8)

        self.disconnect_btn = ctk.CTkButton(frame, text="Disconnect", width=120, command=self.disconnect_ws)
        self.disconnect_btn.grid(row=0, column=1, padx=8, pady=8)

        self.send_ping_btn = ctk.CTkButton(self, text="Send ping", width=120, command=self.send_ping)
        self.send_ping_btn.pack(pady=6)

        # For receiving messages (simple listbox-like)
        self.log_box = ctk.CTkTextbox(self, width=460, height=110)
        self.log_box.pack(pady=8)
        self.log_box.insert("0.0", "Messages will appear here...\n")

        self.grab_set()
        self.focus_force()

    def _set_status(self, s: str):
        self.status_var.set(s)

    def _append_log(self, text: str):
        self.log_box.insert("end", text + "\n")
        # auto scroll
        self.log_box.see("end")

    def connect_ws(self):
        try:
            token = self.auth.get_access_token()
            if not token:
                Popup.error(self, "Brak tokena dostępu. Zaloguj się ponownie.")
                return

            # Build ws URL: adapt if your path or port differ
            # if backend uses e.g. /ws/signaling  -> ws://host:port/ws/signaling
            # use wss for TLS
            base = self.auth.base_url.replace("https://", "").replace("http://", "")
            # base could be host:port/api -> strip /api
            if base.endswith("/api"):
                base = base[:-4]
            host = base
            ws_path = "/ws"
            ws_url = f"wss://{host}{ws_path}"

            cookie_header = self.auth.get_cookie_header()
            # TLS verify based on session setting
            verify_tls = getattr(self.auth.session, "verify", True)

            # create client
            self.ws_client = WebSocketClient(
                url=ws_url,
                cookie_header=cookie_header,
                on_message=self._on_ws_message,
                on_status=self._on_ws_status,
                verify_tls=verify_tls
            )

            self._append_log(f"Connecting to {ws_url} ...")
            self.ws_client.connect()
        except Exception as e:
            Popup.error(self, f"Connect error: {e}")

    def disconnect_ws(self):
        try:
            if not self.ws_client:
                self._append_log("Not connected")
                return
            self.ws_client.disconnect()
            self.ws_client = None
            self._set_status("DISCONNECTED")
            self._append_log("Disconnected")
        except Exception as e:
            Popup.error(self, f"Disconnect error: {e}")

    def _on_ws_message(self, message: str):
        # Runs from background thread -> schedule insert into UI mainloop
        def cb():
            try:
                # pretty print JSON if possible
                try:
                    j = json.loads(message)
                    pretty = json.dumps(j, indent=2, ensure_ascii=False)
                    self._append_log(pretty)
                except Exception:
                    self._append_log(message)
            except Exception:
                pass
        self.after(0, cb)

    def _on_ws_status(self, status: str):
        # schedule UI update on main thread
        def cb():
            self._set_status(status)
        self.after(0, cb)

    def send_ping(self):
        if not self.ws_client:
            Popup.error(self, "Not connected")
            return
        # backend expects SignalingMessage {type, from, to, payload}
        username = self.user_info.get("username")
        msg = {"type": "ping", "from": username, "to": username, "payload": "ping"}
        try:
            import json as _json
            self.ws_client.send(_json.dumps(msg))
            self._append_log("Sent ping")
        except Exception as e:
            Popup.error(self, f"Send error: {e}")
