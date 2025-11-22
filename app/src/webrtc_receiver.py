# webrtc_receiver.py
import asyncio
import threading
import traceback
import numpy as np
import cv2
import os
from typing import Callable, Optional, Dict, Any
from aiortc import RTCPeerConnection, RTCSessionDescription, RTCIceServer, RTCConfiguration
from aiortc.contrib.media import MediaBlackhole
from dotenv import load_dotenv

from virtual_cam import VirtualCamera
from vbcable_player import VBCablePlayer
import logging
logger = logging.getLogger(__name__)

load_dotenv()

API_HOST = os.getenv("PUBLIC_IP")
API_PORT = os.getenv("SPRING_PORT")
FRONTEND_PORT = os.getenv("FRONTEND_PORT")
TURN_PORT = os.getenv("TURN_PORT")
TURN_USERNAME = os.getenv("TURN_USERNAME")
TURN_PASSWORD = os.getenv("TURN_PASSWORD")
STUN_URL = os.getenv("STUN_URL")


class WebRTCReceiver:
    """
    Minimal WebRTC receiver.
    - Video -> virtual camera + preview
    - Audio -> compute RMS for UI slider (audio_level)
    - Audio -> optionally forward to VB-Cable (CABLE Input) in realtime
    """

    def __init__(self, on_stream_start: Callable[[], None],
                 on_stream_stop: Callable[[], None],
                 virtual_cam_device: Optional[str] = None,
                 vbc_device_name: str = "CABLE Input",
                 vbc_enabled: bool = True):
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

        # audio level for slider (0..1)
        self.audio_level = 0.0

        # VB-Cable player (forwards to "CABLE Input")
        # You can disable forwarding by setting vbc_enabled=False
        self._vbc_enabled = vbc_enabled
        self._vbc_player: Optional[VBCablePlayer] = None
        if self._vbc_enabled:
            try:
                # frames_per_buffer default 960 matches typical frame samples (per your logs)
                self._vbc_player = VBCablePlayer(device_name_substr=vbc_device_name,
                                                 samplerate=48000, channels=2, dtype="int16",
                                                 frames_per_buffer=960)
            except Exception as e:
                logger.error("[WEBRTC RECIEVER] Initialization error: %s", e)
                self._vbc_player = None

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
            except Exception as e:
                logger.error("[WEBRTC RECIEVER - HANDLE OFFER]: %s", e)
                pass
            self._pc = None

        pc = RTCPeerConnection(
            RTCConfiguration(
                iceServers=[
                    RTCIceServer(
                        urls=[f"turn:{API_HOST}:{TURN_PORT}?transport=udp"],
                        username=TURN_USERNAME,
                        credential=TURN_PASSWORD),
                    RTCIceServer(
                        urls=[f"turn:{API_HOST}:{TURN_PORT}?transport=tcp"],
                        username=TURN_USERNAME,
                        credential=TURN_PASSWORD),
                    RTCIceServer(
                        urls=[STUN_URL])
                ]
            )
        )

        self._pc = pc

        @pc.on("track")
        def on_track(track):
            logger.info("[WEBRTC RECIEVER] Track received")
            if track.kind == "video":
                if not self.has_stream:
                    self.has_stream = True
                    try:
                        self.on_stream_start()
                    except Exception as e:
                        logger.error("[WEBRTC RECIEVER - ON TRACK]: %s", e)
                        pass
                asyncio.ensure_future(self._recv_video(track), loop=self._loop)

            elif track.kind == "audio":
                logger.info("[AUDIO] Track received")
                asyncio.ensure_future(self._recv_audio(track), loop=self._loop)

        try:
            desc = RTCSessionDescription(
                sdp=offer.get("sdp", ""),
                type=offer.get("type", "offer")
            )
            await pc.setRemoteDescription(desc)

            for cand in self._pending_candidates:
                try:
                    await pc.addIceCandidate(cand)
                except Exception as e:
                    pass
            self._pending_candidates.clear()

            answer = await pc.createAnswer()
            await pc.setLocalDescription(answer)

            answer_obj = {
                "type": pc.localDescription.type,
                "sdp": pc.localDescription.sdp
            }
            send_answer_callback(answer_obj)

        except Exception as e:
            logger.error("[WEBRTC RECEIVER - HANDLE OFER]: %s", e)
            traceback.print_exc()

    async def _recv_video(self, track):

        try:
            while True:
                frame = await track.recv()
                img = frame.to_ndarray(format="bgr24")

                target_h = 720
                h, w = img.shape[:2]
                scale = target_h / h
                new_w = int(w * scale)
                img = cv2.resize(img, (new_w, target_h))

                with self._frame_lock:
                    self.latest_frame = img

                if not self._virtual_cam_started:
                    self._virtual_cam.start(width=new_w, height=target_h, fps=self._virtual_fps)
                    self._virtual_cam_started = True

                self._virtual_cam.send_frame(img)

        except Exception as e:
            logger.error("[WEBRTC RECIEVER - VIDEO]: %s", e)
            pass
        finally:
            self._cleanup_after_stream_stop()

    async def _recv_audio(self, track):
        """
        Receives audio frames from WebRTC:
         - logs first few frames
         - forwards raw bytes to VB-Cable player in realtime (if enabled)
         - updates self.audio_level for UI slider
        """
        first_frames_logged = False
        frame_count_for_log = 0

        # Start VB-Cable
        if self._vbc_enabled and self._vbc_player is not None:
            try:
                if not self._vbc_player.is_running():
                    self._vbc_player.start()
                    logger.info("[VBCABLE] Player started")
            except Exception as e:
                logger.error("[AUDIO] VB-Cable start error: %s", e)

        try:
            while True:
                frame = await track.recv()
                arr = frame.to_ndarray()
                if not first_frames_logged and frame_count_for_log < 5:
                    frame_count_for_log += 1
                    logger.info(
                        "\n=== AUDIO FRAME #%s ===\n"
                        "   shape: %s dtype: %s range: %s...%s",
                        frame_count_for_log,
                        arr.shape,
                        arr.dtype,
                        arr.min(),
                        arr.max(),
                    )

                    if frame_count_for_log == 5:
                        first_frames_logged = True

                # Konwersja do int16 / interleaved
                try:
                    s = np.asarray(arr)

                    # planar: (channels, frames) → (frames, channels)
                    if s.ndim == 2 and s.shape[0] in (1, 2, 4) and s.shape[0] <= s.shape[1]:
                        s = s.T

                    # flatten jeśli 1-kanałowy
                    if s.ndim == 1:
                        s = s.reshape(-1, 1)

                    # float → int16
                    if s.dtype.kind == 'f':
                        s = np.clip(s, -1.0, 1.0)
                        s = (s * 32767.0).astype(np.int16)
                    else:
                        if s.dtype != np.int16:
                            max_val = np.iinfo(s.dtype).max
                            s = (s.astype(np.float32) / max_val * 32767.0).astype(np.int16)

                    interleaved = s.reshape(-1).tobytes()

                except Exception as e:
                    logger.error("[AUDIO] Conversion failed: %s", e)
                    interleaved = arr.astype(np.int16).reshape(-1).tobytes()

                # Wysyłanie do VB-Cable
                if self._vbc_enabled and self._vbc_player is not None:
                    try:
                        self._vbc_player.write(interleaved)
                    except Exception as e:
                        logger.error("[VBCABLE] Write error: %s", e)

                # RMS
                try:
                    s16 = np.frombuffer(interleaved, dtype=np.int16)
                    if s16.size % 2 == 0:
                        s16 = s16.reshape(-1, 2)
                    else:
                        s16 = s16.reshape(-1, 1)

                    f = s16.astype(np.float32) / 32768.0
                    rms = np.sqrt(np.mean(f * f))

                    self.audio_level = max(0.0, min(1.0, rms * 4.2))
                except Exception as e:
                    logger.error("[AUDIO] RMS: %s", e)
                    self.audio_level = 0.0

        except Exception as e:
            logger.error("[AUDIO] Stream ended: %s", e)

        finally:
            # Stop VB-Cable
            if self._vbc_player is not None:
                try:
                    self._vbc_player.stop()
                    logger.info("[VBCABLE] Player stopped")
                except Exception as e:
                    logger.error("[AUDIO] VB-Cable stop error: %s", e)

            self.audio_level = 0.0
            self._cleanup_after_stream_stop()

    def _cleanup_after_stream_stop(self):
        if self.has_stream:
            self.has_stream = False
            try:
                self.on_stream_stop()
            except Exception as e:
                logger.error("[WEBRTC RECIEVER - CLEANUP AFTER STREAM STOP]: %s", e)
                pass

        self.stop_preview()
        self.stop_virtual_camera()

    def add_candidate(self, candidate: Dict[str, Any]):
        if not candidate or "candidate" not in candidate:
            return

        if not self._pc:
            self._pending_candidates.append(candidate)
            return

        try:
            asyncio.run_coroutine_threadsafe(
                self._pc.addIceCandidate(candidate), self._loop
            )
        except Exception as e:
            logger.error("[WEBRTC RECIEVER - ADD CANDIDATE]: %s", e)
            traceback.print_exc()

    def start_preview(self):
        if self._preview_thread and self._preview_thread.is_alive():
            return

        self._preview_running.set()
        self._preview_thread = threading.Thread(
            target=self._preview_loop, daemon=True
        )
        self._preview_thread.start()

    def stop_preview(self):
        if self._preview_thread and self._preview_thread.is_alive():
            self._preview_running.clear()
            self._preview_thread.join(timeout=1)

        try:
            cv2.destroyAllWindows()
        except Exception as e:
            logger.error("[WEBRTC RECIEVER - STOP PREVIEW]: %s", e)
            pass

        self._preview_thread = None

    def _preview_loop(self):
        cv2.namedWindow("Stream Preview", cv2.WINDOW_NORMAL)

        while self._preview_running.is_set():

            try:
                visible = cv2.getWindowProperty("Stream Preview", cv2.WND_PROP_VISIBLE)
                if visible < 1:
                    self._preview_running.clear()
                    break
            except cv2.error:
                self._preview_running.clear()
                break

            with self._frame_lock:
                frame = None if self.latest_frame is None else self.latest_frame.copy()

            if frame is None:
                black = np.zeros((360, 640, 3), dtype=np.uint8)
                cv2.putText(black, "No frame...", (20, 180),
                            cv2.FONT_HERSHEY_SIMPLEX, 1.0, (255, 255, 255), 2)
                cv2.imshow("Stream Preview", black)
            else:
                cv2.imshow("Stream Preview", frame)

            if cv2.waitKey(1) & 0xFF == ord('q'):
                self._preview_running.clear()
                break

        try:
            cv2.destroyWindow("Stream Preview")
        except Exception as e:
            logger.error("[WEBRTC RECIEVER - PREVIEW LOOP]: %s", e)
            pass

    def stop_virtual_camera(self):
        try:
            if self._virtual_cam_started:
                self._virtual_cam.stop()
        except Exception as e:
            logger.error("[WEBRTC RECIEVER - STOP VIRTUAL CAMERA]: %s", e)
            pass
        self._virtual_cam_started = False

    def stop_stream(self):
        self.stop_preview()
        self.stop_virtual_camera()

        if self._pc:
            try:
                asyncio.run_coroutine_threadsafe(self._pc.close(), self._loop).result(timeout=2)
            except Exception as e:
                logger.error("[WEBRTC RECIEVER - STOP STREAM]: %s", e)
                pass
            self._pc = None

        # Stop VB-Cable player if still running
        if self._vbc_player is not None:
            try:
                self._vbc_player.stop()
            except Exception as e:
                logger.error("[WEBRTC RECIEVER - STOP STREAM]: %s", e)
                pass

        self.latest_frame = None

        if self.has_stream:
            self.has_stream = False
            try:
                self.on_stream_stop()
            except Exception as e:
                logger.error("[WEBRTC RECIEVER - STOP STREAM]: %s", e)
                pass

    def close(self):
        try:
            self.stop_stream()
        except Exception:
            pass

        try:
            def _stop():
                self._loop.stop()

            self._loop.call_soon_threadsafe(_stop)
        except Exception as e:
            logger.error("[WEBRTC RECIEVER - CLOSE]: %s", e)
            pass

        if self._thread and self._thread.is_alive():
            try:
                self._thread.join(timeout=2)
            except Exception as e:
                logger.error("[WEBRTC RECIEVER - CLOSE THREAD]: %s", e)
                pass

