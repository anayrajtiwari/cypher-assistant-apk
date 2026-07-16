"""
Base Tool Definition & Registry
"""

class Tool:
    def __init__(self, name: str, description: str, parameters: dict, func):
        self.name = name
        self.description = description
        self.parameters = parameters
        self.func = func

    def to_schema(self) -> dict:
        return {
            "name": self.name,
            "description": self.description,
            "parameters": self.parameters,
        }

    def execute(self, **kwargs):
        return self.func(**kwargs)

class ToolRegistry:
    def __init__(self):
        self.tools = {}

    def register(self, tool: Tool):
        self.tools[tool.name] = tool

    def get_tool(self, name: str) -> Tool:
        return self.tools.get(name)

    def get_schemas(self) -> list:
        return [tool.to_schema() for tool in self.tools.values()]

    def execute(self, name: str, args: dict):
        tool = self.get_tool(name)
        if not tool:
            return f"Error: Tool '{name}' not found."
        try:
            return tool.execute(**args)
        except Exception as e:
            return f"Tool Execution Error ({name}): {str(e)}"
