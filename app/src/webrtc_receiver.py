# webrtc_receiver.py
import asyncio
import threading
import traceback
from typing import Callable, Optional, Dict, Any
import numpy as np
import cv2

from aiortc import RTCPeerConnection, RTCSessionDescription, RTCIceServer, RTCConfiguration
from aiortc.contrib.media import MediaBlackhole

from virtual_cam import VirtualCamera

class WebRTCReceiver:
    """
    Real WebRTC receiver using aiortc.
    - Handles offer -> creates answer
    - Accepts ICE candidates (as dicts)
    - Receives media tracks (video & audio)
    - Stores latest video frame in self.latest_frame (numpy BGR) protected by a lock
    - Provides start_preview() / stop_preview() to open an OpenCV window that displays latest frames
    - Optionally publishes frames to a system virtual camera via VirtualCamera
    """

    def __init__(self, on_stream_start: Callable[[], None], on_stream_stop: Callable[[], None], virtual_cam_device: Optional[str] = None):
        self.on_stream_start = on_stream_start
        self.on_stream_stop = on_stream_stop

        self._loop = asyncio.new_event_loop()
        self._thread = threading.Thread(target=self._run_loop, daemon=True)
        self._thread.start()

        self._pc: Optional[RTCPeerConnection] = None
        self._pending_candidates: list[Dict[str, Any]] = []

        self.latest_frame: Optional[np.ndarray] = None
        self._frame_lock = threading.Lock()

        self._preview_thread: Optional[threading.Thread] = None
        self._preview_running = threading.Event()
        self.has_stream = False
        self._audio_sink = MediaBlackhole()

        # Virtual camera
        self._virtual_cam = VirtualCamera(device=virtual_cam_device)
        self._virtual_cam_started = False
        self._virtual_fps = 30

    def _run_loop(self):
        asyncio.set_event_loop(self._loop)
        self._loop.run_forever()

    def _run_coro(self, coro):
        return asyncio.run_coroutine_threadsafe(coro, self._loop)

    def receive_offer(self, offer: dict, send_answer_callback: Callable[[dict], None]):
        self._run_coro(self._handle_offer(offer, send_answer_callback))

    async def _handle_offer(self, offer: dict, send_answer_callback: Callable[[dict], None]):
        if self._pc:
            try:
                await self._pc.close()
            except Exception:
                pass
            self._pc = None

        pc = RTCPeerConnection(
            RTCConfiguration(
                iceServers=[
                    # TURN UDP first
                    RTCIceServer(urls=["turn:87.205.113.203:9001?transport=udp"], username="webrtc", credential="supersecretpassword"),
                    RTCIceServer(urls=["turn:87.205.113.203:9001?transport=tcp"], username="webrtc", credential="supersecretpassword"),
                    RTCIceServer(urls=["stun:stun.l.google.com:19302"])
                ]
            )
        )
        self._pc = pc

        @pc.on("track")
        def on_track(track):
            print(f"[WebRTCReceiver] got track: kind={track.kind}")
            if track.kind == "video":
                if not self.has_stream:
                    self.has_stream = True
                    try: self.on_stream_start()
                    except Exception: pass
                asyncio.ensure_future(self._recv_video(track), loop=self._loop)
            elif track.kind == "audio":
                asyncio.ensure_future(self._recv_audio(track), loop=self._loop)

        try:
            desc = RTCSessionDescription(sdp=offer.get("sdp", ""), type=offer.get("type", "offer"))
            await pc.setRemoteDescription(desc)
            for cand in self._pending_candidates:
                try: await pc.addIceCandidate(cand)
                except Exception: pass
            self._pending_candidates.clear()

            answer = await pc.createAnswer()
            await pc.setLocalDescription(answer)
            answer_obj = {"type": pc.localDescription.type, "sdp": pc.localDescription.sdp}
            try: send_answer_callback(answer_obj)
            except Exception: self._loop.call_soon_threadsafe(lambda: send_answer_callback(answer_obj))
        except Exception:
            traceback.print_exc()

    async def _recv_video(self, track):
        try:
            while True:
                frame = await track.recv()
                img = frame.to_ndarray(format="bgr24")

                # resize do 720p
                target_height = 720
                h, w = img.shape[:2]
                scale = target_height / h
                new_w = int(w * scale)
                img = cv2.resize(img, (new_w, target_height))

                with self._frame_lock:
                    self.latest_frame = img

                if not self._virtual_cam_started:
                    self._virtual_cam.start(width=new_w, height=target_height, fps=self._virtual_fps)
                    self._virtual_cam_started = True

                if self._virtual_cam_started:
                    self._virtual_cam.send_frame(img)
        except Exception:
            pass
        finally:
            self._cleanup_after_stream_stop()

    async def _recv_audio(self, track):
        try:
            while True:
                await track.recv()
        except Exception:
            pass

    def _cleanup_after_stream_stop(self):
        if self.has_stream:
            self.has_stream = False
            try: self.on_stream_stop()
            except Exception: pass
        self.stop_preview()
        self.stop_virtual_camera()

    def add_candidate(self, candidate: Dict[str, Any]):
        if not candidate or "candidate" not in candidate:
            return
        if not self._pc:
            self._pending_candidates.append(candidate)
            return
        try: asyncio.run_coroutine_threadsafe(self._pc.addIceCandidate(candidate), self._loop)
        except Exception: traceback.print_exc()

    def start_preview(self):
        if self._preview_thread and self._preview_thread.is_alive(): return
        self._preview_running.set()
        self._preview_thread = threading.Thread(target=self._preview_loop, daemon=True)
        self._preview_thread.start()

    def stop_preview(self):
        if self._preview_thread and self._preview_thread.is_alive():
            self._preview_running.clear()
            self._preview_thread.join(timeout=1)
        try: cv2.destroyAllWindows()
        except Exception: pass
        self._preview_thread = None

    def _preview_loop(self):
        cv2.namedWindow("Stream Preview", cv2.WINDOW_NORMAL)

        while self._preview_running.is_set():
            frame = None
            with self._frame_lock:
                if self.latest_frame is not None:
                    frame = self.latest_frame.copy()

            if frame is None:
                black = np.zeros((360, 640, 3), dtype=np.uint8)
                cv2.putText(black, "No frame yet...", (20, 180), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (255, 255, 255), 2)
                cv2.imshow("Stream Preview", black)
            else:
                cv2.imshow("Stream Preview", frame)

            key = cv2.waitKey(1) & 0xFF
            if key == ord('q'):
                self._preview_running.clear()
                break

        try:
            cv2.destroyWindow("Stream Preview")
        except Exception:
            pass

    def stop_virtual_camera(self):
        try:
            if self._virtual_cam_started:
                self._virtual_cam.stop()
        except Exception: pass
        self._virtual_cam_started = False

    def stop_stream(self):
        self.stop_preview()
        self.stop_virtual_camera()
        if self._pc:
            try: asyncio.run_coroutine_threadsafe(self._pc.close(), self._loop).result(timeout=2)
            except Exception: pass
            self._pc = None
        with self._frame_lock:
            self.latest_frame = None
        if self.has_stream:
            self.has_stream = False
            try: self.on_stream_stop()
            except Exception: pass

    def close(self):
        try: self.stop_stream()
        except Exception: pass
        try: self._loop.call_soon_threadsafe(self._loop.stop)
        except Exception: pass
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=2)
