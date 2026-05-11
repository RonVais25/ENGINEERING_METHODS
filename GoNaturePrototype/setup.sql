CREATE DATABASE IF NOT EXISTS gonature;
USE gonature;

DROP TABLE IF EXISTS `Order`;

CREATE TABLE `Order` (
    order_number          INT PRIMARY KEY,
    order_date            DATE NOT NULL,
    number_of_visitors    INT  NOT NULL,
    confirmation_code     INT  NOT NULL,
    subscriber_id         INT  NOT NULL,
    date_of_placing_order DATE NOT NULL
);

INSERT INTO `Order` VALUES (1023, '2026-06-12', 4, 392,  4821, '2026-04-20');
INSERT INTO `Order` VALUES (1055, '2026-06-15', 2, 1774, 4821, '2026-04-28');
INSERT INTO `Order` VALUES (1087, '2026-06-28', 6, 2891, 4821, '2026-05-02');
