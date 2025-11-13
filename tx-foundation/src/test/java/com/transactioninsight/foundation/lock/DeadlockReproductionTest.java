package com.transactioninsight.foundation.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 类说明 / Class Description:
 * 中文：死锁复现实验，包含经典双事务死锁、索引顺序不一致死锁、间隙锁与插入意向锁冲突。
 * English: Deadlock reproduction experiments including classic two-transaction deadlock, index-order mismatch deadlock, and gap lock vs insert intent conflict.
 *
 * 使用场景 / Use Cases:
 * 中文：演示循环等待的本质与 MySQL 自动检测与回滚策略。
 * English: Demonstrate essence of circular wait and MySQL auto detection/rollback strategy.
 *
 * 设计目的 / Design Purpose:
 * 中文：通过并发线程与同步栅栏构造稳定的交叉锁序，观察死锁与回滚。
 * English: Use concurrent threads and latches to stably construct cross lock orders and observe deadlocks/rollbacks.
 */
@SpringBootTest
public class DeadlockReproductionTest {

    @Autowired
    private DataSource dataSource;

    private void seed() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS account_transaction");
            c.createStatement().executeUpdate(
                    "CREATE TABLE account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, balance DECIMAL(15,2) NOT NULL, " +
                            "frozen_amount DECIMAL(15,2) NOT NULL DEFAULT 0) ENGINE=InnoDB");
            c.createStatement().executeUpdate("INSERT INTO account_transaction (balance) VALUES (1000),(2000),(3000),(4000),(5000),(6000),(7000),(8000),(9000),(10000)");
            c.createStatement().executeUpdate("CREATE INDEX idx_balance ON account_transaction(balance)");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：经典双事务死锁：T1锁定id=1后等待id=2，T2锁定id=2后等待id=1；预期其中一个被回滚。
     * English: Classic two-transaction deadlock: T1 locks id=1 then waits id=2; T2 locks id=2 then waits id=1; expect one rolled back.
     */
    @Test
    @DisplayName("Lock-8A: Classic two-transaction deadlock with cross lock order")
    void classicTwoTxDeadlock() throws Exception {
        seed();
        AtomicBoolean t1Committed = new AtomicBoolean(false);
        AtomicBoolean t2RolledBack = new AtomicBoolean(false);
        CountDownLatch ready = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            try (Connection a = dataSource.getConnection()) {
                a.setAutoCommit(false);
                // 中文：T1更新id=1，加写锁
                // English: T1 updates id=1, acquiring write lock
                try (PreparedStatement ps = a.prepareStatement("UPDATE account_transaction SET balance = balance - 100 WHERE id = 1")) { ps.executeUpdate(); }
                // 中文：等待，确保T2获得id=2锁
                // English: Wait to ensure T2 acquires id=2 lock
                ready.countDown(); ready.await();
                // 中文：再更新id=2，形成循环等待
                // English: Then update id=2 forming circular wait
                try (PreparedStatement ps = a.prepareStatement("UPDATE account_transaction SET balance = balance + 100 WHERE id = 2")) { ps.executeUpdate(); }
                a.commit();
                t1Committed.set(true);
            } catch (Exception ignored) { /* deadlock possible */ }
        });

        Thread t2 = new Thread(() -> {
            try (Connection b = dataSource.getConnection()) {
                b.setAutoCommit(false);
                // 中文：T2更新id=2，加写锁
                // English: T2 updates id=2, acquiring write lock
                try (PreparedStatement ps = b.prepareStatement("UPDATE account_transaction SET balance = balance - 100 WHERE id = 2")) { ps.executeUpdate(); }
                ready.countDown(); ready.await();
                // 中文：再更新id=1，触发死锁检测
                // English: Then update id=1 triggering deadlock detection
                try (PreparedStatement ps = b.prepareStatement("UPDATE account_transaction SET balance = balance + 100 WHERE id = 1")) { ps.executeUpdate(); }
                b.commit();
            } catch (Exception e) {
                // 中文：其中一个事务被回滚
                // English: One transaction is rolled back
                t2RolledBack.set(true);
            }
        });

        t1.start(); t2.start(); t1.join(); t2.join();
        // 中文：断言至少一个事务被回滚，另一个可能提交
        // English: Assert at least one rolled back, the other may commit
        assertThat(t2RolledBack.get() || !t1Committed.get()).isTrue();
    }

    /**
     * 方法说明 / Method Description:
     * 中文：索引顺序不一致导致死锁；一个事务按二级索引顺序更新，另一个按主键倒序更新。
     * English: Deadlock due to inconsistent index order: one updates by secondary index order, the other by PK reverse order.
     */
    @Test
    @DisplayName("Lock-8B: Deadlock caused by inconsistent update order")
    void indexOrderMismatchDeadlock() throws Exception {
        seed();
        AtomicBoolean deadlocked = new AtomicBoolean(false);
        CountDownLatch barrier = new CountDownLatch(2);

        Thread tA = new Thread(() -> {
            try (Connection a = dataSource.getConnection()) {
                a.setAutoCommit(false);
                // 中文：通过二级索引范围更新（balance升序）
                // English: Range update via secondary index (balance ascending)
                try (PreparedStatement ps = a.prepareStatement("UPDATE account_transaction SET frozen_amount = frozen_amount + 100 WHERE balance BETWEEN 5000 AND 8000")) { ps.executeUpdate(); }
                barrier.countDown(); barrier.await();
                a.commit();
            } catch (Exception e) { deadlocked.set(true); }
        });

        Thread tB = new Thread(() -> {
            try (Connection b = dataSource.getConnection()) {
                b.setAutoCommit(false);
                // 中文：按主键倒序更新相同行集
                // English: Update same row set by PK reverse order
                try (PreparedStatement ps = b.prepareStatement("UPDATE account_transaction SET frozen_amount = frozen_amount + 100 WHERE id IN (15,14,2)")) { ps.executeUpdate(); }
                barrier.countDown(); barrier.await();
                b.commit();
            } catch (Exception e) { deadlocked.set(true); }
        });

        tA.start(); tB.start(); tA.join(); tB.join();
        assertThat(deadlocked.get()).isTrue();
    }

    /**
     * 方法说明 / Method Description:
     * 中文：间隙锁与插入意向锁冲突导致死锁；双方持有间隙锁后都尝试获取插入意向锁。
     * English: Deadlock due to gap lock vs insert intent conflict; both hold gap locks then attempt insert intent.
     */
    @Test
    @DisplayName("Lock-8C: Deadlock between gap lock and insert intent lock")
    void gapVsInsertIntentDeadlock() throws Exception {
        seed();
        AtomicBoolean oneRolledBack = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(2);

        Thread tA = new Thread(() -> {
            try (Connection a = dataSource.getConnection()) {
                a.setAutoCommit(false);
                // 中文：T1锁定不存在值的间隙
                // English: T1 locks gap on non-existent value
                try (PreparedStatement ps = a.prepareStatement("SELECT * FROM account_transaction WHERE balance = 6000 FOR UPDATE")) { ps.executeQuery(); }
                latch.countDown(); latch.await();
                // 中文：T1尝试插入间隙
                // English: T1 attempts insert into gap
                try (PreparedStatement ps = a.prepareStatement("INSERT INTO account_transaction (balance,frozen_amount) VALUES (6100,0)")) { ps.executeUpdate(); }
                a.commit();
            } catch (Exception e) { oneRolledBack.set(true); }
        });

        Thread tB = new Thread(() -> {
            try (Connection b = dataSource.getConnection()) {
                b.setAutoCommit(false);
                // 中文：T2锁定同间隙
                // English: T2 locks same gap
                try (PreparedStatement ps = b.prepareStatement("SELECT * FROM account_transaction WHERE balance = 6000 FOR UPDATE")) { ps.executeQuery(); }
                latch.countDown(); latch.await();
                // 中文：T2尝试插入同间隙，形成死锁
                // English: T2 attempts insert into gap forming deadlock
                try (PreparedStatement ps = b.prepareStatement("INSERT INTO account_transaction (balance,frozen_amount) VALUES (6000,0)")) { ps.executeUpdate(); }
                b.commit();
            } catch (Exception e) { oneRolledBack.set(true); }
        });

        tA.start(); tB.start(); tA.join(); tB.join();
        assertThat(oneRolledBack.get()).isTrue();
    }
}

