package com.transactioninsight.distributed.tcc;

public interface TccAction {
    void tryAction();

    void confirm();

    void cancel();
}
