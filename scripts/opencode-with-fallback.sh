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
#   OPENCODE_MODELS  model chain (default: big-pickle, mimo-v2.5-free,
#                    hy3-free, nemotron-3-ultra-free, laguna-s-2.1-free)
#   OPENCODE_CMD     command prefix executed in run mode, split on
#                    whitespace, WITHOUT the `run` subcommand
#                    (default: "opencode"; e.g. "npx --yes opencode-ai@1.18.21")
#   ZEN_BASE         Zen API base (default: https://opencode.ai/zen/v1)
#   OPENCODE_API_KEY used to authenticate the probe requests
#
# Transient upstream hiccups must not kill a release pipeline: every model is
# probed OPENCODE_PROBE_ATTEMPTS times (backoff OPENCODE_PROBE_BACKOFF s), and
# the whole chain is walked OPENCODE_CHAIN_ROUNDS times with a
# OPENCODE_CHAIN_RETRY_WAIT s pause between rounds before giving up.
set -euo pipefail

CHAIN="${OPENCODE_MODELS:-big-pickle,mimo-v2.5-free,hy3-free,nemotron-3-ultra-free,laguna-s-2.1-free}"
ZEN_URL="${ZEN_BASE:-https://opencode.ai/zen/v1}/chat/completions"
PROBE_ATTEMPTS="${OPENCODE_PROBE_ATTEMPTS:-2}"
PROBE_BACKOFF="${OPENCODE_PROBE_BACKOFF:-15}"
CHAIN_ROUNDS="${OPENCODE_CHAIN_ROUNDS:-2}"
CHAIN_RETRY_WAIT="${OPENCODE_CHAIN_RETRY_WAIT:-60}"

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

# A single probe can fail transiently (rate limit, network blip, brief
# upstream outage) — retry a few times before declaring the model unusable.
probe_with_retry() {
  local model="$1" attempt=1
  while [ "$attempt" -le "$PROBE_ATTEMPTS" ]; do
    if probe "$model"; then
      return 0
    fi
    if [ "$attempt" -lt "$PROBE_ATTEMPTS" ]; then
      echo "::notice::Probe for OpenCode model '${model}' failed (attempt ${attempt}/${PROBE_ATTEMPTS}) - retrying in ${PROBE_BACKOFF}s ..." >&2
      sleep "$PROBE_BACKOFF"
    fi
    attempt=$((attempt + 1))
  done
  return 1
}

selected=""
round=1
while [ -z "$selected" ] && [ "$round" -le "$CHAIN_ROUNDS" ]; do
  if [ "$round" -gt 1 ]; then
    echo "::notice::No usable OpenCode model in round $((round - 1)) - retrying whole chain in ${CHAIN_RETRY_WAIT}s ..." >&2
    sleep "$CHAIN_RETRY_WAIT"
  fi
  while IFS= read -r model; do
    model="${model//[[:space:]]/}"
    [ -n "$model" ] || continue
    if probe_with_retry "$model"; then
      selected="$model"
      break
    fi
    echo "::warning::OpenCode model '${model}' not usable, trying next candidate ..." >&2
  done < <(tr ',' '\n' <<<"$CHAIN")
  round=$((round + 1))
done

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
