CREATE TABLE product (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  name                VARCHAR(120) NOT NULL,
  brand               VARCHAR(60)  NOT NULL,
  category            VARCHAR(30)  NOT NULL,
  price               INT          NOT NULL CHECK (price >= 0),
  image_url           VARCHAR(255),
  stock               INT          NOT NULL DEFAULT 0 CHECK (stock >= 0),
  claim_type          VARCHAR(20)  CHECK (claim_type IN ('무당류','저당류','무첨가당')),
  kcal                DECIMAL(7,1) NOT NULL,
  sugar_g             DECIMAL(6,2) NOT NULL,
  carb_g              DECIMAL(6,2) NOT NULL,
  protein_g            DECIMAL(6,2),
  fat_g                DECIMAL(6,2),
  sodium_mg            DECIMAL(8,2),
  serving_size         DECIMAL(7,1),
  serving_unit         VARCHAR(10),
  nutrition_facts_url  VARCHAR(255),
  verification_source VARCHAR(255),
  created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sweetener (
  id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE product_sweetener (
  product_id   BIGINT NOT NULL,
  sweetener_id BIGINT NOT NULL,
  amount_g   DECIMAL(6,2),
  PRIMARY KEY (product_id, sweetener_id),
  CONSTRAINT fk_ps_product   FOREIGN KEY (product_id)   REFERENCES product(id),
  CONSTRAINT fk_ps_sweetener FOREIGN KEY (sweetener_id) REFERENCES sweetener(id)
);

CREATE TABLE product_allergen (
  product_id BIGINT      NOT NULL,
  allergen   VARCHAR(30) NOT NULL,
  PRIMARY KEY (product_id, allergen),
  CONSTRAINT fk_pa_product FOREIGN KEY (product_id) REFERENCES product(id)
);

CREATE INDEX idx_product_category ON product(category);
CREATE INDEX idx_product_sugar    ON product(sugar_g);
CREATE INDEX idx_product_price    ON product(price);
