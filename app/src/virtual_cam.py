#virtual_cam.py
import threading, queue, time, numpy as np
import pyvirtualcam
from pyvirtualcam import PixelFormat
import logging
logger = logging.getLogger(__name__)

class VirtualCamera:
    def __init__(self, device: str | None = None):
        self.device = device
        self._thread = None
        self._q = queue.Queue(maxsize=4)
        self._running = threading.Event()
        self._width = None
        self._height = None
        self._fps = None

    def start(self, width:int, height:int, fps:int=30):
        if self._thread and self._thread.is_alive(): return
        self._width = width
        self._height = height
        self._fps = fps
        self._running.set()
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self):
        self._running.clear()
        try:
            while not self._q.empty(): self._q.get_nowait()
        except Exception: pass
        if self._thread: self._thread.join(timeout=1)
        self._thread = None

    def send_frame(self, bgr_frame: np.ndarray):
        if not self._running.is_set(): return
        try: self._q.put_nowait(bgr_frame)
        except queue.Full:
            try: _ = self._q.get_nowait(); self._q.put_nowait(bgr_frame)
            except Exception: pass

    def _run(self):
        try:
            with pyvirtualcam.Camera(width=self._width, height=self._height, fps=self._fps, device=self.device, fmt=PixelFormat.RGB) as cam:
                frame_period = 1.0 / max(1,self._fps)
                last_send = 0.0
                while self._running.is_set():
                    try: frame = self._q.get(timeout=0.2)
                    except Exception:
                        now = time.time()
                        if now - last_send >= frame_period:
                            black = np.zeros((self._height,self._width,3),dtype=np.uint8)
                            cam.send(black[:,:,::-1])
                            cam.sleep_until_next_frame()
                            last_send = now
                        continue
                    if frame is None: continue
                    if frame.shape[0]!=self._height or frame.shape[1]!=self._width:
                        import cv2; frame = cv2.resize(frame,(self._width,self._height))
                    cam.send(frame[:,:,::-1])
                    cam.sleep_until_next_frame()
                    last_send = time.time()
        except Exception as e:
            logger.error("[VIRTUAL CAMERA ERROR] %s", e)
            return
