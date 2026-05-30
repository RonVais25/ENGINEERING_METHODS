-- =========================================================
-- GoNature Full Database Schema
-- Course: 61756 - Software Engineering Methods
-- Purpose: Full relational DB structure for GoNature system
-- =========================================================

DROP DATABASE IF EXISTS gonature;
CREATE DATABASE gonature CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE gonature;

-- =========================================================
-- 1. Users / Employees / Roles
-- =========================================================

CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    role VARCHAR(50) NOT NULL,
    park_id INT,
    is_logged_in BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE
);

-- =========================================================
-- 2. Parks and Park Parameters
-- =========================================================

CREATE TABLE parks (
    park_id INT AUTO_INCREMENT PRIMARY KEY,
    park_name VARCHAR(100) NOT NULL,
    location VARCHAR(100),
    maximum_capacity INT NOT NULL,
    reservation_gap INT NOT NULL,
    estimated_stay_duration INT NOT NULL,
    current_visitors INT DEFAULT 0
);

ALTER TABLE users
ADD CONSTRAINT fk_users_park
FOREIGN KEY (park_id) REFERENCES parks(park_id);

CREATE TABLE park_parameter_updates (
    update_id INT AUTO_INCREMENT PRIMARY KEY,
    park_id INT NOT NULL,
    parameter_name VARCHAR(50) NOT NULL,
    old_value INT NOT NULL,
    new_value INT NOT NULL,
    requested_by INT NOT NULL,
    approved_by INT,
    status VARCHAR(30) DEFAULT 'PENDING',
    request_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    approval_date DATETIME,

    FOREIGN KEY (park_id) REFERENCES parks(park_id),
    FOREIGN KEY (requested_by) REFERENCES users(user_id),
    FOREIGN KEY (approved_by) REFERENCES users(user_id)
);

-- =========================================================
-- 3. Visitors / Subscribers / Authorized Guides
-- =========================================================

CREATE TABLE visitors (
    visitor_id INT AUTO_INCREMENT PRIMARY KEY,
    id_number VARCHAR(20) NOT NULL UNIQUE,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    phone VARCHAR(20),
    email VARCHAR(100)
);

CREATE TABLE family_subscriptions (
    subscription_id INT AUTO_INCREMENT PRIMARY KEY,
    visitor_id INT NOT NULL,
    family_members_count INT NOT NULL,
    credit_card_number VARCHAR(30),
    payment_method VARCHAR(30),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,

    FOREIGN KEY (visitor_id) REFERENCES visitors(visitor_id)
);

CREATE TABLE authorized_guides (
    guide_id INT AUTO_INCREMENT PRIMARY KEY,
    visitor_id INT NOT NULL,
    registration_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    registered_by INT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,

    FOREIGN KEY (visitor_id) REFERENCES visitors(visitor_id),
    FOREIGN KEY (registered_by) REFERENCES users(user_id)
);

-- =========================================================
-- 4. Reservations
-- =========================================================

CREATE TABLE reservations (
    reservation_id INT AUTO_INCREMENT PRIMARY KEY,
    visitor_id INT NOT NULL,
    park_id INT NOT NULL,
    subscription_id INT,
    guide_id INT,
    visit_date DATE NOT NULL,
    arrival_time TIME NOT NULL,
    number_of_visitors INT NOT NULL,
    visitor_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
    qr_code VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    confirmed_at DATETIME,
    cancelled_at DATETIME,

    FOREIGN KEY (visitor_id) REFERENCES visitors(visitor_id),
    FOREIGN KEY (park_id) REFERENCES parks(park_id),
    FOREIGN KEY (subscription_id) REFERENCES family_subscriptions(subscription_id),
    FOREIGN KEY (guide_id) REFERENCES authorized_guides(guide_id)
);

-- =========================================================
-- 5. Waiting List
-- =========================================================

CREATE TABLE waiting_list (
    waiting_id INT AUTO_INCREMENT PRIMARY KEY,
    visitor_id INT NOT NULL,
    park_id INT NOT NULL,
    requested_date DATE NOT NULL,
    requested_time TIME NOT NULL,
    number_of_visitors INT NOT NULL,
    position_in_queue INT NOT NULL,
    status VARCHAR(30) DEFAULT 'WAITING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    offer_sent_at DATETIME,
    hold_until DATETIME,

    FOREIGN KEY (visitor_id) REFERENCES visitors(visitor_id),
    FOREIGN KEY (park_id) REFERENCES parks(park_id)
);

