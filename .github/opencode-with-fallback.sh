#!/usr/bin/env bash
# Runs FlutLink's OpenCode CI commands against the first reachable Zen model.
#
# The model chain comes from $OPENCODE_MODELS (highest priority first, comma
# separated). Every entry is probed with a tiny chat completion before the
# real agent run, so catalog removals and upstream outages fall through to
# the next candidate instead of failing the workflow.
#
# Usage:
#   .github/opencode-with-fallback.sh [--pick-only] [--auto] [opencode-run-args...]
#
#   --pick-only   print "opencode/<id>" or "puter/<id>" of the first usable
#                 model and exit (for action inputs that take a model string)
#   --auto        explicit run mode (default). Recognised so workflow callers
#                 can state their intent without passing an unknown flag to
#                 `opencode run`; it does not change behaviour.
#
# Providers: a chain entry "opencode/<id>" (default; historically just
# "<id>") is probed against the OpenCode Zen API; a chain entry "puter/<id>"
# is probed against Puter's OpenAI-compatible endpoint. Puter entries are the
# token-gated last-resort fallback for the agentic opencode runs: they are
# only probed when PUTER_AUTH_TOKEN is set, otherwise skipped with a notice
# so a repo without the secret behaves exactly as before (Zen only).
#
# A puter/<id> pick only works if the project opencode config declares the
# "puter" provider. That provider lives in the tracked
# .opencode/opencode.json, so a CI checkout already carries it. As a safety
# net, whenever a puter model is selected this script ensures the provider is
# present in "$PWD/.opencode/opencode.json" (no-op when already configured,
# so the tracked file never gets dirtied in CI).
#
# Exit codes:
#   0  a usable model was found (and run, unless --pick-only)
#   1  no usable model in the whole chain after all rounds (see below). In
#      --pick-only mode nothing is printed, so callers MUST guard on the
#      empty/absent model (e.g. `MODEL=$(... ) || true; echo "model=$MODEL"`).
#
# The probed model is ALWAYS echoed as a workflow notice whether or not the
# caller picked it, so CI logs are greppable:
#   ::notice::OpenCode model selected: opencode/<id>
#   ::notice::Puter model selected: puter/<id>
#
# Environment:
#   OPENCODE_MODELS  model chain (default: big-pickle, mimo-v2.5-free,
#                    hy3-free, nemotron-3-ultra-free, laguna-s-2.1-free).
#                    Use "puter/<id>" entries to add the Puter fallback.
#   OPENCODE_CMD     command prefix executed in run mode, split on
#                    whitespace, WITHOUT the `run` subcommand
#                    (default: "opencode"; e.g. "npx --yes opencode-ai@1.18.29")
#   ZEN_BASE         Zen API base (default: https://opencode.ai/zen/v1)
#   OPENCODE_API_KEY used to authenticate the Zen probe requests
#   PUTER_BASE       Puter OpenAI-compatible base (default:
#                    https://api.puter.com/puterai/openai/v1)
#   PUTER_AUTH_TOKEN used to authenticate the Puter probe requests; if unset,
#                    every "puter/<id>" chain entry is skipped (token gate).
#
# Transient upstream hiccups must not kill a release pipeline: every model is
# probed OPENCODE_PROBE_ATTEMPTS times (backoff OPENCODE_PROBE_BACKOFF s), and
# the whole chain is walked OPENCODE_CHAIN_ROUNDS times with a
# OPENCODE_CHAIN_RETRY_WAIT s pause between rounds before giving up.
set -euo pipefail

