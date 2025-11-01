package com.transactioninsight.distributed.tcc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class TccContext {

    private final String globalTxId;
    private final List<TccAction> actions = new ArrayList<>();

    TccContext(String globalTxId) {
        this.globalTxId = globalTxId;
    }

    void addAction(TccAction action) {
        actions.add(action);
    }

    List<TccAction> getActions() {
        return Collections.unmodifiableList(actions);
    }

    String getGlobalTxId() {
        return globalTxId;
    }
}
