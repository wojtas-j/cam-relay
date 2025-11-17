# webrtc_receiver.py
import asyncio
import threading
import traceback
from typing import Callable, Optional, Dict, Any
import numpy as np
import cv2

from aiortc import RTCPeerConnection, RTCSessionDescription
from aiortc.contrib.media import MediaBlackhole

class WebRTCReceiver:
    """
    Real WebRTC receiver using aiortc.
    - Handles offer -> creates answer
    - Accepts ICE candidates (as dicts)
    - Receives media tracks (video & audio)
    - Stores latest video frame in self.latest_frame (numpy BGR) protected by a lock
    - Provides start_preview() / stop_preview() to open an OpenCV window that displays latest frames
    """

    def __init__(self, on_stream_start: Callable[[], None], on_stream_stop: Callable[[], None]):
        # Callbacks invoked when stream starts/stops (synchronous)
        self.on_stream_start = on_stream_start
        self.on_stream_stop = on_stream_stop

        # asyncio loop running in background thread
        self._loop = asyncio.new_event_loop()
        self._thread = threading.Thread(target=self._run_loop, daemon=True)
        self._thread.start()

        # peer connection (only one at a time)
        self._pc: Optional[RTCPeerConnection] = None

        # pending ICE candidates received before PC is ready
        self._pending_candidates: list[Dict[str, Any]] = []

        # latest video frame (numpy BGR) and lock
        self.latest_frame: Optional[np.ndarray] = None
        self._frame_lock = threading.Lock()

        # preview thread / control
        self._preview_thread: Optional[threading.Thread] = None
        self._preview_running = threading.Event()

        # helper media sink for audio if we don't play it
        self._audio_sink = None

        self.has_stream = False

    # --------------------------
    # asyncio loop management
    # --------------------------
    def _run_loop(self):
        asyncio.set_event_loop(self._loop)
        self._loop.run_forever()

    def _run_coro(self, coro):
        """Schedule coroutine on the background loop and return future."""
        return asyncio.run_coroutine_threadsafe(coro, self._loop)

    # --------------------------
    # offer handling
    # --------------------------
    def receive_offer(self, offer: dict, send_answer_callback: Callable[[dict], None]):
        """
        Public synchronous entry: pass the parsed offer dict ({"type":..., "sdp":...})
        and a callback which will be called with the answer dict to be sent back via WebSocket.
        """
        # Schedule the async handler
        self._run_coro(self._handle_offer(offer, send_answer_callback))

    async def _handle_offer(self, offer: dict, send_answer_callback: Callable[[dict], None]):
        # Cleanup any previous peer
        if self._pc:
            try:
                await self._pc.close()
            except Exception:
                pass
            self._pc = None

        pc = RTCPeerConnection()
        self._pc = pc

        # In case audio arrives but we don't play it, sink it to blackhole
        self._audio_sink = MediaBlackhole()

        @pc.on("track")
        def on_track(track):
            try:
                if track.kind == "video":
                    # mark stream started (only once)
                    if not self.has_stream:
                        self.has_stream = True
                        try:
                            self.on_stream_start()
                        except Exception:
                            pass

                    # spawn background task to consume frames and store latest_frame
                    asyncio.ensure_future(self._recv_video(track), loop=self._loop)

                elif track.kind == "audio":
                    # we don't play audio in GUI; consume to avoid buildup
                    asyncio.ensure_future(self._recv_audio(track), loop=self._loop)

            except Exception:
                traceback.print_exc()

        # When this side generates ICE candidates (rare for a receiver behind NAT),
        # you might want to capture them and send back to the sender. We intentionally
        # do not auto-send candidates here because signaling send callback is
        # provided only for the answer in the current integration. If you need
        # Python-generated candidates to be sent back, extend the API to accept
        # a send_candidate callback from the caller.
        try:
            # set remote description (offer)
            desc = RTCSessionDescription(sdp=offer.get("sdp", ""), type=offer.get("type", "offer"))
            await pc.setRemoteDescription(desc)

            # process any pending candidates that arrived before PC creation
            if self._pending_candidates:
                for cand in self._pending_candidates:
                    try:
                        await pc.addIceCandidate(cand)
                    except Exception:
                        # ignore failures adding old candidates
                        pass
                self._pending_candidates.clear()

            # create answer
            answer = await pc.createAnswer()
            await pc.setLocalDescription(answer)

            # send answer back as dict
            answer_obj = {"type": pc.localDescription.type, "sdp": pc.localDescription.sdp}
            # send via provided callback (synchronous)
            try:
                send_answer_callback(answer_obj)
            except Exception:
                # as fallback, schedule callback on loop executor
                self._loop.call_soon_threadsafe(lambda: send_answer_callback(answer_obj))

        except Exception:
            traceback.print_exc()

    async def _recv_video(self, track):
        """
        Consumes frames from a VideoStreamTrack and stores the latest frame (BGR ndarray).
        """
        try:
            while True:
                frame = await track.recv()
                # convert to BGR numpy array
                img = frame.to_ndarray(format="bgr24")
                with self._frame_lock:
                    self.latest_frame = img
        except Exception:
            # track ended or connection closed
            pass
        finally:
            # When video track ends, stop preview if running and notify
            self._cleanup_after_stream_stop()

    async def _recv_audio(self, track):
        """
        Consume audio so it doesn't buffer. Use MediaBlackhole to discard.
        """
        try:
            while True:
                frame = await track.recv()
                # discard
        except Exception:
            pass

    def _cleanup_after_stream_stop(self):
        # Called when tracks end
        if self.has_stream:
            self.has_stream = False
            try:
                self.on_stream_stop()
            except Exception:
                pass
        # stop preview window if open
        self.stop_preview()

    # --------------------------
    # ICE candidates
    # --------------------------
    def add_candidate(self, candidate: Dict[str, Any]):
        """
        Public synchronous method to add ICE candidate dict (parsed JSON).
        Accepts candidate dict in format: {"candidate": "...", "sdpMid": "...", "sdpMLineIndex": ...}
        If peer connection is not yet created, store it in pending list.
        """
        if not candidate:
            return

        # ensure keys exist (simple validation)
        if not isinstance(candidate, dict) or "candidate" not in candidate:
            return

        if not self._pc:
            # store until PC ready
            self._pending_candidates.append(candidate)
            return

        # schedule addition on loop; aiortc accepts dict directly
        try:
            asyncio.run_coroutine_threadsafe(self._pc.addIceCandidate(candidate), self._loop)
        except Exception:
            traceback.print_exc()

    # --------------------------
    # preview controls
    # --------------------------
    def start_preview(self):
        """
        Start a preview window showing the latest frames. Non-blocking.
        If preview already running, no-op.
        """
        if self._preview_thread and self._preview_thread.is_alive():
            return
        self._preview_running.set()
        self._preview_thread = threading.Thread(target=self._preview_loop, daemon=True)
        self._preview_thread.start()

    def stop_preview(self):
        """
        Stop preview window if running.
        """
        if self._preview_thread and self._preview_thread.is_alive():
            self._preview_running.clear()
            # wait a little for thread to join (non-blocking)
            self._preview_thread.join(timeout=1)
        # close OpenCV windows safely
        try:
            cv2.destroyAllWindows()
        except Exception:
            pass
        self._preview_thread = None

    def _preview_loop(self):
        """
        Runs in separate thread, polls self.latest_frame and displays it.
        """
        cv2.namedWindow("Stream Preview", cv2.WINDOW_NORMAL)
        while self._preview_running.is_set():
            frame = None
            with self._frame_lock:
                if self.latest_frame is not None:
                    frame = self.latest_frame.copy()
            if frame is None:
                # display a black placeholder
                black = np.zeros((360, 640, 3), dtype=np.uint8)
                cv2.putText(black, "No frame yet...", (20, 180), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (255,255,255), 2)
                cv2.imshow("Stream Preview", black)
            else:
                cv2.imshow("Stream Preview", frame)
            key = cv2.waitKey(30) & 0xFF
            # allow closing preview with 'q'
            if key == ord('q'):
                self._preview_running.clear()
                break
        try:
            cv2.destroyWindow("Stream Preview")
        except Exception:
            pass

    # --------------------------
    # stop / cleanup
    # --------------------------
    def stop_stream(self):
        """
        Close peer connection, stop preview, and cleanup resources.
        """
        # stop preview
        self.stop_preview()

        if self._pc:
            future = asyncio.run_coroutine_threadsafe(self._pc.close(), self._loop)
            try:
                future.result(timeout=2)
            except Exception:
                pass
            self._pc = None

        # clear latest frame
        with self._frame_lock:
            self.latest_frame = None

        # mark stream stopped and call callback
        if self.has_stream:
            self.has_stream = False
            try:
                self.on_stream_stop()
            except Exception:
                pass

    def close(self):
        """
        Full shutdown: stop everything and stop event loop.
        """
        try:
            self.stop_stream()
        except Exception:
            pass

        # stop loop
        try:
            self._loop.call_soon_threadsafe(self._loop.stop)
        except Exception:
            pass
        # join thread
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=2)
