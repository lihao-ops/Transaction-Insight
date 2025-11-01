package com.transactioninsight.distributed;

import com.transactioninsight.distributed.outbox.OrderService;
import com.transactioninsight.distributed.outbox.OutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxMessageRepository repository;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setup() {
        repository.deleteAll();
    }

    @Test
    void orderCreationPersistsOutboxMessage() {
        orderService.createOrder("order-1", Map.of("amount", 100));
        assertThat(repository.count()).isEqualTo(1);
    }
}
