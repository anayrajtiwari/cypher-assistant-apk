"""
Cypher Permission Manager
=========================
Three-tier permission architecture:
1. AUTO (🟢): Safe/read-only info like disk_usage, datetime, weather. Executed immediately.
2. SESSION (🟡): Ask once per session. Remembers authorization until exit (e.g. read_file, web_search, run_code).
3. EXPLICIT (🔴): Requires confirmation every single time (e.g. delete_file, kill_process, run_command, shutdown).
"""

import sys

AUTO_ALLOW_TOOLS = {
    "disk_usage",
    "system_info",
    "calculator",
    "datetime",
    "weather",
    "file_info",
    "list_directory",
}

SESSION_ALLOW_TOOLS = {
    "read_file",
    "search_files",
    "web_search",
    "read_webpage",
    "list_processes",
    "run_code",
    "write_code",
    "compile_and_run",
    "notes",
}

EXPLICIT_TOOLS = {
    "write_file",
    "delete_file",
    "kill_process",
    "run_command",
    "shutdown",
    "restart",
    "install_package",
    "download_file",
}


class PermissionManager:
    def __init__(self, tui_callback=None):
        self.session_granted = set()
        self.tui_callback = tui_callback  # If running in TUI, prompt via TUI dialog

    def is_auto_allowed(self, tool_name: str) -> bool:
        return tool_name in AUTO_ALLOW_TOOLS

    def check_permission(self, tool_name: str, args: dict) -> bool:
        if self.is_auto_allowed(tool_name):
            return True

        if tool_name in self.session_granted:
            return True

        is_explicit = tool_name in EXPLICIT_TOOLS

        if self.tui_callback:
            approved, remember_session = self.tui_callback(tool_name, args, is_explicit)
            if approved and remember_session and not is_explicit:
                self.session_granted.add(tool_name)
            return approved

        # Console prompt fallback
        if is_explicit:
            prompt_str = f"\n⚠️  [SECURITY] Cypher needs EXPLICIT permission to run '{tool_name}' with args {args}. Allow? [y/N]: "
            ans = input(prompt_str).strip().lower()
            return ans == "y"
        else:
            prompt_str = f"\n🔑 [PERMISSION] Cypher requests access for '{tool_name}' ({args}). Allow for this session? [y/N]: "
            ans = input(prompt_str).strip().lower()
            if ans == "y":
                self.session_granted.add(tool_name)
                return True
            return False
