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

/**
 * 测试目标：验证订单服务在创建订单时会将消息写入 outbox 表。
 * 事务知识点：本地消息表（Transactional Outbox）保证本地事务与异步消息的一致投递。
 * 说明：调用 createOrder 后断言产生一条待发布消息，KafkaTemplate 使用 Mock 避免外部依赖。
 */
@SpringBootTest
class OutboxIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxMessageRepository repository;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 清理历史消息，避免统计干扰。
     */
    @BeforeEach
    void setup() {
        repository.deleteAll();
    }

    /**
     * 核心断言：createOrder 会持久化一条消息，KafkaTemplate 被 mock 避免真实外部依赖。
     */
    @Test
    void orderCreationPersistsOutboxMessage() {
        orderService.createOrder("order-1", Map.of("amount", 100));
        assertThat(repository.count()).isEqualTo(1);
    }
}
