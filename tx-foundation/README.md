# Transaction Foundation 实验总览（基于 Spring Boot + MySQL）

本模块聚合了你在会话中提出的所有事务与性能实验，按“实验目的 → 流程 → 预期现象”的结构整理，并明确到具体测试类与方法，便于一键执行和验证。

## 运行准备
- 本地 MySQL 可用，并存在库 `transaction_study`
- `tx-foundation/src/main/resources/application.yml` 已配置数据源（明文密码）
- 在项目根执行：`mvn -DskipTests=false test`

---

## 实验 A：ACID 四性验证
- 测试类：`tx-foundation/src/test/java/com/transactioninsight/foundation/acid/AcidTransactionTest.java`

1) 原子性（Atomicity）
- 方法：`atomicityRollbackOnFailure`
- 流程：事务中先成功更新记录1，再故意让记录2更新失败 → 回滚事务 → 读回两条记录
- 预期：两条记录均保持初始值；不存在“部分写入”现象

2) 一致性（Consistency）
- 方法：`consistencySumInvariant`
- 流程：两行间执行转账（扣一加一）并提交 → 统计总余额（前后对比）
- 预期：提交后总额守恒（不变）

3) 隔离性（Isolation）
- 方法：`isolationUncommittedInvisible`
- 流程：两会话在 RC 下运行；会话B未提交写入，会话A两次读取分别在提交前/后
- 预期：提交前不可见，提交后可见（RC 每次读新快照）

4) 持久性（Durability）
- 方法：`durabilityCommittedPersists`
- 流程：会话1更新并提交 → 关闭连接 → 会话2重连读取
- 预期：读到已提交值（数据持久化）

---

## 实验 1：MVCC 与事务隔离级别
- 目录：`tx-foundation/src/test/java/com/transactioninsight/foundation/isolation`

1) 脏读（READ UNCOMMITTED）
- 类/方法：`DirtyReadTest.dirtyRead`
- 流程：两会话 RU；会话B未提交更新，会话A在提交前后分别读取
- 预期：提交前读到新值（脏读），回滚后读回旧值

2) 不可重复读（READ COMMITTED）
- 类/方法：`ReadCommittedNonRepeatableReadTest.nonRepeatableRead`
- 流程：会话A首次读取 → 会话B更新并提交 → 会话A再次读取
- 预期：第二次读取可见新值（不可重复读）

3) 幻读与间隙锁（REPEATABLE READ）
- 类/方法：`RepeatableReadPhantomGapLockTest.snapshotVsCurrentRead`
- 流程：A快照读统计 → B插入 → A再次快照读与当前读对比
- 预期：快照读不变；当前读可见新数据
- 类/方法：`RepeatableReadPhantomGapLockTest.gapLockPreventsInsert`
- 流程：A范围 `FOR UPDATE` 锁定 → B插入间隙 → 设置短超时
- 预期：B插入阻塞并超时（Next-Key Lock 生效）

4) MVCC 版本链可见性（RR）
- 类/方法：`MvccVisibilityTest.mvccVisibility`
- 流程：A首次读取形成 Read View；B/C两次更新提交；A再次读取
- 预期：A始终看到事务开始时的版本（后续版本不可见）

5) 乐观锁（CAS）并发控制
- 类/方法：`OptimisticLockCasTest.optimisticLockCas`
- 流程：两会话基于同版本更新：A先成功提升版本，B随后因版本不同影响行数=0；读回最终版本与余额
- 预期：CAS 成功一次；后续旧版本更新失败；最终版本+余额符合预期

---

## 实验 2：锁机制深度实验
- 目录：`tx-foundation/src/test/java/com/transactioninsight/foundation/lock`

6) 行锁 vs 表锁触发条件
- 类/方法：`RowVsTableLockTest.pkEqualityRowLock`
- 流程：A锁定 `id=1`；B锁定 `id=2` 成功；B更新 `id=1` 阻塞并超时
- 预期：主键等值触发行锁；同主键冲突时阻塞
- 类/方法：`RowVsTableLockTest.tableLockNoIndex`
- 流程：删除 `idx_user_id` → A 按 `user_id` 当前读 → B任意更新阻塞并超时
- 预期：无索引扫描触发表锁
- 类/方法：`RowVsTableLockTest.indexInvalidationByImplicitCast`
- 流程：对 `account_no` 用数字字面量 → `EXPLAIN` 显示全表扫描
- 预期：隐式类型转换致索引失效（锁升级）

