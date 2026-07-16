"""
Cypher (Cy) Mobile Daemon Service
=================================
Runs in parallel with Android 16 OS as a background daemon.
Modules:
- Standby Voice Listener ("zed_one_eight" / "zee_one_eight")
- Telephony Interceptor (Announce caller -> Voice Pick/Reject -> Call Recording -> Mid-Call "Invoke" Assistant)
- ReAct Agent & Permission Gateway Execution
"""

import os
import sys
import time
import json
from cypher.android_bridge import AndroidCapabilities
from cypher.call_manager import TelephonyManagerAndroid
from cypher.wake_word_engine import WakeWordDetector

class CypherMobileDaemon:
    def __init__(self):
        self.android = AndroidCapabilities()
        self.telephony = TelephonyManagerAndroid(tts_func=self.android.tts_speak)
        self.detector = WakeWordDetector(on_wake_callback=self.on_wake_up)
        self.in_standby = True

    def on_wake_up(self):
        self.in_standby = False
        self.android.tts_speak("At your service, Boss. How can I assist you?")

    def handle_incoming_call_event(self, caller_name: str, voice_command: str, record_first: bool = False):
        """Processes incoming call workflow as requested by Boss."""
        if record_first:
            self.telephony.start_call_recording(caller_id=caller_name)

        # 1. Announce caller overriding ringtone
        self.telephony.announce_incoming_call(caller_name)

        # 2. Process Boss's voice decision
        cmd = voice_command.strip().lower()
        if "yes" in cmd or "answer" in cmd or "pick" in cmd:
            self.telephony.answer_call()
        elif "no" in cmd or "disconnect" in cmd or "reject" in cmd:
            self.telephony.disconnect_call()

    def handle_in_call_command(self, phrase: str):
        """Processes live during an active phone call."""
        p = phrase.strip().lower()
        if "disconnect the call" in p or "end call" in p:
            return self.telephony.disconnect_call()
        elif "record conversation" in p or "record call" in p:
            return self.telephony.start_call_recording()
        elif "invoke" in p:
            return self.telephony.invoke_mid_call_assistant()
        return None

    def start_background_daemon(self):
        print("🤖 [Cy Mobile Daemon] Starting parallel OS background daemon...")
        self.detector.start_listening_loop()

if __name__ == "__main__":
    daemon = CypherMobileDaemon()
    daemon.start_background_daemon()
