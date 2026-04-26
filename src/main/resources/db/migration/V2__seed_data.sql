-- =============================================================
-- V2__seed_data.sql
-- 範例資料：預設使用者 + 商品 + 庫存
-- =============================================================
INSERT INTO users (username, email) VALUES
  ('alice', 'alice@example.com'),
  ('bob',   'bob@example.com'),
  ('carol', 'carol@example.com');

INSERT INTO products (sku, name, description, price) VALUES
  ('SKU-1001', 'Limited Sneakers',  '限量球鞋，模擬秒殺商品', 3990.00),
  ('SKU-1002', 'Concert Ticket',    '演唱會門票',           2500.00),
  ('SKU-1003', 'Game Skin Pack',    '遊戲虛擬商品',           199.00);

INSERT INTO product_inventory (product_id, available_stock, reserved_stock, version)
SELECT id, 100, 0, 0 FROM products WHERE sku = 'SKU-1001';

INSERT INTO product_inventory (product_id, available_stock, reserved_stock, version)
SELECT id, 50, 0, 0  FROM products WHERE sku = 'SKU-1002';

INSERT INTO product_inventory (product_id, available_stock, reserved_stock, version)
SELECT id, 1000, 0, 0 FROM products WHERE sku = 'SKU-1003';
