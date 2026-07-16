#!/bin/bash
# Cypher (Cy) One-Click Installer & Launcher for Android 16
echo "========================================================="
echo "  Installing Cypher (Cy) Mobile Agent on Android 16..."
echo "========================================================="

# 1. Update Termux dependencies
pkg update -y
pkg install -y python termux-api ffmpeg

# 2. Install Python packages
pip install --upgrade pip
pip install faster-whisper webrtcvad llama-cpp-python numpy requests duckduckgo-search beautifulsoup4 psutil

# 3. Setup Permissions
termux-setup-storage

# 4. Create data directories
mkdir -p ~/Projects/Assistant/data/whisper-models
mkdir -p ~/Projects/Assistant/data/call_recordings

# 5. Launch Parallel Background Daemon
echo "Starting Cypher OS Service in background..."
echo "Wake word: 'Zed One Eight' / 'Zee One Eight'"
python3 -m cypher.cypher_mobile_daemon
