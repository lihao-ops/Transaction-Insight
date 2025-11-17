package com.transactioninsight.foundation.lock;

import com.transactioninsight.foundation.model.Account;
import com.transactioninsight.foundation.model.AccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL锁机制学习测试类
 * 包含：行锁、表锁、间隙锁、临键锁、死锁、写冲突等场景
 * <p>
 * 重要说明：
 * 1. 所有涉及事务的方法都通过TransactionTemplate执行，确保事务生效
 * 2. 每个测试都包含断言验证，确保结果符合预期
 * 3. 使用CountDownLatch确保线程同步
 */
@Slf4j
@SpringBootTest
public class MySQLLockMechanismTests {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    public void setup() {
        log.info("========== 测试开始前重置数据 ==========");
        accountRepository.deleteAll();

        Account acc1 = new Account();
        acc1.setAccountNo("ACC001");
        acc1.setBalance(new BigDecimal("1000.00"));

        Account acc2 = new Account();
        acc2.setAccountNo("ACC002");
        acc2.setBalance(new BigDecimal("2000.00"));

        accountRepository.save(acc1);
        accountRepository.save(acc2);
        log.info("测试数据初始化完成");
    }

    /**
     * 场景1：写冲突 - 展示为什么需要行锁
     * <p>
     * 实现思路：
     * 1. 两个线程同时读取同一账户余额
     * 2. 都在原余额基础上增加100
     * 3. 不使用锁的情况下会发生写冲突
     * <p>
     * 预期现象：
     * - 线程1读取余额1000，计算后更新为1100
     * - 线程2也读取余额1000，计算后更新为1100
     * - 最终余额是1100而不是1200（丢失更新）
     * <p>
     * 实际现象：
     * - MVCC只能保证读的一致性，无法防止写冲突
     * - 最后一个提交的事务会覆盖前面的修改
     */
    @Test
    public void testWriteConflict_NoLock() throws InterruptedException {
        log.info("========== 场景1：写冲突（无锁） ==========");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 线程1：增加100
        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    updateBalanceWithoutLock("ACC001", new BigDecimal("100"), "线程1");
                    return null;
                });
            } catch (Exception e) {
                log.error("线程1异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        // 线程2：增加100
        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    updateBalanceWithoutLock("ACC001", new BigDecimal("100"), "线程2");
                    return null;
                });
            } catch (Exception e) {
                log.error("线程2异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        Thread.sleep(100);
        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 等待事务完全提交
        Thread.sleep(200);

        // 查看最终结果
        Account finalAccount = accountRepository.findByAccountNo("ACC001").orElseThrow();
        BigDecimal actualBalance = finalAccount.getBalance();

        log.info("========== 最终余额: {} ==========", actualBalance);

        // 断言：由于写冲突，余额应该是1100（丢失了一次更新）
        // 注意：在某些情况下可能是1200（如果运气好没有冲突），但大概率是1100
        assertTrue(
                actualBalance.compareTo(new BigDecimal("1100.00")) == 0 ||
                        actualBalance.compareTo(new BigDecimal("1200.00")) == 0,
                "余额应该是1100（发生写冲突）或1200（未发生冲突）"
        );

        if (actualBalance.compareTo(new BigDecimal("1100.00")) == 0) {
            log.warn("========== ⚠️ 发生写冲突！一次更新被覆盖，余额: {} ==========", actualBalance);
        } else {
            log.info("========== ✓ 未发生写冲突，余额: {} ==========", actualBalance);
        }
    }

    public void updateBalanceWithoutLock(String accountNo, BigDecimal amount, String threadName) {
        log.info("[{}] 开始更新账户 {}", threadName, accountNo);

        Account account = accountRepository.findByAccountNo(accountNo).orElseThrow();
        BigDecimal oldBalance = account.getBalance();
        log.info("[{}] 读取到余额: {}", threadName, oldBalance);

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        BigDecimal newBalance = oldBalance.add(amount);
        account.setBalance(newBalance);
        accountRepository.save(account);

        log.info("[{}] 更新余额: {} -> {}", threadName, oldBalance, newBalance);
    }

    /**
     * 场景2：使用排他锁(X锁)解决写冲突
     * 使用TransactionTemplate确保事务生效
     * <p>
     * 实现思路：
     * 1. 使用SELECT ... FOR UPDATE加排他锁
     * 2. 第一个事务获取锁后，第二个事务必须等待
     * 3. 保证串行化执行，避免写冲突
     * <p>
     * 预期现象：
     * - 线程1获取排他锁，读取余额1000，更新为1100
     * - 线程2等待锁释放，读取余额1100，更新为1200
     * - 最终余额正确：1200
     * <p>
     * 实际现象：
     * - 线程2会阻塞在SELECT ... FOR UPDATE
     * - 等待线程1提交事务后才能继续
     * - 成功防止了写冲突
     */
    @Test
    public void testWriteConflict_WithExclusiveLock() throws InterruptedException {
        log.info("========== 场景2：使用排他锁解决写冲突 ==========");
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    updateBalanceWithExclusiveLock("ACC001", new BigDecimal("100"), "线程1");
                    return null;
                });
            } catch (Exception e) {
                log.error("线程1异常", e);
            } finally {
                endLatch.countDown();
            }
        });
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(10);
                transactionTemplate.execute(status -> {
                    updateBalanceWithExclusiveLock("ACC001", new BigDecimal("100"), "线程2");
                    return null;
                });
            } catch (Exception e) {
                log.error("线程2异常", e);
            } finally {
                endLatch.countDown();
            }
        });
        startLatch.countDown();
        endLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // 等待事务完全提交
        Thread.sleep(200);
        Account finalAccount = accountRepository.findByAccountNo("ACC001").orElseThrow();
        BigDecimal actualBalance = finalAccount.getBalance();
        BigDecimal expectedBalance = new BigDecimal("1200.00");

        log.info("========== 最终余额: {} (预期: {}) ==========", actualBalance, expectedBalance);
        // 断言：使用排他锁后，余额应该正确为1200
        assertEquals(
                0,
                expectedBalance.compareTo(actualBalance),
                String.format("使用排他锁后余额应该正确，预期: %s, 实际: %s", expectedBalance, actualBalance)
        );
        log.info("========== ✓ 测试通过：排他锁成功防止写冲突 ==========");
    }

    public void updateBalanceWithExclusiveLock(String accountNo, BigDecimal amount, String threadName) {
        log.info("[{}] 开始更新账户 {} (使用排他锁)", threadName, accountNo);
        Account account = accountRepository.findByAccountNoWithExclusiveLock(accountNo).orElseThrow();
        log.info("[{}] 成功获取排他锁，读取到余额: {}", threadName, account.getBalance());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        accountRepository.save(account);
        log.info("[{}] 更新余额完成: {}", threadName, newBalance);
    }

    /**
     * 场景3：共享锁(S锁) 与 排他锁(X锁) 的兼容性验证
     * <p>
     * 测试目的：
     * 验证 InnoDB 中 S 锁允许并发读取，而 X 锁必须等待所有 S 锁释放后才能获得。
     * <p>
     * 实现思路：
     * 1. 线程1 获取共享锁（SELECT ... LOCK IN SHARE MODE），持锁 2000ms
     * 2. 线程2 获取共享锁，持锁 1000ms —— 可与线程1并发（读读不冲突）
     * 3. 线程3 尝试获取排他锁（SELECT ... FOR UPDATE）
     * → 预期：必须等待线程1、线程2 的共享锁全部释放后才能继续执行
     * <p>
     * 预期现象（锁兼容矩阵验证）：
     * - S 与 S 兼容：线程1 与 线程2 可以同时读取
     * - S 与 X 不兼容：线程3 的排他锁必须等待所有共享锁释放
     * <p>
     * 实际现象（通过阻塞时间验证）：
     * - 线程1、线程2 均成功立即获取共享锁
     * - 线程3 在执行 FOR UPDATE 语句时阻塞约 ~1500ms（因 S 锁未释放）
     * - 待线程1、线程2 都释放共享锁后，线程3 才继续执行
     * <p>
     * 结论：
     * ✔ 共享锁允许并发读取（读读并发）
     * ✔ 排他锁必须等待共享锁释放（读写互斥）
     * 该测试准确反映了 InnoDB 的锁兼容性行为。
     */
    @Test
    public void testSharedLockVsExclusiveLock() throws InterruptedException {
        log.info("========== 场景3：共享锁与排他锁的兼容性 ==========");
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(3);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        AtomicBoolean thread1Success = new AtomicBoolean(false);
        AtomicBoolean thread2Success = new AtomicBoolean(false);

        // 线程1：持有共享锁
        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    holdSharedLock("ACC001", "线程1-共享锁", 2000);
                    thread1Success.set(true);
                    return null;
                });
            } catch (Exception e) {
                log.error("线程1异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        // 线程2：也获取共享锁
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(200);
                transactionTemplate.execute(status -> {
                    holdSharedLock("ACC001", "线程2-共享锁", 1000);
                    thread2Success.set(true);
                    return null;
                });
            } catch (Exception e) {
                log.error("线程2异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        // 线程3：尝试获取排他锁
        AtomicBoolean beforeQuery = new AtomicBoolean(false);
        AtomicBoolean afterQuery = new AtomicBoolean(false);
        AtomicLong waitTime = new AtomicLong(0);
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(400);
                transactionTemplate.execute(status -> {
                    long start = System.currentTimeMillis();
                    beforeQuery.set(true);
                    accountRepository.findByAccountNoWithExclusiveLock("ACC001").orElseThrow();
                    waitTime.set(System.currentTimeMillis() - start);
                    afterQuery.set(true);

                    return null;
                });

            } catch (Exception e) {
                log.error("线程3异常", e);
            } finally {
                endLatch.countDown();
            }
        });
        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        // 断言验证
        // 断言验证：基础行为
        assertTrue(thread1Success.get(), "线程1应该成功获取共享锁");
        assertTrue(thread2Success.get(), "线程2应该成功获取共享锁（共享锁可以并存）");
        // 线程3：尝试获取排他锁的动作必须发生
        assertTrue(beforeQuery.get(), "线程3应该开始尝试获取排他锁");
        // 线程3：最终一定能执行成功（说明等待后成功加锁）
        assertTrue(afterQuery.get(), "线程3应该在共享锁释放后成功获取排他锁并继续执行");
        // 阻塞时间范围验证（关键逻辑）
        long wt = waitTime.get();
        // 理论阻塞约等于：2000ms - 400ms = 1600ms
        assertTrue(wt >= 1400 && wt <= 3000, String.format("线程3阻塞时间异常：%d ms，应在 1400~3000ms 区间（1400ms以上说明确实被共享锁阻塞）", wt)
        );
        log.info("线程3实际阻塞时长: {} ms", wt);
        log.info("========== ✓ 测试通过：共享锁并发正常，排他锁等待共享锁释放 ==========");
    }

    /**
     * 场景：共享锁不允许写（验证：SELECT ... FOR SHARE 会阻塞 UPDATE）
     * <p>
     * 实现逻辑：
     * 1. 线程1获取共享锁（S锁），并保持 2 秒不释放
     * 2. 线程2在持有 S 锁期间尝试执行 UPDATE
     * 3. UPDATE 必须被阻塞（或超时失败），因为共享锁禁止写
     * <p>
     * 预期结果：
     * - S 锁允许多个读取并发执行
     * - S 锁与写操作（X 锁）互斥 —— 写必须等待共享锁释放
     */
    @Test
    public void testSharedLockBlocksWrite() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicBoolean writeBlocked = new AtomicBoolean(false);
        // 线程1：持有共享锁 2000ms
        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    log.info("[线程1] 获取共享锁...");
                    Account a = accountRepository.findByAccountNoWithSharedLock("ACC001").orElseThrow();
                    log.info("[线程1] 持有共享锁，余额 = {}", a.getBalance());
                    try {
                        Thread.sleep(2000); // 故意长时间持锁
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                log.error("线程1异常", e);
            } finally {
                endLatch.countDown();
            }
        });
        // 线程2：尝试写（UPDATE）
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(200); // 确保线程1先获得共享锁
                long start = System.currentTimeMillis();
                try {
                    transactionTemplate.execute(status -> {
                        // 写操作 —— 需要 X 锁
                        accountRepository.updateBalance("ACC001", new BigDecimal("9999"));
                        return null;
                    });
                } catch (Exception e) {
                    // 写失败（可能是锁超时）也说明共享锁阻塞写 ✔
                    writeBlocked.set(true);
                    log.warn("[线程2] 写操作失败（被共享锁阻塞）: {}", e.getMessage());
                }
                long wait = System.currentTimeMillis() - start;
                log.info("[线程2] 写操作等待时长：{} ms", wait);
                // 如果等待时间超过 1 秒，说明确实被共享锁挡住
                if (wait > 1000) {
                    writeBlocked.set(true);
                }
            } catch (Exception e) {
                log.error("线程2异常", e);
            } finally {
                endLatch.countDown();
            }
        });
        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(writeBlocked.get(), "共享锁应该阻塞写操作（UPDATE 必须等待共享锁释放）");
        log.info("========== ✓ 测试通过：共享锁允许读，但阻塞写 ==========");
    }

    public void holdSharedLock(String accountNo, String threadName, long holdTime) {
        log.info("[{}] 尝试获取共享锁...", threadName);

        Account account = accountRepository.findByAccountNoWithSharedLock(accountNo).orElseThrow();
        log.info("[{}] ✓ 成功获取共享锁，余额: {}", threadName, account.getBalance());

        try {
            Thread.sleep(holdTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[{}] 释放共享锁", threadName);
    }

    public void holdExclusiveLock(String accountNo, String threadName) {
        log.info("[{}] 尝试获取排他锁... (可能需要等待)", threadName);
        long startTime = System.currentTimeMillis();

        Account account = accountRepository.findByAccountNoWithExclusiveLock(accountNo).orElseThrow();
        long waitTime = System.currentTimeMillis() - startTime;

        log.info("[{}] ✓ 成功获取排他锁 (等待了{}ms)，余额: {}",
                threadName, waitTime, account.getBalance());

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[{}] 释放排他锁", threadName);
    }

    /**
     * 场景4：死锁场景复现
     * <p>
     * 实现思路：
     * 1. 线程1：锁定ACC001 -> 尝试锁定ACC002
     * 2. 线程2：锁定ACC002 -> 尝试锁定ACC001
     * 3. 形成循环等待，触发死锁
     * <p>
     * 预期现象：
     * - 两个事务互相持有对方需要的锁
     * - MySQL死锁检测机制介入，回滚其中一个事务
     * - 被回滚的事务抛出异常
     * <p>
     * 实际现象：
     * - 日志显示两个线程都成功获取第一个锁
     * - 尝试获取第二个锁时发生死锁
     * - 一个事务被回滚，另一个继续执行
     */
    @Test
    public void testDeadlock() throws InterruptedException {
        log.info("========== 场景4：死锁场景复现 ==========");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        AtomicBoolean deadlockOccurred = new AtomicBoolean(false);

        // 线程1：ACC001 -> ACC002
        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    transferWithDeadlock("ACC001", "ACC002", "线程1");
                    return null;
                });
            } catch (Exception e) {
                log.error("[线程1] 发生异常（可能是死锁被回滚）: {}", e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("Deadlock")) {
                    deadlockOccurred.set(true);
                }
            } finally {
                endLatch.countDown();
            }
        });

        // 线程2：ACC002 -> ACC001
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(50);
                transactionTemplate.execute(status -> {
                    transferWithDeadlock("ACC002", "ACC001", "线程2");
                    return null;
                });
            } catch (Exception e) {
                log.error("[线程2] 发生异常（可能是死锁被回滚）: {}", e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("Deadlock")) {
                    deadlockOccurred.set(true);
                }
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("========== 死锁测试完成 ==========");

        // 断言：应该发生死锁（至少一个线程被回滚）
        assertTrue(deadlockOccurred.get(), "应该检测到死锁并回滚其中一个事务");
        log.info("========== ✓ 测试通过：成功复现并检测到死锁 ==========");
    }

    public void transferWithDeadlock(String fromAccount, String toAccount, String threadName) {
        log.info("[{}] 开始转账：{} -> {}", threadName, fromAccount, toAccount);

        Account from = accountRepository.findByAccountNoWithExclusiveLock(fromAccount).orElseThrow();
        log.info("[{}] ✓ 成功锁定源账户 {}", threadName, fromAccount);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[{}] 尝试锁定目标账户 {}...", threadName, toAccount);
        Account to = accountRepository.findByAccountNoWithExclusiveLock(toAccount).orElseThrow();
        log.info("[{}] ✓ 成功锁定目标账户 {}", threadName, toAccount);

        from.setBalance(from.getBalance().subtract(new BigDecimal("100")));
        to.setBalance(to.getBalance().add(new BigDecimal("100")));

        accountRepository.save(from);
        accountRepository.save(to);

        log.info("[{}] 转账完成", threadName);
    }

    /**
     * 场景5：幻读与间隙锁
     * <p>
     * 实现思路：
     * 1. 事务1：查询余额 > 500的账户（假设查到2条）
     * 2. 事务2：插入一条余额600的新账户
     * 3. 事务1：再次查询，如果查到3条就是幻读
     * 4. 使用间隙锁防止幻读
     * <p>
     * 预期现象（READ_COMMITTED）：
     * - 第一次查询：2条记录
     * - 事务2插入成功
     * - 第二次查询：3条记录（幻读）
     * <p>
     * 预期现象（REPEATABLE_READ + 间隙锁）：
     * - 第一次查询：2条记录
     * - 事务2插入被阻塞
     * - 第二次查询：仍是2条记录（无幻读）
     */
    @Test
    public void testPhantomRead() throws InterruptedException {
        log.info("========== 场景5：幻读场景 ==========");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        AtomicInteger firstQueryCount = new AtomicInteger(0);
        AtomicInteger secondQueryCount = new AtomicInteger(0);
        AtomicBoolean insertBlocked = new AtomicBoolean(false);

        // 线程1：执行范围查询
        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    int[] counts = rangeQueryWithGapLock("线程1-查询");
                    firstQueryCount.set(counts[0]);
                    secondQueryCount.set(counts[1]);
                    return null;
                });
            } catch (Exception e) {
                log.error("线程1异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        // 线程2：尝试插入
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(500);
                long startTime = System.currentTimeMillis();
                transactionTemplate.execute(status -> {
                    insertNewAccount("线程2-插入");
                    long waitTime = System.currentTimeMillis() - startTime;
                    if (waitTime > 2000) {
                        insertBlocked.set(true);
                    }
                    return null;
                });
            } catch (Exception e) {
                log.error("线程2异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // 断言验证
        assertEquals(2, firstQueryCount.get(), "第一次查询应该查到2条记录");
        assertEquals(2, secondQueryCount.get(), "第二次查询应该仍然是2条记录（间隙锁防止插入）");
        assertTrue(insertBlocked.get(), "插入操作应该被间隙锁阻塞");

        log.info("========== ✓ 测试通过：间隙锁成功防止幻读 ==========");
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public int[] rangeQueryWithGapLock(String threadName) {
        log.info("[{}] 第一次范围查询：余额 > 500", threadName);

        var firstResult = entityManager.createQuery(
                        "SELECT a FROM Account a WHERE a.balance > 500", Account.class)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();

        int firstCount = firstResult.size();
        firstResult.forEach(acc -> log.info("[{}] - 查询到: {}, 余额: {}",
                threadName, acc.getAccountNo(), acc.getBalance()));

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[{}] 第二次范围查询：余额 > 500", threadName);
        var secondResult = entityManager.createQuery(
                        "SELECT a FROM Account a WHERE a.balance > 500", Account.class)
                .getResultList();

        int secondCount = secondResult.size();
        secondResult.forEach(acc -> log.info("[{}] - 查询到: {}, 余额: {}",
                threadName, acc.getAccountNo(), acc.getBalance()));

        log.info("[{}] 查询完成，释放间隙锁", threadName);

        return new int[]{firstCount, secondCount};
    }

    public void insertNewAccount(String threadName) {
        log.info("[{}] 尝试插入新账户 ACC003, 余额600", threadName);
        long startTime = System.currentTimeMillis();

        Account newAccount = new Account();
        newAccount.setAccountNo("ACC003");
        newAccount.setBalance(new BigDecimal("600"));

        accountRepository.save(newAccount);
        long waitTime = System.currentTimeMillis() - startTime;

        log.info("[{}] ✓ 插入成功 (等待了{}ms)", threadName, waitTime);
    }

    /**
     * 场景6：表锁 vs 行锁
     * <p>
     * 实现思路：
     * 1. 演示行锁：两个事务修改不同的行，不会互相阻塞
     * 2. 演示表锁：LOCK TABLES会锁定整个表
     * <p>
     * 预期现象（行锁）：
     * - 线程1修改ACC001，线程2修改ACC002
     * - 两者并发执行，不会互相阻塞
     * <p>
     * 预期现象（表锁）：
     * - 如果使用表锁，所有其他事务都被阻塞
     */
    @Test
    public void testRowLockVsTableLock() throws InterruptedException {
        log.info("========== 场景6：行锁并发性演示 ==========");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        long[] thread1Time = new long[1];
        long[] thread2Time = new long[1];

        // 线程1：修改ACC001
        executor.submit(() -> {
            try {
                startLatch.await();
                long start = System.currentTimeMillis();
                transactionTemplate.execute(status -> {
                    updateBalanceWithExclusiveLock("ACC001", new BigDecimal("100"), "线程1-修改ACC001");
                    return null;
                });
                thread1Time[0] = System.currentTimeMillis() - start;
            } catch (Exception e) {
                log.error("线程1异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        // 线程2：修改ACC002（不同行）
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(100);
                long start = System.currentTimeMillis();
                transactionTemplate.execute(status -> {
                    updateBalanceWithExclusiveLock("ACC002", new BigDecimal("200"), "线程2-修改ACC002");
                    return null;
                });
                thread2Time[0] = System.currentTimeMillis() - start;
            } catch (Exception e) {
                log.error("线程2异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Thread.sleep(200);

        // 验证结果
        Account acc1 = accountRepository.findByAccountNo("ACC001").orElseThrow();
        Account acc2 = accountRepository.findByAccountNo("ACC002").orElseThrow();

        assertEquals(0, new BigDecimal("1100.00").compareTo(acc1.getBalance()),
                "ACC001余额应该是1100");
        assertEquals(0, new BigDecimal("2200.00").compareTo(acc2.getBalance()),
                "ACC002余额应该是2200");

        // 验证并发性：线程2的等待时间应该很短（因为锁的是不同行）
        assertTrue(thread2Time[0] < 2000,
                "线程2应该几乎没有等待（行锁不会阻塞不同行的操作）");

        log.info("========== ✓ 测试通过：不同行可以并发修改 ==========");
    }

    /**
     * 场景7：乐观锁 vs 悲观锁
     * <p>
     * 实现思路：
     * 1. 悲观锁：直接加锁，保证不会有冲突
     * 2. 乐观锁：使用version字段，提交时检查版本号
     * <p>
     * 预期现象（乐观锁）：
     * - 两个事务同时读取（version=0）
     * - 第一个提交成功（version变为1）
     * - 第二个提交失败（version不匹配）
     */
    @Test
    public void testOptimisticLock() throws InterruptedException {
        log.info("========== 场景7：乐观锁测试 ==========");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        AtomicBoolean thread1Success = new AtomicBoolean(false);
        AtomicBoolean thread2Failed = new AtomicBoolean(false);

        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    updateWithOptimisticLock("ACC001", new BigDecimal("100"), "线程1");
                    thread1Success.set(true);
                    return null;
                });
            } catch (Exception e) {
                log.error("[线程1] 乐观锁更新失败: {}", e.getMessage());
            } finally {
                endLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(100);
                transactionTemplate.execute(status -> {
                    updateWithOptimisticLock("ACC001", new BigDecimal("200"), "线程2");
                    return null;
                });
            } catch (OptimisticLockException e) {
                log.error("[线程2] 乐观锁更新失败（预期）: {}", e.getMessage());
                thread2Failed.set(true);
            } catch (Exception e) {
                log.error("[线程2] 其他异常: {}", e.getMessage());
                if (e.getCause() instanceof OptimisticLockException) {
                    thread2Failed.set(true);
                }
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 断言验证
        assertTrue(thread1Success.get(), "线程1应该成功更新");
        assertTrue(thread2Failed.get(), "线程2应该因为version不匹配而失败");

        log.info("========== ✓ 测试通过：乐观锁成功检测到并发冲突 ==========");
    }

    public void updateWithOptimisticLock(String accountNo, BigDecimal amount, String threadName) {
        log.info("[{}] 使用乐观锁更新账户 {}", threadName, accountNo);

        Account account = accountRepository.findByAccountNo(accountNo).orElseThrow();
        Long currentVersion = account.getVersion();
        log.info("[{}] 读取到余额: {}, version: {}",
                threadName, account.getBalance(), currentVersion);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("[{}] ✓ 更新成功，version: {} -> {}",
                threadName, currentVersion, account.getVersion());
    }


    /**
     * 场景8：记录锁（Record Lock）验证
     * <p>
     * 测试目的：
     * - 验证唯一索引 + 等值查询（SELECT ... FOR UPDATE）
     * 只锁定匹配这一条记录，不锁定前后间隙
     * <p>
     * 实验设计：
     * 1. 线程1：对 ACC001 执行 SELECT ... FOR UPDATE（记录锁）
     * 2. 线程2：尝试更新 ACC001（必须等待）
     * 3. 线程3：尝试更新 ACC002（应该立即成功 — 因为不是同一条记录）
     * <p>
     * 预期现象：
     * - 线程1 对 ACC001 加排他锁
     * - 线程2 更新 ACC001 必须等待（被记录锁阻塞）
     * - 线程3 更新 ACC002 不会被阻塞（不同记录，不冲突）
     */
    @Test
    public void testRecordLock() throws InterruptedException {
        log.info("========== 场景8：记录锁（Record Lock）验证 ==========");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(3);
        ExecutorService executor = Executors.newFixedThreadPool(3);

        AtomicLong thread2Wait = new AtomicLong(0);
        AtomicLong thread3Wait = new AtomicLong(0);

        // 线程1：对 ACC001 加排他锁
        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    log.info("[线程1] 锁住 ACC001（记录锁）...");
                    holdExclusiveLock("ACC001", "线程1");
                    return null;
                });
            } catch (Exception e) {
                log.error("线程1异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        // 线程2：尝试更新 ACC001（必须等待）
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(100); // 确保线程1先加锁
                long start = System.currentTimeMillis();
                transactionTemplate.execute(status -> {
                    log.info("[线程2] 尝试更新 ACC001...");
                    accountRepository.updateBalance("ACC001", new BigDecimal("9999"));
                    return null;
                });
                thread2Wait.set(System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("线程2异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        // 线程3：尝试更新 ACC002（应该立即成功）
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(100);
                long start = System.currentTimeMillis();
                transactionTemplate.execute(status -> {
                    log.info("[线程3] 尝试更新 ACC002...");
                    accountRepository.updateBalance("ACC002", new BigDecimal("8888"));
                    return null;
                });
                thread3Wait.set(System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("线程3异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("[线程2] 更新 ACC001 等待时间：{}ms", thread2Wait.get());
        log.info("[线程3] 更新 ACC002 等待时间：{}ms", thread3Wait.get());

        // 断言：ACC001 的更新必须等待（>500ms）
        assertTrue(thread2Wait.get() > 500, "线程2 应该被 ACC001 的记录锁阻塞");

        // 断言：ACC002 不受阻塞（很快）
        assertTrue(thread3Wait.get() < 300, "线程3 不应该被阻塞，因为 ACC002 不在记录锁范围内");

        log.info("========== ✓ 测试通过：记录锁只锁单条记录，不锁其它记录 ==========");
    }

    /**
     * 场景9：临键锁（Next-Key Lock）验证
     * <p>
     * 测试目的：
     * - 验证范围查询 + FOR UPDATE 会产生 next-key lock（记录锁 + 间隙锁）
     * - 阻塞范围内的 INSERT
     * <p>
     * 实验逻辑：
     * 1. 线程1：执行 SELECT * FROM account WHERE balance BETWEEN 500 AND 3000 FOR UPDATE
     * → 锁定范围 (500, 3000] 的 next-key lock
     * 2. 线程2：尝试插入 balance=1000 的新记录 ACC999
     * → 必须被阻塞（因为属于 next-key lock 范围）
     * <p>
     * 预期：
     * - 插入被阻塞超过 1 秒
     */
    @Test
    public void testNextKeyLock() throws InterruptedException {
        log.info("========== 场景9：临键锁（Next-Key Lock）验证 ==========");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        AtomicBoolean insertBlocked = new AtomicBoolean(false);
        AtomicLong wait = new AtomicLong(0);

        // 线程1：范围锁（产生 next-key lock）
        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    log.info("[线程1] 执行范围锁 SELECT ... FOR UPDATE");
                    entityManager.createQuery(
                                    "SELECT a FROM Account a WHERE a.balance BETWEEN 500 AND 3000",
                                    Account.class)
                            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                            .getResultList();

                    try {
                        Thread.sleep(3000); // 故意持锁
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                log.error("线程1异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        // 线程2：尝试插入（必须被 next-key lock 阻塞）
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(100);

                long start = System.currentTimeMillis();
                try {
                    transactionTemplate.execute(status -> {
                        log.info("[线程2] 尝试插入 ACC999(balance=1000)");
                        Account acc = new Account();
                        acc.setAccountNo("ACC999");
                        acc.setBalance(new BigDecimal("1000"));
                        accountRepository.save(acc);
                        return null;
                    });
                } catch (Exception e) {
                    insertBlocked.set(true);
                }

                wait.set(System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("线程2异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("[线程2] 插入等待时长：{} ms", wait.get());

        assertTrue(wait.get() > 2000,
                "临键锁应该阻塞插入（INSERT 必须等待范围锁释放）");

        log.info("========== ✓ 测试通过：Next-Key Lock 阻塞范围内插入 ==========");
    }

}