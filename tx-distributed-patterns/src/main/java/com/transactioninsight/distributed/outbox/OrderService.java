package com.transactioninsight.distributed.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OutboxMessageRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OutboxMessageRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void createOrder(String orderId, Object payload) {
        try {
            String serialized = objectMapper.writeValueAsString(payload);
            OutboxMessage message = new OutboxMessage(orderId, "OrderCreated", serialized);
            outboxRepository.save(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize order payload", e);
        }
    }
}

@Component
class OutboxRelay {

    private final OutboxMessageRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    OutboxRelay(OutboxMessageRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<OutboxMessage> pending = repository.findByStatus(OutboxStatus.PENDING);
        pending.forEach(message -> {
            try {
                kafkaTemplate.send(message.getEventType(), message.getAggregateId(), message.getPayload());
                message.markSent();
            } catch (Exception ex) {
                message.markFailed();
            }
            repository.save(message);
        });
    }
}
