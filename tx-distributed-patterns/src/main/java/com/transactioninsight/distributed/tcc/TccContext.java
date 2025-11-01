package com.transactioninsight.distributed.tcc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 维护单个全局事务上下文的内部类，保存已注册的分支动作。
 */
class TccContext {

    private final String globalTxId;
    private final List<TccAction> actions = new ArrayList<>();

    TccContext(String globalTxId) {
        this.globalTxId = globalTxId;
    }

    /**
     * 注册分支操作。
     */
    void addAction(TccAction action) {
        actions.add(action);
    }

    /**
     * @return 只读的分支操作集合
     */
    List<TccAction> getActions() {
        return Collections.unmodifiableList(actions);
    }

    /**
     * @return 全局事务 ID
     */
    String getGlobalTxId() {
        return globalTxId;
    }
}
