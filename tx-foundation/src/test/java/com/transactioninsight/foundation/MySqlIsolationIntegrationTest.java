package com.transactioninsight.foundation;

import com.transactioninsight.foundation.mysql.Account;
import com.transactioninsight.foundation.mysql.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MySqlIsolationIntegrationTest {

    @Autowired
    private AccountRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        repository.save(new Account(new BigDecimal("500")));
    }

    @Test
    @DisplayName("READ UNCOMMITTED allows dirty read")
    void dirtyReadOccursUnderReadUncommitted() throws Exception {
        try (Connection writer = dataSource.getConnection();
             Connection reader = dataSource.getConnection()) {
            writer.setAutoCommit(false);
            reader.setAutoCommit(false);

            writer.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            reader.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);

            updateBalance(writer, 1L, new BigDecimal("1000"));

            BigDecimal dirtyBalance = queryBalance(reader, 1L);
            assertThat(dirtyBalance).isEqualByComparingTo("1000");

            writer.rollback();

            BigDecimal cleanBalance = queryBalance(reader, 1L);
            assertThat(cleanBalance).isEqualByComparingTo("500");
        }
    }

    private void updateBalance(Connection connection, long id, BigDecimal amount) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE account SET balance = ? WHERE id = ?")) {
            ps.setBigDecimal(1, amount);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private BigDecimal queryBalance(Connection connection, long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT balance FROM account WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
                throw new IllegalStateException("Account not found");
            }
        }
    }
}
