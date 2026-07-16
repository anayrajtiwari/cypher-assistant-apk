"""
System & Device Tools for Cypher
"""

import psutil
import subprocess
import shutil
from cypher.tools.base import Tool

def disk_usage(path: str = "/") -> str:
    """Check disk/SSD storage usage and available space. Auto-allowed."""
    try:
        usage = shutil.disk_usage(path)
        total_gb = usage.total / (1024**3)
        used_gb = usage.used / (1024**3)
        free_gb = usage.free / (1024**3)
        pct_used = (usage.used / usage.total) * 100
        return (
            f"💾 Storage Statistics for '{path}':\n"
            f"• Total Space: {total_gb:.1f} GB\n"
            f"• Used Space:  {used_gb:.1f} GB ({pct_used:.1f}%)\n"
            f"• Free Space:  {free_gb:.1f} GB\n"
            f"• Status: {'⚠️ Warning: High Usage' if pct_used > 85 else '🟢 Healthy'}"
        )
    except Exception as e:
        return f"Error reading disk usage: {str(e)}"

def system_info() -> str:
    """Get general system specifications (CPU, Memory, OS). Auto-allowed."""
    try:
        cpu_usage = psutil.cpu_percent(interval=0.5)
        memory = psutil.virtual_memory()
        mem_used = memory.used / (1024**3)
        mem_total = memory.total / (1024**3)

        return (
            f"🖥️ System Specs & Live Load:\n"
            f"• CPU Cores: {psutil.cpu_count(logical=True)} ({psutil.cpu_count(logical=False)} physical)\n"
            f"• CPU Load:  {cpu_usage}%\n"
            f"• RAM Usage: {mem_used:.1f} GB / {mem_total:.1f} GB ({memory.percent}%)\n"
            f"• Boot Time: {psutil.boot_time()}"
        )
    except Exception as e:
        return f"Error reading system info: {str(e)}"

def list_processes(limit: int = 15) -> str:
    """List running processes ordered by CPU usage."""
    procs = []
    for p in psutil.process_iter(['pid', 'name', 'cpu_percent', 'memory_percent']):
        try:
            procs.append(p.info)
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            pass
    procs.sort(key=lambda x: x.get('cpu_percent', 0) or 0, reverse=True)

    lines = ["PID\tName\t\tCPU%\tRAM%"]
    for p in procs[:limit]:
        lines.append(f"{p['pid']}\t{p['name'][:15]:<15}\t{p['cpu_percent']}%\t{p['memory_percent']:.1f}%")
    return "\n".join(lines)

def run_command(command: str) -> str:
    """Run a shell command on the host OS."""
    try:
        res = subprocess.run(command, shell=True, capture_output=True, text=True, timeout=30)
        out = res.stdout
        if res.stderr:
            out += f"\n[STDERR]:\n{res.stderr}"
        return out if out else f"Command executed (Return code: {res.returncode})"
    except subprocess.TimeoutExpired:
        return "Command timed out after 30 seconds."
    except Exception as e:
        return f"Error running command: {str(e)}"

def kill_process(pid: int) -> str:
    """Terminate a process by PID."""
    try:
        p = psutil.Process(pid)
        name = p.name()
        p.terminate()
        return f"Process '{name}' (PID {pid}) terminated."
    except Exception as e:
        return f"Error killing process {pid}: {str(e)}"

def register_system_tools(registry):
    registry.register(Tool("disk_usage", "Get storage space & SSD usage statistics (Auto-allowed)", {"type": "object", "properties": {"path": {"type": "string", "default": "/"}}}, disk_usage))
    registry.register(Tool("system_info", "Get CPU, Memory, and System specifications (Auto-allowed)", {"type": "object", "properties": {}}, system_info))
    registry.register(Tool("list_processes", "List top running processes by resource consumption", {"type": "object", "properties": {"limit": {"type": "integer", "default": 15}}}, list_processes))
    registry.register(Tool("run_command", "Execute a terminal shell command", {"type": "object", "properties": {"command": {"type": "string"}}, "required": ["command"]}, run_command))
    registry.register(Tool("kill_process", "Terminate a running process by PID", {"type": "object", "properties": {"pid": {"type": "integer"}}, "required": ["pid"]}, kill_process))
