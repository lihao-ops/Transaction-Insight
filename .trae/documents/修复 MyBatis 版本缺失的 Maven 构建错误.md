## 原因与定位
- 多个模块的 `pom.xml` 中引入了 `org.mybatis.spring.boot:mybatis-spring-boot-starter`，但未声明 `version`，且父 POM 的 `dependencyManagement` 仅导入了 `spring-boot-dependencies`，并不管理 MyBatis 版本，因此 Maven 报错。
- 受影响位置：
  - `tx-foundation/pom.xml:32-34`
  - `tx-spring-core/pom.xml:36-38`
  - `tx-chaos-engineering/pom.xml:34-36`
  - `tx-monitoring/pom.xml:30-32`
  - `tx-distributed-patterns/pom.xml:31-33`
  - `common-infrastructure/pom.xml:36-38`
- 官方推荐在 Spring Boot 项目中为 MyBatis Starter明确版本，当前与 Boot 3.5.x 匹配的版本为 `3.0.5`（官方文档与中央仓库信息）。

## 解决方案（优先方案：父 POM 统一管理）
- 在根 `pom.xml` 的 `<properties>` 中新增：
  - `<mybatis.spring.boot.version>3.0.5</mybatis.spring.boot.version>`
- 在根 `pom.xml` 的 `<dependencyManagement>` 中新增：
  - `<dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>${mybatis.spring.boot.version}</version></dependency>`
- 保持各子模块对该依赖不写版本，以便由父 POM 统一管理；这样一次改动即可消除所有“version 缺失”错误。

## 备选方案（局部修复）
- 在每个使用 MyBatis 的子模块 `pom.xml` 中，为 `mybatis-spring-boot-starter` 直接声明 `<version>3.0.5</version>`；适合快速止血，但不利于统一版本治理。

## 验证步骤
- 运行构建验证（不跳过测试更稳妥）：
  - `mvn -DskipTests package` 或 `mvn verify`
- 检查有效 POM 中的版本解析：
  - `mvn -q help:effective-pom | findstr /I mybatis-spring-boot-starter`
- 如仍报错，确认所有受影响模块均受父 POM 管理（`<parent>` 指向根 POM），且未在子 POM 中误用 `<dependencyManagement>` 覆盖父级。

## 说明：与 YAML 配置无关
- 你贴出的 Redis/Kafka/Knife4j/SpringDoc 等 YAML 配置与 Maven POM 解析错误无关；该问题仅由依赖版本缺失引起。
- 如期望 Spring Boot 自动读取标准配置前缀，请注意常用前缀为 `spring.data.redis.*` 与 `spring.kafka.*`（你贴的是示例或自定义前缀则无需调整）。

## 执行计划
1. 修改根 `pom.xml`，增加 MyBatis 版本属性与 `dependencyManagement` 条目。
2. 保持子模块不写版本；如某子模块手工写了版本，统一改为由父 POM 管理。
3. 执行 Maven 构建并确认错误消失。
4. 输出修改点与验证结果；如需，我再协助梳理 YAML 配置的前缀与敏感信息外部化。