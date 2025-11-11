package com.transactioninsight.springcore.mysql;

import com.transactioninsight.springcore.mysql.service.AccountService;
import com.transactioninsight.springcore.mysql.service.AccountService.AccountSeed;
import com.transactioninsight.springcore.mysql.service.TransactionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试目标：验证提交后的账户余额在新连接中仍能读取。
 * 事务知识点：持久性（Durability），关注提交日志刷新后的数据可见性。
 * 说明：先通过事务执行充值，再用新的 JDBC 连接读取余额，确保结果与服务层读取一致。
 */
class DurabilityIntegrationTest extends AbstractMySqlTransactionIntegrationTest {

    private static final Long ALICE = 1L;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionExecutor transactionExecutor;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        accountService.resetAccounts(List.of(
                new AccountSeed(ALICE, "Alice", new BigDecimal("80.00"))
        ));
    }

    @Test
    void committedChangesSurviveConnectionRecreation() {
        transactionExecutor.executeWithoutResult(
                TransactionDefinition.ISOLATION_READ_COMMITTED,
                false,
                () -> accountService.deposit(ALICE, new BigDecimal("20.00"))
        );

        JdbcTemplate template = new JdbcTemplate(dataSource);
        BigDecimal balanceFromNewConnection = template.queryForObject(
                "select balance from accounts where id = ?",
                (rs, rowNum) -> rs.getBigDecimal(1),
                ALICE
        );

        assertThat(balanceFromNewConnection).isEqualByComparingTo(new BigDecimal("100.00"));

        BigDecimal balanceViaService = transactionExecutor.execute(
                TransactionDefinition.ISOLATION_READ_COMMITTED,
                true,
                () -> accountService.getBalance(ALICE)
        );

        assertThat(balanceViaService).isEqualByComparingTo(balanceFromNewConnection);
    }
}
