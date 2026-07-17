#!/usr/bin/env python3
"""Cypher desktop test harness.

A deliberately small desktop sandbox for testing Cypher's agent/action loop
without touching the Android application. Start with text commands so tool
execution can be debugged independently from wake-word/STT/TTS issues.
"""

from __future__ import annotations

import platform
import shutil
import subprocess
import sys
import webbrowser
from dataclasses import dataclass
from typing import Callable
from urllib.parse import quote_plus


@dataclass
class ToolResult:
    success: bool
    message: str


class DesktopTools:
    """Safe, explicit desktop capabilities exposed to Cypher."""

    def open_url(self, url: str) -> ToolResult:
        try:
            opened = webbrowser.open(url)
            return ToolResult(opened, f"Opened {url}" if opened else f"Could not open {url}")
        except Exception as exc:
            return ToolResult(False, f"Failed to open URL: {exc}")

    def open_youtube(self) -> ToolResult:
        return self.open_url("https://www.youtube.com")

    def search_web(self, query: str) -> ToolResult:
        if not query.strip():
            return ToolResult(False, "Search query is empty")
        return self.open_url(f"https://www.google.com/search?q={quote_plus(query)}")

    def open_app(self, app: str) -> ToolResult:
        """Launch a small allowlisted set of desktop apps without shell=True."""
        app = app.strip().lower()
        system = platform.system().lower()
        candidates: dict[str, dict[str, list[list[str]]]] = {
            "linux": {
                "terminal": [["x-terminal-emulator"], ["gnome-terminal"], ["konsole"]],
                "calculator": [["gnome-calculator"], ["kcalc"]],
            },
            "windows": {
                "terminal": [["cmd.exe"]],
                "calculator": [["calc.exe"]],
            },
            "darwin": {
                "terminal": [["open", "-a", "Terminal"]],
                "calculator": [["open", "-a", "Calculator"]],
            },
        }
        commands = candidates.get(system, {}).get(app, [])
        for command in commands:
            if system == "windows" or command[0] == "open" or shutil.which(command[0]):
                try:
                    subprocess.Popen(command)
                    return ToolResult(True, f"Opened {app}")
                except Exception:
                    continue
        return ToolResult(False, f"I don't have a desktop launcher for '{app}' yet")


class CypherAgent:
    """Minimal deterministic agent router for validating real tool execution."""

    def __init__(self, tools: DesktopTools):
        self.tools = tools

    def handle(self, command: str) -> ToolResult:
        text = " ".join(command.lower().strip().split())

        if text in {"exit", "quit", "shutdown cypher"}:
            return ToolResult(True, "__EXIT__")

        if "open youtube" in text or text in {"youtube", "launch youtube"}:
            return self.tools.open_youtube()

        if text.startswith("search for "):
            return self.tools.search_web(command.strip()[11:])

        if text.startswith("search "):
            return self.tools.search_web(command.strip()[7:])

        if text.startswith("open "):
            return self.tools.open_app(text[5:])

        return ToolResult(False, "No matching tool yet. This is where the local LLM/tool-call parser will plug in next.")


def main() -> int:
    print("Cypher Desktop Test")
    print("Agent/action sandbox — Android branch is untouched.")
    print("Try: open youtube | search for llama.cpp | open terminal | open calculator | exit")

    agent = CypherAgent(DesktopTools())
    while True:
        try:
            command = input("\nBoss > ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nCypher offline.")
            return 0

        if not command:
            continue

        result = agent.handle(command)
        if result.message == "__EXIT__":
            print("Cypher > Going offline.")
            return 0

        status = "OK" if result.success else "ERROR"
        print(f"Cypher [{status}] > {result.message}")


if __name__ == "__main__":
    sys.exit(main())
