import os
import sys
import logging
from dotenv import load_dotenv
from pathlib import Path
logger = logging.getLogger(__name__)

def load_env():
    if getattr(sys, "frozen", False):
        base_dir = Path(sys.executable).parent
    else:
        base_dir = Path(__file__).resolve().parents[2]

    env_path = base_dir / ".env.production"

    if env_path.exists():
        load_dotenv(env_path)
    else:
        logging.info("[WARN] .env not found at: %s", env_path)

# ---- IMPORT VALUES AFTER load_env ----
load_env()

API_HOST = os.getenv("PUBLIC_IP")
API_PORT = os.getenv("SPRING_PORT")
FRONTEND_PORT = os.getenv("FRONTEND_PORT")
TURN_PORT = os.getenv("TURN_PORT")
TURN_USERNAME = os.getenv("TURN_USERNAME")
TURN_PASSWORD = os.getenv("TURN_PASSWORD")
STUN_URL = os.getenv("STUN_URL")

