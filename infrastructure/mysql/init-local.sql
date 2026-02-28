-- Rivoo Local Development Database Setup
-- Run as: mysql -u root -p < init-local.sql

-- Create databases
CREATE DATABASE IF NOT EXISTS auth_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS salon_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS staff_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS client_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS appointment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS notification_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS billing_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create application user
CREATE USER IF NOT EXISTS 'rivoo'@'localhost' IDENTIFIED BY 'rivoo123';

-- Grant privileges
GRANT ALL PRIVILEGES ON auth_db.* TO 'rivoo'@'localhost';
GRANT ALL PRIVILEGES ON salon_db.* TO 'rivoo'@'localhost';
GRANT ALL PRIVILEGES ON staff_db.* TO 'rivoo'@'localhost';
GRANT ALL PRIVILEGES ON client_db.* TO 'rivoo'@'localhost';
GRANT ALL PRIVILEGES ON appointment_db.* TO 'rivoo'@'localhost';
GRANT ALL PRIVILEGES ON notification_db.* TO 'rivoo'@'localhost';
GRANT ALL PRIVILEGES ON billing_db.* TO 'rivoo'@'localhost';

FLUSH PRIVILEGES;
