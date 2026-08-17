CREATE TABLE member (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  email      VARCHAR(120) NOT NULL UNIQUE,
  password   VARCHAR(255) NOT NULL,
  name       VARCHAR(40)  NOT NULL,
  created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cart_item (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id  BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  qty        INT    NOT NULL CHECK (qty > 0),
  added_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_cart UNIQUE (member_id, product_id),
  CONSTRAINT fk_cart_member FOREIGN KEY (member_id) REFERENCES member(id)
);

CREATE TABLE orders (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no       VARCHAR(20) NOT NULL UNIQUE,
  member_id      BIGINT      NOT NULL,
  total_price    BIGINT      NOT NULL CHECK (total_price >= 0),
  status         VARCHAR(15) NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING','PAID','COMPLETED','CANCELLED')),
  payment_method VARCHAR(20),
  ordered_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paid_at        TIMESTAMP,
  CONSTRAINT fk_order_member FOREIGN KEY (member_id) REFERENCES member(id)
);

CREATE TABLE order_item (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id     BIGINT       NOT NULL,
  product_id   BIGINT       NOT NULL,
  product_name VARCHAR(120) NOT NULL,
  qty          INT          NOT NULL CHECK (qty > 0),
  unit_price   BIGINT       NOT NULL,
  CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX idx_orders_member ON orders(member_id, ordered_at);
