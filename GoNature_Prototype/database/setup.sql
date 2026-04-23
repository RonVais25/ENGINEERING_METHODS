-- =====================================================================
-- GoNature prototype - database setup script
-- Run this ONCE, on the machine that will host the MySQL server,
-- BEFORE launching the server JAR.
-- =====================================================================

-- 1) Create the database
CREATE DATABASE IF NOT EXISTS gonature
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE gonature;

-- 2) Create the Order table
-- Note: `Order` is a reserved word in SQL, so it must be wrapped in backticks.
DROP TABLE IF EXISTS `Order`;
CREATE TABLE `Order` (
    order_number          INT         NOT NULL,
    order_date            DATE        NOT NULL,
    number_of_visitors    INT         NOT NULL,
    confirmation_code     INT         NOT NULL,
    subscriber_id         INT         NOT NULL,
    date_of_placing_order DATE        NOT NULL,
    PRIMARY KEY (order_number)
    -- FK to a Subscriber table is not part of the prototype scope
);

-- 3) Seed data so the client has something to read
INSERT INTO `Order` VALUES
    (1001, '2026-05-15', 4, 54321, 101, '2026-04-20'),
    (1002, '2026-05-17', 2, 11223, 102, '2026-04-21'),
    (1003, '2026-05-20', 8, 77889, 103, '2026-04-22'),
    (1004, '2026-06-01', 1, 33445, 104, '2026-04-23'),
    (1005, '2026-06-03', 6, 99887, 105, '2026-04-24');

-- 4) Quick verification
SELECT * FROM `Order`;
