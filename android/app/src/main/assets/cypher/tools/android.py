"""
Cypher Android OS Agent Tool Modules
"""

from cypher.tools.base import ToolRegistry, Tool
from cypher.android_bridge import AndroidCapabilities

android_cap = AndroidCapabilities()

def register_android_tools(registry: ToolRegistry):
    registry.register(
        Tool(
            name="open_gallery",
            description="Opens Android photo gallery to view photos or albums (Triggers explicit security permission prompt).",
            parameters={
                "type": "object",
                "properties": {
                    "album": {"type": "string", "description": "Optional name of album to open."}
                }
            },
            func=lambda album="All": android_cap.open_gallery(album)
        )
    )

    registry.register(
        Tool(
            name="install_package",
            description="Triggers installation of an APK package file on Android 16 (Triggers explicit security prompt).",
            parameters={
                "type": "object",
                "properties": {
                    "apk_path": {"type": "string", "description": "Path to target .apk file."}
                },
                "required": ["apk_path"]
            },
            func=lambda apk_path: android_cap.install_package(apk_path)
        )
    )

    registry.register(
        Tool(
            name="delete_file",
            description="Permanently deletes a file from Android phone storage (Triggers explicit security prompt).",
            parameters={
                "type": "object",
                "properties": {
                    "file_path": {"type": "string", "description": "Absolute path of file to delete."}
                },
                "required": ["file_path"]
            },
            func=lambda file_path: android_cap.delete_file(file_path)
        )
    )

    registry.register(
        Tool(
            name="tts_speak",
            description="Speaks response out loud using Text-To-Speech audio output.",
            parameters={
                "type": "object",
                "properties": {
                    "text": {"type": "string", "description": "Text message to speak."}
                },
                "required": ["text"]
            },
            func=lambda text: android_cap.tts_speak(text)
        )
    )

