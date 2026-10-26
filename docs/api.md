# U54 演示主链路 API

本页只列出演示网页实际使用的接口。打开 `http://127.0.0.1:3000/` 时，Nginx 把 `/hub/` 转给 CareerHub、把 `/careerai/` 转给 CareerAI。下表使用浏览器看到的路径；直接调用服务时需去掉相应前缀，CareerHub 默认端口为 8080，CareerAI 默认端口为 8081。演示 ID 由数据库生成，不能假定总是 1。

| 步骤 | 方法与路径 | 请求要点 | 返回要点 |
| --- | --- | --- | --- |
| 登录 | `POST /hub/auth/login` | JSON：`email`、`password` | `data.token`，后续作为 Bearer Token |
| 当前用户 | `GET /hub/user/profile` | `Authorization: Bearer <token>` | `data.id` 等用户资料 |
| 简历列表 | `GET /hub/resumes?userId=<id>` | Bearer Token；`userId` 必须属于当前用户 | 简历数组，包含 `id`、`filename` 等 |
| 上传 PDF | `POST /hub/resumes/upload?userId=<id>` | Bearer Token；multipart 字段 `file`；PDF 不超过 5 MiB | 新简历的 `id` |
| 搜索岗位 | `GET /hub/jobs/search?keyword=Java&status=OPEN&page=1&size=10` | 关键词、状态和分页 | `records` 岗位数组 |
| 指定岗位分析 | `POST /careerai/ai/rag/compare/{resumeId}/{jobId}` | Bearer Token；JSON：`question` | JSON：`answer`，其中可含 `[R]`、`[J]`、`[K]` 来源 |

## 一次分析请求

```http
POST /careerai/ai/rag/compare/{resumeId}/{jobId}
Authorization: Bearer <登录取得的 Token>
Content-Type: application/json; charset=utf-8

{"question":"请分别说明有证据的必要条件、当前简历没有证据的必要条件和加分项，并引用来源。"}
```

成功响应示意：

```json
{"answer":"……简历证据 [R1]；岗位要求 [J1]……\n检索到的来源：……"}
```

`[R]` 指简历片段，`[J]` 指本次选中岗位的 JD 片段，`[K]` 指通用知识库片段。回答由模型生成，引用与结论仍需人工核对；“简历未写”只表示当前没有证据。

## 边界与失败处理

- CareerHub 根据 Token 校验简历归属；CareerAI 将请求中的 Token 转发给 CareerHub 下载指定 PDF。没有有效 Token 时无法读取简历。
- `resumeId`、`jobId` 必须为正数，`question` 不能为空；参数不合要求时返回 400，缺失登录凭证时返回 401。
- 页面显示登录失效、上传过大、后端不可用等错误。RAG 可能因数据库、Ollama 或外部模型不可用而失败；HTTP 200 与生成回答也不代表模型结论已被事实验证。
- `POST /ai/agent/skill-gap` 是另一条按求职目标自主搜索岗位的流程，不等同于此处指定 `jobId` 的分析；异步知识导入也不在该演示页中操作。
- 当前简历接口返回领域对象，可能包含服务端存储路径。公开部署前应改成仅暴露所需字段的响应 DTO，并完成隐私与权限复核。

实现位置：CareerHub 的 `AuthController`、`ProfileController`、`ResumeController`、`JobController` 与 `ResumeServiceImpl`；CareerAI 的 `RagQaController`、`CareerHubRagQaService`。
