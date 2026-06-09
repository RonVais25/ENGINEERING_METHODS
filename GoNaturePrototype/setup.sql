CREATE DATABASE IF NOT EXISTS gonature;
USE gonature;

-- ---------------------------------------------------------------------------
-- Idempotent rebuild: drop everything with FK checks off so order is moot and
-- re-running this script always starts from a clean slate.
-- ---------------------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `Order`;
DROP TABLE IF EXISTS active_session;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS parameter_change_request;
DROP TABLE IF EXISTS visit;
DROP TABLE IF EXISTS waiting_list_entry;
DROP TABLE IF EXISTS reservation;
DROP TABLE IF EXISTS guide;
DROP TABLE IF EXISTS subscriber;
DROP TABLE IF EXISTS park;
DROP TABLE IF EXISTS visitor;
DROP TABLE IF EXISTS `user`;
SET FOREIGN_KEY_CHECKS = 1;

-- ===========================================================================
-- EXISTING DEMO TABLE -- DO NOT MODIFY
-- ===========================================================================
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

-- user: staff accounts. park_id FK is deferred (circular with park.manager_id)
CREATE TABLE `user` (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(50) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,   -- a later session decides hashing; demo may store plain
    role            ENUM('PARK_EMPLOYEE','PARK_MANAGER','DEPT_MANAGER','SERVICE_REP') NOT NULL,
    full_name       VARCHAR(100),
    park_id         INT NULL                 -- FK -> park.id, added via ALTER (circular)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- visitor: identified by national ID (assigned)
CREATE TABLE visitor (
    id              BIGINT PRIMARY KEY,
    full_name       VARCHAR(100),
    phone           VARCHAR(20),
    email           VARCHAR(100),
    is_subscriber   BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- park: manager_id FK is deferred (circular with user.park_id)
CREATE TABLE park (
    id                   INT PRIMARY KEY AUTO_INCREMENT,
    name                 VARCHAR(100) NOT NULL,
    max_capacity         INT NOT NULL,
    gap_size             INT NOT NULL DEFAULT 0,
    default_stay_minutes INT NOT NULL DEFAULT 240,
    manager_id           INT NULL            -- FK -> user.id
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- subscriber: a visitor who subscribed (1:1 with visitor)
CREATE TABLE subscriber (
    visitor_id      BIGINT PRIMARY KEY,
    family_size     INT NOT NULL DEFAULT 1,
    joined_on       DATE NOT NULL,
    CONSTRAINT fk_subscriber_visitor FOREIGN KEY (visitor_id) REFERENCES visitor(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- guide: a visitor approved as a group guide
CREATE TABLE guide (
    visitor_id      BIGINT PRIMARY KEY,
    registered_by   INT,
    approved_on     DATE,
    CONSTRAINT fk_guide_visitor FOREIGN KEY (visitor_id)    REFERENCES visitor(id),
    CONSTRAINT fk_guide_user    FOREIGN KEY (registered_by) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- reservation: a booking against a park
CREATE TABLE reservation (
    id                INT PRIMARY KEY AUTO_INCREMENT,
    park_id           INT NOT NULL,
    visitor_id        BIGINT NOT NULL,
    visit_date        DATE NOT NULL,
    visit_time        TIME NULL,
    party_size        INT NOT NULL,
    visit_type        ENUM('INDIVIDUAL','FAMILY','GROUP') NOT NULL,
    status            ENUM('PENDING','CONFIRMED','WAITING','CANCELLED','COMPLETED','NO_SHOW') NOT NULL DEFAULT 'PENDING',
    is_group          BOOLEAN NOT NULL DEFAULT FALSE,
    guide_id          BIGINT NULL,
    price_cents       INT NOT NULL DEFAULT 0,
    paid_in_advance   BOOLEAN NOT NULL DEFAULT FALSE,
    confirmation_code INT NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservation_park    FOREIGN KEY (park_id)    REFERENCES park(id),
    CONSTRAINT fk_reservation_visitor FOREIGN KEY (visitor_id) REFERENCES visitor(id),
    CONSTRAINT fk_reservation_guide   FOREIGN KEY (guide_id)   REFERENCES guide(visitor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- waiting_list_entry: queue position for a WAITING reservation
CREATE TABLE waiting_list_entry (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    reservation_id  INT NOT NULL,
    queued_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    grab_offered_at DATETIME NULL,
    grab_expires_at DATETIME NULL,
    CONSTRAINT fk_wle_reservation FOREIGN KEY (reservation_id) REFERENCES reservation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- visit: an actual entry into a park.
-- price_cents/visit_type are persisted only for CASUAL walk-ins (no reservation);
-- reservation-backed visits leave them NULL and derive both from the reservation.
CREATE TABLE visit (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    reservation_id  INT NULL,
    park_id         INT NOT NULL,
    visitor_id      BIGINT NULL,
    entered_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exited_at       DATETIME NULL,
    headcount       INT NOT NULL DEFAULT 1,
    price_cents     INT NULL,
    visit_type      ENUM('INDIVIDUAL','FAMILY','GROUP') NULL,
    CONSTRAINT fk_visit_reservation FOREIGN KEY (reservation_id) REFERENCES reservation(id),
    CONSTRAINT fk_visit_park        FOREIGN KEY (park_id)        REFERENCES park(id),
    CONSTRAINT fk_visit_visitor     FOREIGN KEY (visitor_id)     REFERENCES visitor(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- parameter_change_request: a park manager's request to change a park parameter.
CREATE TABLE parameter_change_request (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    park_id         INT NOT NULL,
    requested_by    INT NOT NULL,
    field           ENUM('MAX_CAPACITY','GAP_SIZE','DEFAULT_STAY_MINUTES') NOT NULL,
    old_value       INT,
    new_value       INT NOT NULL,
    status          ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    decided_by      INT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at      DATETIME NULL,
    CONSTRAINT fk_pcr_park         FOREIGN KEY (park_id)      REFERENCES park(id),
    CONSTRAINT fk_pcr_requested_by FOREIGN KEY (requested_by) REFERENCES `user`(id),
    CONSTRAINT fk_pcr_decided_by   FOREIGN KEY (decided_by)   REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- notification: simulated/popup message addressed to a visitor or a user.
-- acknowledged_at backs the notification center's unread highlight (NULL = unread).
-- It is distinct from sent_at because sent_at is stamped at delivery time, before
-- the recipient has actually seen the message, so it cannot double as a seen-marker.
CREATE TABLE notification (
    id                   INT PRIMARY KEY AUTO_INCREMENT,
    recipient_visitor_id BIGINT NULL,
    recipient_user_id    INT NULL,
    channel              ENUM('SIM_EMAIL','SIM_SMS','POPUP') NOT NULL,
    body                 TEXT NOT NULL,
    scheduled_for        DATETIME NULL,
    sent_at              DATETIME NULL,
    acknowledged_at      DATETIME NULL,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_visitor FOREIGN KEY (recipient_visitor_id) REFERENCES visitor(id),
    CONSTRAINT fk_notification_user    FOREIGN KEY (recipient_user_id)    REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- active_session: single login lock. 
CREATE TABLE active_session (
    actor_id        BIGINT NOT NULL,
    kind            ENUM('USER','VISITOR') NOT NULL,
    since           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (actor_id, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- Deferred circular FKs between `user` and `park`.
-- ---------------------------------------------------------------------------
ALTER TABLE `user` ADD CONSTRAINT fk_user_park    FOREIGN KEY (park_id)    REFERENCES park(`id`);
ALTER TABLE park   ADD CONSTRAINT fk_park_manager FOREIGN KEY (manager_id) REFERENCES `user`(`id`);

-- 1) Parks (manager_id NULL for now -- users don't exist yet). ids: 1, 2.
INSERT INTO park (name, max_capacity, gap_size, default_stay_minutes, manager_id) VALUES
('Galilee Park', 100, 10, 240, NULL),
('Carmel Park',  100, 10, 240, NULL);

-- 2) Users. ids: 1=dept_mgr, 2=galilee_mgr, 3=carmel_mgr, 4=service_rep, 5=park_emp.
INSERT INTO `user` (username, password_hash, role, full_name, park_id) VALUES
('dept_mgr',    'changeme', 'DEPT_MANAGER',  'Dana Department', NULL),
('galilee_mgr', 'changeme', 'PARK_MANAGER',  'Gil Galilee',     1),
('carmel_mgr',  'changeme', 'PARK_MANAGER',  'Carmela Carmel',  2),
('service_rep', 'changeme', 'SERVICE_REP',   'Sara Service',    NULL),
('park_emp',    'changeme', 'PARK_EMPLOYEE', 'Eli Employee',    1);

-- 3) Now that users exist, point each park at its park manager.
UPDATE park SET manager_id = 2 WHERE id = 1;   -- Galilee Park -> galilee_mgr
UPDATE park SET manager_id = 3 WHERE id = 2;   -- Carmel Park  -> carmel_mgr

-- 4) Visitors (national-ID-style BIGINTs). 200000001 is a subscriber.
INSERT INTO visitor (id, full_name, phone, email, is_subscriber) VALUES
(200000001, 'Vera Visitor', '050-1111111', 'vera@example.com',   TRUE),
(200000002, 'Victor Visit', '050-2222222', 'victor@example.com', FALSE),
(200000003, 'Gad Guide',    '050-3333333', 'gad@example.com',    FALSE),
(200000004, 'Greta Group',  '050-4444444', 'greta@example.com',  FALSE);

-- 5) One subscriber row and one guide row (guide registered by the service rep).
INSERT INTO subscriber (visitor_id, family_size, joined_on) VALUES
(200000001, 3, '2026-01-15');

INSERT INTO guide (visitor_id, registered_by, approved_on) VALUES
(200000003, 4, '2026-02-01');

-- 6) Reservations: spread of status + visit_type. The GROUP one uses the guide.
--    Auto-inc ids -> 1..5; the WAITING/GROUP one is id 3.
INSERT INTO reservation
    (park_id, visitor_id, visit_date, visit_time, party_size, visit_type, status, is_group, guide_id, price_cents, paid_in_advance, confirmation_code) VALUES
(1, 200000001, '2026-06-10', '09:00:00',  1, 'INDIVIDUAL', 'PENDING',   FALSE, NULL,      5000,  FALSE, NULL),
(1, 200000001, '2026-06-12', '10:30:00',  4, 'FAMILY',     'CONFIRMED', FALSE, NULL,      18000, TRUE,  4821),
(2, 200000004, '2026-06-15', '11:00:00', 12, 'GROUP',      'WAITING',   TRUE,  200000003, 50000, FALSE, NULL),
(2, 200000002, '2026-06-18', '14:00:00',  1, 'INDIVIDUAL', 'CANCELLED', FALSE, NULL,      5000,  FALSE, NULL),
(1, 200000002, '2026-06-20', '13:00:00',  3, 'FAMILY',     'CONFIRMED', FALSE, NULL,      14000, TRUE,  1234);

-- 7) One row each for the remaining tables, just to prove the FKs resolve.
INSERT INTO waiting_list_entry (reservation_id, queued_at) VALUES
(3, '2026-06-02 09:00:00');

INSERT INTO visit (reservation_id, park_id, visitor_id, entered_at, exited_at, headcount) VALUES
(2, 1, 200000001, '2026-06-12 10:35:00', NULL, 4);

INSERT INTO parameter_change_request (park_id, requested_by, field, old_value, new_value, status) VALUES
(1, 2, 'MAX_CAPACITY', 100, 120, 'PENDING');

INSERT INTO notification (recipient_visitor_id, recipient_user_id, channel, body, scheduled_for) VALUES
(200000001, NULL, 'SIM_EMAIL', 'Your reservation is confirmed.', NULL);

-- active_session intentionally left empty (populated at runtime by login).
