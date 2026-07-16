"""
Filesystem Tools for Cypher
"""

import os
import glob
from cypher.tools.base import Tool

def list_directory(path: str = ".") -> str:
    abs_path = os.path.abspath(os.path.expanduser(path))
    if not os.path.exists(abs_path):
        return f"Path does not exist: {abs_path}"
    try:
        entries = os.listdir(abs_path)
        items = []
        for entry in entries[:50]:  # Limit to 50 items
            full_item = os.path.join(abs_path, entry)
            is_dir = os.path.isdir(full_item)
            size = os.path.getsize(full_item) if not is_dir else "-"
            type_str = "DIR" if is_dir else "FILE"
            items.append(f"[{type_str}] {entry} ({size} bytes)")
        return "\n".join(items) if items else "Directory is empty."
    except Exception as e:
        return f"Error listing directory: {str(e)}"

def read_file(filepath: str, max_lines: int = 500) -> str:
    abs_path = os.path.abspath(os.path.expanduser(filepath))
    if not os.path.exists(abs_path):
        return f"File not found: {abs_path}"
    try:
        with open(abs_path, "r", encoding="utf-8", errors="replace") as f:
            lines = [f.readline() for _ in range(max_lines)]
            content = "".join(lines)
            if f.readline():
                content += f"\n... [Truncated after {max_lines} lines]"
            return content
    except Exception as e:
        return f"Error reading file: {str(e)}"

def write_file(filepath: str, content: str, append: bool = False) -> str:
    abs_path = os.path.abspath(os.path.expanduser(filepath))
    os.makedirs(os.path.dirname(abs_path), exist_ok=True)
    mode = "a" if append else "w"
    try:
        with open(abs_path, mode, encoding="utf-8") as f:
            f.write(content)
        action = "Appended to" if append else "Wrote"
        return f"{action} file successfully: {abs_path}"
    except Exception as e:
        return f"Error writing file: {str(e)}"

def delete_file(filepath: str) -> str:
    abs_path = os.path.abspath(os.path.expanduser(filepath))
    if not os.path.exists(abs_path):
        return f"File does not exist: {abs_path}"
    try:
        os.remove(abs_path)
        return f"File deleted: {abs_path}"
    except Exception as e:
        return f"Error deleting file: {str(e)}"

def search_files(pattern: str, search_dir: str = ".") -> str:
    abs_dir = os.path.abspath(os.path.expanduser(search_dir))
    full_pattern = os.path.join(abs_dir, "**", pattern)
    matches = glob.glob(full_pattern, recursive=True)[:30]
    if not matches:
        return f"No files matching '{pattern}' in {abs_dir}"
    return "\n".join(matches)

def file_info(filepath: str) -> str:
    abs_path = os.path.abspath(os.path.expanduser(filepath))
    if not os.path.exists(abs_path):
        return f"File does not exist: {abs_path}"
    stat = os.stat(abs_path)
    return (
        f"Path: {abs_path}\n"
        f"Size: {stat.st_size} bytes ({stat.st_size / (1024*1024):.2f} MB)\n"
        f"Is Directory: {os.path.isdir(abs_path)}\n"
        f"Modified: {stat.st_mtime}"
    )

def register_filesystem_tools(registry):
    registry.register(Tool("list_directory", "List files and folders in a path", {"type": "object", "properties": {"path": {"type": "string", "default": "."}}}, list_directory))
    registry.register(Tool("read_file", "Read text content of a file", {"type": "object", "properties": {"filepath": {"type": "string"}}, "required": ["filepath"]}, read_file))
    registry.register(Tool("write_file", "Create or overwrite a file with content", {"type": "object", "properties": {"filepath": {"type": "string"}, "content": {"type": "string"}, "append": {"type": "boolean", "default": False}}, "required": ["filepath", "content"]}, write_file))
    registry.register(Tool("delete_file", "Delete a file from disk", {"type": "object", "properties": {"filepath": {"type": "string"}}, "required": ["filepath"]}, delete_file))
    registry.register(Tool("search_files", "Search files matching a glob pattern", {"type": "object", "properties": {"pattern": {"type": "string"}, "search_dir": {"type": "string", "default": "."}}, "required": ["pattern"]}, search_files))
    registry.register(Tool("file_info", "Get metadata of a file", {"type": "object", "properties": {"filepath": {"type": "string"}}, "required": ["filepath"]}, file_info))
