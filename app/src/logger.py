# logger.py
import logging
from pathlib import Path
from logging.handlers import RotatingFileHandler
from datetime import datetime
import sys

def setup_logging(debug=False):
    log_dir = Path("logs")
    log_dir.mkdir(exist_ok=True)

    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    log_file = log_dir / f"app_{timestamp}.log"

    # Create root logger
    logger = logging.getLogger()
    logger.setLevel(logging.DEBUG if debug else logging.INFO)

    # Remove any previously added handlers (important for PyInstaller)
    if logger.hasHandlers():
        logger.handlers.clear()

    # ------------------------
    # FILE HANDLER (always on)
    # ------------------------
    file_handler = RotatingFileHandler(
        log_file,
        maxBytes=5 * 1024 * 1024,  # 5 MB
        backupCount=5,
        encoding="utf-8"
    )
    file_handler.setFormatter(logging.Formatter(
        "%(asctime)s [%(levelname)s] %(name)s: %(message)s"
    ))
    logger.addHandler(file_handler)

    # ------------------------
    # CONSOLE (only in debug)
    # ------------------------
    running_as_exe = getattr(sys, "frozen", False)

    if debug or not running_as_exe:
        console = logging.StreamHandler()
        console.setFormatter(logging.Formatter(
            "%(asctime)s [%(levelname)s] %(name)s: %(message)s"
        ))
        logger.addHandler(console)

    logger.info("Logger initialized (debug=%s)", debug)
