# 工作复盘总结 - 2026-01-24

## 📋 项目背景

**项目类型：** Monorepo（personalenglishai）  
**当前阶段：** 后端基础能力验证  
**技术栈：** Spring Boot 3.2.5 + Maven + Java 17 + MyBatis + MySQL  
**约束：** 只允许修改 backend 模块

---

## ✅ 完成的工作清单

### 1. 健康检查接口（基础验证）

**任务：** 创建最小健康检查接口，验证后端 HTTP 服务

**实现：**
- 创建 `PingController.java`
- 提供 `GET /api/ping` 接口
- 返回纯文本 "ok"

**文件：**
- `backend/src/main/java/com/personalenglishai/backend/controller/PingController.java`

**难点：** 无

---

### 2. MyBatis + MySQL 接入

**任务：** 接入 MyBatis 和 MySQL，为后续功能做准备

**实现：**
- 添加依赖：`mybatis-spring-boot-starter:3.0.3`、`mysql-connector-j:8.3.0`
- 创建 `application.yml` 配置文件
- 配置数据源（支持环境变量）
- 配置 MyBatis mapper 扫描路径

**文件：**
- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/schema.sql`

**难点：**
- 依赖版本选择（Spring Boot 3.2.x 兼容性）
- 环境变量配置方式

---

### 3. 数据库连通性验证接口

**任务：** 创建临时接口验证数据库连接

**实现：**
- 添加 `spring-boot-starter-jdbc` 依赖
- 在 `PingController` 中添加 `GET /api/db/ping` 接口
- 使用 `JdbcTemplate` 执行 `SELECT 1`

**文件：**
- `backend/pom.xml`
- `backend/src/main/java/com/personalenglishai/backend/controller/PingController.java`

**难点：** 无

---

### 4. 学段可选的 AI 生成逻辑

**任务：** 实现用户学段配置和 AI 生成功能

**实现：**
- 创建 `user_profile` 表（包含 `ai_mode` 和 `study_stage` 字段）
- 创建 `PATCH /api/users/me/profile/stage` 接口
- 创建 `POST /api/ai/generate` 接口
- 实现 `UserProfileService` 和 `AiGenerateService`

**文件：**
- `backend/src/main/java/com/personalenglishai/backend/entity/UserProfile.java`
- `backend/src/main/java/com/personalenglishai/backend/mapper/UserProfileMapper.java`
- `backend/src/main/resources/mapper/UserProfileMapper.xml`
- `backend/src/main/java/com/personalenglishai/backend/service/UserProfileService.java`
- `backend/src/main/java/com/personalenglishai/backend/service/AiGenerateService.java`
- `backend/src/main/java/com/personalenglishai/backend/controller/UserProfileController.java`
- `backend/src/main/java/com/personalenglishai/backend/controller/AiController.java`
- 相关 DTO 类

**难点：**
- 业务逻辑设计（ai_mode 的切换逻辑）
- 用户ID获取方式（临时使用 X-User-Id header）

---

### 5. 用户注册功能

**任务：** 实现用户注册接口

**实现：**
- 创建 `users` 表
- 创建 `POST /api/auth/register` 接口
- 使用 BCrypt 加密密码
- 实现参数验证和唯一性校验

**文件：**
- `backend/src/main/java/com/personalenglishai/backend/entity/User.java`
- `backend/src/main/java/com/personalenglishai/backend/mapper/UserMapper.java`
- `backend/src/main/resources/mapper/UserMapper.xml`
- `backend/src/main/java/com/personalenglishai/backend/service/UserService.java`
- `backend/src/main/java/com/personalenglishai/backend/controller/AuthController.java`
- 相关 DTO 类

**难点：**
- 表结构设计（users vs user）
- 密码加密方式选择（SHA-256 → BCrypt）
- HTTP 状态码选择（409 vs 400）

---

### 6. 用户登录和 JWT 认证

**任务：** 实现登录接口和 JWT 生成/验证

**实现：**
- 创建 `POST /api/auth/login` 接口
- 实现 JWT 工具类（生成、验证、解析）
- 创建 JWT 拦截器
- 配置 WebConfig 注册拦截器

**文件：**
- `backend/pom.xml`（添加 jjwt 依赖）
- `backend/src/main/java/com/personalenglishai/backend/util/JwtUtil.java`
- `backend/src/main/java/com/personalenglishai/backend/interceptor/JwtInterceptor.java`
- `backend/src/main/java/com/personalenglishai/backend/config/WebConfig.java`
- `backend/src/main/java/com/personalenglishai/backend/controller/AuthController.java`
- 相关 DTO 类

**难点：**
- JWT 密钥配置（WeakKeyException）
- JWT 密钥长度要求（至少 32 字节）
- 从配置读取密钥的方式
- JWT payload 结构设计

---

### 7. 修复 JWT 生成问题

**任务：** 修复 WeakKeyException，优化 JWT 配置

**实现：**
- 从 `application.yml` 读取 JWT 密钥和过期时间
- 构造函数中校验密钥长度（至少 32 字节）
- 使用 `Keys.hmacShaKeyFor()` 生成 SecretKey
- 修复 payload 结构（sub=userId, nickname）

**文件：**
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/personalenglishai/backend/util/JwtUtil.java`

