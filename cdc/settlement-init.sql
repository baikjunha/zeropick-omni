CREATE TABLE IF NOT EXISTS pos_stock_ledger (
    product_id  BIGINT PRIMARY KEY,
    store_code  VARCHAR(20),
    stock       INT,
    updated_at  VARCHAR(64) NULL
);
