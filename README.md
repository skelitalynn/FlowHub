# FlowHub

面向高并发场景的后端工程实践项目，覆盖缓存策略、地理检索、秒杀下单、流式订单处理与鉴权。目标是以真实业务链路演练系统设计与工程化能力，并为 AI/Agent 能力扩展预留接口与数据通道。

## 项目亮点（已实现）
- **Token + Redis 鉴权**：登录态存 Redis，拦截器刷新 TTL
- **多级缓存策略**：缓存穿透/击穿治理，逻辑过期与互斥锁重建
- **地理检索**：基于 Redis GEO 的附近商铺分页查询
- **秒杀链路**：库存扣减 + 异步下单，保证高并发下可用性
- **Redis Streams**：订单流异步消费与 pending-list 兜底

## 业务能力（当前覆盖）
- 商铺查询/更新（含缓存更新）
- 商铺类型查询
- 博客与评论基础链路
- 用户登录/签到/基础信息
- 秒杀券下单

## 规划路线（占位）
### AI / Agent
- 语义检索：自然语言搜索店铺（Embedding + 向量检索）
- 推荐理由：对推荐结果生成可解释说明（LLM）
- 生活助手 Agent：多步骤调用（查店铺/下单/订单状态）

### 实时与工程化
- 热度榜与趋势分析（Streams/Kafka/Flink）
- Docker Compose 一键启动
- 可观测性：日志规范、TraceId、指标面板
- 接口测试与压测脚本

## 技术栈
- Spring Boot 2.x
- MyBatis-Plus
- Redis / Redis Streams
- MySQL
- Maven

## 运行环境
- JDK 8+
- MySQL 5.7+
- Redis 6.x
- Maven 3.6+

## 快速开始
### 1) 导入数据库
SQL 文件：`src/main/resources/db/flowhub.sql`

### 2) 修改配置
`src/main/resources/application.yaml`

### 3) 启动
```bash
mvn spring-boot:run
```
主类：`com.flowhub.HmDianPingApplication`

## 目录结构
```
src/main/java/com/flowhub/
  controller/
  service/
  mapper/
  entity/
  dto/
  utils/
src/main/resources/
  application.yaml
  db/flowhub.sql
  mapper/
```

## 备注
- `target/` 为编译输出，不要手动修改
- 端口冲突可修改 `server.port`