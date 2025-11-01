package com.transactioninsight.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class TransactionMetricsAspect {

    private final MeterRegistry meterRegistry;

    public TransactionMetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object measureTransactionTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            long duration = System.currentTimeMillis() - start;
            Timer.builder("transaction.duration")
                    .tag("method", pjp.getSignature().toShortString())
                    .register(meterRegistry)
                    .record(duration, TimeUnit.MILLISECONDS);
        }
    }
}
