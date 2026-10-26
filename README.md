# CareerAI｜从简历到有依据的岗位分析

CareerAI 是一个 Java 后端实践项目：它向独立的 [CareerHub-showcase](https://github.com/zzztie/CareerHub-showcase) 服务读取用户授权的简历 PDF 与岗位 JD，提取原文、检索相关证据，再生成可核对来源的分析。项目还实现了目标岗位 Agent、异步知识导入和本地 Docker Compose 演示环境。

**当前状态：本机联调与作品展示阶段。** 已完成核心服务启动、匿名样例的指定岗位 RAG 端到端测试，以及本地可视化页面的登录、简历读取、岗位选择和分析验证；尚未对外上线。

## 面向用户的一条主线

1. 用户在 CareerHub 登录并上传 PDF 简历；CareerHub 保存文件、元数据并校验归属。
2. 用户选择 CareerHub 中的一条在招岗位。
3. CareerAI 接收 resumeId、jobId、问题与登录 Token，向 CareerHub 下载 PDF 和 JD。
4. CareerAI 将原文切块，调用 Ollama bge-m3 计算向量，在 PostgreSQL/pgvector 中检索简历 [R]、岗位 [J] 和知识库 [K] 证据。
5. 大模型依据检索片段回答，展示匹配点、缺少的证据以及来源。简历未写某项技能时，输出“当前没有证据”，不推断候选人不会。

## 核心能力与代码边界

| 能力 | 已实现的职责 | 入口 |
| --- | --- | --- |
| 指定岗位 RAG 对比 | 下载指定简历和 JD、切块、向量检索、引用来源作答 | POST /ai/rag/compare/{resumeId}/{jobId} |
| 目标岗位 Agent | 按求职目标有限次搜索与读取岗位，Java 限制步骤、候选岗位和错误边界 | POST /ai/agent/skill-gap |
| 异步知识导入 | 上传原文件、创建任务、经 RabbitMQ 消费、生成向量并查询任务状态 | POST /ai/knowledge/import |
| 简历解析与其他 AI 接口 | PDF 文本提取、模型解析、会话与消息等实验性能力 | careerai-server/src/main/java |
| 本地部署 | CareerHub、CareerAI、MySQL、Redis、pgvector、RabbitMQ、Ollama 联合启动 | compose.yaml |

Agent 与指定岗位 RAG 是不同链路：Agent 接收 resumeId 和 goal，自行选择岗位；RAG 接收明确的 jobId，比较一份指定 JD。RabbitMQ 只传递异步任务，向量由 Ollama 计算。

## 架构与运行

- [架构图与数据流](docs/architecture.md)
- [演示主链路 API](docs/api.md)
- [Known Issues 与公开准备边界](docs/known-issues.md)
- [代码注释与日志约定](docs/code-style.md)
- [Docker Compose 启动与排错](README-docker.md)
- [本地可视化演示、匿名样例与接口对应](demo/README.md)
- [匿名演示截图](demo/screenshots/README.md)

项目目录：

    CareerAI/
    ├── careerai-server/       Spring Boot 服务
    ├── compose.yaml           本地核心服务
    ├── compose.reuse-ollama.yaml  复用当前机器已有模型卷
    ├── compose.demo.yaml      U54 本地演示页面
    ├── demo/                  虚构样例、演示脚本与页面
    └── docs/architecture.md  系统与调用链路

全新 Compose 数据库不会自动导入原 IDEA 环境中的简历和岗位。演示数据必须单独初始化；演示 ID 不应写死在公共文档中。
演示启动脚本位于 [demo/start-demo.sh](demo/start-demo.sh)，支持新环境与本机复用 Ollama 模型卷两种方式。

## 面试中如何演示

1. 用 30 秒说明场景：简历与岗位匹配结论需要回到原文核对。
2. 展示匿名简历、虚构 JD 与登录后的页面；选择一条岗位。
3. 发起一次 RAG 对比，指出 [R]、[J]、[K] 分别来自什么文档，以及“无证据”和“不会”的区别。
4. 打开架构图，解释 CareerHub 的权限边界、CareerAI 的检索与模型调用、Ollama 和 pgvector 的职责。
5. 如实说明限制：每次对比重建向量、模型可能把弱证据判断过强、外部模型依赖网络和密钥。

## 验证与限制

- 两个 Java 项目分别通过本地 Maven 跳过测试打包；Compose 镜像构建和核心服务启动已在本机验证。
- 匿名简历与虚构 JD 的指定岗位 RAG 对比已从 WSL 内成功返回含 [R]、[J]、[K] 来源的回答。此结果不代表任何真实候选人的匹配程度。
- U54 本地演示页面已通过 Nginx 在 http://127.0.0.1:3000/ 打开。浏览器已用匿名样例完成登录、读取简历、选岗位、发起 RAG 分析，显示含 [R]、[J]、[K] 来源的回答。
- 本项目不是公网生产部署。真实 .env、Token、密码、用户 PDF 与本地运行日志不应发布。
- RAG 回答仍需人工核对；当前样例中“整理错误码与测试用例”不足以证明完整问题排查经历，这类边界是后续评测与提示优化重点。
- 本仓库和配套的 CareerHub-showcase 使用各自独立的 Git 历史；真实 `.env`、本机配置、调试请求、运行日志和用户上传文件不属于展示版本。两套 Java 服务目前仅在本机演示，尚未公网部署。

CareerHub 是独立项目，负责用户、岗位、简历和认证。CareerAI 不复制其身份数据，而是在读取简历时转发当前用户的 Token，由 CareerHub 决定是否允许访问。
