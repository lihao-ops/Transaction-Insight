package com.transactioninsight.foundation.lock;


import com.transactioninsight.foundation.model.Account;
import com.transactioninsight.foundation.model.AccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MySQL锁机制学习测试类
 * 包含：行锁、表锁、间隙锁、临键锁、死锁、写冲突等场景
 */
@Slf4j
@SpringBootTest
public class MySQLLockMechanismTests {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EntityManager entityManager;

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
                startLatch.await(); // 等待同时启动
                updateBalanceWithoutLock("ACC001", new BigDecimal("100"), "线程1");
            } catch (Exception e) {
                log.error("线程1异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        // 线程2：增加100
        executor.submit(() -> {
            try {
                startLatch.await(); // 等待同时启动
                updateBalanceWithoutLock("ACC001", new BigDecimal("100"), "线程2");
            } catch (Exception e) {
                log.error("线程2异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        Thread.sleep(100); // 确保两个线程都准备好
        startLatch.countDown(); // 同时启动
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 查看最终结果
        Account finalAccount = accountRepository.findByAccountNo("ACC001").orElseThrow();
        log.info("========== 最终余额: {} (预期1200，实际可能是1100) ==========", finalAccount.getBalance());
    }

    @Transactional
    public void updateBalanceWithoutLock(String accountNo, BigDecimal amount, String threadName) {
        log.info("[{}] 开始更新账户 {}", threadName, accountNo);

        // 普通查询，不加锁
        Account account = accountRepository.findByAccountNo(accountNo).orElseThrow();
        BigDecimal oldBalance = account.getBalance();
        log.info("[{}] 读取到余额: {}", threadName, oldBalance);

        try {
            Thread.sleep(50); // 模拟业务处理
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
                updateBalanceWithExclusiveLock("ACC001", new BigDecimal("100"), "线程1");
            } catch (Exception e) {
                log.error("线程1异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(10); // 稍微延迟，让线程1先获取锁
                updateBalanceWithExclusiveLock("ACC001", new BigDecimal("100"), "线程2");
            } catch (Exception e) {
                log.error("线程2异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Account finalAccount = accountRepository.findByAccountNo("ACC001").orElseThrow();
        log.info("========== 最终余额: {} (预期1200，使用锁后正确) ==========",
                finalAccount.getBalance());
    }

    @Transactional
    public void updateBalanceWithExclusiveLock(String accountNo, BigDecimal amount, String threadName) {
        log.info("[{}] 开始更新账户 {} (使用排他锁)", threadName, accountNo);

        // 使用排他锁查询 - SELECT ... FOR UPDATE
        Account account = accountRepository.findByAccountNoWithExclusiveLock(accountNo).orElseThrow();
        log.info("[{}] 成功获取排他锁，读取到余额: {}", threadName, account.getBalance());

        try {
            Thread.sleep(1000); // 模拟长时间业务处理
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        accountRepository.save(account);

        log.info("[{}] 更新余额完成: {}", threadName, newBalance);
        // 事务提交时释放锁
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

        // 线程1：持有共享锁
        executor.submit(() -> {
            try {
                startLatch.await();
                holdSharedLock("ACC001", "线程1-共享锁", 2000);
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
                holdSharedLock("ACC001", "线程2-共享锁", 1000);
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
                Thread.sleep(400);
                holdExclusiveLock("ACC001", "线程3-排他锁");
            } catch (Exception e) {
                log.error("线程3异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
    }

    @Transactional
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

    @Transactional
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

        // 线程1：ACC001 -> ACC002
        executor.submit(() -> {
            try {
                startLatch.await();
                transferWithDeadlock("ACC001", "ACC002", "线程1");
            } catch (Exception e) {
                log.error("[线程1] 发生异常（可能是死锁被回滚）: {}", e.getMessage());
            } finally {
                endLatch.countDown();
            }
        });

        // 线程2：ACC002 -> ACC001
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(50); // 稍微延迟，确保能形成死锁
                transferWithDeadlock("ACC002", "ACC001", "线程2");
            } catch (Exception e) {
                log.error("[线程2] 发生异常（可能是死锁被回滚）: {}", e.getMessage());
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("========== 死锁测试完成，查看日志中的死锁信息 ==========");
    }

    @Transactional
    public void transferWithDeadlock(String fromAccount, String toAccount, String threadName) {
        log.info("[{}] 开始转账：{} -> {}", threadName, fromAccount, toAccount);

        // 第一步：锁定源账户
        Account from = accountRepository.findByAccountNoWithExclusiveLock(fromAccount).orElseThrow();
        log.info("[{}] ✓ 成功锁定源账户 {}", threadName, fromAccount);

        try {
            Thread.sleep(500); // 增加死锁概率
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 第二步：锁定目标账户（这里可能死锁）
        log.info("[{}] 尝试锁定目标账户 {}...", threadName, toAccount);
        Account to = accountRepository.findByAccountNoWithExclusiveLock(toAccount).orElseThrow();
        log.info("[{}] ✓ 成功锁定目标账户 {}", threadName, toAccount);

        // 执行转账
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

        // 线程1：执行范围查询
        executor.submit(() -> {
            try {
                startLatch.await();
                rangeQueryWithGapLock("线程1-查询");
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
                Thread.sleep(500); // 等待线程1先查询
                insertNewAccount("线程2-插入");
            } catch (Exception e) {
                log.error("线程2异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void rangeQueryWithGapLock(String threadName) {
        log.info("[{}] 第一次范围查询：余额 > 500", threadName);

        // 使用FOR UPDATE会加临键锁（记录锁+间隙锁）
        entityManager.createQuery(
                        "SELECT a FROM Account a WHERE a.balance > 500", Account.class)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList()
                .forEach(acc -> log.info("[{}] - 查询到: {}, 余额: {}",
                        threadName, acc.getAccountNo(), acc.getBalance()));

        try {
            Thread.sleep(3000); // 持有锁一段时间
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[{}] 第二次范围查询：余额 > 500", threadName);
        entityManager.createQuery(
                        "SELECT a FROM Account a WHERE a.balance > 500", Account.class)
                .getResultList()
                .forEach(acc -> log.info("[{}] - 查询到: {}, 余额: {}",
                        threadName, acc.getAccountNo(), acc.getBalance()));

        log.info("[{}] 查询完成，释放间隙锁", threadName);
    }

    @Transactional
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

        // 线程1：修改ACC001
        executor.submit(() -> {
            try {
                startLatch.await();
                updateBalanceWithExclusiveLock("ACC001", new BigDecimal("100"), "线程1-修改ACC001");
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
                updateBalanceWithExclusiveLock("ACC002", new BigDecimal("200"), "线程2-修改ACC002");
            } catch (Exception e) {
                log.error("线程2异常", e);
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("========== 行锁测试完成：不同行可以并发修改 ==========");
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

        executor.submit(() -> {
            try {
                startLatch.await();
                updateWithOptimisticLock("ACC001", new BigDecimal("100"), "线程1");
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
                updateWithOptimisticLock("ACC001", new BigDecimal("200"), "线程2");
            } catch (Exception e) {
                log.error("[线程2] 乐观锁更新失败（预期）: {}", e.getMessage());
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
    }

    @Transactional
    public void updateWithOptimisticLock(String accountNo, BigDecimal amount, String threadName) {
        log.info("[{}] 使用乐观锁更新账户 {}", threadName, accountNo);

        // 不加锁查询
        Account account = accountRepository.findByAccountNo(accountNo).orElseThrow();
        Long currentVersion = account.getVersion();
        log.info("[{}] 读取到余额: {}, version: {}",
                threadName, account.getBalance(), currentVersion);

        try {
            Thread.sleep(1000); // 模拟业务处理
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        account.setBalance(account.getBalance().add(amount));
        // JPA会自动检查version，如果不匹配会抛出OptimisticLockException
        accountRepository.save(account);

        log.info("[{}] ✓ 更新成功，version: {} -> {}",
                threadName, currentVersion, account.getVersion());
    }
}