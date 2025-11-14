import requests
import urllib3

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


class AuthClient:
    def __init__(self, base_url="https://localhost:8080/api"):
        self.session = requests.Session()
        self.base_url = base_url
        self.session.verify = False  # for self-signed certs

    def login(self, username, password):
        url = f"{self.base_url}/auth/login"
        resp = self.session.post(url, json={"username": username, "password": password})

        if resp.status_code != 200:
            try:
                msg = resp.json().get("message", "Login failed")
            except Exception:
                msg = "Login failed"
            raise Exception(msg)

        # backend returns empty body → just return True
        return True

    def get_current_user(self):
        url = f"{self.base_url}/auth/me"
        resp = self.session.get(url)

        if resp.status_code != 200:
            raise Exception("Cannot fetch user")

        return resp.json()
