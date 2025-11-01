# tx-monitoring 模块使用说明

## 模块功能
- 提供 `TransactionMetricsAspect` 切面，自动统计所有 `@Transactional` 方法的执行耗时。
- 通过 Micrometer 指标注册表输出数据，可无缝对接 Prometheus / Grafana。
- 作为可插拔的监控模块，供其他事务实验在调试阶段使用。

## 启动与运行
- 将本模块加入目标应用的依赖即可，无需单独启动。
- 若想单独验证切面，可临时创建一个 `@Transactional` 的示例 Bean，并执行：
  ```bash
  mvn -pl tx-monitoring test
  ```
  （模块本身无测试，命令用于确保依赖与编译环境完整。）
- 将 Prometheus 注册表加入运行命令以导出指标：
  ```bash
  mvn spring-boot:run -pl tx-foundation -am \
    -Dmanagement.endpoints.web.exposure.include=prometheus \
    -Dmanagement.prometheus.metrics.export.enabled=true
  ```

## 依赖关系
- 依赖 `common-infrastructure` 以复用基础配置（可选）。
- 可与任何带有 `@Transactional` 的模块组合，统一收集指标。
