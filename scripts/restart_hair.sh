#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVER="$ROOT/server"
cd "$SERVER"

echo "强制重建 .venv（旧路径 shebang 已失效）..."
rm -rf .venv
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt

# 确认 shebang 指向新路径
head -1 .venv/bin/uvicorn

mkdir -p supervisor
cat > supervisor/hair.conf <<'EOF'
[program:hair]
directory=/root/hair-clinic/server
command=/root/hair-clinic/server/.venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000
autostart=true
autorestart=true
startsecs=3
stopwaitsecs=10
stdout_logfile=/var/log/supervisor/hair.stdout.log
stderr_logfile=/var/log/supervisor/hair.stderr.log
stdout_logfile_maxbytes=10MB
stderr_logfile_maxbytes=10MB
stdout_logfile_backups=3
stderr_logfile_backups=3
EOF

cp supervisor/hair.conf /etc/supervisor/conf.d/hair.conf
supervisorctl reread
supervisorctl update
supervisorctl stop hair 2>/dev/null || true
sleep 1
# FATAL 后要用 start，不要用 restart
supervisorctl start hair
sleep 3
supervisorctl status hair
echo '---'
head -1 .venv/bin/uvicorn
curl -sI http://127.0.0.1:8000/download/hairclinic.apk | head -5
curl -s http://127.0.0.1:8000/api/app/info
echo
