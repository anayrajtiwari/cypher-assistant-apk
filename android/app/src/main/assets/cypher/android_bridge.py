"""
Cypher Android OS Agent & Permission Bridge
==========================================
Enables agentic control over Android OS (Gallery, Installed Apps, Camera, File System, System Intents)
with a 3-tier Security Permission Gateway and Text-to-Speech (TTS) integration.
"""

import os
import sys
import json
import subprocess
from typing import Tuple, Dict, Any

# Android 3-Tier Security Tool Schema
ANDROID_AUTO_TOOLS = {
    "get_battery_status",
    "get_storage_status",
    "get_device_time",
    "tts_speak",
    "list_installed_apps",
}

ANDROID_SESSION_TOOLS = {
    "launch_app",
    "read_media_files",
    "read_contacts",
    "read_notifications",
    "vibrate_device",
}

ANDROID_EXPLICIT_TOOLS = {
    "open_gallery",
    "delete_file",
    "install_package",
    "uninstall_package",
    "send_sms",
    "make_phone_call",
    "take_photo",
}

class AndroidPermissionGateway:
    """Security prompt manager for sensitive Android operations."""

    def __init__(self):
        self.session_permissions = set()

    def request_access(self, tool_name: str, args: Dict[str, Any]) -> bool:
        if tool_name in ANDROID_AUTO_TOOLS:
            return True

        if tool_name in self.session_permissions and tool_name not in ANDROID_EXPLICIT_TOOLS:
            return True

        is_explicit = tool_name in ANDROID_EXPLICIT_TOOLS
        risk_level = "🔴 HIGH SECURITY (Requires Permission Every Time)" if is_explicit else "🟡 MEDIUM (Session Access)"

        print(f"\n=======================================================")
        print(f"🔒 [ANDROID SECURITY GATEWAY] Cypher Requested Action:")
        print(f"   ► Tool: {tool_name}")
        print(f"   ► Parameters: {json.dumps(args)}")
        print(f"   ► Risk Tier: {risk_level}")
        print(f"=======================================================")

        try:
            choice = input(f"Boss, allow Cypher to execute '{tool_name}'? [y/N]: ").strip().lower()
            if choice == "y":
                if not is_explicit:
                    self.session_permissions.add(tool_name)
                return True
            else:
                print(f"🛑 [Access Denied] Action '{tool_name}' was blocked by Boss.")
                return False
        except (KeyboardInterrupt, EOFError):
            return False


class AndroidCapabilities:
    """Executes native Android commands via Termux-API or System Intents."""

    def __init__(self):
        self.gateway = AndroidPermissionGateway()

    def tts_speak(self, text: str, engine: str = "auto") -> str:
        """Text-To-Speech execution for voice response output."""
        print(f"🔊 [Cypher Speech Synthesis]: \"{text}\"")
        try:
            # Android Termux TTS integration
            subprocess.run(["termux-tts-speak", text], check=False)
        except FileNotFoundError:
            pass
        return f"Spoke: {text}"

    def open_gallery(self, album: str = "All") -> str:
        """Opens Android Gallery photo library after explicit user approval."""
        if not self.gateway.request_access("open_gallery", {"album": album}):
            return "Permission denied by Boss to open Gallery."

        print("📷 Opening Android Gallery app...")
        try:
            subprocess.run(["termux-open-url", "content://media/external/images/media"], check=False)
        except Exception:
            subprocess.run(["am", "start", "-a", "android.intent.action.VIEW", "-t", "image/*"], check=False)
        return "Gallery opened successfully."

    def install_package(self, apk_path: str) -> str:
        """Installs APK package on Android after explicit prompt."""
        if not self.gateway.request_access("install_package", {"apk_path": apk_path}):
            return "Permission denied by Boss to install package."

        print(f"📦 Installing APK package from: {apk_path}...")
        try:
            subprocess.run(["termux-open", apk_path], check=False)
        except Exception as e:
            return f"Failed to launch installer: {str(e)}"
        return f"Package installation triggered for {apk_path}."

    def delete_file(self, file_path: str) -> str:
        """Deletes file on Android storage after explicit confirmation."""
        if not self.gateway.request_access("delete_file", {"file_path": file_path}):
            return "Permission denied by Boss to delete file."

        if os.path.exists(file_path):
            os.remove(file_path)
            return f"File {file_path} deleted successfully."
        return f"File {file_path} not found."


def get_android_tool_definitions():
    """Returns OpenAI / Qwen JSON schema for Android tools."""
    return [
        {
            "name": "open_gallery",
            "description": "Opens Android photo gallery to view images/photos (Requires explicit permission).",
            "parameters": {
                "type": "object",
                "properties": {
                    "album": {"type": "string", "description": "Optional specific album name."}
                }
            }
        },
        {
            "name": "install_package",
            "description": "Installs an APK package file on Android 16 (Requires explicit permission).",
            "parameters": {
                "type": "object",
                "properties": {
                    "apk_path": {"type": "string", "description": "Path to target .apk file."}
                },
                "required": ["apk_path"]
            }
        },
        {
            "name": "delete_file",
            "description": "Deletes a file from Android phone storage (Requires explicit permission).",
            "parameters": {
                "type": "object",
                "properties": {
                    "file_path": {"type": "string", "description": "Absolute path of target file."}
                },
                "required": ["file_path"]
            }
        },
        {
            "name": "tts_speak",
            "description": "Speaks a message out loud using Text-To-Speech audio output.",
            "parameters": {
                "type": "object",
                "properties": {
                    "text": {"type": "string", "description": "Text content to speak out loud."}
                },
                "required": ["text"]
            }
        }
    ]
