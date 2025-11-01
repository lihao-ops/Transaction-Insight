-- tx-spring-core 模块测试所需的表结构（如切换到 MySQL）

CREATE TABLE IF NOT EXISTS experiment_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
