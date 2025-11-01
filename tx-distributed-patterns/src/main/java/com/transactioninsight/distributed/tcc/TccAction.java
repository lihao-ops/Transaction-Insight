package com.transactioninsight.distributed.tcc;

/**
 * TCC（Try-Confirm-Cancel）分支操作约束接口。
 */
public interface TccAction {
    /** 预留资源，例如冻结余额。 */
    void tryAction();

    /** 确认阶段，真正扣减或提交资源。 */
    void confirm();

    /** 取消阶段，释放 Try 阶段预留的资源。 */
    void cancel();
}