-- =========================================================
-- 6. Visits / Entry / Exit
-- =========================================================

CREATE TABLE visits (
    visit_id INT AUTO_INCREMENT PRIMARY KEY,
    reservation_id INT,
    park_id INT NOT NULL,
    entry_time DATETIME NOT NULL,
    exit_time DATETIME,
    visitors_count INT NOT NULL,
    entry_type VARCHAR(30) NOT NULL,

    FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
    FOREIGN KEY (park_id) REFERENCES parks(park_id)
);

-- =========================================================
-- 7. Billing / Payments
-- =========================================================

CREATE TABLE bills (
    bill_id INT AUTO_INCREMENT PRIMARY KEY,
    reservation_id INT,
    visit_id INT,
    base_amount DECIMAL(10,2) NOT NULL,
    discount_amount DECIMAL(10,2) DEFAULT 0,
    final_amount DECIMAL(10,2) NOT NULL,
    pricing_type VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
    FOREIGN KEY (visit_id) REFERENCES visits(visit_id)
);

CREATE TABLE payments (
    payment_id INT AUTO_INCREMENT PRIMARY KEY,
    bill_id INT NOT NULL,
    payment_method VARCHAR(30),
    payment_status VARCHAR(30),
    paid_at DATETIME,

    FOREIGN KEY (bill_id) REFERENCES bills(bill_id)
);

-- =========================================================
-- 8. Promotions
-- =========================================================

CREATE TABLE promotions (
    promotion_id INT AUTO_INCREMENT PRIMARY KEY,
    promotion_name VARCHAR(100) NOT NULL,
    discount_percent DECIMAL(5,2) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    park_id INT,
    defined_by INT NOT NULL,
    approved_by INT,
    status VARCHAR(30) DEFAULT 'PENDING',

    FOREIGN KEY (park_id) REFERENCES parks(park_id),
    FOREIGN KEY (defined_by) REFERENCES users(user_id),
    FOREIGN KEY (approved_by) REFERENCES users(user_id)
);

-- =========================================================
-- 9. Notifications
-- =========================================================

CREATE TABLE notifications (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    recipient_email VARCHAR(100),
    recipient_phone VARCHAR(20),
    notification_type VARCHAR(50),
    channel VARCHAR(20),
    message_text TEXT,
    sent_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(30)
);

-- =========================================================
-- 10. Generated Reports
-- =========================================================

CREATE TABLE generated_reports (
    report_id INT AUTO_INCREMENT PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    park_id INT,
    generated_by INT NOT NULL,
    start_date DATE,
    end_date DATE,
    generated_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (park_id) REFERENCES parks(park_id),
    FOREIGN KEY (generated_by) REFERENCES users(user_id)
);

-- =========================================================
-- 11. Cancellation / No-show Tracking
-- =========================================================

CREATE TABLE reservation_cancellations (
    cancellation_id INT AUTO_INCREMENT PRIMARY KEY,
    reservation_id INT NOT NULL,
    cancellation_type VARCHAR(30) NOT NULL,
    reason VARCHAR(255),
    cancelled_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id)
);

-- =========================================================
-- 12. Demo Seed Data
-- =========================================================

INSERT INTO parks (park_id, park_name, location, maximum_capacity, reservation_gap, estimated_stay_duration, current_visitors)
VALUES
(1, 'Carmel Forest', 'Haifa District', 100, 20, 120, 0),
(2, 'Ein Gedi', 'Dead Sea Area', 80, 15, 90, 0),
(3, 'Banias', 'Golan Heights', 120, 25, 150, 0);

INSERT INTO users (user_id, username, password_hash, first_name, last_name, email, role, park_id, is_logged_in, is_active)
VALUES
(1, 'service1', '1234', 'Service', 'Representative', 'service@gonature.com', 'SERVICE_REPRESENTATIVE', NULL, FALSE, TRUE),
(2, 'worker1', '1234', 'Park', 'Worker', 'worker@gonature.com', 'PARK_WORKER', 1, FALSE, TRUE),
(3, 'manager1', '1234', 'Park', 'Manager', 'manager@gonature.com', 'PARK_MANAGER', 1, FALSE, TRUE),
(4, 'dept1', '1234', 'Department', 'Manager', 'dept@gonature.com', 'DEPARTMENT_MANAGER', NULL, FALSE, TRUE);

