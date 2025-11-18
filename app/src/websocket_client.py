# websocket_client.py
import ssl
import threading
import time
import traceback
from typing import Callable, Optional

try:
    import websocket  # websocket-client
except Exception as e:
    raise RuntimeError("Missing dependency 'websocket-client'. Install with: pip install websocket-client") from e

class WebSocketClient:
    """
    Lightweight WebSocket client using websocket-client.WebSocketApp.
    - Pass cookie header from AuthClient.get_cookie_header()
    - on_status: callable(status_str)
    - on_message: callable(message_str)
    """

    def __init__(self, url: str, cookie_header: str, on_message: Callable[[str], None], on_status: Optional[Callable[[str], None]] = None, verify_tls: bool = True):
        self.url = url
        self.cookie_header = cookie_header
        self.on_message = on_message
        self.on_status = on_status or (lambda s: None)
        self.verify_tls = verify_tls

        self._ws_app = None
        self._thread = None
        self._closed = threading.Event()

    def _log_status(self, msg: str):
        try:
            self.on_status(msg)
        except Exception:
            pass

    def _on_open(self, ws):
        self._log_status("CONNECTED")

    def _on_message(self, ws, message):
        try:
            self.on_message(message)
        except Exception:
            traceback.print_exc()

    def _on_close(self, ws, close_status_code, close_msg):
        self._log_status(f"CLOSED ({close_status_code})")

    def _on_error(self, ws, error):
        self._log_status(f"ERROR: {error}")

    def connect(self):
        if self._thread and self._thread.is_alive():
            self._log_status("ALREADY CONNECTED")
            return

        headers = []
        if self.cookie_header:
            headers.append(f"Cookie: {self.cookie_header}")

        self._ws_app = websocket.WebSocketApp(
            self.url,
            header=headers,
            on_open=self._on_open,
            on_message=self._on_message,
            on_close=self._on_close,
            on_error=self._on_error,
        )

        def run():
            self._closed.clear()
            # sslopt: by default None -> system cert verification
            sslopt = {}
            if not self.verify_tls:
                sslopt = {"cert_reqs": ssl.CERT_NONE}
            try:
                # run_forever blocks until closed
                self._ws_app.run_forever(sslopt=sslopt)
            except Exception as e:
                self._log_status(f"RUN ERROR: {e}")
            finally:
                self._closed.set()

        self._thread = threading.Thread(target=run, daemon=True)
        self._thread.start()

        # Wait small time for connection attempt to settle
        time.sleep(0.1)
        self._log_status("CONNECTING")

    def disconnect(self):
        if self._ws_app:
            try:
                self._ws_app.close()
            except Exception:
                pass
        # wait a little for clean close
        if self._thread:
            self._thread.join(timeout=2)
        self._log_status("DISCONNECTED")

    def send(self, text: str):
        if self._ws_app and self._ws_app.sock and self._ws_app.sock.connected:
            self._ws_app.send(text)
        else:
            raise RuntimeError("WebSocket is not connected")
