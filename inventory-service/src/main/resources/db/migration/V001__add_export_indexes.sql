-- Add indexes for shop_exports table
-- Note: MySQL does not support IF NOT EXISTS for CREATE INDEX
-- If index already exists, you can safely ignore the error or drop it first
CREATE INDEX idx_export_type_status_date ON shop_exports(export_type, status, exports_date);
CREATE INDEX idx_export_code ON shop_exports(export_code);
CREATE INDEX idx_exports_date ON shop_exports(exports_date);
CREATE INDEX idx_exports_store_id ON shop_exports(stores_id);
CREATE INDEX idx_exports_customer_id ON shop_exports(customers_id);

-- Add indexes for shop_export_details table
CREATE INDEX idx_export_details_export_id ON shop_export_details(exports_id);
CREATE INDEX idx_export_details_product_id ON shop_export_details(products_id);
CREATE INDEX idx_export_details_store_id ON shop_export_details(stores_id);

