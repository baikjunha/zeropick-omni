-- 가상 오프라인 POS 재고 테이블.
-- product_id 는 제로픽 product 테이블의 id 와 맞춘다 (시드 1~490).
CREATE TABLE IF NOT EXISTS pos_stock (
    product_id   BIGINT PRIMARY KEY,
    store_code   VARCHAR(10) NOT NULL DEFAULT 'GANGNAM01',
    stock        INT NOT NULL,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO pos_stock (product_id, store_code, stock) VALUES
    (1, 'GANGNAM01', 30),
    (2, 'GANGNAM01', 25),
    (3, 'GANGNAM01', 40),
    (4, 'GANGNAM01', 12),
    (5, 'GANGNAM01', 55);

-- Debezium 접속용 계정 (root 를 커넥터에 쓰지 않기 위한 최소 권한 계정)
CREATE USER IF NOT EXISTS 'debezium'@'%' IDENTIFIED BY 'dbz1234';
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'debezium'@'%';
FLUSH PRIVILEGES;