CHAIN="${OPENCODE_MODELS:-big-pickle,mimo-v2.5-free,hy3-free,nemotron-3-ultra-free,laguna-s-2.1-free}"
ZEN_URL="${ZEN_BASE:-https://opencode.ai/zen/v1}/chat/completions"
PUTER_URL="${PUTER_BASE:-https://api.puter.com/puterai/openai/v1}/chat/completions"
PROBE_ATTEMPTS="${OPENCODE_PROBE_ATTEMPTS:-2}"
PROBE_BACKOFF="${OPENCODE_PROBE_BACKOFF:-15}"
CHAIN_ROUNDS="${OPENCODE_CHAIN_ROUNDS:-2}"
CHAIN_RETRY_WAIT="${OPENCODE_CHAIN_RETRY_WAIT:-60}"

PICK_ONLY=0
# Collect recognised flags; everything else is forwarded to `opencode run`.
declare -a RUN_ARGS=()
for arg in "$@"; do
  case "$arg" in
    --pick-only) PICK_ONLY=1 ;;
    --auto) ;; # explicit run-mode alias (default); recognised, but a no-op
    *) RUN_ARGS+=("$arg") ;;
  esac
done

# probe(model): POST a tiny chat completion, 200 => usable. Routes by prefix:
#   opencode/  -> Zen URL with OPENCODE_API_KEY
#   puter/     -> Puter URL with Bearer PUTER_AUTH_TOKEN
#   bare id    -> Zen URL (legacy/default, treated as opencode/<id>)
probe() {
  local raw="$1" url auth code body escaped
  body="$(mktemp)"
  if [[ "$raw" == puter/* ]]; then
    # Token gate: without PUTER_AUTH_TOKEN a Puter entry is unsatisfiable and
    # is skipped entirely rather than probed against an unauthenticated URL.
    if [ -z "${PUTER_AUTH_TOKEN:-}" ]; then
      rm -f "$body"
      return 1
    fi
    url="$PUTER_URL"
    auth="Bearer ${PUTER_AUTH_TOKEN}"
  else
    url="$ZEN_URL"
    auth="Bearer ${OPENCODE_API_KEY:-}"
  fi
  # The wire model id is the part after the provider prefix.
  local wire="${raw#*\/}"
  # Escape for safe JSON embedding (prevent injection).
  escaped=$(printf '%s' "$wire" | sed 's/\\/\\\\/g; s/"/\\"/g; s/\//\\\//g')
  code="$(curl -sS -m 30 -o "$body" -w '%{http_code}' -X POST "$url" \
    -H "Authorization: ${auth}" \
    -H "Content-Type: application/json" \
    -d '{"model":"'"$escaped"'","messages":[{"role":"user","content":"Reply with the single word OK."}],"max_tokens":5}' 2>/dev/null || echo 000)"
  rm -f "$body"
  [ "$code" = "200" ]
}

# provider(model): canonical qualified id ("opencode/<id>" | "puter/<id>").
provider() {
  if [[ "$1" == puter/* ]]; then echo "puter"; else echo "opencode"; fi
}

# ensure_puter_config(): write or merge the "puter" provider into the project
# opencode config (.opencode/opencode.json). Called only when a puter model is
# selected. The provider lives in the repo's TRACKED .opencode/opencode.json,
# so a fresh CI checkout already carries it — in that case this is a no-op to
# avoid dirtying the working tree. It only (re)writes when the provider is
# missing (e.g. a gitignored local file that predates the provider), so
# `opencode run --model puter/<id>` can still resolve the provider.
ensure_puter_config() {
  local cfg=".opencode/opencode.json"
  local base="${PUTER_BASE:-https://api.puter.com/puterai/openai/v1}"
  if command -v node >/dev/null 2>&1; then
    PUTER_BASE_JSON="$base" node - "$cfg" <<'EOF' || return 1
const fs = require("fs");
const path = require("path");
const cfg = process.argv[2];
let json = {};
try { json = JSON.parse(fs.readFileSync(cfg, "utf8")); } catch { /* fresh file */ }
const existing = json.provider && json.provider.puter;
if (existing && existing.options && existing.options.apiKey === "{env:PUTER_AUTH_TOKEN}") {
  process.exit(0); // already configured (tracked project config) - keep tree clean
}
json.provider = json.provider || {};
json.provider.puter = {
  npm: "@ai-sdk/openai-compatible",
  name: "Puter (OpenAI-compatible)",
  options: { baseURL: process.env.PUTER_BASE_JSON, apiKey: "{env:PUTER_AUTH_TOKEN}" },
  models: { "gpt-5-nano": { name: "gpt-5-nano" } },
};
fs.mkdirSync(path.dirname(cfg), { recursive: true });
fs.writeFileSync(cfg, JSON.stringify(json, null, 2) + "\n");
EOF
    return $?
  fi
  if [ -f "$cfg" ]; then
    echo "::warning::node fehlt - puter provider kann nicht in vorhandene $cfg gemerged werden" >&2
    return 1
  fi
  mkdir -p .opencode
  {
    printf '{\n  "provider": {\n    "puter": {\n'
    printf '      "npm": "@ai-sdk/openai-compatible",\n      "name": "Puter (OpenAI-compatible)",\n'
    printf '      "options": { "baseURL": "%s", "apiKey": "{env:PUTER_AUTH_TOKEN}" },\n' "$base"
    printf '      "models": { "gpt-5-nano": { "name": "gpt-5-nano" } }\n    }\n  }\n}\n'
  } > "$cfg"
}

# A single probe can fail transiently (rate limit, network blip, brief
# upstream outage) — retry a few times before declaring the model unusable.
probe_with_retry() {
  local model="$1" attempt=1 prov
  prov="$(provider "$model")"
  while [ "$attempt" -le "$PROBE_ATTEMPTS" ]; do
    if probe "$model"; then
      return 0
    fi
    if [ "$attempt" -lt "$PROBE_ATTEMPTS" ]; then
      echo "::notice::Probe for ${prov} model '${model}' failed (attempt ${attempt}/${PROBE_ATTEMPTS}) - retrying in ${PROBE_BACKOFF}s ..." >&2
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
    echo "::notice::No usable model in round $((round - 1)) - retrying whole chain in ${CHAIN_RETRY_WAIT}s ..." >&2
    sleep "$CHAIN_RETRY_WAIT"
  fi
  while IFS= read -r model; do
    model="${model//[[:space:]]/}"
    [ -n "$model" ] || continue
    # Bare ids are treated as OpenCode/Zen entries for backwards compatibility.
    if [[ "$model" != opencode/* && "$model" != puter/* ]]; then
      model="opencode/${model}"
    fi
    if [[ "$model" == puter/* ]] && [ -z "${PUTER_AUTH_TOKEN:-}" ]; then
      # Token gate: no secret configured -> skip Puter entries with a clear
      # notice instead of probing an unauthenticated endpoint.
      echo "::notice::Puter-Fallback ${model} uebersprungen (PUTER_AUTH_TOKEN nicht gesetzt)" >&2
      continue
    fi
    if probe_with_retry "$model"; then
      selected="$model"
      break
    fi
    echo "::warning::$(provider "$model") model '${model}' not usable, trying next candidate ..." >&2
  done < <(tr ',' '\n' <<<"$CHAIN")
  round=$((round + 1))
done

if [ -z "$selected" ]; then
  echo "::error::No usable model in chain [${CHAIN}] - OpenCode-Zen: https://opencode.ai/zen/v1/models, Puter: https://developer.puter.com/ai/models/" >&2
  echo "::error::Pick step produced no model - callers must treat an empty/absent model as a hard skip." >&2
  exit 1
fi

echo "::notice::$(provider "$selected") model selected: ${selected}" >&2

if [[ "$selected" == puter/* ]]; then
  ensure_puter_config
fi

if [ "$PICK_ONLY" = "1" ]; then
  echo "${selected}"
  exit 0
fi

read -r -a cmd <<<"${OPENCODE_CMD:-opencode}"
echo "Running OpenCode with model: ${selected}" >&2
exec "${cmd[@]}" run --model "${selected}" "${RUN_ARGS[@]}"
