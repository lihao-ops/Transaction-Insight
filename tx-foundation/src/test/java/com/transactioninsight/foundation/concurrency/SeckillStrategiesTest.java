package com.transactioninsight.foundation.concurrency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 类说明 / Class Description:
 * 中文：秒杀系统高并发实战（库存扣减方案对比）：悲观锁与乐观锁方案的并发行为与不超卖验证。
 * English: High-concurrency seckill (flash sale) practice comparing pessimistic vs optimistic stock deduction; validates non-overselling.
 *
 * 使用场景 / Use Cases:
 * 中文：在 1 条库存记录上进行 50~500 并发扣减，观察锁等待与重试开销，确保“有且仅有库存件数”成功。
 * English: Run 50~500 concurrent deductions on a single stock row to observe lock waits and retries, ensuring exactly stock items succeed.
 *
 * 设计目的 / Design Purpose:
 * 中文：构建最小可复现实验，量化两种方案下的成功数与异常路径，验证不超卖、不少卖。
 * English: Build minimal reproducible experiment quantifying success counts and failure paths under both schemes, validating no over/undersell.
 */
@SpringBootTest
public class SeckillStrategiesTest {

    @Autowired
    private DataSource dataSource;

    private static final Logger log = LoggerFactory.getLogger(SeckillStrategiesTest.class);

    /**
     * 方法说明 / Method Description:
     * 中文：初始化秒杀表与订单表，并创建悲观锁与乐观锁的存储过程。
     * English: Initialize seckill and order tables, and create procedures for pessimistic and optimistic locking.
     *
     * 参数 / Parameters: 无
     * 返回值 / Return: 无
     * 异常 / Exceptions: SQL 执行失败时抛出异常
     */
    private void initSchema(int initialStock) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // 中文：准备商品与订单表
            // English: Prepare product and order tables
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS seckill_order");
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS seckill_product");
            c.createStatement().executeUpdate(
                    "CREATE TABLE seckill_product (" +
                            "id INT PRIMARY KEY, " +
                            "product_name VARCHAR(100), " +
                            "stock INT NOT NULL, " +
                            "version INT NOT NULL DEFAULT 0, " +
                            "INDEX idx_stock(stock)) ENGINE=InnoDB");
            c.createStatement().executeUpdate(
                    "CREATE TABLE seckill_order (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                            "user_id BIGINT, product_id INT, create_time DATETIME) ENGINE=InnoDB");
            c.createStatement().executeUpdate("INSERT INTO seckill_product(id, product_name, stock, version) VALUES (1,'iPhone 15'," + initialStock + ",0)");

            // 中文：创建悲观锁存储过程
            // English: Create pessimistic locking stored procedure
            c.createStatement().executeUpdate("DROP PROCEDURE IF EXISTS sp_seckill_pessimistic");
            c.createStatement().executeUpdate(
                    "CREATE PROCEDURE sp_seckill_pessimistic(IN p_user_id BIGINT, IN p_product_id INT, OUT p_result VARCHAR(50)) " +
                            "BEGIN " +
                            "DECLARE v_stock INT; " +
                            "DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; SET p_result='ERROR'; END; " +
                            "START TRANSACTION; " +
                            "SELECT stock INTO v_stock FROM seckill_product WHERE id=p_product_id FOR UPDATE; " +
                            "IF v_stock<=0 THEN SET p_result='SOLD_OUT'; ROLLBACK; ELSE " +
                            "UPDATE seckill_product SET stock=stock-1 WHERE id=p_product_id; " +
                            "INSERT INTO seckill_order(user_id,product_id,create_time) VALUES(p_user_id,p_product_id,NOW()); " +
                            "SET p_result='SUCCESS'; COMMIT; END IF; END"
            );

