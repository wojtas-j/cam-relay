# vbcable_player.py
import sounddevice as sd
import threading
import numpy as np
from typing import Optional

class VBCablePlayer:
    def __init__(self,
                 device_name_substr: str = "CABLE Input",
                 samplerate: int = 48000,
                 channels: int = 2,
                 dtype: str = "int16",
                 frames_per_buffer: int = 960): # W razie potrzeby zmienić na 1920 -> większe opóźnienie, mniej glitchy potencjalnie
        self.device_name_substr = device_name_substr
        self.samplerate = samplerate
        self.channels = channels
        self.dtype = dtype
        self.frames_per_buffer = frames_per_buffer

        self._stream: Optional[sd.RawOutputStream] = None
        self._buf = bytearray()
        self._lock = threading.Lock()
        self._running = False
        self._stop_event = threading.Event()
        self._device = self._find_device_by_name(device_name_substr)
        self._underflow_count = 0

    def _find_device_by_name(self, name_substr: str):
        """
        Search for devices lists.
        """
        try:
            devices = sd.query_devices()
            name_substr_lower = name_substr.lower()
            for idx, dev in enumerate(devices):
                if name_substr_lower in dev['name'].lower() and dev['max_output_channels'] >= self.channels:
                    return idx
        except Exception as e:
            print(f"[VBCABLE PLAYER ERROR - FIND DEVICE] {e}")
            pass
        return None

    def start(self):
        if self._running:
            return
        self._stop_event.clear()
        self._underflow_count = 0

        def callback(outdata, frames, time_info, status):
            # outdata: memoryview / buffer to be filled with raw bytes
            requested_bytes = frames * self.channels * np.dtype(self.dtype).itemsize
            with self._lock:
                if len(self._buf) >= requested_bytes:
                    # Enough data: copy and remove
                    outdata[:] = bytes(self._buf[:requested_bytes])
                    del self._buf[:requested_bytes]
                else:
                    # Underflow: fill what we have then zeros
                    have = len(self._buf)
                    if have > 0:
                        outdata[:have] = bytes(self._buf)
                        del self._buf[:have]
                    # rest zeros
                    outdata[have:requested_bytes] = b'\x00' * (requested_bytes - have)
                    self._underflow_count += 1

        try:
            self._stream = sd.RawOutputStream(
                samplerate=self.samplerate,
                channels=self.channels,
                dtype=self.dtype,
                blocksize=self.frames_per_buffer,
                callback=callback,
                device=self._device,
                latency='low'
            )
            self._stream.start()
            self._running = True
        except Exception as e:
            print(f"[VBCABLE PLAYER ERROR - START] {e}")
            raise RuntimeError(f"Cannot start VBCablePlayer stream (device '{self.device_name_substr}'): {e}")

    def write(self, pcm_bytes: bytes):
        """
        Add raw PCM (int16, interleaved) to interial buffer.
        """
        if not pcm_bytes:
            return
        with self._lock:
            self._buf.extend(pcm_bytes)

    def stop(self):
        if not self._running:
            return
        self._stop_event.set()
        try:
            if self._stream:
                try:
                    self._stream.stop()
                except Exception as e:
                    print(f"[VBCABLE PLAYER ERROR - STOP] {e}")
                    pass
                try:
                    self._stream.close()
                except Exception as e:
                    print(f"[VBCABLE PLAYER ERROR - STOP] {e}")
                    pass
        finally:
            self._stream = None
            with self._lock:
                self._buf.clear()
            self._running = False

    def is_running(self) -> bool:
        return self._running
