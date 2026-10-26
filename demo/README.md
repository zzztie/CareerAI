# CareerAI U54 本地可视化演示

这个页面把 CareerHub 的登录、简历和岗位接口，与 CareerAI 的指定岗位 RAG 对比串成一条可操作的演示流程。页面使用真实 Java API；anonymous-resume.pdf 和 anonymous-job.txt 是完全虚构的样例。当前仅供本机演示，不是公网服务。

## 业务流程

1. 用演示账号登录 CareerHub，取得 JWT。页面只在当前内存中保存 Token，刷新后需重新登录。
2. 读取当前用户的简历，选择一份匿名 PDF；从 CareerHub 搜索并选择一条在招岗位。
3. 点击分析后，浏览器向 CareerAI 发送 resumeId、jobId、问题和 Token。
4. CareerAI 带 Token 从 CareerHub 下载 PDF，读取选中岗位 JD；提取文字、切块、计算向量，并检索简历 [R]、JD [J] 和知识库 [K] 片段。
5. 模型根据片段作答，页面呈现回答和来源。简历未提及的能力只能说“当前简历没有证据”。

    浏览器
       ↓ 同源 HTTP :3000
    本地演示页面 / Nginx
       ├── /hub → CareerHub → MySQL、Redis、PDF 数据卷
       └── /careerai → CareerAI → Ollama、pgvector、外部大模型
                              └── 携带用户 Token 回到 CareerHub 读取 PDF 与 JD

## 演示数据

- anonymous-resume.pdf：虚构候选人的 Java 后端实习简历。
- anonymous-job.txt：虚构岗位 JD；必要条件包含 Java、Spring Boot、MySQL、Redis、Linux 与问题排查，加分项单独列出。
- seed-demo-job.sql：可重复执行的岗位导入 SQL。运行 bash demo/seed-job.sh；输出的岗位 ID 由数据库决定，不能在全新环境中假定总是 1。
- register-and-upload.ps1：在 Windows PowerShell 中提示用户自行输入演示密码，注册或登录 CareerHub 并上传匿名 PDF。只输出用户和简历 ID；不会保存密码或 Token。
- compare-inside-wsl.py：在 WSL 内直接调用两项目，用于排除 Windows 端口转发问题。

`seed-job.sh` 会查询并打印实际岗位 ID；上传脚本会打印实际简历 ID。调用对比脚本时把这两个 ID 明确传入，例如 PowerShell 中使用 `-ResumeId <简历ID> -JobId <岗位ID>`，WSL 中使用 `--resume-id <简历ID> --job-id <岗位ID>`，不要假定它们都是 1。

请勿在演示中使用真实简历、真实招聘资料、真实 Token 或任何生产环境密钥。

## 启动页面

先按 ../README-docker.md 启动并验证 U53 的 Compose 核心服务。当前机器复用已有 Ollama 模型卷时，在 Ubuntu/WSL 执行：

    cd /mnt/d/projects/CareerAI
    sudo docker compose -f compose.yaml -f compose.reuse-ollama.yaml -f compose.demo.yaml up -d --no-deps demo-web
    sudo docker compose -f compose.yaml -f compose.reuse-ollama.yaml -f compose.demo.yaml ps demo-web
    curl -I http://127.0.0.1:3000/

在能够正常下载 bge-m3 的全新环境中，可把上述命令中的 -f compose.reuse-ollama.yaml 去掉。页面地址为 http://127.0.0.1:3000/。demo-web 只发布到本机回环地址。Nginx 使用 /hub/ 和 /careerai/ 两个前缀分别转发请求；浏览器无需直接访问 Windows 的 8081，也不需要额外开放跨域权限。

也可在 WSL 中使用 `sudo bash demo/start-demo.sh --reuse-ollama --web-only` 启动已运行的本机栈上的页面；首次从空环境启动时使用 `sudo bash demo/start-demo.sh`，前提是已按 Docker 文档准备 `.env`。复用模型卷前，先停止旧的独立 Ollama 容器。脚本只启动服务，不会自动导入岗位或创建账号。

## 主要接口

接口参数、响应与权限边界详见 [演示主链路 API](../docs/api.md)。

| 页面动作 | 路径 | 服务 |
| --- | --- | --- |
| 登录 | POST /hub/auth/login | CareerHub |
| 当前用户 | GET /hub/user/profile | CareerHub |
| 简历列表 / 上传 | GET /hub/resumes?userId=... / POST /hub/resumes/upload?userId=... | CareerHub |
| 岗位搜索 | GET /hub/jobs/search?keyword=Java&status=OPEN&page=1&size=10 | CareerHub |
| 指定岗位分析 | POST /careerai/ai/rag/compare/{resumeId}/{jobId} | CareerAI |

## 当前限制与待验收

完整的问题清单见 [Known Issues](../docs/known-issues.md)。

- U54 页面已在本机通过 Nginx 打开。浏览器用匿名样例完成登录、读取简历、选择演示岗位与 RAG 分析，回答含 [R]、[J]、[K] 来源。页面刷新后需重新登录。
- 当前 RAG 对比每次都会重新计算并写入简历、JD 和知识库向量，首次响应可能较慢。
- 模型回答有来源，但仍需人工核对。例如样例简历提到“整理错误码和测试用例”，这不能直接证明完成过完整的问题排查。
- 新 Compose 数据库为空，需要先导入虚构岗位、注册演示用户并上传 PDF。
- DeepSeek 等外部模型请求需要本机可用的密钥与网络。真实 .env、演示密码、Token 与运行日志都不能进入公开仓库。
- 这是本地作品演示。公开 README、截图和录屏可以独立发布；公网可交互服务尚未部署。
- 匿名截图请保存到 [screenshots](screenshots/README.md)，并在发布前检查画面不含密码、Token 或真实个人资料。
- CareerHub 当前仓库的配置文件与历史记录尚需脱敏审查；请勿直接将现有仓库设为公开。
