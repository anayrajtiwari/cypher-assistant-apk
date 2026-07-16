"""
Multi-Language Code Execution Engine for Cypher
Supports Python, JavaScript, TypeScript, C, C++, Rust, Go, Java, Bash, Ruby, PHP, Lua, R, etc.
Automatically compiles and runs code blocks.
"""

import os
import tempfile
import subprocess
from cypher.tools.base import Tool

LANG_MAP = {
    "python": {"ext": ".py", "cmd": ["python3"]},
    "py": {"ext": ".py", "cmd": ["python3"]},
    "javascript": {"ext": ".js", "cmd": ["node"]},
    "js": {"ext": ".js", "cmd": ["node"]},
    "typescript": {"ext": ".ts", "cmd": ["npx", "ts-node"]},
    "ts": {"ext": ".ts", "cmd": ["npx", "ts-node"]},
    "bash": {"ext": ".sh", "cmd": ["bash"]},
    "sh": {"ext": ".sh", "cmd": ["bash"]},
    "zsh": {"ext": ".sh", "cmd": ["zsh"]},
    "c": {"ext": ".c", "compile": ["gcc", "{file}", "-o", "{out}"], "run": ["{out}"]},
    "cpp": {"ext": ".cpp", "compile": ["g++", "-std=c++17", "{file}", "-o", "{out}"], "run": ["{out}"]},
    "c++": {"ext": ".cpp", "compile": ["g++", "-std=c++17", "{file}", "-o", "{out}"], "run": ["{out}"]},
    "rust": {"ext": ".rs", "compile": ["rustc", "{file}", "-o", "{out}"], "run": ["{out}"]},
    "rs": {"ext": ".rs", "compile": ["rustc", "{file}", "-o", "{out}"], "run": ["{out}"]},
    "go": {"ext": ".go", "cmd": ["go", "run"]},
    "golang": {"ext": ".go", "cmd": ["go", "run"]},
    "java": {"ext": ".java", "compile": ["javac", "{file}"], "run": ["java", "-cp", "{dir}", "{classname}"]},
    "ruby": {"ext": ".rb", "cmd": ["ruby"]},
    "php": {"ext": ".php", "cmd": ["php"]},
    "lua": {"ext": ".lua", "cmd": ["lua"]},
    "r": {"ext": ".r", "cmd": ["Rscript"]},
}

def run_code(language: str, code: str) -> str:
    """Execute code snippet in specified programming language."""
    lang_key = language.lower().strip()
    if lang_key not in LANG_MAP:
        return f"Unsupported language '{language}'. Supported languages: {', '.join(sorted(set(LANG_MAP.keys())))}"

    spec = LANG_MAP[lang_key]
    ext = spec["ext"]

    with tempfile.TemporaryDirectory() as tmpdir:
        filename = f"code{ext}"
        filepath = os.path.join(tmpdir, filename)

        with open(filepath, "w", encoding="utf-8") as f:
            f.write(code)

        try:
            # Compiled language pipeline
            if "compile" in spec:
                outpath = os.path.join(tmpdir, "program")
                compile_cmd = [arg.format(file=filepath, out=outpath) for arg in spec["compile"]]
                
                comp_res = subprocess.run(compile_cmd, capture_output=True, text=True, timeout=15)
                if comp_res.returncode != 0:
                    return f"❌ Compilation Error ({language}):\n{comp_res.stderr or comp_res.stdout}"

                run_cmd = [arg.format(out=outpath, dir=tmpdir, classname="Main") for arg in spec["run"]]
                run_res = subprocess.run(run_cmd, capture_output=True, text=True, timeout=15)
                output = run_res.stdout
                if run_res.stderr:
                    output += f"\n[STDERR]:\n{run_res.stderr}"
                return output or "Executed successfully with no stdout output."

            # Interpreted language pipeline
            else:
                cmd = spec["cmd"] + [filepath]
                run_res = subprocess.run(cmd, capture_output=True, text=True, timeout=20)
                output = run_res.stdout
                if run_res.stderr:
                    output += f"\n[STDERR]:\n{run_res.stderr}"
                return output or "Executed successfully with no stdout output."

        except subprocess.TimeoutExpired:
            return f"Execution timed out (exceeded limit)."
        except Exception as e:
            return f"Execution error: {str(e)}"

def register_code_tools(registry):
    registry.register(Tool(
        "run_code",
        "Compile and execute code in any programming language (Python, JS, C, C++, Rust, Go, Java, Bash, etc.)",
        {
            "type": "object",
            "properties": {
                "language": {"type": "string", "description": "Programming language (e.g. python, c, cpp, rust, go, js, bash)"},
                "code": {"type": "string", "description": "Source code content to execute"}
            },
            "required": ["language", "code"]
        },
        run_code
    ))
