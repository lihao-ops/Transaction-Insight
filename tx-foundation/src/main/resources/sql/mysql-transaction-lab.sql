-- ==========================================
-- MySQL 事务原理验证实验手册
-- ==========================================

-- 【准备工作】创建数据库和表
CREATE DATABASE IF NOT EXISTS tx_lab CHARACTER SET utf8mb4;
USE tx_lab;

DROP TABLE IF EXISTS account;
CREATE TABLE account (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(20) NOT NULL,
    balance DECIMAL(10,2) NOT NULL
) ENGINE=InnoDB;

INSERT INTO account (name, balance) VALUES
('张三', 1000.00),
('李四', 1000.00);

SELECT * FROM account;  -- 查看初始数据


-- ==========================================
-- 实验1：验证【原子性 Atomicity】
-- ==========================================
-- 【目的】证明事务要么全成功，要么全失败，不会出现中间状态

-- 【操作步骤】
START TRANSACTION;
UPDATE account SET balance = balance - 500 WHERE name = '张三';  -- 张三减500
UPDATE account SET balance = balance + 500 WHERE name = '李四';  -- 李四加500
-- 现在故意不提交，而是回滚
ROLLBACK;

-- 【查看结果】
SELECT * FROM account;

-- 【预期现象】张三和李四的余额都还是1000，没有任何变化
-- 【原理解释】ROLLBACK触发了undo log回滚，所有修改都被撤销


-- ==========================================
-- 实验2：验证【一致性 Consistency】
-- ==========================================
-- 【目的】证明事务前后，数据的完整性约束得到保持（如总金额守恒）

-- 【操作步骤】
-- 先查看总金额
SELECT SUM(balance) AS total FROM account;  -- 应该是2000

START TRANSACTION;
UPDATE account SET balance = balance - 300 WHERE name = '张三';
UPDATE account SET balance = balance + 300 WHERE name = '李四';
COMMIT;

-- 【查看结果】
SELECT SUM(balance) AS total FROM account;  -- 依然是2000
SELECT * FROM account;

-- 【预期现象】转账前后总金额不变，保持2000元
-- 【原理解释】一致性是ACID的综合体现，保证业务规则不被破坏


-- ==========================================
-- 实验3：验证【持久性 Durability】
-- ==========================================
-- 【目的】证明事务一旦提交，数据永久保存，即使数据库崩溃也不丢失

-- 【操作步骤】
START TRANSACTION;
UPDATE account SET balance = 1500 WHERE name = '张三';
COMMIT;  -- 提交后立即重启MySQL服务

-- 【查看结果】重启MySQL后执行
SELECT * FROM account WHERE name = '张三';

-- 【预期现象】张三的余额是1500，修改被永久保存
-- 【原理解释】COMMIT时数据写入redo log并刷盘，即使崩溃也能恢复


-- ==========================================
-- 实验4：验证【脏读】(READ UNCOMMITTED)
-- ==========================================
-- 【目的】证明在最低隔离级别下，可以读到未提交的数据

-- 【会话1 - 窗口1执行】
SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
START TRANSACTION;
UPDATE account SET balance = 9999 WHERE name = '张三';
-- 注意：不要提交，保持事务开启

-- 【会话2 - 窗口2执行】
SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
SELECT * FROM account WHERE name = '张三';

-- 【预期现象】会话2能看到9999（脏读）
-- 【原理解释】READ UNCOMMITTED不加任何锁，直接读取当前最新值

-- 【会话1继续】
ROLLBACK;  -- 回滚后，会话2之前读到的9999就是"脏数据"


-- ==========================================
-- 实验5：验证【不可重复读】(READ COMMITTED)
-- ==========================================
-- 【目的】证明在读已提交级别，同一事务内多次读取可能得到不同结果

-- 【会话1 - 窗口1执行】
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
SELECT balance FROM account WHERE name = '李四';  -- 第一次读，记住这个值

-- 【会话2 - 窗口2执行】
UPDATE account SET balance = balance + 500 WHERE name = '李四';
COMMIT;

-- 【会话1继续】
SELECT balance FROM account WHERE name = '李四';  -- 第二次读
COMMIT;

-- 【预期现象】两次读取的值不同，第二次读到了会话2提交的新值
-- 【原理解释】READ COMMITTED每次读取都是最新提交的版本


-- ==========================================
-- 实验6：验证【MVCC - 可重复读】(REPEATABLE READ)
-- ==========================================
-- 【目的】证明在可重复读级别，同一事务内多次读取结果一致（快照读）

