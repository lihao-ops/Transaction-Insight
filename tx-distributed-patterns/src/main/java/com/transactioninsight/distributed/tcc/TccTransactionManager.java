package com.transactioninsight.distributed.tcc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极简版 TCC 事务管理器：维护全局事务上下文并负责协调 Confirm/Cancel 调用。
 */
@Component
public class TccTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(TccTransactionManager.class);

    private final Map<String, TccContext> contexts = new ConcurrentHashMap<>();

    /**
     * 初始化一个新的全局事务上下文。
     */
    public void begin(String globalTxId) {
        contexts.put(globalTxId, new TccContext(globalTxId));
    }

    /**
     * 注册分支行为，供后续 Confirm/Cancel 循环调用。
     */
    public void registerBranch(String globalTxId, TccAction action) {
        contexts.computeIfAbsent(globalTxId, TccContext::new).addAction(action);
    }

    /**
     * 执行 Confirm 阶段，逐个调用已注册分支的 confirm() 方法。
     */
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

    /**
     * 执行 Cancel 阶段，逐个调用分支的 cancel() 方法以释放资源。
     */
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
