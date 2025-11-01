# tx-chaos-engineering 模块使用说明

## 模块功能
- 为分布式事务提供混沌实验脚本，支持注入网络延迟、断连等异常场景。
- 当前示例给出 TCC Confirm 阶段网络分区的实验脚本骨架，可结合任意混沌平台或自定义代理实现。
- 结合 `tx-distributed-patterns` 模块的 TCC 账户服务，验证补偿逻辑的鲁棒性。

## 启动与运行
- 默认测试被 `@Disabled` 注解跳过，避免在缺乏故障注入设施时误触。
- 如需运行混沌实验，请结合现有的网络故障注入工具（如 tc、Chaos Mesh 等）手工触发，再执行模块测试。

## 依赖关系
- 依赖 `tx-distributed-patterns` 复用 TCC 管理器与账户服务。
- 间接依赖 `common-infrastructure` 中的配置，以保持环境一致。