**难点：**
- WeakKeyException 的根本原因（密钥长度不足）
- Spring Bean 构造注入配置值
- 启动期校验的重要性

---

### 8. 添加登录接口日志

**任务：** 为登录接口添加详细日志，便于问题定位

**实现：**
- 使用 SLF4J Logger
- 在关键步骤添加 INFO/WARN/DEBUG/ERROR 级别日志

**文件：**
- `backend/src/main/java/com/personalenglishai/backend/controller/AuthController.java`

**难点：** 无

---

### 9. 修复 user_profile 表结构问题

**任务：** 修复 SQL 错误，优化 1:1 附表设计

**实现：**
- 移除 `UserProfile` 实体中的 `id` 字段
- 使用 `user_id` 作为主键
- 更新 Mapper XML 映射
- 更新数据库 schema

**文件：**
- `backend/src/main/java/com/personalenglishai/backend/entity/UserProfile.java`
- `backend/src/main/resources/mapper/UserProfileMapper.xml`
- `backend/src/main/resources/db/schema.sql`

**难点：**
- 1:1 关系表设计最佳实践
- 主键选择（独立 id vs 外键作为主键）
- MyBatis resultMap 主键映射

---

## 🔍 关键问题与难点分析

### 1. JWT WeakKeyException

**问题描述：**
- 使用硬编码的短密钥 "your-secret-key"（16 字节）
- jjwt 0.12.x 要求密钥至少 32 字节
- 运行时抛出 `WeakKeyException`

**解决方案：**
- 从配置文件读取密钥（支持环境变量）
- 构造函数中校验密钥长度
- 提供默认值（至少 32 字节）

**经验总结：**
- 密钥管理应通过配置，不要硬编码
- 启动期校验可以提前发现问题
- 默认值要符合安全要求

---

### 2. 1:1 关系表设计选择

**问题描述：**
- `user_profile` 表应该使用独立 `id` 还是 `user_id` 作为主键？
- SQL 查询报错：`Unknown column 'id' in 'field list'`

**方案对比：**

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| 独立 id 主键 | 灵活，可扩展为 1:N | 冗余，1:1 关系下无意义 | 可能变为 1:N |
| user_id 主键 | 简洁，符合语义，性能好 | 未来扩展需要重构 | 确定是 1:1 关系 |

**最终选择：** user_id 作为主键（符合当前业务需求）

**经验总结：**
- 1:1 关系表使用外键作为主键是行业最佳实践
- 减少冗余字段，提高查询性能
- 符合 DDD 聚合根设计思想

---

### 3. 密码加密方式选择

**问题描述：**
- 初始使用 SHA-256（不安全）
- 需要改为 BCrypt（行业标准）

**解决方案：**
- 添加 `spring-security-crypto` 依赖
- 使用 `BCryptPasswordEncoder` 加密
- 使用 `matches()` 方法验证

**经验总结：**
- 密码加密必须使用安全的哈希算法（BCrypt/Argon2）
- SHA-256 等单向哈希不适合密码存储
- BCrypt 自动处理 salt，更安全

