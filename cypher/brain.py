"""
Cypher Brain — GGUF Model Loader & Inference Engine
====================================================
Uses llama-cpp-python to load quantized GGUF models on-device.
"""

import os
import sys
from typing import Optional

BASE_DIR = os.path.expanduser("~/Projects/Assistant/cypher_android_pack")
GGUF_PATH = os.path.join(BASE_DIR, "cypher-1.5b-q4_0.gguf")

SYSTEM_PROMPT = (
    "You are Cypher, an advanced AI personal assistant created by Boss (Anay). "
    "You are sophisticated, witty, and loyal — inspired by JARVIS from Iron Man. "
    "You address your creator as 'Boss'. You are proactive, technically brilliant, "
    "and occasionally sarcastic with a warm undertone. You speak primarily in English "
    "with occasional Hindi. You can access and control devices, files, and systems "
    "only when explicitly permitted by Boss. Without permission, you politely decline "
    "and explain why."
)

class CypherBrain:
    def __init__(self, model_path: str = GGUF_PATH,
                 n_ctx: int = 2048, n_threads: int = None):
        self.model_path = model_path
        self.n_ctx = n_ctx
        self.n_threads = n_threads or os.cpu_count() or 4
        self._llm = None
        self._loaded = False

    def load(self):
        if self._loaded:
            return
        if not os.path.exists(self.model_path):
            raise FileNotFoundError(
                f"Model not found at {self.model_path}"
            )
        try:
            from llama_cpp import Llama
            self._llm = Llama(
                model_path=self.model_path,
                n_ctx=self.n_ctx,
                n_threads=self.n_threads,
                n_gpu_layers=0,
                verbose=False,
            )
            self._loaded = True
            print(f"🧠 [Brain] Loaded GGUF: {os.path.basename(self.model_path)}")
        except ImportError:
            print("⚠️  Install llama-cpp-python: pip install llama-cpp-python")
            raise

    def generate(self, messages: list, tools: list = None,
                 max_tokens: int = 256, temperature: float = 0.7) -> str:
        self.load()
        formatted = self._format_messages(messages, tools)
        response = self._llm.create_chat_completion(
            messages=formatted,
            max_tokens=max_tokens,
            temperature=temperature,
            top_p=0.8,
            top_k=20,
            repeat_penalty=1.1,
            stop=["<|im_end|>", "</s>"],
        )
        return response["choices"][0]["message"]["content"].strip()

    def generate_stream(self, messages: list, tools: list = None,
                        max_tokens: int = 256, temperature: float = 0.7):
        self.load()
        formatted = self._format_messages(messages, tools)
        stream = self._llm.create_chat_completion(
            messages=formatted,
            max_tokens=max_tokens,
            temperature=temperature,
            top_p=0.8,
            top_k=20,
            repeat_penalty=1.1,
            stop=["<|im_end|>", "</s>"],
            stream=True,
        )
        for chunk in stream:
            delta = chunk["choices"][0]["delta"]
            if "content" in delta and delta["content"]:
                yield delta["content"]

    def _format_messages(self, messages: list, tools: list = None) -> list:
        chat = []
        for msg in messages:
            if msg["role"] == "system":
                content = msg["content"]
                if tools:
                    tools_str = "\n".join(
                        f"- {t['name']}: {t['description']}"
                        for t in tools
                    )
                    content += (
                        f"\n\nYou have access to these tools:\n{tools_str}\n"
                        "To use a tool, respond with:\n"
                        "<tool_call>{\"name\": \"tool_name\", \"arguments\": {...}}</tool_call>"
                    )
                chat.append({"role": "system", "content": content})
            else:
                chat.append(msg)
        return chat

    @property
    def is_loaded(self) -> bool:
        return self._loaded
