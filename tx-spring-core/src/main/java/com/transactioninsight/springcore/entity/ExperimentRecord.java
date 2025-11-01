package com.transactioninsight.springcore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "experiment_record")
public class ExperimentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String scenario;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public ExperimentRecord() {
    }

    public ExperimentRecord(String scenario) {
        this.scenario = scenario;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getScenario() {
        return scenario;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