---

### 4. HTTP 状态码选择

**问题描述：**
- 用户名/邮箱重复应该返回什么状态码？
- 400 vs 409 的选择

**解决方案：**
- 使用 409 Conflict（资源冲突）
- 400 用于参数验证失败
- 409 用于资源已存在

**经验总结：**
- HTTP 状态码要符合语义
- 409 专门用于资源冲突场景
- RESTful API 设计要遵循标准

---

### 5. 用户ID获取方式

**问题描述：**
- 多个接口需要获取当前用户ID
- 临时使用 `X-User-Id` header（不安全）

**当前方案：**
- 登录接口使用 `X-User-Id` header（临时）
- 其他接口通过 JWT token 获取用户ID

**待优化：**
- 所有接口应从 JWT token 中获取用户ID
- 移除 `X-User-Id` header 依赖

---

## 📊 技术决策记录

### 1. MyBatis 配置方式

**选择：** 在 `application.yml` 中配置（方案 A）

**原因：**
- 最小配置，无需额外 Java Config
- Spring Boot 自动配置支持
- 配置集中管理

---

### 2. JWT 库选择

**选择：** jjwt 0.12.3

**原因：**
- 官方推荐，Spring Boot 3.x 兼容
- API 简洁，文档完善
- 支持最新的 JWT 标准

---

### 3. 密码加密库

**选择：** Spring Security Crypto（BCrypt）

**原因：**
- Spring 生态集成
- 无需引入完整 Spring Security
- BCrypt 是行业标准

---

### 4. 表命名规范

**选择：** `users`（复数形式）

**原因：**
- 符合常见数据库命名习惯
- 避免 MySQL 保留字冲突

---

## 🎯 最佳实践总结

### 1. 数据库设计

- ✅ 1:1 关系表使用外键作为主键
- ✅ 使用外键约束保证数据一致性
- ✅ 合理使用索引（主键自带索引，避免冗余）

### 2. 安全实践

- ✅ 密码使用 BCrypt 加密
- ✅ JWT 密钥从配置读取，不硬编码
- ✅ JWT 密钥长度至少 32 字节
- ✅ 启动期校验配置有效性

### 3. API 设计

- ✅ RESTful 风格（POST /api/auth/login）
- ✅ 正确的 HTTP 状态码（201, 409, 401 等）
- ✅ 统一的响应格式（DTO）
- ✅ 参数验证和错误处理

### 4. 代码组织

- ✅ 分层架构（Controller → Service → Mapper）
- ✅ 使用 DTO 隔离实体和 API
- ✅ 合理的异常处理
- ✅ 添加日志便于调试

---

## 📝 待优化事项

1. **用户ID获取方式**
   - [ ] 所有接口从 JWT token 获取用户ID
   - [ ] 移除 `X-User-Id` header 依赖

2. **异常处理**
   - [ ] 统一异常处理机制
   - [ ] 返回更详细的错误信息

3. **配置管理**
   - [ ] JWT 密钥应使用环境变量（生产环境）
   - [ ] 数据库密码不应写在配置文件中

4. **测试**
   - [ ] 添加单元测试
   - [ ] 添加集成测试

---

## 🔧 技术栈版本

- Spring Boot: 3.2.5
- Java: 17
- MyBatis: 3.0.3
- MySQL Connector: 8.3.0
- JWT (jjwt): 0.12.3
- Spring Security Crypto: 6.2.4

---

## 📚 参考资源

- [MyBatis Spring Boot Starter](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)
- [jjwt Documentation](https://github.com/jwtk/jjwt)
- [Spring Security Crypto](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [RESTful API Design Best Practices](https://restfulapi.net/)

---

## 💡 经验教训

1. **配置管理很重要**：密钥、密码等敏感信息应通过环境变量管理
2. **启动期校验**：在构造函数中校验配置，可以提前发现问题
3. **表设计要符合业务**：1:1 关系使用外键主键，避免过度设计
4. **日志是调试利器**：关键步骤添加日志，问题定位更快
5. **遵循行业标准**：密码加密、JWT 使用等要遵循最佳实践

---

**生成时间：** 2026-01-24  
**文档版本：** 1.0