INSERT INTO visitors (visitor_id, id_number, first_name, last_name, phone, email)
VALUES
(1, '123456789', 'Demo', 'Visitor', '0501234567', 'visitor@gonature.com'),
(2, '987654321', 'Group', 'Guide', '0507654321', 'guide@gonature.com');

INSERT INTO family_subscriptions (subscription_id, visitor_id, family_members_count, credit_card_number, payment_method, is_active)
VALUES
(1, 1, 4, NULL, 'CASH', TRUE);

INSERT INTO authorized_guides (guide_id, visitor_id, registered_by, is_active)
VALUES
(1, 2, 1, TRUE);

INSERT INTO reservations (reservation_id, visitor_id, park_id, subscription_id, guide_id, visit_date, arrival_time, number_of_visitors, visitor_type, status, qr_code)
VALUES
(1, 1, 1, 1, NULL, '2026-06-01', '10:00:00', 4, 'SUBSCRIBER', 'APPROVED', 'QR-RES-1'),
(2, 2, 1, NULL, 1, '2026-06-02', '09:30:00', 15, 'GROUP_PREBOOKED', 'APPROVED', 'QR-RES-2');

INSERT INTO visits (visit_id, reservation_id, park_id, entry_time, exit_time, visitors_count, entry_type)
VALUES
(1, 1, 1, '2026-06-01 10:05:00', '2026-06-01 12:00:00', 4, 'RESERVATION'),
(2, 2, 1, '2026-06-02 09:40:00', '2026-06-02 11:30:00', 15, 'GROUP');

INSERT INTO bills (bill_id, reservation_id, visit_id, base_amount, discount_amount, final_amount, pricing_type)
VALUES
(1, 1, 1, 200.00, 50.00, 150.00, 'SUBSCRIBER'),
(2, 2, 2, 750.00, 277.50, 472.50, 'GROUP_PREBOOKED');

INSERT INTO payments (payment_id, bill_id, payment_method, payment_status, paid_at)
VALUES
(1, 1, 'CASH', 'PAID', '2026-06-01 12:05:00'),
(2, 2, 'CREDIT_CARD', 'PAID', '2026-06-02 11:35:00');

INSERT INTO promotions (promotion_id, promotion_name, discount_percent, start_date, end_date, park_id, defined_by, approved_by, status)
VALUES
(1, 'Summer Discount', 10.00, '2026-06-01', '2026-08-31', 1, 3, 4, 'APPROVED');

INSERT INTO waiting_list (waiting_id, visitor_id, park_id, requested_date, requested_time, number_of_visitors, position_in_queue, status)
VALUES
(1, 1, 1, '2026-06-10', '10:00:00', 4, 1, 'WAITING');

INSERT INTO notifications (recipient_email, recipient_phone, notification_type, channel, message_text, status)
VALUES
('visitor@gonature.com', '0501234567', 'RESERVATION_CONFIRMATION', 'EMAIL', 'Your reservation was approved.', 'SENT'),
('visitor@gonature.com', '0501234567', 'REMINDER', 'SMS', 'Reminder: your visit is tomorrow.', 'SENT');

-- =========================================================
-- 13. Useful Report Queries
-- =========================================================

-- Visits Report
-- SELECT
--     p.park_name,
--     r.visitor_type,
--     v.entry_time,
--     v.exit_time,
--     TIMESTAMPDIFF(MINUTE, v.entry_time, v.exit_time) AS stay_duration_minutes,
--     v.visitors_count
-- FROM visits v
-- JOIN parks p ON v.park_id = p.park_id
-- LEFT JOIN reservations r ON v.reservation_id = r.reservation_id
-- WHERE v.entry_time BETWEEN ? AND ?
-- ORDER BY v.entry_time;

-- Usage Report: periods where park was below full capacity
-- SELECT
--     p.park_name,
--     v.entry_time,
--     v.exit_time,
--     p.maximum_capacity,
--     v.visitors_count
-- FROM visits v
-- JOIN parks p ON v.park_id = p.park_id
-- WHERE v.visitors_count < p.maximum_capacity;

-- Cancellation Report
-- SELECT
--     r.reservation_id,
--     p.park_name,
--     r.visit_date,
--     r.visitor_type,
--     rc.cancellation_type,
--     rc.cancelled_at
-- FROM reservation_cancellations rc
-- JOIN reservations r ON rc.reservation_id = r.reservation_id
-- JOIN parks p ON r.park_id = p.park_id
-- WHERE r.visit_date BETWEEN ? AND ?;

-- =========================================================
-- End of GoNature Full Database Schema
-- =========================================================
