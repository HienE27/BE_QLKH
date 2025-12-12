-- Add indexes for shop_stocks table
CREATE INDEX idx_stock_product_id ON shop_stocks(products_id);
CREATE INDEX idx_stock_store_id ON shop_stocks(stores_id);
CREATE INDEX idx_stock_product_store ON shop_stocks(products_id, stores_id);

-- Note: MySQL 8.0.41 does not support IF NOT EXISTS for CREATE INDEX.
-- If an index already exists, the command will throw an error.
-- You can run these commands individually and ignore errors for existing indexes.

