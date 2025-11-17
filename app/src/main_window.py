# main_window.py
import json
import customtkinter as ctk
from popup import Popup
from websocket_client import WebSocketClient
from webrtc_receiver import WebRTCReceiver


class MainWindow(ctk.CTkToplevel):
    def __init__(self, parent, user_info, auth_client):
        super().__init__(parent)

        self.title("Main Window")
        self.geometry("900x600")

        self.auth = auth_client
        self.user_info = user_info
        self.ws_client = None

        # store last received payload
        self.last_received_payload = None

        # ----------------------
        # STATUS VARIABLES
        # ----------------------
        self.status_var = ctk.StringVar(value="DISCONNECTED")
        self.connected_users_var = ctk.StringVar(value="No connected users")
        self.stream_status_var = ctk.StringVar(value="STREAM INACTIVE")

        # ----------------------
        # WebRTC receiver (real)
        # ----------------------
        self.webrtc = WebRTCReceiver(
            on_stream_start=self._on_stream_start,
            on_stream_stop=self._on_stream_stop
        )

        # ----------------------
        # UI SETUP
        # ----------------------
        ctk.CTkLabel(self, text="WS Status:", font=ctk.CTkFont(size=16)).pack(pady=(20, 5))
        self.status_label = ctk.CTkLabel(
            self,
            textvariable=self.status_var,
            font=ctk.CTkFont(size=20, weight="bold")
        )
        self.status_label.pack(pady=5)

        ctk.CTkLabel(self, text="Connected Users:", font=ctk.CTkFont(size=18)).pack(pady=(30, 5))
        self.connected_label = ctk.CTkLabel(
            self,
            textvariable=self.connected_users_var,
            font=ctk.CTkFont(size=24, weight="bold"),
            text_color="#00bfff"
        )
        self.connected_label.pack(pady=10)

        # STREAM STATUS LABEL
        ctk.CTkLabel(self, text="Stream Status:", font=ctk.CTkFont(size=18)).pack(pady=(30, 5))
        self.stream_label = ctk.CTkLabel(
            self,
            textvariable=self.stream_status_var,
            font=ctk.CTkFont(size=26, weight="bold"),
            text_color="#aaaaaa"
        )
        self.stream_label.pack(pady=10)

        # BUTTONS FRAME
        frame = ctk.CTkFrame(self)
        frame.pack(padx=20, pady=25)

        # CONNECT BUTTON
        self.connect_btn = ctk.CTkButton(frame, text="Connect", width=120, command=self.connect_ws)
        self.connect_btn.grid(row=0, column=0, padx=12, pady=8)

        # DISCONNECT BUTTON
        self.disconnect_btn = ctk.CTkButton(frame, text="Disconnect", width=120, command=self.disconnect_ws)
        self.disconnect_btn.grid(row=0, column=1, padx=12, pady=8)

        # STREAM PREVIEW BUTTON (NOW IN SAME ROW)
        self.preview_btn = ctk.CTkButton(
            frame,
            text="Stream Preview",
            width=150,
            command=self._on_stream_preview_click
        )
        self.preview_btn.grid(row=0, column=2, padx=12, pady=8)
        self.preview_btn.configure(state="disabled")

        self._update_buttons(False)

        self.user_list = []
        self.grab_set()
        self.focus_force()

    # --------------------------
    # INTERNAL HELPERS
    # --------------------------

    def _set_status(self, s: str):
        self.status_var.set(s)

    def _update_buttons(self, connected: bool):
        if connected:
            self.connect_btn.configure(state="disabled")
            self.disconnect_btn.configure(state="normal")
        else:
            self.connect_btn.configure(state="normal")
            self.disconnect_btn.configure(state="disabled")

    # --------------------------
    # STREAM STATE HANDLING
    # --------------------------

    def _on_stream_start(self):
        self.stream_status_var.set("STREAM ACTIVE")
        self.stream_label.configure(text_color="#00ff00")
        self.preview_btn.configure(state="normal")

    def _on_stream_stop(self):
        self.stream_status_var.set("STREAM INACTIVE")
        self.stream_label.configure(text_color="#aaaaaa")
        self.preview_btn.configure(state="disabled")

    # --------------------------
    # STREAM PREVIEW BUTTON
    # --------------------------

    def _on_stream_preview_click(self):
        """
        Toggle preview window. If preview already running, stop it.
        Otherwise open preview window (reads frames produced by WebRTCReceiver).
        """
        if not self.webrtc.has_stream:
            Popup.info(self, "No active stream to preview")
            return

        # if preview already open -> stop it
        if getattr(self.webrtc, "_preview_running", None) and self.webrtc._preview_running.is_set():
            # stop preview
            self.webrtc.stop_preview()
            print("[Stream Preview] stopped by user")
            return

        # start preview thread/window
        self.webrtc.start_preview()
        print("[Stream Preview] started; showing frames in separate window")

    # --------------------------
    # CONNECT / DISCONNECT
    # --------------------------

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
            self.webrtc.stop_stream()

        except Exception as e:
            Popup.error(self, f"Disconnect error: {e}")

    # --------------------------
    # WS MESSAGE HANDLING
    # --------------------------

    def _on_ws_message(self, message: str):
        try:
            data = json.loads(message)
        except:
            return

        msg_type = data.get("type")

        # Capture payload for debug
        if "payload" in data:
            self.last_received_payload = data["payload"]

        if msg_type == "user-list":
            users = data.get("payload", [])
            self.after(0, lambda: self._update_user_list(users))

        elif msg_type == "offer":
            # payload is JSON-string; parse to object
            payload = json.loads(data.get("payload", "{}"))
            # pass offer to webrtc receiver; callback will send answer back via websocket
            self.webrtc.receive_offer(payload, lambda ans: self._send_answer(ans, data))

        elif msg_type == "candidate":
            payload = json.loads(data.get("payload", "{}"))
            self.webrtc.add_candidate(payload)

    def _send_answer(self, answer_obj, request):
        if not self.ws_client:
            return

        response = {
            "type": "answer",
            "from": self.user_info.get("username"),
            "to": request.get("from"),
            "payload": json.dumps(answer_obj)
        }
        self.ws_client.send(json.dumps(response))

    # --------------------------
    # WS STATUS HANDLING
    # --------------------------

    def _on_ws_status(self, status: str):
        self.after(0, lambda: self._set_status(status))

        if status.upper() == "CONNECTED":
            self.after(0, lambda: self._update_buttons(True))
        else:
            self.after(0, lambda: self._update_buttons(False))
            self.after(0, self._clear_users)
            self.webrtc.stop_stream()

    # --------------------------
    # USERS LIST
    # --------------------------

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
