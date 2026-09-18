#!/usr/bin/env bash

set -euo pipefail

mkdir -p agent-reports

echo "Checking Ollama model..."
ollama show qwen3.8-custom:latest >/dev/null

echo "Checking Microbot Agent Server..."
bash ./microbot-cli state

echo "Starting local gameplay agent..."
opencode run \
  --auto \
  --agent osrs-player \
  --file ./OSRS_AGENT_TASK.md \
  --title "Local private OSRS gameplay test" \
  "Execute the attached task. On Windows, run every Microbot command using the exact format: bash ./microbot-cli COMMAND." \
  2>&1 | tee ./agent-reports/osrs-player-console.log