# Cypher Desktop Test

This branch contains a lightweight desktop sandbox for testing Cypher's agent/action execution independently from Android.

The Android application is intentionally left untouched.

## Run

Requires Python 3.10+ and no third-party dependencies.

```bash
python3 desktop_test/cypher_desktop.py
```

On Windows:

```powershell
python desktop_test/cypher_desktop.py
```

## Current commands

- `open youtube`
- `search for <query>`
- `open terminal`
- `open calculator`
- `exit`

The first milestone is deliberately text-only. This isolates the agent/tool execution layer from wake-word, STT, TTS, Android permissions, and foreground-service behavior.

## Next milestone

Once real desktop actions are confirmed working, connect the existing/local GGUF model through a desktop-compatible llama.cpp interface and require structured tool calls. Then add STT/TTS and finally wake-word support.
