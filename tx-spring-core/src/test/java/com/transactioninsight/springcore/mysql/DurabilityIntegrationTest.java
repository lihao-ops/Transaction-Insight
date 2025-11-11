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
