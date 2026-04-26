-- =============================================================
-- V1__init_schema.sql
-- 初始化 schema：使用者、商品、庫存、訂單、訂單項、付款、事件
-- =============================================================

CREATE TABLE IF NOT EXISTS users (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    username     VARCHAR(64)  NOT NULL,
    email        VARCHAR(128) NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS products (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    sku         VARCHAR(64)   NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    description TEXT,
    price       DECIMAL(12,2) NOT NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_sku (sku)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 把庫存獨立成一張表：高併發扣庫存只更新這張表，避免熱資料行干擾商品讀取
CREATE TABLE IF NOT EXISTS product_inventory (
    product_id      BIGINT NOT NULL,
    available_stock INT    NOT NULL,
    reserved_stock  INT    NOT NULL DEFAULT 0,
    version         BIGINT NOT NULL DEFAULT 0,    -- 樂觀鎖
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_no        VARCHAR(64)   NOT NULL,
    user_id         BIGINT        NOT NULL,
    status          VARCHAR(32)   NOT NULL,        -- CREATED / PAID / COMPLETED / CANCELLED / EXPIRED
    total_amount    DECIMAL(12,2) NOT NULL,
    lock_strategy   VARCHAR(32)   NOT NULL,        -- OPTIMISTIC / REDIS_LOCK
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at      DATETIME      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_user (user_id),
    KEY idx_orders_status_expires (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_items (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    order_id    BIGINT        NOT NULL,
    product_id  BIGINT        NOT NULL,
    sku         VARCHAR(64)   NOT NULL,
    quantity    INT           NOT NULL,
    unit_price  DECIMAL(12,2) NOT NULL,
    subtotal    DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_items_order (order_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payments (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    order_id    BIGINT        NOT NULL,
    amount      DECIMAL(12,2) NOT NULL,
    status      VARCHAR(32)   NOT NULL,            -- PENDING / SUCCESS / FAILED
    paid_at     DATETIME      NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_payments_order (order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 事件紀錄：consumer 寫入；之後可以用來重建狀態或審計
CREATE TABLE IF NOT EXISTS order_events (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL,
    event_type   VARCHAR(64)  NOT NULL,            -- ORDER_CREATED / ORDER_PAID / ...
    payload      TEXT,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_events_order (order_id),
    KEY idx_order_events_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 庫存扣減紀錄：審計用、可協助對帳超賣風險
CREATE TABLE IF NOT EXISTS stock_deduction_logs (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    product_id   BIGINT       NOT NULL,
    order_id     BIGINT       NOT NULL,
    quantity     INT          NOT NULL,
    strategy     VARCHAR(32)  NOT NULL,            -- OPTIMISTIC / REDIS_LOCK
    success      TINYINT(1)   NOT NULL,
    error_msg    VARCHAR(255),
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_stock_logs_product (product_id),
    KEY idx_stock_logs_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
