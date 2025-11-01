# common-infrastructure 模块使用说明

## 模块功能
- 提供统一的数据源、Redis 与 Kafka 配置，确保所有业务模块具备一致的连接参数。
- 暴露基础 Bean（`DataSource`、`StringRedisTemplate`、`KafkaTemplate`），避免在子模块中重复定义。
- 作为其他模块的 Maven 依赖，引导多模块工程在同一套配置下运行。

## 启动与运行
- 该模块本身不需要独立启动，随任意依赖它的 Spring Boot 应用一起装配。
- 在本地运行任意子模块前，请先在 `application.yml` 中配置数据库、Redis、Kafka 的地址。例如：
  ```bash
  mvn -pl tx-foundation -am spring-boot:run \
    -Dspring.datasource.url=jdbc:mysql://localhost:3306/transaction_test \
    -Dspring.datasource.username=root \
    -Dspring.datasource.password=secret
  ```
- 如果只想验证配置是否正确，可以执行快速测试：
  ```bash
  mvn -pl common-infrastructure test
  ```
  （模块内无测试类，命令可确保依赖下载完整。）

## 依赖关系
- 被 `tx-foundation`、`tx-spring-core`、`tx-distributed-patterns`、`tx-monitoring` 等模块直接依赖。
- 不依赖其他业务模块，仅依赖 Spring Boot 自动配置与第三方客户端库。