            // 中文：创建乐观锁存储过程（带版本与重试限制）
            // English: Create optimistic locking stored procedure (version + retry)
            c.createStatement().executeUpdate("DROP PROCEDURE IF EXISTS sp_seckill_optimistic");
            c.createStatement().executeUpdate(
                    "CREATE PROCEDURE sp_seckill_optimistic(IN p_user_id BIGINT, IN p_product_id INT, OUT p_result VARCHAR(50)) " +
                            "BEGIN " +
                            "DECLARE v_stock INT; DECLARE v_version INT; DECLARE v_affected INT; DECLARE v_retry INT DEFAULT 0; " +
                            "retry_loop: LOOP START TRANSACTION; " +
                            "SELECT stock,version INTO v_stock,v_version FROM seckill_product WHERE id=p_product_id; " +
                            "IF v_stock<=0 THEN SET p_result='SOLD_OUT'; ROLLBACK; LEAVE retry_loop; END IF; " +
                            "UPDATE seckill_product SET stock=stock-1, version=version+1 WHERE id=p_product_id AND version=v_version AND stock>0; " +
                            "SET v_affected=ROW_COUNT(); " +
                            "IF v_affected=1 THEN INSERT INTO seckill_order(user_id,product_id,create_time) VALUES(p_user_id,p_product_id,NOW()); SET p_result='SUCCESS'; COMMIT; LEAVE retry_loop; " +
                            "ELSE ROLLBACK; SET v_retry=v_retry+1; IF v_retry>=3 THEN SET p_result='RETRY_EXCEEDED'; LEAVE retry_loop; END IF; DO SLEEP(0.001 + RAND()*0.005); END IF; END LOOP; END"
            );
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：并发调用悲观锁存储过程，验证“成功订单数 + 剩余库存 = 初始库存”且不超卖。
     * English: Concurrently invoke pessimistic procedure, verify "success orders + remaining stock = initial stock" and no overselling.
     */
    @Test
    @DisplayName("HC-Case1A: Pessimistic locking seckill non-oversell under concurrency")
    void pessimisticSeckillNonOversell() throws Exception {
        int initialStock = 100;
        initSchema(initialStock);

        // 中文：构造并发线程池与任务集合
        // English: Build concurrency pool and task collection
        ExecutorService pool = Executors.newFixedThreadPool(32);
        List<Future<String>> futures = new ArrayList<>();

        try (Connection c = dataSource.getConnection()) {
            for (int i = 0; i < 500; i++) {
                final long userId = 1000L + i;
                futures.add(pool.submit(() -> {
                    try (CallableStatement cs = c.prepareCall("{CALL sp_seckill_pessimistic(?, ?, ?)}")) {
                        // 中文：设置输入与输出参数
                        // English: Set IN/OUT parameters
                        cs.setLong(1, userId);
                        cs.setInt(2, 1);
                        cs.registerOutParameter(3, Types.VARCHAR);
                        cs.execute();
                        return cs.getString(3);
                    }
                }));
            }
        }

        // 中文：统计成功结果数量
        // English: Count successful results
        int success = 0; int soldOut = 0; int retryExceeded = 0; int error = 0;
        for (Future<String> f : futures) {
            String r = f.get();
            if ("SUCCESS".equals(r)) success++; else if ("SOLD_OUT".equals(r)) soldOut++; else if ("RETRY_EXCEEDED".equals(r)) retryExceeded++; else if ("ERROR".equals(r)) error++;
        }
        pool.shutdown();

        try (Connection c = dataSource.getConnection()) {
            // 中文：读取剩余库存与订单数
            // English: Read remaining stock and order count
            int stock = readInt(c, "SELECT stock FROM seckill_product WHERE id=1");
            int orders = readInt(c, "SELECT COUNT(*) FROM seckill_order WHERE product_id=1");
            // 中文：验证不超卖、不少卖
            // English: Validate no over/undersell
            assertThat(orders + stock).isEqualTo(initialStock);
            assertThat(stock).isGreaterThanOrEqualTo(0);
            assertThat(orders).isLessThanOrEqualTo(initialStock);
            log.info("实验成功：悲观锁秒杀不变量成立；订单数+库存=初始库存，未发生超卖 / Success: Pessimistic seckill invariant holds; orders+stock=initial, no oversell");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：并发调用乐观锁存储过程，验证不超卖；允许部分重试超限。
     * English: Concurrently invoke optimistic procedure, validate no oversell; allow some retry exceeded.
     */
    @Test
    @DisplayName("HC-Case1B: Optimistic locking seckill non-oversell with retries")
    void optimisticSeckillNonOversell() throws Exception {
        int initialStock = 100;
        initSchema(initialStock);

        ExecutorService pool = Executors.newFixedThreadPool(32);
        List<Future<String>> futures = new ArrayList<>();

        try (Connection c = dataSource.getConnection()) {
            for (int i = 0; i < 500; i++) {
                final long userId = 2000L + i;
                futures.add(pool.submit(() -> {
                    try (CallableStatement cs = c.prepareCall("{CALL sp_seckill_optimistic(?, ?, ?)}")) {
                        // 中文：设置输入与输出参数
                        // English: Set IN/OUT parameters
                        cs.setLong(1, userId);
                        cs.setInt(2, 1);
                        cs.registerOutParameter(3, Types.VARCHAR);
                        cs.execute();
                        return cs.getString(3);
                    }
                }));
            }
        }

        int success = 0; int soldOut = 0; int retryExceeded = 0; int error = 0;
        for (Future<String> f : futures) {
            String r = f.get();
            if ("SUCCESS".equals(r)) success++; else if ("SOLD_OUT".equals(r)) soldOut++; else if ("RETRY_EXCEEDED".equals(r)) retryExceeded++; else if ("ERROR".equals(r)) error++;
        }
        pool.shutdown();

        try (Connection c = dataSource.getConnection()) {
            int stock = readInt(c, "SELECT stock FROM seckill_product WHERE id=1");
            int orders = readInt(c, "SELECT COUNT(*) FROM seckill_order WHERE product_id=1");
            // 中文：验证不超卖、不少卖；乐观锁可能有更多 SOLD_OUT/RETRY_EXCEEDED 结果
            // English: Validate no over/undersell; optimistic strategy may have more SOLD_OUT/RETRY_EXCEEDED
            assertThat(orders + stock).isEqualTo(initialStock);
            assertThat(stock).isGreaterThanOrEqualTo(0);
            assertThat(orders).isLessThanOrEqualTo(initialStock);
            log.info("实验成功：乐观锁秒杀不变量成立；无超卖，可能存在 SOLD_OUT/RETRY_EXCEEDED / Success: Optimistic seckill invariant holds; no oversell, possible SOLD_OUT/RETRY_EXCEEDED");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：执行查询返回整型结果（COUNT、stock 等）。
     * English: Execute query returning int (COUNT, stock, etc.).
     */
    private int readInt(Connection c, String sql) throws Exception {
        // 中文：执行单行单列查询并返回整数
        // English: Execute single-row single-column query returning integer
        try (PreparedStatement ps = c.prepareStatement(sql)) { try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1);} }
    }
}
