"""
Cypher Audio Capture Engine
============================
Captures 16-bit PCM mono 16kHz audio via Termux or PyAudio.
"""

import os
import sys
import time
import struct
import threading
import subprocess
import wave
import tempfile
from typing import Optional, Callable

SAMPLE_RATE = 16000
CHANNELS = 1
SAMPLE_WIDTH = 2

class AudioCapture:
    def __init__(self):
        self._running = False
        self._process = None
        self._listeners: list[Callable[[bytes], None]] = []

    def start_stream(self) -> bool:
        self._running = True
        self._pipe_path = "/data/data/com.termux/files/usr/tmp/cy_audio.pcm"
        try:
            os.mkfifo(self._pipe_path)
        except FileExistsError:
            pass
        self._process = subprocess.Popen(
            ["termux-microphone-record", "-f", self._pipe_path,
             "-r", str(SAMPLE_RATE), "-c", "1", "-b", "16",
             "-l", "0", "-e", "pcm"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )

        def read_loop():
            with open(self._pipe_path, "rb") as f:
                while self._running:
                    chunk = f.read(4096)
                    if not chunk:
                        break
                    for cb in self._listeners:
                        try:
                            cb(chunk)
                        except Exception:
                            pass

        threading.Thread(target=read_loop, daemon=True).start()
        print("🎤 [AudioCapture] Stream started.")
        return True

    def stop_stream(self):
        self._running = False
        if self._process:
            self._process.terminate()
            self._process = None

    def add_listener(self, callback: Callable[[bytes], None]):
        self._listeners.append(callback)

    def remove_listener(self, callback: Callable[[bytes], None]):
        if callback in self._listeners:
            self._listeners.remove(callback)

    def is_active(self) -> bool:
        return self._running

    @staticmethod
    def record_clip(duration: float = 5.0) -> Optional[bytes]:
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
            path = tmp.name
        subprocess.run(
            ["termux-microphone-record", "-f", path,
             "-r", str(SAMPLE_RATE), "-c", "1", "-b", "16",
             "-d", str(int(duration * 1000)), "-e", "wav"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )
        with open(path, "rb") as f:
            data = f.read()
        os.unlink(path)
        return data

    @staticmethod
    def save_wav(data: bytes, filepath: str):
        with wave.open(filepath, "wb") as wf:
            wf.setnchannels(CHANNELS)
            wf.setsampwidth(SAMPLE_WIDTH)
            wf.setframerate(SAMPLE_RATE)
            wf.writeframes(data)
