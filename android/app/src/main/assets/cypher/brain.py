"""
Cypher Brain — Model Loader & Inference Engine
Loads base Qwen2.5 model + fine-tuned Cypher LoRA adapter.
"""

import os
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import PeftModel

BASE_MODEL_PATH = os.path.expanduser("~/Projects/Assistant")
ADAPTER_PATH = os.path.join(BASE_MODEL_PATH, "cypher-lora-adapter")

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
    def __init__(self, base_path=BASE_MODEL_PATH, adapter_path=ADAPTER_PATH):
        self.base_path = base_path
        self.adapter_path = adapter_path
        self.model = None
        self.tokenizer = None
        self._load()

    def _load(self):
        print("⚡ [Cypher Engine] Loading tokenizer...")
        self.tokenizer = AutoTokenizer.from_pretrained(self.adapter_path)
        if self.tokenizer.pad_token is None:
            self.tokenizer.pad_token = self.tokenizer.eos_token

        print("⚡ [Cypher Engine] Loading base model on CPU (optimized bfloat16 & 8 threads)...")
        import multiprocessing
        cores = multiprocessing.cpu_count() // 2 or 4
        torch.set_num_threads(cores)

        base_model = AutoModelForCausalLM.from_pretrained(
            self.base_path,
            torch_dtype=torch.bfloat16,
            device_map="cpu",
            trust_remote_code=True,
        )

        print("⚡ [Cypher Engine] Attaching fine-tuned Cypher adapter...")
        self.model = PeftModel.from_pretrained(base_model, self.adapter_path)
        self.model.eval()
        print("✓ [Cypher Engine] Online and operational.")

    def generate(self, messages: list, tools: list = None, max_new_tokens: int = 256) -> str:
        text = self.tokenizer.apply_chat_template(
            messages,
            tools=tools,
            tokenize=False,
            add_generation_prompt=True,
        )
        inputs = self.tokenizer([text], return_tensors="pt").to(self.model.device)

        with torch.inference_mode():
            outputs = self.model.generate(
                **inputs,
                max_new_tokens=max_new_tokens,
                temperature=0.7,
                top_p=0.8,
                top_k=20,
                repetition_penalty=1.1,
                do_sample=True,
            )

        generated_ids = outputs[0][inputs.input_ids.shape[1]:]
        return self.tokenizer.decode(generated_ids, skip_special_tokens=True).strip()

    def generate_stream(self, messages: list, tools: list = None, max_new_tokens: int = 256):
        """Yields generated text tokens in real time using TextIteratorStreamer."""
        from transformers import TextIteratorStreamer
        from threading import Thread

        text = self.tokenizer.apply_chat_template(
            messages,
            tools=tools,
            tokenize=False,
            add_generation_prompt=True,
        )
        inputs = self.tokenizer([text], return_tensors="pt").to(self.model.device)

        streamer = TextIteratorStreamer(self.tokenizer, skip_prompt=True, skip_special_tokens=True)
        generation_kwargs = dict(
            **inputs,
            streamer=streamer,
            max_new_tokens=max_new_tokens,
            temperature=0.7,
            top_p=0.8,
            top_k=20,
            repetition_penalty=1.1,
            do_sample=True,
        )

        thread = Thread(target=self._run_generation, args=(generation_kwargs,))
        thread.start()

        for new_text in streamer:
            yield new_text
        thread.join()

    def _run_generation(self, kwargs):
        with torch.inference_mode():
            self.model.generate(**kwargs)

