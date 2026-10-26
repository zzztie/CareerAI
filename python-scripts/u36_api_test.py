import requests


response = requests.get(
    "http://localhost:8080/jobs/1"
)


print(response.status_code)

job = response.json()

print(job["title"])