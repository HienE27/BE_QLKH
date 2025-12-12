-- Add indexes for shop_imports table to improve query performance
CREATE INDEX idx_import_type_status_date ON shop_imports(import_type, status, imports_date);
CREATE INDEX idx_import_code ON shop_imports(import_code);
CREATE INDEX idx_imports_date ON shop_imports(imports_date);
CREATE INDEX idx_imports_store_id ON shop_imports(stores_id);
CREATE INDEX idx_imports_supplier_id ON shop_imports(supplier_id);

-- Add indexes for shop_import_details table
CREATE INDEX idx_import_details_import_id ON shop_import_details(imports_id);
CREATE INDEX idx_import_details_product_id ON shop_import_details(products_id);
CREATE INDEX idx_import_details_store_id ON shop_import_details(stores_id);

-- Note: MySQL 8.0.41 does not support IF NOT EXISTS for CREATE INDEX.
-- If an index already exists, the command will throw an error.
-- You can run these commands individually and ignore errors for existing indexes,
-- or check for existence before creating if using a migration tool that supports conditional execution.

