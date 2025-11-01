package com.transactioninsight.distributed.tcc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TccTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(TccTransactionManager.class);

    private final Map<String, TccContext> contexts = new ConcurrentHashMap<>();

    public void begin(String globalTxId) {
        contexts.put(globalTxId, new TccContext(globalTxId));
    }

    public void registerBranch(String globalTxId, TccAction action) {
        contexts.computeIfAbsent(globalTxId, TccContext::new).addAction(action);
    }

    public void commit(String globalTxId) {
        TccContext context = contexts.remove(globalTxId);
        if (context == null) {
            return;
        }
        context.getActions().forEach(action -> {
            try {
                action.confirm();
            } catch (Exception ex) {
                log.error("Confirm failed for tx {}", context.getGlobalTxId(), ex);
            }
        });
    }

    public void rollback(String globalTxId) {
        TccContext context = contexts.remove(globalTxId);
        if (context == null) {
            return;
        }
        context.getActions().forEach(action -> {
            try {
                action.cancel();
            } catch (Exception ex) {
                log.error("Cancel failed for tx {}", context.getGlobalTxId(), ex);
            }
        });
    }
}
