"""
Cypher Android OS Agent Tool Modules
"""

from cypher.tools.base import ToolRegistry, Tool
from cypher.android_bridge import AndroidCapabilities

_cap = AndroidCapabilities()

_TOOL_FN_MAP = {
    "get_battery_status": lambda: _cap.get_battery_status(),
    "get_storage_status": lambda: _cap.get_storage_status(),
    "get_device_time": lambda: _cap.get_device_time(),
    "list_installed_apps": lambda: _cap.list_installed_apps(),
    "get_network_status": lambda: _cap.get_network_status(),
    "read_contacts": lambda: _cap.read_contacts(),
    "read_notifications": lambda: _cap.read_notifications(),
    "get_location": lambda: _cap.get_location(),
    "vibrate_device": lambda duration_ms=500: _cap.vibrate_device(duration_ms),
    "launch_app": lambda package: _cap.launch_app(package),
    "clipboard_read": lambda: _cap.clipboard_read(),
    "clipboard_write": lambda text: _cap.clipboard_write(text),
    "open_url": lambda url: _cap.open_url(url),
    "send_sms": lambda number, message: _cap.send_sms(number, message),
    "make_phone_call": lambda number: _cap.make_phone_call(number),
    "take_photo": lambda camera_id=0: _cap.take_photo(camera_id),
    "set_volume": lambda stream="music", level=10: _cap.set_volume(stream, level),
    "toggle_flashlight": lambda on=True: _cap.toggle_flashlight(on),
    "take_screenshot": lambda: _cap.take_screenshot(),
    "send_notification": lambda title, content: _cap.send_notification(title, content),
    "share_file": lambda file_path: _cap.share_file(file_path),
    "uninstall_package": lambda package: _cap.uninstall_package(package),
    "install_package": lambda apk_path: _cap.install_package(apk_path),
    "delete_file": lambda file_path: _cap.delete_file(file_path),
    "open_gallery": lambda album="All": _cap.open_gallery(album),
    "tts_speak": lambda text: _cap.tts_speak(text),
}

_TOOL_SCHEMAS = [
    Tool("get_battery_status", "Get battery level and charge status.", {"type": "object", "properties": {}}, _TOOL_FN_MAP["get_battery_status"]),
    Tool("get_storage_status", "Get storage information.", {"type": "object", "properties": {}}, _TOOL_FN_MAP["get_storage_status"]),
    Tool("get_device_time", "Get current device date and time.", {"type": "object", "properties": {}}, _TOOL_FN_MAP["get_device_time"]),
    Tool("list_installed_apps", "List installed packages.", {"type": "object", "properties": {}}, _TOOL_FN_MAP["list_installed_apps"]),
    Tool("get_network_status", "Get WiFi/network info.", {"type": "object", "properties": {}}, _TOOL_FN_MAP["get_network_status"]),
    Tool("read_contacts", "Read phone contacts.", {"type": "object", "properties": {}}, _TOOL_FN_MAP["read_contacts"]),
    Tool("read_notifications", "Read current notifications.", {"type": "object", "properties": {}}, _TOOL_FN_MAP["read_notifications"]),
    Tool("get_location", "Get GPS coordinates.", {"type": "object", "properties": {}}, _TOOL_FN_MAP["get_location"]),
    Tool("vibrate_device", "Vibrate the phone.", {"type": "object", "properties": {"duration_ms": {"type": "integer", "default": 500}}}, _TOOL_FN_MAP["vibrate_device"]),
    Tool("launch_app", "Launch an app by package name.", {"type": "object", "properties": {"package": {"type": "string"}}, "required": ["package"]}, _TOOL_FN_MAP["launch_app"]),
    Tool("clipboard_read", "Read clipboard text.", {"type": "object", "properties": {}}, _TOOL_FN_MAP["clipboard_read"]),
    Tool("clipboard_write", "Write text to clipboard.", {"type": "object", "properties": {"text": {"type": "string"}}, "required": ["text"]}, _TOOL_FN_MAP["clipboard_write"]),
    Tool("open_url", "Open URL in browser.", {"type": "object", "properties": {"url": {"type": "string"}}, "required": ["url"]}, _TOOL_FN_MAP["open_url"]),
    Tool("send_sms", "Send SMS message.", {"type": "object", "properties": {"number": {"type": "string"}, "message": {"type": "string"}}, "required": ["number", "message"]}, _TOOL_FN_MAP["send_sms"]),
    Tool("make_phone_call", "Make a phone call.", {"type": "object", "properties": {"number": {"type": "string"}}, "required": ["number"]}, _TOOL_FN_MAP["make_phone_call"]),
    Tool("take_photo", "Capture a photo from camera.", {"type": "object", "properties": {"camera_id": {"type": "integer", "default": 0}}}, _TOOL_FN_MAP["take_photo"]),
    Tool("set_volume", "Set volume level.", {"type": "object", "properties": {"stream": {"type": "string", "default": "music"}, "level": {"type": "integer", "default": 10}}}, _TOOL_FN_MAP["set_volume"]),
    Tool("toggle_flashlight", "Turn flashlight on/off.", {"type": "object", "properties": {"on": {"type": "boolean", "default": True}}}, _TOOL_FN_MAP["toggle_flashlight"]),
    Tool("take_screenshot", "Capture a screenshot.", {"type": "object", "properties": {}}, _TOOL_FN_MAP["take_screenshot"]),
    Tool("send_notification", "Send system notification.", {"type": "object", "properties": {"title": {"type": "string"}, "content": {"type": "string"}}, "required": ["title", "content"]}, _TOOL_FN_MAP["send_notification"]),
    Tool("share_file", "Share a file.", {"type": "object", "properties": {"file_path": {"type": "string"}}, "required": ["file_path"]}, _TOOL_FN_MAP["share_file"]),
    Tool("uninstall_package", "Uninstall an app.", {"type": "object", "properties": {"package": {"type": "string"}}, "required": ["package"]}, _TOOL_FN_MAP["uninstall_package"]),
    Tool("install_package", "Install an APK.", {"type": "object", "properties": {"apk_path": {"type": "string"}}, "required": ["apk_path"]}, _TOOL_FN_MAP["install_package"]),
    Tool("delete_file", "Delete a file from storage.", {"type": "object", "properties": {"file_path": {"type": "string"}}, "required": ["file_path"]}, _TOOL_FN_MAP["delete_file"]),
    Tool("open_gallery", "Open photo gallery.", {"type": "object", "properties": {"album": {"type": "string", "default": "All"}}}, _TOOL_FN_MAP["open_gallery"]),
    Tool("tts_speak", "Speak text out loud.", {"type": "object", "properties": {"text": {"type": "string"}}, "required": ["text"]}, _TOOL_FN_MAP["tts_speak"]),
]

def register_android_tools(registry: ToolRegistry):
    for tool in _TOOL_SCHEMAS:
        registry.register(tool)
