package com.transactioninsight.springcore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Spring 事务传播实验记录表的实体，记录每次实验的场景与创建时间。
 */
@Entity
@Table(name = "experiment_record")
public class ExperimentRecord {

    /** 主键自增 ID，方便测试断言行数。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 实验场景描述，用于区分不同传播方式。 */
    @Column(nullable = false)
    private String scenario;

    /** 记录生成时间，帮助分析事务耗时。 */
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** JPA 默认构造器。 */
    public ExperimentRecord() {
    }

    /**
     * @param scenario 本次实验场景描述
     */
    public ExperimentRecord(String scenario) {
        this.scenario = scenario;
        this.createdAt = Instant.now();
    }

    /**
     * @return 主键 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * @return 实验场景描述
     */
    public String getScenario() {
        return scenario;
    }

    /**
     * @return 创建时间戳
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
