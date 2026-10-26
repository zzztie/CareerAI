#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

reuse_ollama=false
web_only=false
for arg in "$@"; do
  case "$arg" in
    --reuse-ollama) reuse_ollama=true ;;
    --web-only) web_only=true ;;
    *) echo "未知参数：$arg" >&2; echo "用法：sudo bash demo/start-demo.sh [--reuse-ollama] [--web-only]" >&2; exit 2 ;;
  esac
done

files=(-f compose.yaml)
if "$reuse_ollama"; then
  files+=(-f compose.reuse-ollama.yaml)
fi
files+=(-f compose.demo.yaml)

docker compose "${files[@]}" config --quiet
if "$web_only"; then
  docker compose "${files[@]}" up -d --no-deps demo-web
else
  if "$reuse_ollama"; then
    echo "复用模型卷：请先确认旧的 careerai-ollama 容器已停止。" >&2
  fi
  docker compose "${files[@]}" up --build -d
fi

docker compose "${files[@]}" ps
echo "演示地址：http://127.0.0.1:3000/"
