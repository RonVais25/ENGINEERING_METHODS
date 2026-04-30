-- create the database
CREATE DATABASE IF NOT EXISTS gonature
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE gonature;

-- create the Order table
DROP TABLE IF EXISTS `Order`;
CREATE TABLE `Order` (
    order_number          INT         NOT NULL,
    order_date            DATE        NOT NULL,
    number_of_visitors    INT         NOT NULL,
    confirmation_code     INT         NOT NULL,
    subscriber_id         INT         NOT NULL,
    date_of_placing_order DATE        NOT NULL,
    PRIMARY KEY (order_number)
);

-- dummy data so the client has something to read
INSERT INTO `Order` VALUES
    (1001, '2026-05-15', 4, 54321, 101, '2026-04-20'),
    (1002, '2026-05-17', 2, 11223, 102, '2026-04-21'),
    (1003, '2026-05-20', 8, 77889, 103, '2026-04-22'),
    (1004, '2026-06-01', 1, 33445, 104, '2026-04-23'),
    (1005, '2026-06-03', 6, 99887, 105, '2026-04-24');

-- show the table created
SELECT * FROM `Order`;
