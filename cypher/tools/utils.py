"""
Utility Tools (Calculator, Datetime, Weather)
"""

import math
from datetime import datetime
import requests
from cypher.tools.base import Tool

def calculator(expression: str) -> str:
    """Evaluate mathematical expression safely. Auto-allowed."""
    allowed_names = {k: v for k, v in math.__dict__.items() if not k.startswith("__")}
    allowed_names.update({"abs": abs, "round": round, "max": max, "min": min, "sum": sum, "pow": pow})
    try:
        result = eval(expression, {"__builtins__": None}, allowed_names)
        return f"Result: {result}"
    except Exception as e:
        return f"Math Evaluation Error: {str(e)}"

def current_datetime() -> str:
    """Get current system date, time, and day of week. Auto-allowed."""
    now = datetime.now()
    return f"Current Local Time: {now.strftime('%A, %B %d, %Y - %I:%M:%S %p %Z')}"

def weather(city: str = "auto") -> str:
    """Get current weather information."""
    try:
        # wttr.in gives clean plaintext weather
        url = f"https://wttr.in/{city}?format=3" if city != "auto" else "https://wttr.in/?format=3"
        res = requests.get(url, timeout=5)
        if res.status_code == 200:
            return f"Weather Report: {res.text.strip()}"
        return "Unable to fetch weather right now."
    except Exception as e:
        return f"Weather error: {str(e)}"

def register_utils_tools(registry):
    registry.register(Tool("calculator", "Evaluate mathematical expressions (Auto-allowed)", {"type": "object", "properties": {"expression": {"type": "string"}}, "required": ["expression"]}, calculator))
    registry.register(Tool("datetime", "Get current system date and time (Auto-allowed)", {"type": "object", "properties": {}}, current_datetime))
    registry.register(Tool("weather", "Get current weather for a location (Auto-allowed)", {"type": "object", "properties": {"city": {"type": "string", "default": "auto"}}}, weather))
