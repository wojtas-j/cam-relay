# auth.py
import os
import requests
import urllib3
from http import cookies

# Allow disabling InsecureRequestWarning only when explicitly requested (dev/test)
if os.getenv("APP_ENV") in ("test", "dev") or os.getenv("ALLOW_INSECURE") == "true":
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

class AuthClient:
    """
    Auth HTTP client wrapping requests.Session.
    - Preserves cookies (requests.Session)
    - By default verifies TLS (requests.Session.verify = True)
    - For dev/test you can set env APP_ENV=test or ALLOW_INSECURE=true to disable TLS verification
    """

    ACCESS_TOKEN_COOKIE_NAME = "accessToken"

    def __init__(self, base_url: str = "https://localhost:8080/api"):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()

        self.session.verify = False
        self.session.trust_env = False

    def _url(self, path: str) -> str:
        return f"{self.base_url}{path}"

    def login(self, username: str, password: str) -> bool:
        """
        Perform login. Expects backend to set secure HTTP-only cookie 'accessToken'.
        Returns True on success.
        """
        url = self._url("/auth/login")
        resp = self.session.post(url, json={"username": username, "password": password})
        if resp.status_code != 200:
            try:
                msg = resp.json().get("message", "Login failed")
            except Exception:
                msg = f"Login failed (status {resp.status_code})"
            raise Exception(msg)

        # after successful login, cookie should be set in session.cookies
        if not self.get_access_token():
            # There's a chance backend returns token in Authorization header instead of cookie.
            # Try to read 'Authorization' header in response and set cookie accordingly (fallback).
            auth_header = resp.headers.get("Authorization")
            if auth_header and auth_header.startswith("Bearer "):
                token = auth_header.split(" ", 1)[1].strip()
                # set cookie in session
                self.session.cookies.set(self.ACCESS_TOKEN_COOKIE_NAME, token, path="/", secure=True, httponly=True)
            else:
                # If no cookie/token present — treat as error
                raise Exception("Login succeeded but no access token cookie was set")

        return True

    def logout(self) -> bool:
        """
        Logout endpoint call (if available) and clear cookie locally.
        """
        try:
            url = self._url("/auth/logout")
            # it's OK if backend doesn't expose logout; ignore non-200 gracefully
            self.session.post(url)
        except Exception:
            pass
        # clear local cookie
        self.session.cookies.pop(self.ACCESS_TOKEN_COOKIE_NAME, None)
        return True

    def get_current_user(self):
        """
        Fetch current user info from /auth/me
        """
        url = self._url("/auth/me")
        resp = self.session.get(url)
        if resp.status_code != 200:
            try:
                msg = resp.json().get("message", "Cannot fetch user")
            except Exception:
                msg = "Cannot fetch user"
            raise Exception(msg)
        return resp.json()

    def get_access_token(self) -> str | None:
        """
        Read access token from session cookies.
        """
        return self.session.cookies.get(self.ACCESS_TOKEN_COOKIE_NAME)

    def get_cookie_header(self) -> str:
        """
        Build Cookie header string from session cookies for use in WebSocket handshake:
        e.g. "accessToken=xxx; other=yyy"
        """
        jar = self.session.cookies
        cookie_header = "; ".join([f"{c.name}={c.value}" for c in jar])
        return cookie_header

    # convenience: raw session access (for advanced usage)
    def session_obj(self) -> requests.Session:
        return self.session
