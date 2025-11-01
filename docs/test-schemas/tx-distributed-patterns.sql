-- tx-distributed-patterns 模块 Outbox 功能所需的表结构
-- 该表存储待投递的领域事件消息，确保跨服务数据的一致性。

CREATE TABLE IF NOT EXISTS outbox_message (
    -- 自增主键，唯一标识一条消息
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    -- 业务聚合根或实体的标识，便于定位事件来源
    aggregate_id VARCHAR(128) NOT NULL,
    -- 事件类型，用于区分不同的消息语义
    event_type VARCHAR(128) NOT NULL,
    -- 事件内容载荷，通常为 JSON 字符串
    payload TEXT NOT NULL,
    -- 消息状态，如 PENDING、SENT 等
    status VARCHAR(32) NOT NULL,
    -- 消息创建时间
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 最近一次状态更新时间
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
