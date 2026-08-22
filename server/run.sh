#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ ! -d .venv ]]; then
  python3 -m venv .venv
  .venv/bin/pip install -r requirements.txt
fi
# 必须 0.0.0.0，手机才能通过局域网 IP 访问
exec .venv/bin/uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
