package com.transactioninsight.springcore.entity;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 实验记录仓储接口，封装对 {@link ExperimentRecord} 的基本 CRUD 能力。
 */
public interface ExperimentRecordRepository extends JpaRepository<ExperimentRecord, Long> {
}
