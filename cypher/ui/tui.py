"""
Cypher Rich Terminal User Interface (TUI)
Features:
- Split screen (Live Chat + System Sidebar)
- Realtime System Load Monitor (CPU, RAM, Disk Gauge)
- Colorized Syntax Highlighting & Markdown Rendering
- JARVIS-style Proactive Alert Panel
"""

import sys
import os
import time
import psutil
import shutil
from rich.console import Console
from rich.layout import Layout
from rich.panel import Panel
from rich.text import Text
from rich.markdown import Markdown
from rich.live import Live
from rich.table import Table

console = Console()

class CypherTUI:
    def __init__(self):
        self.console = console
        self.layout = Layout()
        self._setup_layout()

    def _setup_layout(self):
        self.layout.split(
            Layout(name="header", size=3),
            Layout(name="main", ratio=1),
            Layout(name="footer", size=3),
        )

        self.layout["main"].split_row(
            Layout(name="chat", ratio=3),
            Layout(name="sidebar", ratio=1),
        )

    def get_system_sidebar_panel(self) -> Panel:
        cpu_usage = psutil.cpu_percent()
        memory = psutil.virtual_memory()
        disk = shutil.disk_usage("/")

        cpu_bar = "█" * int(cpu_usage / 10) + "░" * (10 - int(cpu_usage / 10))
        ram_bar = "█" * int(memory.percent / 10) + "░" * (10 - int(memory.percent / 10))
        disk_pct = (disk.used / disk.total) * 100
        disk_bar = "█" * int(disk_pct / 10) + "░" * (10 - int(disk_pct / 10))

        table = Table(show_header=False, expand=True, box=None)
        table.add_column("Metric", style="cyan")
        table.add_column("Gauge", style="bold green")

        table.add_row("CPU Load", f"{cpu_usage:>5.1f}% [{cpu_bar}]")
        table.add_row("RAM Usage", f"{memory.percent:>5.1f}% [{ram_bar}]")
        table.add_row("Disk (SSD)", f"{disk_pct:>5.1f}% [{disk_bar}]")
        table.add_row("Free Disk", f"{disk.free / (1024**3):.1f} GB")

        sidebar_text = Text()
        sidebar_text.append("\n📊 LIVE HARDWARE STATS\n", style="bold yellow")
        
        alerts = []
        if disk_pct > 90:
            alerts.append("🔴 Disk space low!")
        if cpu_usage > 85:
            alerts.append("🟡 High CPU utilization!")
        if not alerts:
            alerts.append("🟢 Systems nominal.")

        sidebar_panel = Panel(
            table,
            title="[bold blue]JARVIS Telemetry[/bold blue]",
            border_style="cyan",
            subtitle="[dim]" + " | ".join(alerts) + "[/dim]",
        )
        return sidebar_panel

    def render_header(self) -> Panel:
        title = Text("🤖 CYPHER v1.0 — Advanced Personal AI Assistant", style="bold magenta center")
        return Panel(title, border_style="bold magenta", padding=(0, 1))

    def render_footer(self) -> Panel:
        footer_str = "[bold cyan]Boss (Anay)[/bold cyan] | 🎤 [green]Voice (Whisper Ready)[/green] | 🔧 [yellow]Tools: 22 Loaded[/yellow] | Type 'exit' to quit"
        return Panel(Text.from_markup(footer_str), border_style="dim white")

def display_welcome_banner():
    console.print("\n[bold cyan]=====================================================[/bold cyan]")
    console.print("[bold green]  🤖 CYPHER ONLINE — Iron-Man / JARVIS Interface[/bold green]")
    console.print("[bold yellow]  Created for: Boss (Anay)[/bold yellow]")
    console.print("[bold cyan]=====================================================\n[/bold cyan]")
