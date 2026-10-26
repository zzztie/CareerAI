#!/usr/bin/env python3
"""U54 local-only RAG demo. No password or token is written to disk."""
import argparse
import getpass
import json
import time
import urllib.error
import urllib.request
from pathlib import Path

DEMO = Path(__file__).resolve().parent
EMAIL = "careerai-u54-demo@example.invalid"


def positive_id(value):
    number = int(value)
    if number <= 0:
        raise argparse.ArgumentTypeError("ID must be greater than zero")
    return number


parser = argparse.ArgumentParser(description="Run the local CareerAI RAG demo")
parser.add_argument("--resume-id", required=True, type=positive_id)
parser.add_argument("--job-id", required=True, type=positive_id)
args = parser.parse_args()

def post_json(url, payload, headers=None, timeout=300):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json; charset=utf-8", **(headers or {})},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        output = DEMO / "rag-error-response.txt"
        output.write_text(body, encoding="utf-8")
        raise SystemExit(f"HTTP {error.code}; response saved to {output}") from None

password = getpass.getpass("U54 demo account password: ")
login = post_json(
    "http://127.0.0.1:8080/auth/login",
    {"email": EMAIL, "password": password},
    timeout=15,
)
del password
token = login.get("data", {}).get("token")
if not token:
    raise SystemExit("Login returned no token.")

question = (
    "请依据这份简历和岗位 JD，分别列出有证据的必要条件、当前简历没有证据的必要条件，"
    "以及加分项；引用 [R] 和 [J] 来源，不要把简历未写当成候选人不会。"
)
started = time.monotonic()
result = post_json(
    f"http://127.0.0.1:8081/ai/rag/compare/{args.resume_id}/{args.job_id}",
    {"question": question},
    headers={"Authorization": f"Bearer {token}"},
    timeout=300,
)
del token
answer = result.get("answer")
if not answer:
    raise SystemExit("Comparison returned no answer.")
output = DEMO / "last-compare-answer.txt"
output.write_text(answer, encoding="utf-8")
print(f"elapsedSeconds={time.monotonic() - started:.1f}")
print(f"answerFile={output}")
print(answer)
