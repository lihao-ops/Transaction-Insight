package com.transactioninsight.springcore.mysql;

import com.transactioninsight.springcore.mysql.service.AccountService;
import com.transactioninsight.springcore.mysql.service.AccountService.AccountSeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtomicityIntegrationTest extends AbstractMySqlTransactionIntegrationTest {

    private static final Long ALICE = 1L;
    private static final Long BOB = 2L;

    @Autowired
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService.resetAccounts(List.of(
                new AccountSeed(ALICE, "Alice", new BigDecimal("100.00")),
                new AccountSeed(BOB, "Bob", new BigDecimal("50.00"))
        ));
    }

    @Test
    void transferIsRolledBackWhenFailureOccurs() {
        BigDecimal aliceBefore = accountService.getBalance(ALICE);
        BigDecimal bobBefore = accountService.getBalance(BOB);

        assertThatThrownBy(() -> accountService.transferAndFail(ALICE, BOB, new BigDecimal("30.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated failure");

        assertThat(accountService.getBalance(ALICE)).isEqualByComparingTo(aliceBefore);
        assertThat(accountService.getBalance(BOB)).isEqualByComparingTo(bobBefore);
    }
}
