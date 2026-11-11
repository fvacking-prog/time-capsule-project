#!/usr/bin/env bash
set -euo pipefail

generate_key() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -base64 32 | tr -d '\r\n'
    elif command -v python3 >/dev/null 2>&1; then
        python3 -c 'import os, base64; print(base64.b64encode(os.urandom(32)).decode("ascii"), end="")'
    else
        head -c 32 /dev/urandom | base64 | tr -d '\r\n'
    fi
}

KEY="$(generate_key)"

if [ "${1:-}" = "--env" ]; then
    printf 'CAPSULE_AES_KEY=%s\n' "$KEY"
else
    printf '%s\n' "$KEY"
    if [ -t 1 ]; then
        printf '# Скопируйте ключ в .env или выполните: export CAPSULE_AES_KEY=%s\n' "$KEY" >&2
    fi
fi

