package com.transactioninsight.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 AOP 的事务指标采集切面，自动统计 @Transactional 方法的执行耗时。
 */
@Aspect
@Component
public class TransactionMetricsAspect {

    private final MeterRegistry meterRegistry;

    /**
     * @param meterRegistry Micrometer 指标注册表
     */
    public TransactionMetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 拦截所有带 @Transactional 的方法并记录执行耗时，指标名为 transaction.duration。
     */
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object measureTransactionTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            long duration = System.currentTimeMillis() - start;
            // 核心逻辑：通过方法签名作为 tag，以便在监控平台上区分不同业务操作。
            Timer.builder("transaction.duration")
                    .tag("method", pjp.getSignature().toShortString())
                    .register(meterRegistry)
                    .record(duration, TimeUnit.MILLISECONDS);
        }
    }
}
