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
     * 场景3：共享锁(S锁) vs 排他锁(X锁)的兼容性
     * <p>
     * 实现思路：
     * 1. 线程1持有共享锁(SELECT ... LOCK IN SHARE MODE)
     * 2. 线程2尝试获取共享锁(可以成功)
     * 3. 线程3尝试获取排他锁(必须等待)
     * <p>
     * 预期现象：
     * - 多个共享锁可以共存（读读不冲突）
     * - 排他锁与共享锁互斥（读写冲突）
     * <p>
     * 实际现象：
     * - 线程1、2都能获取共享锁，同时读取数据
     * - 线程3阻塞等待，直到所有共享锁释放
     */
    @Test
    public void testSharedLockVsExclusiveLock() throws InterruptedException {
        log.info("========== 场景3：共享锁与排他锁的兼容性 ==========");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(3);
        ExecutorService executor = Executors.newFixedThreadPool(3);

        AtomicBoolean thread1Success = new AtomicBoolean(false);
        AtomicBoolean thread2Success = new AtomicBoolean(false);
        AtomicBoolean thread3Blocked = new AtomicBoolean(false);

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
        executor.submit(() -> {
            try {
                startLatch.await();
                long startTime = System.currentTimeMillis();
                transactionTemplate.execute(status -> {
                    // 在这里直接验证线程3是否被阻塞
                    Account account = accountRepository.findByAccountNoWithExclusiveLock("ACC001").orElseThrow();
                    if (account != null) {
                        long waitTime = System.currentTimeMillis() - startTime;
                        if (waitTime > 10000) {
                            thread3Blocked.set(true); // 如果排他锁被阻塞超过1000ms，设置为true
                        }
                    }
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
        assertTrue(thread1Success.get(), "线程1应该成功获取共享锁");
        assertTrue(thread2Success.get(), "线程2应该成功获取共享锁（共享锁可以并存）");
        assertTrue(thread3Blocked.get(), "线程3应该被阻塞，直到共享锁释放");

        log.info("========== ✓ 测试通过：共享锁可以并存，排他锁需要等待 ==========");
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
}