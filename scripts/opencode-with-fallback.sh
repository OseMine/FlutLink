#!/usr/bin/env bash
# Runs FlutLink's OpenCode CI commands against the first reachable Zen model.
#
# The model chain comes from $OPENCODE_MODELS (highest priority first, comma
# separated). Every entry is probed with a tiny chat completion before the
# real agent run, so catalog removals and upstream outages fall through to
# the next candidate instead of failing the workflow.
#
# Usage:
#   scripts/opencode-with-fallback.sh [--pick-only] [opencode-run-args...]
#
#   --pick-only   print "opencode/<id>" of the first usable model and exit
#                 (for action inputs that take a static model string)
#
# Environment:
#   OPENCODE_MODELS  model chain (default: deepseek-v4-flash-free,
#                    x-preview-f-free, big-pickle)
#   OPENCODE_CMD     command prefix executed in run mode, split on
#                    whitespace, WITHOUT the `run` subcommand
#                    (default: "opencode"; e.g. "npx --yes opencode-ai@1.18.21")
#   ZEN_BASE         Zen API base (default: https://opencode.ai/zen/v1)
#   OPENCODE_API_KEY used to authenticate the probe requests
set -euo pipefail

CHAIN="${OPENCODE_MODELS:-deepseek-v4-flash-free,x-preview-f-free,big-pickle}"
ZEN_URL="${ZEN_BASE:-https://opencode.ai/zen/v1}/chat/completions"

PICK_ONLY=0
if [ "${1:-}" = "--pick-only" ]; then
  PICK_ONLY=1
  shift
fi

probe() {
  local model="$1" code body
  body="$(mktemp)"
  code="$(curl -sS -m 30 -o "$body" -w '%{http_code}' -X POST "$ZEN_URL" \
    -H "Authorization: Bearer ${OPENCODE_API_KEY:-}" \
    -H "Content-Type: application/json" \
    -d '{"model":"'"$model"'","messages":[{"role":"user","content":"Reply with the single word OK."}],"max_tokens":5}' 2>/dev/null || echo 000)"
  rm -f "$body"
  [ "$code" = "200" ]
}

selected=""
while IFS= read -r model; do
  model="${model//[[:space:]]/}"
  [ -n "$model" ] || continue
  if probe "$model"; then
    selected="$model"
    break
  fi
  echo "::warning::OpenCode model '${model}' not usable, trying next candidate ..." >&2
done < <(tr ',' '\n' <<<"$CHAIN")

if [ -z "$selected" ]; then
  echo "::error::No usable OpenCode model in chain [${CHAIN}] - check https://opencode.ai/zen/v1/models" >&2
  exit 1
fi

if [ "$PICK_ONLY" = "1" ]; then
  echo "opencode/${selected}"
  exit 0
fi

read -r -a cmd <<<"${OPENCODE_CMD:-opencode}"
echo "Running OpenCode with model: opencode/${selected}" >&2
exec "${cmd[@]}" run --model "opencode/${selected}" "$@"
