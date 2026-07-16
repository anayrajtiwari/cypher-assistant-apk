"""
Cypher Speech-to-Text Engine
==============================
Uses faster-whisper for offline, on-device transcription.
"""

import os
import numpy as np
from typing import Optional

MODEL_SIZE = "tiny"
MODEL_DIR = os.path.expanduser("~/Projects/Assistant/data/whisper-models")

class STTEngine:
    def __init__(self, model_size: str = MODEL_SIZE):
        self.model_size = model_size
        self._model = None
        self._loaded = False

    def load(self):
        if self._loaded:
            return
        try:
            from faster_whisper import WhisperModel
            self._model = WhisperModel(
                self.model_size, device="cpu", compute_type="int8",
                download_root=MODEL_DIR
            )
            self._loaded = True
            print(f"🎙️ [STT] faster-whisper {self.model_size} loaded.")
        except ImportError:
            print("⚠️  Install faster-whisper: pip install faster-whisper")
            raise

    def transcribe(self, audio_data: bytes, language: str = "en") -> str:
        self.load()
        audio_np = (
            np.frombuffer(audio_data, dtype=np.int16).astype(np.float32) / 32768.0
        )
        segments, info = self._model.transcribe(
            audio_np, beam_size=5, language=language,
            vad_filter=True, vad_parameters=dict(
                threshold=0.5, min_speech_duration_ms=500,
                min_silence_duration_ms=500
            )
        )
        text = " ".join(seg.text for seg in segments).strip()
        return text

    def transcribe_file(self, wav_path: str, language: str = "en") -> str:
        self.load()
        segments, info = self._model.transcribe(
            wav_path, beam_size=5, language=language,
            vad_filter=True
        )
        return " ".join(seg.text for seg in segments).strip()
