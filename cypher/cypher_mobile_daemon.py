"""
Cypher (Cy) Mobile Daemon Service
==================================
Background daemon integrating wake-word, STT, LLM inference,
telephony, and Android OS agentic control.
"""

import os
import sys
import time
import json
import signal
import threading

from cypher.audio_capture import AudioCapture
from cypher.wake_word_engine import WakeWordDetector
from cypher.stt_engine import STTEngine
from cypher.brain import CypherBrain, SYSTEM_PROMPT
from cypher.android_bridge import AndroidCapabilities
from cypher.call_manager import TelephonyManagerAndroid
from cypher.tools import build_tool_registry
from cypher.permissions import PermissionManager
from cypher.memory import MemoryManager


class CypherMobileDaemon:
    def __init__(self):
        self.android = AndroidCapabilities()
        self.telephony = TelephonyManagerAndroid(tts_func=self.android.tts_speak)
        self.audio = AudioCapture()
        self.stt = STTEngine()
        self.brain = CypherBrain()
        self.registry = build_tool_registry()
        self.permissions = PermissionManager()
        self.memory = MemoryManager()
        self.current_conv_id = self.memory.create_conversation("Daemon Session")
        self.conversation_history = []

        self.wake_word = WakeWordDetector(
            on_wake_callback=self._on_wake,
        )

        self._shutdown_flag = threading.Event()
        self._active = False
        self._listening_for_command = False

    def _on_wake(self):
        self.android.tts_speak("At your service, Boss.")
        self._wait_and_transcribe()

    def _wait_and_transcribe(self):
        self._active = True
        self.android.vibrate_device(200)
        audio_data = AudioCapture.record_clip(duration=7.0)
        if not audio_data or len(audio_data) < 8000:
            self._active = False
            return

        text = self.stt.transcribe(audio_data)
        print(f"📝 [STT] Boss said: \"{text}\"")
        if not text:
            self.android.tts_speak("I didn't catch that, Boss.")
            self._active = False
            return

        self._handle_command(text)

    def _handle_command(self, user_text: str):
        self.memory.save_message(self.current_conv_id, "user", user_text)
        self.conversation_history.append({"role": "user", "content": user_text})

        tools_schemas = self.registry.get_schemas()
        max_loops = 5

        for loop_count in range(max_loops):
            messages = [{"role": "system", "content": SYSTEM_PROMPT}]
            messages.extend(self.conversation_history[-6:])

            response = self.brain.generate(messages, tools=tools_schemas)

            tool_name, tool_args = self._parse_tool_call(response)

            if not tool_name:
                self.memory.save_message(self.current_conv_id, "assistant", response)
                self.conversation_history.append({"role": "assistant", "content": response})
                self.android.tts_speak(response)
                break

            print(f"⚙️  Tool: {tool_name}({tool_args})")
            allowed = self.permissions.check_permission(tool_name, tool_args)
            if not allowed:
                refusal = f"Boss declined '{tool_name}'."
                self.conversation_history.append({"role": "assistant", "content": response})
                self.conversation_history.append({"role": "user", "content": f"<tool_response>{refusal}</tool_response>"})
                continue

            tool_output = self.registry.execute(tool_name, tool_args)
            print(f"🔧 Result: {tool_output[:200]}")
            self.conversation_history.append({"role": "assistant", "content": response})
            self.conversation_history.append({"role": "user", "content": f"<tool_response>{tool_output}</tool_response>"})

            if loop_count == max_loops - 1:
                final = "I hit a loop limit, Boss. Anything else?"
                self.android.tts_speak(final)
                self.conversation_history.append({"role": "assistant", "content": final})

        self._active = False

    def _parse_tool_call(self, text: str) -> tuple:
        import re
        pattern = r"<tool_call>\s*(\{.*?\})\s*</tool_call>"
        match = re.search(pattern, text, re.DOTALL)
        if match:
            try:
                data = json.loads(match.group(1))
                return data.get("name"), data.get("arguments", {})
            except Exception:
                pass
        return None, None

    def handle_incoming_call_event(self, caller_name: str, voice_command: str, record_first: bool = False):
        if record_first:
            self.telephony.start_call_recording(caller_id=caller_name)
        self.telephony.announce_incoming_call(caller_name)
        cmd = voice_command.strip().lower()
        if "yes" in cmd or "answer" in cmd or "pick" in cmd:
            self.telephony.answer_call()
        elif "no" in cmd or "disconnect" in cmd or "reject" in cmd:
            self.telephony.disconnect_call()

    def handle_in_call_command(self, phrase: str):
        p = phrase.strip().lower()
        if "disconnect" in p or "end call" in p:
            return self.telephony.disconnect_call()
        elif "record" in p:
            return self.telephony.start_call_recording()
        elif "invoke" in p:
            return self.telephony.invoke_mid_call_assistant()
        return None

    def start_background_daemon(self):
        print("🤖 [Cy Mobile Daemon] Starting...")

        signal.signal(signal.SIGINT, lambda s, f: self.stop())
        signal.signal(signal.SIGTERM, lambda s, f: self.stop())

        if not self.audio.start_stream():
            print("❌ Audio stream failed to start.")
            return

        self.wake_word.start_listening(self.audio)

        print("✅ [Cy] Online. Listening for 'Zed One Eight'...")
        try:
            while not self._shutdown_flag.is_set():
                self._shutdown_flag.wait(1)
        except KeyboardInterrupt:
            pass
        finally:
            self.stop()

    def stop(self):
        print("\n🛑 [Cy] Shutting down...")
        self._shutdown_flag.set()
        self.wake_word.stop_listening(self.audio)
        self.audio.stop_stream()
        print("👋 [Cy] Offline. Goodbye, Boss.")


if __name__ == "__main__":
    daemon = CypherMobileDaemon()
    daemon.start_background_daemon()
