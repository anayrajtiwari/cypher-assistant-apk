"""
Cypher Tool Registry Initializer
"""

from cypher.tools.base import ToolRegistry
from cypher.tools.filesystem import register_filesystem_tools
from cypher.tools.system import register_system_tools
from cypher.tools.web import register_web_tools
from cypher.tools.code import register_code_tools
from cypher.tools.utils import register_utils_tools
from cypher.tools.android import register_android_tools

def build_tool_registry() -> ToolRegistry:
    registry = ToolRegistry()
    register_filesystem_tools(registry)
    register_system_tools(registry)
    register_web_tools(registry)
    register_code_tools(registry)
    register_utils_tools(registry)
    register_android_tools(registry)
    return registry

