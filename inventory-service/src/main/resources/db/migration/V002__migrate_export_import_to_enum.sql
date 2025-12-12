-- Migration script to convert VARCHAR columns to ENUM for shop_exports and shop_imports
-- This script ensures data compatibility before changing column types

-- ============================================
-- PART 1: VALIDATE AND CLEAN shop_exports
-- ============================================

-- Check for invalid export_type values
-- Expected values: 'SUPPLIER', 'INTERNAL', 'STAFF', 'ORDER'
SELECT 'Checking shop_exports.export_type...' AS step;
SELECT DISTINCT export_type, COUNT(*) as count 
FROM shop_exports 
WHERE export_type NOT IN ('SUPPLIER', 'INTERNAL', 'STAFF', 'ORDER') 
   OR export_type IS NULL
GROUP BY export_type;

-- Check for invalid status values
-- Expected values: 'PENDING', 'APPROVED', 'REJECTED', 'EXPORTED', 'CANCELLED', 'RETURNED'
SELECT 'Checking shop_exports.status...' AS step;
SELECT DISTINCT status, COUNT(*) as count 
FROM shop_exports 
WHERE status NOT IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPORTED', 'CANCELLED', 'RETURNED') 
   OR status IS NULL
GROUP BY status;

-- Update invalid export_type values to default 'ORDER'
UPDATE shop_exports 
SET export_type = 'ORDER' 
WHERE export_type NOT IN ('SUPPLIER', 'INTERNAL', 'STAFF', 'ORDER') 
   OR export_type IS NULL;

-- Update invalid status values to default 'PENDING'
UPDATE shop_exports 
SET status = 'PENDING' 
WHERE status NOT IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPORTED', 'CANCELLED', 'RETURNED') 
   OR status IS NULL;

-- ============================================
-- PART 2: VALIDATE AND CLEAN shop_imports
-- ============================================

-- Check for invalid import_type values
-- Expected values: 'SUPPLIER', 'INTERNAL', 'STAFF'
SELECT 'Checking shop_imports.import_type...' AS step;
SELECT DISTINCT import_type, COUNT(*) as count 
FROM shop_imports 
WHERE import_type NOT IN ('SUPPLIER', 'INTERNAL', 'STAFF') 
   OR import_type IS NULL
GROUP BY import_type;

-- Check for invalid status values
-- Expected values: 'PENDING', 'APPROVED', 'REJECTED', 'IMPORTED', 'CANCELLED'
SELECT 'Checking shop_imports.status...' AS step;
SELECT DISTINCT status, COUNT(*) as count 
FROM shop_imports 
WHERE status NOT IN ('PENDING', 'APPROVED', 'REJECTED', 'IMPORTED', 'CANCELLED') 
   OR status IS NULL
GROUP BY status;

-- Update invalid import_type values to default 'SUPPLIER'
UPDATE shop_imports 
SET import_type = 'SUPPLIER' 
WHERE import_type NOT IN ('SUPPLIER', 'INTERNAL', 'STAFF') 
   OR import_type IS NULL;

-- Update invalid status values to default 'PENDING'
UPDATE shop_imports 
SET status = 'PENDING' 
WHERE status NOT IN ('PENDING', 'APPROVED', 'REJECTED', 'IMPORTED', 'CANCELLED') 
   OR status IS NULL;

-- ============================================
-- PART 3: CONVERT COLUMNS TO ENUM
-- ============================================

-- Convert shop_exports.export_type to ENUM
-- Note: MySQL 8.0.41 does not support IF NOT EXISTS for ALTER TABLE MODIFY COLUMN
-- If column is already ENUM, this will fail. Check column type first if needed.
ALTER TABLE shop_exports 
  MODIFY COLUMN export_type ENUM('SUPPLIER', 'INTERNAL', 'STAFF', 'ORDER') NOT NULL DEFAULT 'ORDER';

-- Convert shop_exports.status to ENUM
ALTER TABLE shop_exports 
  MODIFY COLUMN status ENUM('PENDING', 'APPROVED', 'REJECTED', 'EXPORTED', 'CANCELLED', 'RETURNED') DEFAULT NULL;

-- Convert shop_imports.import_type to ENUM
ALTER TABLE shop_imports 
  MODIFY COLUMN import_type ENUM('SUPPLIER', 'INTERNAL', 'STAFF') DEFAULT NULL;

-- Convert shop_imports.status to ENUM
ALTER TABLE shop_imports 
  MODIFY COLUMN status ENUM('PENDING', 'APPROVED', 'REJECTED', 'IMPORTED', 'CANCELLED') DEFAULT NULL;

-- ============================================
-- PART 4: VERIFY MIGRATION
-- ============================================

-- Verify shop_exports columns are ENUM
SELECT 'Verifying shop_exports.export_type...' AS step;
SHOW COLUMNS FROM shop_exports WHERE Field = 'export_type';

SELECT 'Verifying shop_exports.status...' AS step;
SHOW COLUMNS FROM shop_exports WHERE Field = 'status';

-- Verify shop_imports columns are ENUM
SELECT 'Verifying shop_imports.import_type...' AS step;
SHOW COLUMNS FROM shop_imports WHERE Field = 'import_type';

SELECT 'Verifying shop_imports.status...' AS step;
SHOW COLUMNS FROM shop_imports WHERE Field = 'status';

-- Final count check
SELECT 'Final record counts...' AS step;
SELECT 'shop_exports' AS table_name, COUNT(*) AS total_records FROM shop_exports
UNION ALL
SELECT 'shop_imports' AS table_name, COUNT(*) AS total_records FROM shop_imports;

