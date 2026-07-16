"""
Cypher Android OS Agent & Permission Bridge
============================================
Enables agentic control over Android OS with 3-tier permission gateway.
"""

import os
import sys
import json
import time
import subprocess
from typing import Dict, Any

ANDROID_AUTO_TOOLS = {
    "get_battery_status", "get_storage_status", "get_device_time",
    "tts_speak", "list_installed_apps", "get_network_status",
}

ANDROID_SESSION_TOOLS = {
    "launch_app", "read_media_files", "read_contacts",
    "read_notifications", "vibrate_device", "get_location",
    "get_weather", "clipboard_read", "search_phone",
}

ANDROID_EXPLICIT_TOOLS = {
    "open_gallery", "delete_file", "install_package",
    "uninstall_package", "send_sms", "make_phone_call",
    "take_photo", "clipboard_write", "send_notification",
    "set_volume", "toggle_flashlight", "take_screenshot",
    "share_file", "open_url",
}

class AndroidCapabilities:
    def __init__(self):
        pass

    def _termux(self, cmd: list) -> str:
        try:
            r = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
            return r.stdout.strip() or r.stderr.strip() or "OK"
        except FileNotFoundError:
            return "termux-api not installed"
        except Exception as e:
            return str(e)

    def tts_speak(self, text: str) -> str:
        print(f"🔊 [Cy]: \"{text}\"")
        return self._termux(["termux-tts-speak", text])

    # --- Auto tools ---
    def get_battery_status(self) -> str:
        return self._termux(["termux-battery-status"])

    def get_storage_status(self) -> str:
        return self._termux(["termux-storage-get", "info"])

    def get_device_time(self) -> str:
        from datetime import datetime
        return datetime.now().isoformat()

    def list_installed_apps(self) -> str:
        return self._termux(["termux-list-packages"])

    def get_network_status(self) -> str:
        return self._termux(["termux-wifi-scaninfo"])

    # --- Session tools ---
    def read_contacts(self) -> str:
        return self._termux(["termux-contact-list"])

    def read_notifications(self) -> str:
        return self._termux(["termux-notification-list"])

    def get_location(self) -> str:
        return self._termux(["termux-location"])

    def vibrate_device(self, duration_ms: int = 500) -> str:
        return self._termux(["termux-vibrate", "-d", str(duration_ms)])

    def launch_app(self, package: str) -> str:
        return self._termux(["termux-open", f"--package={package}"])

    def clipboard_read(self) -> str:
        return self._termux(["termux-clipboard-get"])

    def search_phone(self, query: str) -> str:
        return self._termux(["termux-search", query])

    # --- Explicit tools ---
    def open_gallery(self, album: str = "All") -> str:
        return self._termux(["am", "start", "-a", "android.intent.action.VIEW", "-t", "image/*"])

    def delete_file(self, file_path: str) -> str:
        if os.path.exists(file_path):
            os.remove(file_path)
            return f"Deleted {file_path}."
        return f"Not found: {file_path}"

    def install_package(self, apk_path: str) -> str:
        return self._termux(["termux-open", apk_path])

    def uninstall_package(self, package: str) -> str:
        return self._termux(["pm", "uninstall", package])

    def send_sms(self, number: str, message: str) -> str:
        return self._termux(["termux-sms-send", "-n", number, message])

    def make_phone_call(self, number: str) -> str:
        return self._termux(["termux-telephony-call", number])

    def take_photo(self, camera_id: int = 0) -> str:
        path = os.path.expanduser(f"~/storage/dcim/Cypher/photo_{int(time.time())}.jpg")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        return self._termux(["termux-camera-photo", "-c", str(camera_id), path])

    def clipboard_write(self, text: str) -> str:
        return self._termux(["termux-clipboard-set", text])

    def send_notification(self, title: str, content: str) -> str:
        return self._termux(["termux-notification", "-t", title, "-c", content])

    def set_volume(self, stream: str = "music", level: int = 10) -> str:
        return self._termux(["termux-volume", stream, str(level)])

    def toggle_flashlight(self, on: bool = True) -> str:
        return self._termux(["termux-torch", "on" if on else "off"])

    def take_screenshot(self) -> str:
        path = os.path.expanduser(f"~/storage/pictures/Cypher/screenshot_{int(time.time())}.png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        return self._termux(["termux-screenshot", path])

    def share_file(self, file_path: str) -> str:
        return self._termux(["termux-share", "-a", "share", file_path])

    def open_url(self, url: str) -> str:
        return self._termux(["termux-open-url", url])



