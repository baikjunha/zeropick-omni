CREATE TABLE preference (
  member_id  BIGINT PRIMARY KEY,
  price_min  INT NOT NULL DEFAULT 0,
  price_max  INT NOT NULL DEFAULT 100000,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pref_category (
  member_id BIGINT      NOT NULL,
  category  VARCHAR(30) NOT NULL,
  PRIMARY KEY (member_id, category),
  CONSTRAINT fk_pc_pref FOREIGN KEY (member_id) REFERENCES preference(member_id)
);

CREATE TABLE pref_excluded_sweetener (
  member_id BIGINT      NOT NULL,
  sweetener VARCHAR(40) NOT NULL,
  PRIMARY KEY (member_id, sweetener),
  CONSTRAINT fk_pes_pref FOREIGN KEY (member_id) REFERENCES preference(member_id)
);

CREATE TABLE pref_allergen (
  member_id BIGINT      NOT NULL,
  allergen  VARCHAR(30) NOT NULL,
  PRIMARY KEY (member_id, allergen),
  CONSTRAINT fk_pa_pref FOREIGN KEY (member_id) REFERENCES preference(member_id)
);

CREATE TABLE behavior_log (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id      BIGINT      NOT NULL,
  product_id     BIGINT      NOT NULL,
  category       VARCHAR(30) NOT NULL,
  event_type     VARCHAR(20) NOT NULL
                 CHECK (event_type IN ('PRODUCT_VIEWED','CART_ADDED','ORDER_COMPLETED')),
  qty            INT,
  unit_price     BIGINT,
  order_no       VARCHAR(20),
  payment_method VARCHAR(20),
  occurred_at    TIMESTAMP   NOT NULL
);

CREATE TABLE reco_result (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id  BIGINT       NOT NULL,
  product_id BIGINT       NOT NULL,
  rank_no    INT          NOT NULL,
  score      DECIMAL(8,2) NOT NULL,
  reason     VARCHAR(255),
  created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reco_click (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id  BIGINT    NOT NULL,
  product_id BIGINT    NOT NULL,
  clicked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_behavior_member ON behavior_log(member_id, occurred_at);
CREATE INDEX idx_behavior_type   ON behavior_log(event_type, occurred_at);