-- 【会话1 - 窗口1执行】
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION;
SELECT balance FROM account WHERE name = '张三';  -- 第一次读，假设是1500

-- 【会话2 - 窗口2执行】
UPDATE account SET balance = balance - 200 WHERE name = '张三';
COMMIT;

-- 【会话1继续】
SELECT balance FROM account WHERE name = '张三';  -- 第二次读，依然是1500！
COMMIT;

-- 【预期现象】两次读取的值相同，看不到会话2的修改
-- 【原理解释】MVCC通过undo log保存快照版本，事务开始时生成ReadView


-- ==========================================
-- 实验7：验证【幻读】(REPEATABLE READ + 范围查询)
-- ==========================================
-- 【目的】证明可重复读级别在某些场景下依然会出现幻读

-- 【会话1 - 窗口1执行】
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION;
SELECT * FROM account WHERE balance > 500;  -- 第一次范围查询

-- 【会话2 - 窗口2执行】
INSERT INTO account (name, balance) VALUES ('王五', 2000);
COMMIT;

-- 【会话1继续】
SELECT * FROM account WHERE balance > 500;  -- 第二次范围查询
-- 如果没有间隙锁，可能看到王五（幻读）

-- 【预期现象】InnoDB通过间隙锁(Gap Lock)避免了幻读
-- 【原理解释】Next-Key Lock = 行锁 + 间隙锁，锁定范围防止插入


-- ==========================================
-- 实验8：验证【行锁 - FOR UPDATE】
-- ==========================================
-- 【目的】证明SELECT FOR UPDATE会对行加排他锁

-- 【会话1 - 窗口1执行】
START TRANSACTION;
SELECT * FROM account WHERE name = '张三' FOR UPDATE;  -- 锁住张三这一行
-- 不要提交，保持锁

-- 【会话2 - 窗口2执行】
UPDATE account SET balance = balance + 100 WHERE name = '张三';  -- 尝试修改

-- 【预期现象】会话2被阻塞，等待会话1释放锁
-- 【原理解释】FOR UPDATE加X锁，其他事务无法修改该行

-- 【会话1继续】
COMMIT;  -- 会话2立即执行成功


-- ==========================================
-- 实验9：验证【死锁检测】
-- ==========================================
-- 【目的】证明InnoDB能自动检测死锁并回滚其中一个事务

-- 【会话1 - 窗口1执行】
START TRANSACTION;
UPDATE account SET balance = balance + 10 WHERE name = '张三';  -- 锁住张三

-- 【会话2 - 窗口2执行】
START TRANSACTION;
UPDATE account SET balance = balance + 10 WHERE name = '李四';  -- 锁住李四

-- 【会话1继续】
UPDATE account SET balance = balance + 10 WHERE name = '李四';  -- 等待会话2的锁

-- 【会话2继续】
UPDATE account SET balance = balance + 10 WHERE name = '张三';  -- 等待会话1的锁

-- 【预期现象】其中一个会话报错 "Deadlock found when trying to get lock"
-- 【原理解释】InnoDB检测到循环等待，自动回滚一个事务打破死锁


-- ==========================================
-- 实验10：验证【当前读 vs 快照读】
-- ==========================================
-- 【目的】证明FOR UPDATE是当前读，会读到最新提交的数据

-- 【会话1 - 窗口1执行】
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION;
SELECT balance FROM account WHERE name = '张三';  -- 快照读

-- 【会话2 - 窗口2执行】
UPDATE account SET balance = 3000 WHERE name = '张三';
COMMIT;

-- 【会话1继续】
SELECT balance FROM account WHERE name = '张三';  -- 快照读，旧值
SELECT balance FROM account WHERE name = '张三' FOR UPDATE;  -- 当前读，新值3000！
COMMIT;

-- 【预期现象】普通SELECT读旧版本，FOR UPDATE读最新版本
-- 【原理解释】当前读会加锁并读取最新提交的记录


-- ==========================================
-- 【实验总结】
-- ==========================================
-- ✅ 原子性：通过 undo log 实现回滚
-- ✅ 一致性：业务规则在事务前后保持不变
-- ✅ 隔离性：通过锁和MVCC实现不同隔离级别
-- ✅ 持久性：通过 redo log 保证崩溃恢复
-- ✅ MVCC：通过ReadView + undo log实现快照读
-- ✅ 锁机制：行锁(FOR UPDATE)、间隙锁(防幻读)、死锁检测
