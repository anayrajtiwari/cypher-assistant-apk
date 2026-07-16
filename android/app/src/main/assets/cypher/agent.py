"""
Cypher Core Agent Engine (ReAct Loop)
Handles Tool Call Parsing, Permissions, Execution, and Memory.
"""

import json
import re
from cypher.brain import CypherBrain, SYSTEM_PROMPT
from cypher.permissions import PermissionManager
from cypher.memory import MemoryManager
from cypher.tools import build_tool_registry

class CypherAgent:
    def __init__(self, tui_callback=None):
        self.brain = CypherBrain()
        self.permissions = PermissionManager(tui_callback=tui_callback)
        self.memory = MemoryManager()
        self.registry = build_tool_registry()
        self.current_conv_id = self.memory.create_conversation("Interactive Session")

    def parse_tool_call(self, text: str):
        """Parse <tool_call> {"name": ..., "arguments": ...} </tool_call> or raw json blocks."""
        pattern = r"<tool_call>\s*({.*?})\s*</tool_call>"
        match = re.search(pattern, text, re.DOTALL)
        if match:
            try:
                data = json.loads(match.group(1))
                return data.get("name"), data.get("arguments", {})
            except Exception:
                pass

        # Also fallback regex for plain markdown json code blocks if tool calling in text
        json_pattern = r'```json\s*({.*?"name":.*?})\s*```'
        match_json = re.search(json_pattern, text, re.DOTALL)
        if match_json:
            try:
                data = json.loads(match_json.group(1))
                return data.get("name"), data.get("arguments", {})
            except Exception:
                pass

        return None, None

    def process_message(self, user_text: str, conversation_history: list) -> str:
        # Save user message to SQLite memory
        self.memory.save_message(self.current_conv_id, "user", user_text)

        messages = list(conversation_history)
        if not messages or messages[0].get("role") != "system":
            messages.insert(0, {"role": "system", "content": SYSTEM_PROMPT})

        messages.append({"role": "user", "content": user_text})

        # Get available tool schemas
        tools_schemas = self.registry.get_schemas()

        max_loops = 5
        for loop_count in range(max_loops):
            response_text = self.brain.generate(messages, tools=tools_schemas)

            tool_name, tool_args = self.parse_tool_call(response_text)

            if not tool_name:
                # Normal final message from Cypher
                self.memory.save_message(self.current_conv_id, "assistant", response_text)
                return response_text

            # Tool call detected
            print(f"\n⚙️ [Cypher ReAct] Detected tool request: {tool_name}({tool_args})")
            
            # Check permissions
            allowed = self.permissions.check_permission(tool_name, tool_args)
            if not allowed:
                refusal_msg = f"Boss declined permission to run tool '{tool_name}'."
                print(f"🛑 [Permissions] {refusal_msg}")
                messages.append({"role": "assistant", "content": response_text})
                messages.append({"role": "user", "content": f"<tool_response>\n{refusal_msg}\n</tool_response>"})
                continue

            # Execute tool
            print(f"🚀 [Executing Tool] {tool_name}...")
            tool_output = self.registry.execute(tool_name, tool_args)

            # Append assistant call & tool result to context for next loop pass
            messages.append({"role": "assistant", "content": response_text})
            messages.append({"role": "user", "content": f"<tool_response>\n{tool_output}\n</tool_response>"})

        # Fallback if loops exceeded
        fallback = "Boss, I ran into a loop trying to complete that request. Is there anything specific you'd like me to focus on?"
        self.memory.save_message(self.current_conv_id, "assistant", fallback)
        return fallback

    def process_message_stream(self, user_text: str, conversation_history: list):
        """Streams assistant response tokens in real-time while handling tool execution."""
        self.memory.save_message(self.current_conv_id, "user", user_text)

        messages = list(conversation_history)
        if not messages or messages[0].get("role") != "system":
            messages.insert(0, {"role": "system", "content": SYSTEM_PROMPT})

        messages.append({"role": "user", "content": user_text})
        tools_schemas = self.registry.get_schemas()

        max_loops = 5
        for loop_count in range(max_loops):
            full_response = ""
            for token in self.brain.generate_stream(messages, tools=tools_schemas):
                full_response += token
                yield token

            tool_name, tool_args = self.parse_tool_call(full_response)
            if not tool_name:
                self.memory.save_message(self.current_conv_id, "assistant", full_response)
                return

            print(f"\n⚙️ [Cypher ReAct] Tool call requested: {tool_name}({tool_args})")
            allowed = self.permissions.check_permission(tool_name, tool_args)
            if not allowed:
                refusal = f"Boss declined permission to run tool '{tool_name}'."
                messages.append({"role": "assistant", "content": full_response})
                messages.append({"role": "user", "content": f"<tool_response>\n{refusal}\n</tool_response>"})
                continue

            print(f"🚀 Executing tool {tool_name}...")
            tool_output = self.registry.execute(tool_name, tool_args)
            messages.append({"role": "assistant", "content": full_response})
            messages.append({"role": "user", "content": f"<tool_response>\n{tool_output}\n</tool_response>"})

