DROP DATABASE IF EXISTS gonature;
CREATE DATABASE gonature;
USE gonature;

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

CREATE TABLE parks (
    park_id INT AUTO_INCREMENT PRIMARY KEY,
    park_name VARCHAR(100) NOT NULL,
    location VARCHAR(100),
    maximum_capacity INT NOT NULL,
    reservation_gap INT NOT NULL,
    estimated_stay_duration INT NOT NULL,
    current_visitors INT DEFAULT 0
);

ALTER TABLE users ADD CONSTRAINT fk_users_park FOREIGN KEY (park_id) REFERENCES parks(park_id);

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

CREATE TABLE reservation_cancellations (
    cancellation_id INT AUTO_INCREMENT PRIMARY KEY,
    reservation_id INT NOT NULL,
    cancellation_type VARCHAR(30) NOT NULL,
    reason VARCHAR(255),
    cancelled_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id)
);