7) 间隙锁与 Next-Key Lock
- 类/方法：`GapAndNextKeyLockTest.uniqueEqualityExistVsNonExist`
- 流程：唯一索引等值存在→Record Lock；不存在→Gap Lock；在间隙尝试插入
- 预期：存在不阻塞相邻插入；不存在时插入阻塞并超时
- 类/方法：`GapAndNextKeyLockTest.nextKeyLockOnRange`
- 流程：A范围 `5000~10000` 当前读；B在范围外/内分别插入
- 预期：范围外成功，范围内阻塞并超时（Next-Key 锁）
- 类/方法：`GapAndNextKeyLockTest.gapLocksNonMutualButBlockInsert`
- 流程：A/B在同一间隙锁定不同不存在值；C尝试插入该间隙
- 预期：间隙锁间彼此不冲突，但阻塞 INSERT

8) 死锁复现与分析
- 类/方法：`DeadlockReproductionTest.classicTwoTxDeadlock`
- 流程：T1先锁 `id=1` 后等 `id=2`；T2先锁 `id=2` 后等 `id=1`
- 预期：形成循环等待，至少一个事务被自动回滚
- 类/方法：`DeadlockReproductionTest.indexOrderMismatchDeadlock`
- 流程：一个事务按二级索引范围更新，另一个按主键倒序更新同一集合
- 预期：锁序不一致导致死锁
- 类/方法：`DeadlockReproductionTest.gapVsInsertIntentDeadlock`
- 流程：双方持有间隙锁后都尝试插入意向锁
- 预期：发生死锁，至少一个回滚

9) 锁等待超时与监控
- 类/方法：`LockWaitTimeoutAndMonitoringTest.simulateLockWaitTimeout`
- 流程：A更新并持锁；B设置短 `innodb_lock_wait_timeout` 后更新同一行
- 预期：B超时（常见 1205）
- 类/方法：`LockWaitTimeoutAndMonitoringTest.monitoringQueries`
- 流程：持锁后尝试查询 `information_schema.innodb_lock_waits / innodb_trx / performance_schema.data_locks`
- 预期：可视化阻塞链（权限不足时忽略不失败）

---

## 实验 3：索引优化与查询优化器
- 目录：`tx-foundation/src/test/java/com/transactioninsight/foundation/index`

10) 覆盖索引 vs 回表对比
- 类/方法：`IndexCoverageTest.nonCoveringRequiresBackToTable`
- 流程：仅有 `idx_user_id` → `EXPLAIN` 的 `Extra` 无 `Using index`
- 预期：需要回表；随机 I/O 较多
- 类/方法：`IndexCoverageTest.coveringIndexUsingIndex`
- 流程：新增 `idx_user_cover(user_id,balance,status)` → `EXPLAIN` 的 `Extra` 含 `Using index`；演示耗时对比
- 预期：覆盖索引显著减少回表、耗时更低
- 类/方法：`IndexCoverageTest.selectStarBreaksCovering`
- 流程：`SELECT *` → `EXPLAIN` 的 `Extra` 不含 `Using index`
- 预期：覆盖索引失效，必须回表

11) 联合索引与最左前缀原则
- 类/方法：`CompositeIndexLeftmostPrefixTest.fullMatch`
- 流程：全部列命中联合索引 → `key=idx_status_balance_time`
- 预期：最优访问（range/ref）
- 类/方法：`CompositeIndexLeftmostPrefixTest.prefixMatchAndSkipFirst`
- 流程：仅第一列/前两列可用；跳过首列不可用
- 预期：`key` 变化或 `NULL`
- 类/方法：`CompositeIndexLeftmostPrefixTest.rangeCutsOffFollowing`
- 流程：中间列范围查询中断后续列；新增更优列序索引后恢复利用
- 预期：`key_len`/`key` 随查询/索引调整变化
- 类/方法：`CompositeIndexLeftmostPrefixTest.orBreaksComposite`
- 流程：OR 条件导致联合索引不可用；UNION 改写演示
- 预期：`type=ALL/index`；改写后更优
- 类/方法：`CompositeIndexLeftmostPrefixTest.functionInvalidatesIndex`
- 流程：函数 `YEAR(col)` 导致索引失效；改写为范围后 `type=range`
- 预期：函数/表达式会破坏索引可用性

