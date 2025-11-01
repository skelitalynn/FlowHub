# 黑马点评项目 - Windows本地运行指南

## 项目简介
这是一个基于Spring Boot + MyBatis Plus + Redis的点评系统项目。

## 环境要求
- JDK 8+
- MySQL 5.7+
- Redis 3.0+
- Maven 3.6+

## 本地环境搭建步骤

### 1. 安装MySQL
1. 下载并安装MySQL 5.7或8.0版本
2. 设置root用户密码为：`123456`（或修改application.yaml中的密码）
3. 创建数据库：
```sql
CREATE DATABASE hmdp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 导入数据库数据
1. 打开MySQL客户端（如Navicat、MySQL Workbench等）
2. 连接到本地MySQL服务
3. 选择刚创建的`hmdp`数据库
4. 执行SQL脚本：`src/main/resources/db/hmdp.sql`

### 3. 安装Redis
1. 下载Redis for Windows：https://github.com/microsoftarchive/redis/releases
2. 安装Redis服务
3. 启动Redis服务（默认端口6379，无密码）

### 4. 修改项目配置
配置文件已修改为适合本地环境：
- MySQL连接：localhost:3306
- Redis连接：localhost:6379
- 数据库密码：123456

### 5. 运行项目
1. 确保MySQL和Redis服务都已启动
2. 在项目根目录执行：
```bash
mvn spring-boot:run
```
或者直接运行主类：`com.hmdp.HmDianPingApplication`

### 6. 访问项目
项目启动成功后，访问：http://localhost:8081

## 常见问题

### 1. MySQL连接失败
- 检查MySQL服务是否启动
- 确认数据库`hmdp`是否已创建
- 检查用户名密码是否正确
- 确认MySQL版本兼容性

### 2. Redis连接失败
- 检查Redis服务是否启动
- 确认Redis端口6379是否可用
- 检查防火墙设置

### 3. 端口冲突
如果8081端口被占用，可以修改`application.yaml`中的`server.port`配置

## 项目结构
```
hm-dianping/
├── src/main/java/com/hmdp/
│   ├── controller/     # 控制器层
│   ├── service/        # 服务层
│   ├── mapper/         # 数据访问层
│   ├── entity/         # 实体类
│   ├── dto/           # 数据传输对象
│   └── utils/         # 工具类
├── src/main/resources/
│   ├── application.yaml  # 配置文件
│   └── db/hmdp.sql      # 数据库脚本
└── pom.xml              # Maven配置
```

## 技术栈
- Spring Boot 2.x
- MyBatis Plus
- Redis
- MySQL
- Maven 