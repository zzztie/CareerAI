# CareerHub + CareerAI 架构与边界

本图对应当前本机 Docker Compose 版本。CareerHub 负责账号、岗位和 PDF 简历；CareerAI 负责模型分析、RAG 检索、Agent 工作流和异步知识导入。两个服务通过 HTTP 协作，分别运行。

```mermaid
flowchart LR
    Browser[浏览器 / U54 演示页] --> Nginx[本地 Nginx :3000]
    Nginx --> Hub[CareerHub :8080]
    Nginx --> AI[CareerAI :8081]
    Hub --> HubDB[(MySQL: careerhub)]
    Hub --> Redis[(Redis)]
    Hub --> PDF[(简历 PDF 数据卷)]
    AI -->|携带用户 Token 读取 PDF / JD| Hub
    AI --> AiDB[(MySQL: careerai)]
    AI --> PG[(PostgreSQL / pgvector)]
    AI --> Ollama[Ollama: bge-m3 向量模型]
    AI --> DeepSeek[外部大模型接口]
    AI --> MQ[(RabbitMQ)]
    MQ --> Consumer[CareerAI 知识导入消费者]
    Consumer --> Ollama
    Consumer --> PG
```

## 指定岗位 RAG 对比

```mermaid
sequenceDiagram
    participant U as 浏览器
    participant N as Nginx
    participant A as CareerAI
    participant H as CareerHub
    participant O as Ollama
    participant P as pgvector
    participant M as 外部大模型
    U->>N: resumeId + jobId + question + Token
    N->>A: POST /ai/rag/compare/{resumeId}/{jobId}
    A->>H: 带 Token 下载 PDF；读取选中岗位 JD
    H-->>A: PDF 字节与 JD 原文
    A->>O: 简历、JD、知识库切块后计算向量
    A->>P: 保存并检索相关片段
    P-->>A: [R]、[J]、[K] 证据
    A->>M: 问题 + 证据片段
    M-->>A: 引用来源的回答
    A-->>N: answer
    N-->>U: 分析与来源列表
```

检索时简历与岗位 JD 各自必须有相关片段；知识库只能提供通用建议，不能替代简历经历或补写 JD 要求。当前实现每次请求都会重建上述文档向量，属于已知性能限制。

## 其他两条独立流程

- Agent 岗位分析：POST /ai/agent/skill-gap 接收 resumeId 和 goal；模型提出搜索或读取动作，Java 校验次数与岗位范围，最终返回结构化报告。它不接收指定 jobId，也不走上述 RAG 检索链路。
- 异步知识导入：上传接口保存原文件与任务记录，向 RabbitMQ 发布任务；CareerAI 消费者提取文本、切块、调用 Ollama 生成向量、写入 pgvector，任务状态供后续查询。RabbitMQ 不负责计算向量。

## 当前部署状态

Compose 已在本机启动核心服务，指定岗位 RAG 在 WSL 内使用虚构简历和 JD 完成一次端到端测试。U54 演示页面的 Nginx 容器与浏览器完整链路仍待验证。公网服务、生产监控和自动化发布尚未完成；本架构图不表示已对外上线。
