job = {
    "id":1,
    "title":"Java Backend Intern",
    "salary":5000,
    "skills":["Java","Spring Boot","MySql"]
}

def show_job(job):
    print("岗位名称:",job["title"])
    print("实习薪资:",job["salary"])
    print("岗位技能:")
    for skill in job["skills"]:
        print("-",skill)

show_job(job)

jobs = [
    {
        "title": "Java Backend Intern",
        "score": 95
    },
    {
        "title": "Python AI Intern",
        "score": 88
    },
    {
        "title": "Frontend Intern",
        "score": 75
    }
]

def find_best_job(jobs):
    best = jobs[0]
    for job in jobs:
        if job["score"] > best["score"]:
            best = jobs
    return best

result = find_best_job(jobs)
print(result)

import json


response = """
{
    "username": "Tom",
    "skills": [
        "Java",
        "Redis",
        "Spring Boot"
    ],
    "matchScore": 92
}
"""

def analyze_result(response):
    job = json.loads(response)
    print("用户:",job["username"])
    print("匹配分数:",job["matchScore"])
    print("技能:")
    for skill in job["skills"]:
        print("-",skill)

analyze_result(response)
