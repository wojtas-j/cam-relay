# docker_manager.py
import subprocess
import logging
import os
import sys

# Szukamy pliku compose w typowych miejscach
POSSIBLE_LOCATIONS = [
    ".",
    os.path.dirname(sys.executable),                           # folder EXE
    os.path.abspath(os.path.join(os.getcwd(), "..")),          # 1 poziom wyżej
    os.path.abspath(os.path.join(os.getcwd(), "../..")),       # 2 poziomy wyżej (tu jest!)
]


def find_compose_file():
    for path in POSSIBLE_LOCATIONS:
        candidate = os.path.join(path, "docker-compose-prod.yaml")
        if os.path.exists(candidate):
            logging.info(f"Found docker-compose at: {candidate}")
            return candidate

    logging.error("docker-compose-prod.yaml NOT FOUND in search paths.")
    return None


def run_docker_compose_up():
    compose_file = find_compose_file()
    if compose_file is None:
        return False

    logging.info("Starting Docker Compose...")

    try:
        result = subprocess.run(
            ["docker-compose", "-f", compose_file, "up", "-d"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )

        if result.stdout:
            logging.info("Docker UP stdout:\n" + result.stdout)
        if result.stderr:
            logging.error("Docker UP stderr:\n" + result.stderr)

        return result.returncode == 0

    except Exception as e:
        logging.error(f"Error in run_docker_compose_up: {e}")
        return False


def run_docker_compose_down():
    compose_file = find_compose_file()
    if compose_file is None:
        return False

    logging.info("Stopping Docker Compose...")

    try:
        result = subprocess.run(
            ["docker-compose", "-f", compose_file, "down"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )

        if result.stdout:
            logging.info("Docker DOWN stdout:\n" + result.stdout)
        if result.stderr:
            logging.error("Docker DOWN stderr:\n" + result.stderr)

        return result.returncode == 0

    except Exception as e:
        logging.error(f"Error in run_docker_compose_down: {e}")
        return False


def register_shutdown_hook(root):
    def on_close():
        logging.info("Application closing → shutting down Docker...")
        run_docker_compose_down()
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", on_close)
