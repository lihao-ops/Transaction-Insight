package com.transactioninsight.foundation.model;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 账户数据访问层接口
 *
 * <h3>功能概述</h3>
 * 提供账户实体的数据库访问操作，包括普通查询和各种锁机制的查询方法。
 *
 * <h3>实现思路</h3>
 * <ul>
 *   <li><b>继承JpaRepository：</b>获得基础的CRUD操作能力</li>
 *   <li><b>自定义查询方法：</b>通过方法命名规范或@Query注解实现特定查询</li>
 *   <li><b>锁机制支持：</b>使用@Lock注解配合JPA的LockModeType实现数据库锁</li>
 * </ul>
 *
 * <h3>锁机制说明</h3>
 * <pre>
 * 锁类型           | SQL语法                    | 用途                      | 兼容性
 * ----------------|---------------------------|--------------------------|----------
 * 无锁(快照读)     | SELECT                     | 普通查询，读取快照版本      | 不加锁
 * 共享锁(S锁)      | SELECT ... LOCK IN SHARE MODE | 允许多个事务同时读取    | S锁兼容
 * 排他锁(X锁)      | SELECT ... FOR UPDATE      | 独占访问，准备修改数据     | 与所有锁互斥
 * </pre>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li><b>普通查询：</b>适用于只读操作，不需要保证数据不被修改</li>
 *   <li><b>共享锁：</b>适用于需要保证读取期间数据不被修改，但允许其他事务读取</li>
 *   <li><b>排他锁：</b>适用于读取后准备修改数据，需要独占访问</li>
 * </ul>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>⚠️ 所有带锁的查询必须在事务内执行，否则锁会立即释放</li>
 *   <li>⚠️ 长时间持有锁会降低并发性能，应尽快提交或回滚事务</li>
 *   <li>⚠️ 使用锁时要注意死锁问题，建议统一加锁顺序</li>
 *   <li>⚠️ 确保查询条件命中索引，避免锁升级为表锁</li>
 * </ul>
 *
 * <h3>示例代码</h3>
 * <pre>{@code
 * // 示例1：普通查询（快照读）
 * @Transactional
 * public void normalQuery() {
 *     Optional<Account> account = accountRepository.findByAccountNo("ACC001");
 *     // 读取的是事务开始时的快照版本，其他事务的修改不可见
 * }
 *
 * // 示例2：使用共享锁（当前读）
 * @Transactional
 * public void queryWithSharedLock() {
 *     Optional<Account> account = accountRepository.findByAccountNoWithSharedLock("ACC001");
 *     // 持有共享锁期间，其他事务可以读但不能写
 *     // 事务提交时自动释放锁
 * }
 *
 * // 示例3：使用排他锁（准备修改）
 * @Transactional
 * public void updateWithExclusiveLock() {
 *     Account account = accountRepository.findByAccountNoWithExclusiveLock("ACC001")
 *         .orElseThrow();
 *     account.setBalance(account.getBalance().add(new BigDecimal("100")));
 *     accountRepository.save(account);
 *     // 排他锁确保没有其他事务能同时修改这条数据
 * }
 * }</pre>
 *
 * @author Transaction Insight Team
 * @version 1.0
 * @since 2024-01-01
 * @see Account
 * @see LockModeType
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * 通过账户号查询账户信息（普通查询，不加锁）
     *
     * <h4>执行的SQL</h4>
     * <pre>
     * SELECT
     *     a.id,
     *     a.account_no,
     *     a.balance,
     *     a.version
     * FROM account a
     * WHERE a.account_no = ?
     * </pre>
     *
     * <h4>锁行为</h4>
     * <ul>
     *   <li><b>隔离级别：</b>REPEATABLE READ（MySQL默认）</li>
     *   <li><b>读取方式：</b>快照读（Snapshot Read）</li>
     *   <li><b>锁类型：</b>不加锁</li>
     *   <li><b>MVCC：</b>读取事务开始时的数据版本（通过Read View）</li>
     * </ul>
     *
     * <h4>使用场景</h4>
     * <ul>
     *   <li>✅ 只读查询，不需要修改数据</li>
     *   <li>✅ 对数据一致性要求不高</li>
     *   <li>✅ 需要高并发性能</li>
     *   <li>❌ 查询后需要修改数据（应使用排他锁）</li>
     *   <li>❌ 需要保证数据在事务期间不被修改（应使用共享锁或排他锁）</li>
     * </ul>
     *
     * <h4>并发表现</h4>
     * <pre>
     * 事务1: SELECT (读到balance=1000)
     * 事务2: UPDATE balance=2000, COMMIT
     * 事务1: SELECT (仍然读到balance=1000) ← MVCC保证可重复读
     * </pre>
     *
     * <h4>性能特点</h4>
     * <ul>
     *   <li>⚡ 性能最高，无锁等待</li>
     *   <li>⚡ 支持高并发读取</li>
     *   <li>⚡ 不会阻塞写操作</li>
     * </ul>
     *
     * @param accountNo 账户号，唯一标识一个账户
     * @return Optional包装的账户对象，如果不存在则返回Optional.empty()
     *
     * @see <a href="https://dev.mysql.com/doc/refman/8.0/en/innodb-consistent-read.html">MySQL Consistent Read</a>
     */
    Optional<Account> findByAccountNo(String accountNo);

    /**
     * 通过账户号查询账户信息（使用共享锁/读锁）
     *
     * <h4>执行的SQL</h4>
     * <pre>
     * -- MySQL 8.0+
     * SELECT
     *     a.id,
     *     a.account_no,
     *     a.balance,
     *     a.version
     * FROM account a
     * WHERE a.account_no = ?
     * FOR SHARE;
     *
     * -- MySQL 5.7及之前
     * SELECT ... LOCK IN SHARE MODE;
     * </pre>
     *
     * <h4>锁行为详解</h4>
     * <ul>
     *   <li><b>锁类型：</b>共享锁（Shared Lock / S锁）</li>
     *   <li><b>锁粒度：</b>行级锁（如果accountNo有索引）</li>
     *   <li><b>锁范围：</b>
     *       <ul>
     *           <li>如果是唯一索引：只锁定匹配的记录（Record Lock）</li>
     *           <li>如果是非唯一索引：锁定记录 + 间隙（Next-Key Lock）</li>
     *           <li>如果没有索引：锁定整个表（全表扫描）</li>
     *       </ul>
     *   </li>
     *   <li><b>释放时机：</b>事务提交（COMMIT）或回滚（ROLLBACK）时自动释放</li>
     * </ul>
     *
     * <h4>锁兼容性矩阵</h4>
     * <pre>
     *         | 无锁 | 共享锁(S) | 排他锁(X)
     * --------|------|----------|----------
     * 无锁    |  ✓   |    ✓     |    ✓
     * 共享锁  |  ✓   |    ✓     |    ✗
     * 排他锁  |  ✓   |    ✗     |    ✗
     *
     * ✓ = 兼容（不会阻塞）
     * ✗ = 互斥（会阻塞等待）
     *
     * 注意：无锁读取（快照读）永远不会被阻塞
     * </pre>
     *
     * <h4>使用场景</h4>
     * <ul>
     *   <li>✅ 需要确保读取的数据在事务期间不被修改</li>
     *   <li>✅ 多个事务需要同时读取同一数据</li>
     *   <li>✅ 计算统计数据时，确保数据一致性（如计算订单总额）</li>
     *   <li>✅ 实现读写分离但需要强一致性的场景</li>
     *   <li>❌ 读取后立即要修改（应直接使用排他锁）</li>
     *   <li>❌ 只是普通查询（应使用无锁查询提高性能）</li>
     * </ul>
     *
     * <h4>并发示例</h4>
     * <pre>
     * T1: SELECT ... FOR SHARE (获取共享锁)
     * T2: SELECT ... FOR SHARE (成功，共享锁兼容) ✓
     * T3: SELECT ... FOR UPDATE (阻塞，等待共享锁释放) ⏳
     * T1: COMMIT (释放共享锁)
     * T2: COMMIT (释放共享锁)
     * T3: 获取排他锁成功 ✓
     * </pre>
     *
     * <h4>注意事项</h4>
     * <ul>
     *   <li>⚠️ 必须在事务内调用，否则锁会立即释放</li>
     *   <li>⚠️ 长时间持有共享锁会阻塞写操作，影响并发性能</li>
     *   <li>⚠️ 多个事务按不同顺序加共享锁，后续再加排他锁可能死锁</li>
     *   <li>⚠️ account_no字段必须有索引，否则会锁全表</li>
     * </ul>
     *
     * <h4>实战案例</h4>
     * <pre>{@code
     * // 案例：计算用户订单总额，确保计算期间订单金额不变
     * @Transactional
     * public BigDecimal calculateTotalAmount(String accountNo) {
     *     // 使用共享锁读取账户信息
     *     Account account = accountRepository
     *         .findByAccountNoWithSharedLock(accountNo)
     *         .orElseThrow();
     *
     *     // 在持有共享锁期间，其他事务不能修改这条数据
     *     // 但可以同时读取（多个SELECT ... FOR SHARE可以并存）
     *
     *     // 执行复杂计算
     *     BigDecimal total = performComplexCalculation(account);
     *
     *     return total;
     *     // 事务提交时自动释放共享锁
     * }
     * }</pre>
     *
     * @param accountNo 账户号，必须有索引
     * @return Optional包装的账户对象
     * @throws org.springframework.dao.PessimisticLockingFailureException
     *         如果无法获取锁（例如超时）
     * @throws IllegalTransactionStateException
     *         如果不在事务中调用
     *
     * @see LockModeType#PESSIMISTIC_READ
     * @see <a href="https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html">MySQL Locking Reads</a>
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT a FROM Account a WHERE a.accountNo = :accountNo")
    Optional<Account> findByAccountNoWithSharedLock(@Param("accountNo") String accountNo);

    /**
     * 通过账户号查询账户信息（使用排他锁/写锁）
     *
     * <h4>执行的SQL</h4>
     * <pre>
     * SELECT
     *     a.id,
     *     a.account_no,
     *     a.balance,
     *     a.version
     * FROM account a
     * WHERE a.account_no = ?
     * FOR UPDATE;
     * </pre>
     *
     * <h4>锁行为详解</h4>
     * <ul>
     *   <li><b>锁类型：</b>排他锁（Exclusive Lock / X锁）</li>
     *   <li><b>锁粒度：</b>行级锁（如果accountNo有唯一索引）</li>
     *   <li><b>锁范围：</b>
     *       <ul>
     *           <li>唯一索引等值查询：只锁定匹配的记录（Record Lock）</li>
     *           <li>非唯一索引等值查询：记录锁 + 间隙锁（Next-Key Lock）</li>
     *           <li>范围查询：锁定范围内的记录和间隙（Next-Key Lock）</li>
     *           <li>无索引查询：锁定整个表（全表扫描）⚠️</li>
     *       </ul>
     *   </li>
     *   <li><b>阻塞行为：</b>
     *       <ul>
     *           <li>阻塞其他事务的FOR UPDATE（排他锁）</li>
     *           <li>阻塞其他事务的FOR SHARE（共享锁）</li>
     *           <li>不阻塞普通SELECT（快照读）</li>
     *       </ul>
     *   </li>
     *   <li><b>释放时机：</b>事务提交或回滚时自动释放</li>
     * </ul>
     *
     * <h4>锁兼容性</h4>
     * <pre>
     * 当前持有排他锁时：
     * - 其他事务的SELECT（快照读）       → 不阻塞 ✓
     * - 其他事务的SELECT ... FOR SHARE   → 阻塞等待 ✗
     * - 其他事务的SELECT ... FOR UPDATE  → 阻塞等待 ✗
     * - 其他事务的UPDATE/DELETE          → 阻塞等待 ✗
     * </pre>
     *
     * <h4>典型使用场景</h4>
     * <ul>
     *   <li>✅ <b>库存扣减：</b>读取库存后立即扣减，防止超卖</li>
     *   <li>✅ <b>余额更新：</b>读取余额后转账，防止负数</li>
     *   <li>✅ <b>秒杀活动：</b>保证商品数量准确</li>
     *   <li>✅ <b>订单状态修改：</b>防止并发修改导致状态错乱</li>
     *   <li>✅ <b>分布式锁实现：</b>基于数据库的分布式锁</li>
     *   <li>❌ 只读查询（应使用无锁或共享锁）</li>
     *   <li>❌ 高并发场景（会严重降低并发性能）</li>
     * </ul>
     *
     * <h4>并发执行流程</h4>
     * <pre>
     * 时间 | 事务A                          | 事务B                          | 说明
     * -----|-------------------------------|-------------------------------|--------
     * t1   | BEGIN                         |                               |
     * t2   | SELECT ... FOR UPDATE ✓       |                               | A获取排他锁
     * t3   |                               | BEGIN                         |
     * t4   |                               | SELECT ... FOR UPDATE ⏳      | B被阻塞
     * t5   | UPDATE balance = 1100         |                               |
     * t6   | COMMIT (释放锁)                |                               |
     * t7   |                               | ✓ 获取锁成功                   | B继续执行
     * t8   |                               | SELECT (读到1100)             | 读到A提交的值
     * t9   |                               | UPDATE balance = 1200         |
     * t10  |                               | COMMIT                        |
     *
     * 最终结果：balance = 1200 ✓ 正确
     * </pre>
     *
     * <h4>索引对锁范围的影响</h4>
     * <pre>
     * -- 情况1：account_no有唯一索引（推荐）
     * SELECT ... WHERE account_no = 'ACC001' FOR UPDATE;
     * 锁范围：只锁定account_no='ACC001'这一行 ✓
     *
     * -- 情况2：account_no有普通索引
     * SELECT ... WHERE account_no = 'ACC001' FOR UPDATE;
     * 锁范围：锁定account_no='ACC001'的行 + 间隙锁
     *
     * -- 情况3：account_no没有索引（危险⚠️）
     * SELECT ... WHERE account_no = 'ACC001' FOR UPDATE;
     * 锁范围：锁定整个表！（全表扫描导致）
     *
     * -- 验证方法：
     * EXPLAIN SELECT ... WHERE account_no = 'ACC001';
     * 查看type列：
     * - const / eq_ref → 使用了唯一索引 ✓
     * - ref → 使用了普通索引 ⚠️
     * - ALL → 全表扫描 ✗
     * </pre>
     *
     * <h4>死锁风险与预防</h4>
     * <pre>
     * ⚠️ 死锁案例：
     *
     * 事务A: SELECT * FROM account WHERE id=1 FOR UPDATE;  (锁住id=1)
     * 事务B: SELECT * FROM account WHERE id=2 FOR UPDATE;  (锁住id=2)
     * 事务A: SELECT * FROM account WHERE id=2 FOR UPDATE;  (等待事务B释放)
     * 事务B: SELECT * FROM account WHERE id=1 FOR UPDATE;  (等待事务A释放)
     *
     * 结果：死锁！MySQL检测到后会回滚其中一个事务
     *
     * ✅ 预防策略：
     * 1. 统一加锁顺序（按主键或账户号排序）
     * 2. 尽量缩短事务时间
     * 3. 使用超时机制
     * 4. 考虑使用乐观锁
     * </pre>
     *
     * <h4>性能考虑</h4>
     * <ul>
     *   <li>⚡ 单笔更新：性能较好，等待时间取决于前一个事务</li>
     *   <li>⚠️ 高并发：性能下降明显，TPS会大幅降低</li>
     *   <li>⚠️ 锁等待：如果前面有长事务，会一直等待</li>
     *   <li>💡 优化建议：
     *       <ul>
     *           <li>事务尽量短，快速提交</li>
     *           <li>避免在事务内调用外部接口</li>
     *           <li>高并发场景考虑使用Redis预扣减</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * <h4>实战案例</h4>
     * <pre>{@code
     * // 案例1：安全的余额扣减
     * @Transactional
     * public void deductBalance(String accountNo, BigDecimal amount) {
     *     // 使用排他锁查询，确保没有其他事务同时修改
     *     Account account = accountRepository
     *         .findByAccountNoWithExclusiveLock(accountNo)
     *         .orElseThrow(() -> new AccountNotFoundException());
     *
     *     // 业务校验
     *     if (account.getBalance().compareTo(amount) < 0) {
     *         throw new InsufficientBalanceException();
     *     }
     *
     *     // 扣减余额
     *     account.setBalance(account.getBalance().subtract(amount));
     *     accountRepository.save(account);
     *
     *     // 事务提交时释放锁
     * }
     *
     * // 案例2：安全的转账操作（避免死锁）
     * @Transactional
     * public void transfer(String fromAccountNo, String toAccountNo, BigDecimal amount) {
     *     // 关键：按账户号排序，统一加锁顺序，避免死锁
     *     List<String> sortedAccounts = Arrays.asList(fromAccountNo, toAccountNo)
     *         .stream()
     *         .sorted()
     *         .collect(Collectors.toList());
     *
     *     // 按顺序加锁
     *     Account acc1 = accountRepository
     *         .findByAccountNoWithExclusiveLock(sortedAccounts.get(0))
     *         .orElseThrow();
     *     Account acc2 = accountRepository
     *         .findByAccountNoWithExclusiveLock(sortedAccounts.get(1))
     *         .orElseThrow();
     *
     *     // 确定哪个是from，哪个是to
     *     Account from = acc1.getAccountNo().equals(fromAccountNo) ? acc1 : acc2;
     *     Account to = acc1.getAccountNo().equals(toAccountNo) ? acc1 : acc2;
     *
     *     // 执行转账
     *     from.setBalance(from.getBalance().subtract(amount));
     *     to.setBalance(to.getBalance().add(amount));
     *
     *     accountRepository.saveAll(Arrays.asList(from, to));
     * }
     *
     * // 案例3：库存扣减（防止超卖）
     * @Transactional
     * public boolean deductStock(Long productId, Integer quantity) {
     *     Product product = productRepository
     *         .findByIdForUpdate(productId)
     *         .orElseThrow();
     *
     *     if (product.getStock() < quantity) {
     *         return false; // 库存不足
     *     }
     *
     *     product.setStock(product.getStock() - quantity);
     *     productRepository.save(product);
     *
     *     return true;
     * }
     * }</pre>
     *
     * <h4>监控和调试</h4>
     * <pre>
     * -- 查看当前锁等待情况
     * SELECT * FROM information_schema.INNODB_TRX;
     * SELECT * FROM performance_schema.data_locks;
     * SELECT * FROM performance_schema.data_lock_waits;
     *
     * -- 查看死锁日志
     * SHOW ENGINE INNODB STATUS;
     *
     * -- 设置锁等待超时时间（秒）
     * SET innodb_lock_wait_timeout = 5;
     * </pre>
     *
     * @param accountNo 账户号，<b>强烈建议</b>该字段有唯一索引
     * @return Optional包装的账户对象
     * @throws org.springframework.dao.PessimisticLockingFailureException
     *         无法获取锁时抛出（如超时、死锁）
     * @throws IllegalTransactionStateException
     *         不在事务中调用时抛出
     * @throws org.springframework.dao.DeadlockLoserDataAccessException
     *         检测到死锁时抛出（当前事务被选为牺牲者）
     *
     * @see LockModeType#PESSIMISTIC_WRITE
     * @see <a href="https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html">MySQL Locking Reads</a>
     * @see <a href="https://dev.mysql.com/doc/refman/8.0/en/innodb-deadlocks.html">MySQL Deadlocks</a>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNo = :accountNo")
    Optional<Account> findByAccountNoWithExclusiveLock(@Param("accountNo") String accountNo);
}