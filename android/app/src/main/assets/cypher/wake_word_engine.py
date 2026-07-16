"""
Cypher (Cy) Offline Wake-Word Listener Engine
============================================
Listens continuously in low-power standby mode for target wake triggers:
- "zed_one_eight"
- "zee_one_eight"
- "zed 18" / "zee 18"
"""

import sys
import time
from typing import Callable, Optional

WAKE_WORDS = {
    "zed_one_eight",
    "zee_one_eight",
    "zed 18",
    "zee 18",
    "zed18",
    "zee18",
}

class WakeWordDetector:
    def __init__(self, on_wake_callback: Optional[Callable[[], None]] = None):
        self.on_wake_callback = on_wake_callback
        self.is_listening = False

    def is_wake_word(self, phrase: str) -> bool:
        cleaned = phrase.strip().lower().replace("-", "_")
        return any(ww in cleaned for ww in WAKE_WORDS)

    def start_listening_loop(self):
        """Low-power audio listening loop for wake word trigger."""
        self.is_listening = True
        print("🌙 [Cy Standby] Listening silently in background for wake word: 'zed_one_eight' / 'zee_one_eight'...")

    def trigger_wake(self, matched_phrase: str):
        print(f"\n⚡ [WAKE TRIGGER DETECTED] Wake phrase matched: '{matched_phrase}'!")
        print("🔊 Playing activation chime: *BEEP-BEEP*")
        if self.on_wake_callback:
            self.on_wake_callback()
