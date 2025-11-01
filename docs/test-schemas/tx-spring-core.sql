-- tx-spring-core 模块测试所需的表结构（如切换到 MySQL）
-- 该表用于记录执行过的实验或演练场景，便于回溯验证。

CREATE TABLE IF NOT EXISTS experiment_record (
    -- 自增主键，唯一标识一条实验记录
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    -- 记录对应的实验场景名称，例如某次故障演练编号
    scenario VARCHAR(255) NOT NULL,
    -- 创建时间，标记实验记录生成的时间点
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
