package com.transactioninsight.foundation.longTransaction;

import com.transactioninsight.foundation.deadlock.order_inventory.service.HiddenTxService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 测试目的：
 * -------------------------------
 * 复现“Spring 隐式开启事务，但未提交”的场景，
 * 证明：只要 @Transactional 包裹的方法抛异常但被 try/catch 吃掉，
 * Spring 事务不会自动提交 → 会形成长事务。
 */
@Slf4j
@SpringBootTest
public class HiddenTransactionTest {

    @Resource
    private HiddenTxService hiddenTxService;

    /**
     * TODO 无法复现长事务
     *
     * @Transactional 的事务范围是 方法级生命周期
     * <p>
     * 方法执行结束 → 事务必须结束（无论提交还是回滚）
     * 你没有手动 commit → Spring 自动 rollback。
     * <p>
     * 理论上---
     * Step1：执行一个会抛异常但被 catch 的业务方法；
     * Step2：事务未提交 → InnoDB 会记录一个长事务；
     * Step3：从 information_schema.innodb_trx 查询当前未提交事务；
     */
    @Test
    public void testHiddenTransaction() throws Exception {
        log.warn("========== 开始执行隐式未提交事务测试 ==========");

        hiddenTxService.doUpdateWithHiddenTransaction();

        log.warn("========== 开始查询 innodb_trx（活跃事务列表） ==========");

        String jdbc = "jdbc:mysql://localhost:3306/transaction_study?useSSL=false&serverTimezone=Asia/Shanghai";
        Connection conn = DriverManager.getConnection(jdbc, "root", "Q836184425");

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(
                "SELECT trx_id, trx_state, trx_started, trx_query " +
                        "FROM information_schema.innodb_trx"
        );

        while (rs.next()) {
            log.error("【活跃未提交事务】trx_id={}, state={}, start={}, query={}",
                    rs.getString("trx_id"),
                    rs.getString("trx_state"),
                    rs.getString("trx_started"),
                    rs.getString("trx_query")
            );
        }
        conn.close();

        log.warn("========== 测试结束：如果看到活跃事务，说明你的事务未提交成功 ==========");
    }

    /**
     * 复现成功，但最后会回滚是因为:
     * 运行单元测试，Spring Boot Test 有默认策略：
     * 每个测试方法运行结束后，自动回滚所有事务
     * 测试步骤：
     * 1. 调用业务方法制造一个未提交事务。
     * 2. Sleep 一段时间，确保事务仍处于 RUNNING 状态（方法未退出）。
     * 3. 通过 JDBC 查询 information_schema.innodb_trx 验证事务是否存在。
     * <p>
     * 关键点：
     * - 测试方法不能立即结束，否则 Spring Test 会自动 rollback。
     * - 在方法暂停期间，可在 SQL 客户端中看到长事务记录。
     */
    @Test
    public void testLongTransaction() throws Exception {
        log.warn("========== 开始制造长事务 ==========");
        hiddenTxService.openLongTxWithoutCommit();

        Thread.sleep(5000); // 等待事务挂住

        log.warn("========== 查询 innodb_trx ==========");

        String sql = "SELECT trx_id, trx_state, trx_started, trx_query " +
                "FROM information_schema.innodb_trx";

        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/transaction_study?useSSL=false&serverTimezone=Asia/Shanghai",
                "root", "Q836184425");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                log.error("【活跃事务】trx_id={}, state={}, query={}",
                        rs.getString("trx_id"),
                        rs.getString("trx_state"),
                        rs.getString("trx_query"));
            }
        }

        log.warn("========== 测试结束 ==========");
    }
}