12) ICP（Index Condition Pushdown）
- 类/方法：`IcpOptimizationTest.icpOnOffCompare`
- 流程：默认开启 ICP → `Extra` 含 `Using index condition`；关闭后 → `Using where`
- 预期：ICP 下推索引列上的过滤，减少回表

13) 优化器 Cost Model 分析
- 类/方法：`OptimizerCostModelTest.explainJsonAndOptimizerTrace`
- 流程：`EXPLAIN FORMAT=JSON` 查看 `cost_info`；开启 `optimizer_trace` 读取候选计划与成本
- 预期：能观测优化器选择依据（基数、选择性、成本）

14) 慢查询优化全流程
- 类/方法：`SlowQueryOptimizationFlowTest.slowQueryOptimization`
- 流程：模拟慢查询（LIKE、无索引 JOIN、子查询）→ 添加 FULLTEXT、改写 JOIN 与 EXISTS → 对比 EXPLAIN 与耗时
- 预期：优化后查询更快；覆盖索引/改写能显著提升性能

---

## 实验 4：高并发场景综合案例
- 目录：`tx-foundation/src/test/java/com/transactioninsight/foundation/concurrency`

案例1：秒杀系统（库存扣减方案对比）
- 类/方法：`SeckillStrategiesTest.pessimisticSeckillNonOversell`
- 流程：并发调用悲观锁过程；统计成功、售罄、错误；读回库存与订单数
- 预期：`订单数 + 库存 = 初始库存`；无超卖
- 类/方法：`SeckillStrategiesTest.optimisticSeckillNonOversell`
- 流程：并发调用乐观锁 CAS 过程（含退避与重试）
- 预期：无超卖；可能出现 `RETRY_EXCEEDED`；总体不变量成立

案例2：转账系统（分布式事务）
- 类/方法：`DistributedTransferTest.localTransactionTransfer`
- 流程：本地事务按固定锁序扣减/增加余额；幂等日志防重复；重复调用同 `trans_id`
- 预期：首次成功、重复返回 `DUPLICATE/ERROR`；余额与日志符合预期
- 类/方法：`DistributedTransferTest.twoPhaseCommit`
- 流程：预备冻结 → 提交扣减/增加 → 演示回滚解冻
- 预期：冻结与提交/回滚路径正确，最终余额/冻结额一致

案例3：订单系统分页查询优化
- 类/方法：`PaginationOptimizationTest.deepPaginationOptimization`
- 流程：对比传统深度 LIMIT、子查询覆盖索引回表与游标分页的执行时间
- 预期：子查询/游标优于或不慢于传统深度 LIMIT

案例4：实时报表聚合优化
- 类/方法：`RealtimeAggregationTest.directVsScheduledSummary`
- 流程：直接聚合（慢）→ 调用汇总存储过程刷新聚合表 → 查询聚合表（快）
- 预期：聚合表查询明显更快；样本分支聚合正确

---

## 说明与提醒
- 事件/触发器/监控视图可能需要 MySQL 权限；测试已做容错（权限不足不使测试失败）
- 性能对比使用简易耗时统计（演示级），不等同于严谨压测；如需更精确可引入 `performance_schema` 指标或外部压测工具
- 覆盖索引、ICP、优化器成本等实验在不同 MySQL 版本可能存在 `EXPLAIN` 字段差异，断言采用兼容写法

---

## 快速导航
- ACID：`foundation/acid/AcidTransactionTest.java`
- 隔离级别 & MVCC：`foundation/isolation/*.java`
- 锁机制：`foundation/lock/*.java`
- 索引与优化器：`foundation/index/*.java`
- 高并发综合：`foundation/concurrency/*.java`

如需我补充 Redis 预扣减方案的测试接入（Lua + 队列消费），或在分页/报表中扩展更详细的 `EXPLAIN FORMAT=JSON` 成本对比，请提出具体要求，我将按同样规范追加。 
