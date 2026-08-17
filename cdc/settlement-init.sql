-- 가상 정산 시스템의 원장 테이블.
-- Debezium JDBC Sink 의 자동 DDL 이 updated_at 기본값 문제로 실패하므로 미리 만들어 둔다.
-- (updated_at 은 Debezium ZonedTimestamp 가 문자열로 오므로 VARCHAR 로 받는다)
CREATE TABLE IF NOT EXISTS pos_stock_ledger (
    product_id  BIGINT PRIMARY KEY,
    store_code  VARCHAR(20),
    stock       INT,
    updated_at  VARCHAR(64) NULL
);
