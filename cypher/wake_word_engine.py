"""
Cypher Offline Wake-Word Engine
================================
Listens continuously for wake phrases via VAD + Whisper Tiny.
Wake triggers: "zed_one_eight", "zee_one_eight", "zed 18", "zee 18"
"""

import os
import sys
import time
import queue
import threading
import numpy as np
from typing import Callable, Optional

SAMPLE_RATE = 16000
FRAME_MS = 30
FRAME_SIZE = int(SAMPLE_RATE * FRAME_MS / 1000)

WAKE_WORDS = {
    "zed one eight", "zee one eight",
    "zed 18", "zee 18", "zed18", "zee18",
    "zed_one_eight", "zee_one_eight",
}

class WakeWordDetector:
    def __init__(self, on_wake_callback: Optional[Callable[[], None]] = None,
                 model_size: str = "tiny"):
        self.on_wake_callback = on_wake_callback
        self.model_size = model_size
        self._vad = None
        self._whisper_model = None
        self._running = False
        self._audio_queue: queue.Queue[bytes] = queue.Queue()
        self._speech_buffer = bytearray()
        self._is_speech = False
        self._silence_frames = 0
        self._speech_frames = 0
        self._loaded = False

    def load(self):
        try:
            import webrtcvad
            self._vad = webrtcvad.Vad(2)
        except ImportError:
            print("⚠️  webrtcvad not available. Install: pip install webrtcvad")
            raise

        try:
            from faster_whisper import WhisperModel
            model_path = os.path.expanduser(
                f"~/Projects/Assistant/data/whisper-{self.model_size}"
            )
            self._whisper_model = WhisperModel(
                self.model_size, device="cpu", compute_type="int8",
                download_root=model_path
            )
        except ImportError:
            print("⚠️  faster-whisper not available. Install: pip install faster-whisper")
            raise

        self._loaded = True
        print(f"🌙 [WakeWord] Loaded (VAD + Whisper {self.model_size}).")

    def _feed_audio_to_vad(self, chunk: bytes):
        if not self._loaded:
            return
        self._audio_queue.put(chunk)

    def _process_loop(self):
        while self._running:
            try:
                chunk = self._audio_queue.get(timeout=0.5)
            except queue.Empty:
                if self._is_speech:
                    self._silence_frames += 1
                    if self._silence_frames > 15:
                        self._flush_speech_buffer()
                continue

            if len(chunk) < FRAME_SIZE:
                continue

            is_speech = self._vad.is_speech(chunk[:FRAME_SIZE], SAMPLE_RATE)

            if is_speech:
                self._speech_buffer.extend(chunk[:FRAME_SIZE])
                self._speech_frames += 1
                self._silence_frames = 0
                if not self._is_speech:
                    self._is_speech = True
            elif self._is_speech:
                self._speech_buffer.extend(chunk[:FRAME_SIZE])
                self._silence_frames += 1
                if self._silence_frames > 15:
                    self._flush_speech_buffer()
            else:
                leftover = len(self._speech_buffer)
                if leftover > 0 and leftover < FRAME_SIZE * 5:
                    self._speech_buffer.clear()

    def _flush_speech_buffer(self):
        if len(self._speech_buffer) < FRAME_SIZE * 10:
            self._speech_buffer.clear()
            self._is_speech = False
            self._silence_frames = 0
            self._speech_frames = 0
            return

        audio_data = bytes(self._speech_buffer)
        self._speech_buffer.clear()
        self._is_speech = False
        self._silence_frames = 0
        self._speech_frames = 0

        self._check_wake_word(audio_data)

    def _check_wake_word(self, audio_data: bytes):
        try:
            audio_np = np.frombuffer(audio_data, dtype=np.int16).astype(np.float32) / 32768.0
            segments, _ = self._whisper_model.transcribe(
                audio_np, beam_size=1, language="en",
                vad_filter=False
            )
            text = " ".join(seg.text for seg in segments).lower().strip()
            if not text:
                return

            for ww in WAKE_WORDS:
                if ww in text:
                    print(f"\n⚡ [WAKE] '{ww}' detected in: \"{text}\"")
                    if self.on_wake_callback:
                        self.on_wake_callback()
                    return
        except Exception as e:
            pass

    def start_listening(self, audio_capture):
        if not self._loaded:
            self.load()
        self._running = True
        audio_capture.add_listener(self._feed_audio_to_vad)
        thread = threading.Thread(target=self._process_loop, daemon=True)
        thread.start()
        print("🌙 [Cy Standby] Listening for wake word...")

    def stop_listening(self, audio_capture):
        self._running = False
        audio_capture.remove_listener(self._feed_audio_to_vad)
        print("🌙 [Cy Standby] Wake word listener stopped.")
