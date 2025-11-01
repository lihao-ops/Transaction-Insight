# tx-chaos-engineering 模块使用说明

## 模块功能
- 为分布式事务提供混沌实验脚本，支持注入网络延迟、断连等异常场景。
- 当前示例使用 Testcontainers + Toxiproxy 描述 TCC Confirm 阶段的网络分区案例。
- 结合 `tx-distributed-patterns` 模块的 TCC 账户服务，验证补偿逻辑的鲁棒性。

## 启动与运行
- 默认测试被 `@Disabled` 注解跳过，避免在无 Docker 环境下执行。
- 如需运行混沌实验，请先准备 Docker 或改用本地注入工具，然后执行：
  ```bash
  mvn -pl tx-chaos-engineering test -Dchaos.docker.enabled=true
  ```
- 若不使用 Docker，可参考测试代码手动将 `ToxiproxyContainer` 替换为本地代理脚本或分布式中间件。

## 依赖关系
- 依赖 `tx-distributed-patterns` 复用 TCC 管理器与账户服务。
- 间接依赖 `common-infrastructure` 中的配置，以保持环境一致。
