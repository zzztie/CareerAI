-- U54 fictional demonstration job. Safe to run repeatedly.
INSERT INTO job (title, company, description, status, create_time, update_time)
SELECT
  'Java 后端实习生（演示岗位）',
  '示例科技（完全虚构）',
  '岗位职责：参与 Spring Boot 后端接口开发，维护用户与岗位业务；使用 MySQL 完成数据查询和表结构设计，定位常见接口问题；在导师指导下参与代码评审与技术文档整理。
必要条件：计算机相关专业本科在读，掌握 Java 基础；能使用 Spring Boot 开发 REST API，能编写基础 SQL；了解 Redis 的基本使用和常见缓存场景；具备 Linux 基础操作能力，能说明至少一个问题排查过程。
加分项：了解消息队列或分布式系统基础；有 Docker、自动化测试或团队协作实践。
本岗位完全虚构，仅用于 CareerHub / CareerAI 功能演示。',
  'OPEN',
  NOW(),
  NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM job
  WHERE title = 'Java 后端实习生（演示岗位）'
    AND company = '示例科技（完全虚构）'
);
SELECT id, title, company, status
FROM job
WHERE title = 'Java 后端实习生（演示岗位）'
  AND company = '示例科技（完全虚构）'
ORDER BY id DESC
LIMIT 1;