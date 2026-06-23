CREATE DATABASE IF NOT EXISTS gonature;
USE gonature;

-- ---------------------------------------------------------------------------
-- Idempotent rebuild: drop everything with FK checks off so order is moot and
-- re-running this script always starts from a clean slate.
-- ---------------------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS active_session;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS promotion;
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

-- user: staff accounts. park_id FK is deferred (circular with park.manager_id)
CREATE TABLE `user` (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(50) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,   -- a later session decides hashing; demo may store plain
    role            ENUM('PARK_EMPLOYEE','PARK_MANAGER','DEPT_MANAGER','SERVICE_REP') NOT NULL,
    full_name       VARCHAR(100),
    park_id         INT NULL                 -- FK -> park.id, added via ALTER (circular)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- visitor: identified by national ID (assigned). password_hash mirrors
-- user.password_hash: visitor login is now national ID + password (a later
-- session decides hashing; demo stores plain, e.g. the shared 'changeme').
CREATE TABLE visitor (
    id              BIGINT PRIMARY KEY,
    full_name       VARCHAR(100),
    phone           VARCHAR(20),
    email           VARCHAR(100),
    is_subscriber   BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash   VARCHAR(255) NOT NULL    -- visitor login password; stored exactly like user.password_hash
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
    -- status_changed_at: stamped by ReservationDAO.updateStatus on every status
    -- transition (cancel/confirm/grab/complete/no-show). Backs the Cancellations
    -- Report. reminder_sent_at: reserved for the Scheduler's ReminderJob (session b)
    -- so a confirmation reminder is sent at most once per reservation.
    status_changed_at DATETIME NULL,
    reminder_sent_at  DATETIME NULL,
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

-- promotion: a park manager's temporary discount on their park's visits,
-- approved by a department manager. Mirrors parameter_change_request's
-- request/approve/reject shape. An APPROVED promotion whose window contains the
-- visit date grants an extra additive discount (see PricingService); PENDING /
-- REJECTED rows never affect a price.
CREATE TABLE promotion (
    id               INT PRIMARY KEY AUTO_INCREMENT,
    park_id          INT NOT NULL,
    name             VARCHAR(100) NOT NULL,
    discount_percent INT NOT NULL,                                       -- 0..100
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    status           ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    defined_by       INT NOT NULL,
    approved_by      INT NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at       DATETIME NULL,
    CONSTRAINT fk_promotion_park        FOREIGN KEY (park_id)     REFERENCES park(id),
    CONSTRAINT fk_promotion_defined_by  FOREIGN KEY (defined_by)  REFERENCES `user`(id),
    CONSTRAINT fk_promotion_approved_by FOREIGN KEY (approved_by) REFERENCES `user`(id)
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

-- ===========================================================================
-- SEED DATA
-- ---------------------------------------------------------------------------
-- A realistic ~month of activity that exercises every feature and makes the
-- Reports meaningful, for a live two-machine demo. Every date/time is expressed
-- RELATIVE to CURDATE()/NOW() (+/- INTERVAL) so the data always spans
-- past / today / future no matter when this script is loaded.
--
-- Auto-increment ids reset on each rebuild (the DROP/CREATE above), so the
-- reservation/park/user ids referenced by later INSERTs are deterministic:
--   parks   1..3, users 1..8, reservations 1..29 in the order written below.
-- FK-safe order: parks+users -> visitors -> subscribers/guides -> reservations
--   -> waiting_list_entry / visit / parameter_change_request / notification.
-- ===========================================================================

-- 1) Parks (manager_id NULL for now -- users don't exist yet). ids: 1, 2, 3.
--    Varied capacity / gap / default stay so per-park behaviour differs.
INSERT INTO park (name, max_capacity, gap_size, default_stay_minutes, manager_id) VALUES
('Galilee Park', 100, 10, 240, NULL),
('Carmel Park',   80,  8, 180, NULL),
('Negev Park',   150, 15, 300, NULL);

-- 2) Users -- at least one per role, spread across parks, all share the known
--    demo password 'changeme' so login + quick-login keep working.
--    ids: 1=dept_mgr 2=galilee_mgr 3=carmel_mgr 4=service_rep 5=park_emp
--         6=negev_mgr 7=park_emp2 8=park_emp3.
INSERT INTO `user` (username, password_hash, role, full_name, park_id) VALUES
('dept_mgr',    'changeme', 'DEPT_MANAGER',  'Dana Department', NULL),
('galilee_mgr', 'changeme', 'PARK_MANAGER',  'Gil Galilee',     1),
('carmel_mgr',  'changeme', 'PARK_MANAGER',  'Carmela Carmel',  2),
('service_rep', 'changeme', 'SERVICE_REP',   'Sara Service',    NULL),
('park_emp',    'changeme', 'PARK_EMPLOYEE', 'Eli Employee',    1),
('negev_mgr',   'changeme', 'PARK_MANAGER',  'Nadav Negev',     3),
('park_emp2',   'changeme', 'PARK_EMPLOYEE', 'Tova Teller',     2),
('park_emp3',   'changeme', 'PARK_EMPLOYEE', 'Roni Ranger',     3);

-- 3) Now that users exist, point each park at its park manager.
UPDATE park SET manager_id = 2 WHERE id = 1;   -- Galilee Park -> galilee_mgr
UPDATE park SET manager_id = 3 WHERE id = 2;   -- Carmel Park  -> carmel_mgr
UPDATE park SET manager_id = 6 WHERE id = 3;   -- Negev Park   -> negev_mgr

-- 4) Visitors (national-ID-style BIGINTs). ALL have phone + email so the
--    notification channels always have a delivery target, and ALL share the
--    demo login password 'changeme' (visitor login is now national ID + password,
--    like staff). Subscribers are 200000002 / 200000005 / 200000006; guides are
--    200000003 / 200000011. The two clear login examples: Vera Visitor (200000001)
--    is a plain registered visitor (NOT a subscriber); Victor Visitor (200000002)
--    is registered AND a subscriber (with a matching subscriber row below).
INSERT INTO visitor (id, full_name, phone, email, is_subscriber, password_hash) VALUES
(200000001, 'Vera Visitor',   '050-1111111', 'vera@example.com',    FALSE, 'changeme'),
(200000002, 'Victor Visitor', '050-2222222', 'victor@example.com',  TRUE,  'changeme'),
(200000003, 'Gad Guide',      '050-3333333', 'gad@example.com',     FALSE, 'changeme'),
(200000004, 'Greta Group',    '050-4444444', 'greta@example.com',   FALSE, 'changeme'),
(200000005, 'Sam Subscriber', '050-5555555', 'sam@example.com',     TRUE,  'changeme'),
(200000006, 'Sophie Sub',     '050-6666666', 'sophie@example.com',  TRUE,  'changeme'),
(200000007, 'Noa Nature',     '050-7777777', 'noa@example.com',     FALSE, 'changeme'),
(200000008, 'Omer Outdoors',  '050-8888888', 'omer@example.com',    FALSE, 'changeme'),
(200000009, 'Hila Hiker',     '050-9999999', 'hila@example.com',    FALSE, 'changeme'),
(200000010, 'Tom Tourist',    '050-1010100', 'tom@example.com',     FALSE, 'changeme'),
(200000011, 'Gabi Guide',     '050-1111000', 'gabi@example.com',    FALSE, 'changeme'),
(200000012, 'Maya Member',    '050-1212120', 'maya@example.com',    FALSE, 'changeme');

-- 5) Subscribers (3) and guides (2). Dates are relative so "joined N days ago"
--    stays true over time. Guides are registered by the service rep (user 4).
INSERT INTO subscriber (visitor_id, family_size, joined_on) VALUES
(200000002, 3, CURDATE() - INTERVAL 150 DAY),
(200000005, 4, CURDATE() - INTERVAL  60 DAY),
(200000006, 2, CURDATE() - INTERVAL  20 DAY);

INSERT INTO guide (visitor_id, registered_by, approved_on) VALUES
(200000003, 4, CURDATE() - INTERVAL 120 DAY),
(200000011, 4, CURDATE() - INTERVAL  45 DAY);

-- 6) Reservations (29) -- every status x every visit_type across all 3 parks,
--    visit_date spread over past / today / next 2 weeks, mixed paid_in_advance.
--    GROUP rows are is_group=TRUE and carry a guide_id. status_changed_at is
--    stamped on the COMPLETED/CANCELLED/NO_SHOW rows; the CANCELLED+NO_SHOW
--    rows land on several distinct recent days so the Cancellations Report has
--    a real per-day distribution. Auto-inc ids run 1..29 in the order below.
--    confirmation_code is set on every CONFIRMED/COMPLETED row (gate look-ups).
INSERT INTO reservation
    (park_id, visitor_id, visit_date, visit_time, party_size, visit_type, status, is_group, guide_id, price_cents, paid_in_advance, confirmation_code, status_changed_at) VALUES
-- ids 1..8: COMPLETED in the past (each gets a closed visit below).
(1, 200000001, CURDATE() - INTERVAL 28 DAY, '09:00:00',  1, 'INDIVIDUAL', 'COMPLETED', FALSE, NULL,      5000,  TRUE,  1001, TIMESTAMP(CURDATE() - INTERVAL 28 DAY, '12:30:00')),
(1, 200000002, CURDATE() - INTERVAL 25 DAY, '10:30:00',  4, 'FAMILY',     'COMPLETED', FALSE, NULL,      18000, TRUE,  1002, TIMESTAMP(CURDATE() - INTERVAL 25 DAY, '14:50:00')),
(2, 200000004, CURDATE() - INTERVAL 24 DAY, '11:00:00', 12, 'GROUP',      'COMPLETED', TRUE,  200000003, 50000, TRUE,  1003, TIMESTAMP(CURDATE() - INTERVAL 24 DAY, '14:00:00')),
(3, 200000007, CURDATE() - INTERVAL 21 DAY, '09:30:00',  1, 'INDIVIDUAL', 'COMPLETED', FALSE, NULL,      5000,  FALSE, 1004, TIMESTAMP(CURDATE() - INTERVAL 21 DAY, '13:15:00')),
(2, 200000005, CURDATE() - INTERVAL 18 DAY, '10:00:00',  4, 'FAMILY',     'COMPLETED', FALSE, NULL,      16000, TRUE,  1005, TIMESTAMP(CURDATE() - INTERVAL 18 DAY, '13:40:00')),
(1, 200000008, CURDATE() - INTERVAL 14 DAY, '09:45:00', 10, 'GROUP',      'COMPLETED', TRUE,  200000011, 45000, TRUE,  1006, TIMESTAMP(CURDATE() - INTERVAL 14 DAY, '13:30:00')),
(3, 200000009, CURDATE() - INTERVAL 10 DAY, '14:00:00',  1, 'INDIVIDUAL', 'COMPLETED', FALSE, NULL,      5000,  FALSE, 1007, TIMESTAMP(CURDATE() - INTERVAL 10 DAY, '16:30:00')),
(1, 200000006, CURDATE() - INTERVAL  7 DAY, '13:00:00',  3, 'FAMILY',     'COMPLETED', FALSE, NULL,      14000, TRUE,  1008, TIMESTAMP(CURDATE() - INTERVAL  7 DAY, '17:00:00')),
-- ids 9..11: NO_SHOW on distinct recent days.
(1, 200000002, CURDATE() - INTERVAL 20 DAY, '09:00:00',  1, 'INDIVIDUAL', 'NO_SHOW',   FALSE, NULL,      5000,  FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 20 DAY, '10:00:00')),
(2, 200000010, CURDATE() - INTERVAL 12 DAY, '11:30:00',  3, 'FAMILY',     'NO_SHOW',   FALSE, NULL,      13000, FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 12 DAY, '12:30:00')),
(3, 200000004, CURDATE() - INTERVAL  5 DAY, '10:15:00',  9, 'GROUP',      'NO_SHOW',   TRUE,  200000003, 40000, FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL  5 DAY, '11:15:00')),
-- ids 12..17: CANCELLED, each status_changed_at on a different recent day.
(1, 200000007, CURDATE() - INTERVAL 22 DAY, '09:00:00',  1, 'INDIVIDUAL', 'CANCELLED', FALSE, NULL,      5000,  FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 23 DAY, '08:10:00')),
(2, 200000008, CURDATE() - INTERVAL 15 DAY, '12:00:00',  4, 'FAMILY',     'CANCELLED', FALSE, NULL,      16000, TRUE,  NULL, TIMESTAMP(CURDATE() - INTERVAL 17 DAY, '19:25:00')),
(3, 200000009, CURDATE() - INTERVAL  9 DAY, '10:30:00',  8, 'GROUP',      'CANCELLED', TRUE,  200000011, 38000, FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 11 DAY, '07:45:00')),
(1, 200000010, CURDATE() - INTERVAL  3 DAY, '13:00:00',  1, 'INDIVIDUAL', 'CANCELLED', FALSE, NULL,      5000,  FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL  4 DAY, '21:05:00')),
(2, 200000002, CURDATE() + INTERVAL  2 DAY, '14:00:00',  4, 'FAMILY',     'CANCELLED', FALSE, NULL,      16000, FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL  2 DAY, '10:40:00')),
(1, 200000012, CURDATE() + INTERVAL  5 DAY, '09:30:00',  1, 'INDIVIDUAL', 'CANCELLED', FALSE, NULL,      5000,  FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL  1 DAY, '16:15:00')),
-- ids 18..20: CONFIRMED for TODAY (each gets an OPEN visit -> live occupancy).
(1, 200000001, CURDATE(),                   '09:00:00',  1, 'INDIVIDUAL', 'CONFIRMED', FALSE, NULL,      5000,  TRUE,  2001, NULL),
(2, 200000005, CURDATE(),                   '10:00:00',  4, 'FAMILY',     'CONFIRMED', FALSE, NULL,      16000, TRUE,  2002, NULL),
(3, 200000004, CURDATE(),                   '11:00:00', 14, 'GROUP',      'CONFIRMED', TRUE,  200000003, 60000, TRUE,  2003, NULL),
-- ids 21..23: CONFIRMED in the next few days (mixed paid_in_advance).
(1, 200000007, CURDATE() + INTERVAL  1 DAY, '09:30:00',  1, 'INDIVIDUAL', 'CONFIRMED', FALSE, NULL,      5000,  FALSE, 2004, NULL),
(2, 200000006, CURDATE() + INTERVAL  3 DAY, '10:30:00',  2, 'FAMILY',     'CONFIRMED', FALSE, NULL,      10000, TRUE,  2005, NULL),
(3, 200000008, CURDATE() + INTERVAL  7 DAY, '11:30:00', 11, 'GROUP',      'CONFIRMED', TRUE,  200000011, 55000, TRUE,  2006, NULL),
-- ids 24..26: PENDING, awaiting confirmation (future dates).
(1, 200000009, CURDATE() + INTERVAL  4 DAY, '09:00:00',  1, 'INDIVIDUAL', 'PENDING',   FALSE, NULL,      5000,  FALSE, NULL, NULL),
(2, 200000010, CURDATE() + INTERVAL 10 DAY, '12:00:00',  4, 'FAMILY',     'PENDING',   FALSE, NULL,      16000, FALSE, NULL, NULL),
(3, 200000012, CURDATE() + INTERVAL 12 DAY, '10:00:00',  8, 'GROUP',      'PENDING',   TRUE,  200000003, 38000, FALSE, NULL, NULL),
-- ids 27..29: WAITING -> each has a waiting_list_entry; id 27 has an active grab.
(2, 200000004, CURDATE(),                   '11:00:00', 10, 'GROUP',      'WAITING',   TRUE,  200000011, 50000, FALSE, NULL, NULL),
(1, 200000002, CURDATE() + INTERVAL  2 DAY, '09:00:00',  1, 'INDIVIDUAL', 'WAITING',   FALSE, NULL,      5000,  FALSE, NULL, NULL),
(3, 200000009, CURDATE() + INTERVAL  6 DAY, '13:00:00',  3, 'FAMILY',     'WAITING',   FALSE, NULL,      13000, FALSE, NULL, NULL),
-- id 30: CONFIRMED for TODAY with NO visit row -> the no-show "run now" target.
--   The timed NoShowJob is strict (visit_date < today) and leaves it alone, so a
--   same-day no-show normally only resolves after midnight; a manual forced run
--   (visit_date <= today) marks it NO_SHOW instantly for the live defense.
(2, 200000007, CURDATE(),                   '15:00:00',  1, 'INDIVIDUAL', 'CONFIRMED', FALSE, NULL,      5000,  TRUE,  2007, NULL);

-- 7) Waiting list: one entry per WAITING reservation (27/28/29). Entry for
--    reservation 27 carries an ACTIVE grab offer (expires ~1h from now); the
--    others are plain queue positions still waiting their turn.
INSERT INTO waiting_list_entry (reservation_id, queued_at, grab_offered_at, grab_expires_at) VALUES
(27, NOW() - INTERVAL 2 HOUR, NOW(),  NOW() + INTERVAL 1 HOUR),
(28, NOW() - INTERVAL 1 DAY,  NULL,   NULL),
(29, NOW() - INTERVAL 3 HOUR, NULL,   NULL);

-- 8) Visits (25) -- spread over the past month with varied stay lengths so the
--    average-stay figure is interesting, plus a handful still OPEN today
--    (exited_at NULL) so live occupancy is non-zero in every park.
--    Reservation-backed visits leave visit_type/price_cents NULL (derived from
--    the reservation); CASUAL walk-ins (reservation_id NULL) set both.
-- 8a) Reservation-backed, CLOSED (the 8 COMPLETED reservations above).
INSERT INTO visit (reservation_id, park_id, visitor_id, entered_at, exited_at, headcount) VALUES
(1, 1, 200000001, TIMESTAMP(CURDATE() - INTERVAL 28 DAY, '09:05:00'), TIMESTAMP(CURDATE() - INTERVAL 28 DAY, '12:30:00'),  1),
(2, 1, 200000002, TIMESTAMP(CURDATE() - INTERVAL 25 DAY, '10:35:00'), TIMESTAMP(CURDATE() - INTERVAL 25 DAY, '14:50:00'),  4),
(3, 2, 200000004, TIMESTAMP(CURDATE() - INTERVAL 24 DAY, '11:10:00'), TIMESTAMP(CURDATE() - INTERVAL 24 DAY, '14:00:00'), 12),
(4, 3, 200000007, TIMESTAMP(CURDATE() - INTERVAL 21 DAY, '09:30:00'), TIMESTAMP(CURDATE() - INTERVAL 21 DAY, '13:15:00'),  1),
(5, 2, 200000005, TIMESTAMP(CURDATE() - INTERVAL 18 DAY, '10:20:00'), TIMESTAMP(CURDATE() - INTERVAL 18 DAY, '13:40:00'),  4),
(6, 1, 200000008, TIMESTAMP(CURDATE() - INTERVAL 14 DAY, '09:50:00'), TIMESTAMP(CURDATE() - INTERVAL 14 DAY, '13:30:00'), 10),
(7, 3, 200000009, TIMESTAMP(CURDATE() - INTERVAL 10 DAY, '14:05:00'), TIMESTAMP(CURDATE() - INTERVAL 10 DAY, '16:30:00'),  1),
(8, 1, 200000006, TIMESTAMP(CURDATE() - INTERVAL  7 DAY, '13:10:00'), TIMESTAMP(CURDATE() - INTERVAL  7 DAY, '17:00:00'),  3);
-- 8b) Reservation-backed, OPEN today (the 3 CONFIRMED-for-today reservations).
INSERT INTO visit (reservation_id, park_id, visitor_id, entered_at, exited_at, headcount) VALUES
(18, 1, 200000001, TIMESTAMP(CURDATE(), '09:05:00'), NULL,  1),
(19, 2, 200000005, TIMESTAMP(CURDATE(), '10:10:00'), NULL,  4),
(20, 3, 200000004, TIMESTAMP(CURDATE(), '11:15:00'), NULL, 14);
-- 8c) Casual walk-ins, CLOSED (reservation_id NULL -> visit_type + price_cents set).
INSERT INTO visit (reservation_id, park_id, visitor_id, entered_at, exited_at, headcount, visit_type, price_cents) VALUES
(NULL, 1, 200000010, TIMESTAMP(CURDATE() - INTERVAL 27 DAY, '10:00:00'), TIMESTAMP(CURDATE() - INTERVAL 27 DAY, '13:00:00'), 1, 'INDIVIDUAL',  5000),
(NULL, 2, 200000007, TIMESTAMP(CURDATE() - INTERVAL 23 DAY, '09:15:00'), TIMESTAMP(CURDATE() - INTERVAL 23 DAY, '13:15:00'), 3, 'FAMILY',     12000),
(NULL, 3, NULL,      TIMESTAMP(CURDATE() - INTERVAL 19 DAY, '11:00:00'), TIMESTAMP(CURDATE() - INTERVAL 19 DAY, '13:40:00'), 9, 'GROUP',      40000),
(NULL, 1, 200000009, TIMESTAMP(CURDATE() - INTERVAL 16 DAY, '12:00:00'), TIMESTAMP(CURDATE() - INTERVAL 16 DAY, '15:20:00'), 1, 'INDIVIDUAL',  5000),
(NULL, 2, 200000012, TIMESTAMP(CURDATE() - INTERVAL 13 DAY, '09:40:00'), TIMESTAMP(CURDATE() - INTERVAL 13 DAY, '14:00:00'), 4, 'FAMILY',     13000),
(NULL, 3, 200000008, TIMESTAMP(CURDATE() - INTERVAL 11 DAY, '15:00:00'), TIMESTAMP(CURDATE() - INTERVAL 11 DAY, '16:30:00'), 1, 'INDIVIDUAL',  5000),
(NULL, 2, 200000005, TIMESTAMP(CURDATE() - INTERVAL  6 DAY, '10:30:00'), TIMESTAMP(CURDATE() - INTERVAL  6 DAY, '14:10:00'), 4, 'FAMILY',     15000),
(NULL, 3, NULL,      TIMESTAMP(CURDATE() - INTERVAL  4 DAY, '11:20:00'), TIMESTAMP(CURDATE() - INTERVAL  4 DAY, '14:15:00'), 8, 'GROUP',      38000),
(NULL, 1, 200000002, TIMESTAMP(CURDATE() - INTERVAL  2 DAY, '09:30:00'), TIMESTAMP(CURDATE() - INTERVAL  2 DAY, '13:00:00'), 1, 'INDIVIDUAL',  5000),
(NULL, 3, 200000010, TIMESTAMP(CURDATE() - INTERVAL  1 DAY, '10:00:00'), TIMESTAMP(CURDATE() - INTERVAL  1 DAY, '14:00:00'), 3, 'FAMILY',     12000);
-- 8d) Casual walk-ins, OPEN today (exited_at NULL -> add to live occupancy).
INSERT INTO visit (reservation_id, park_id, visitor_id, entered_at, exited_at, headcount, visit_type, price_cents) VALUES
(NULL, 1, 200000011, NOW() - INTERVAL 2 HOUR, NULL, 1, 'INDIVIDUAL',  5000),
(NULL, 1, 200000007, NOW() - INTERVAL 4 HOUR, NULL, 4, 'FAMILY',     14000),
(NULL, 2, 200000012, NOW() - INTERVAL 1 HOUR, NULL, 3, 'FAMILY',     13000),
(NULL, 3, NULL,      NOW() - INTERVAL 3 HOUR, NULL, 11,'GROUP',      42000);

-- 9) parameter_change_request: 2 PENDING (approval queue is non-empty) + 2 decided
--    (one APPROVED, one REJECTED) for history. Decided rows are stamped by the
--    dept manager (user 1).
INSERT INTO parameter_change_request
    (park_id, requested_by, field, old_value, new_value, status, decided_by, created_at, decided_at) VALUES
(1, 2, 'MAX_CAPACITY',         100, 120, 'PENDING',  NULL, NOW() - INTERVAL  2 DAY, NULL),
(3, 6, 'GAP_SIZE',              15,  20, 'PENDING',  NULL, NOW() - INTERVAL  1 DAY, NULL),
(2, 3, 'DEFAULT_STAY_MINUTES', 180, 210, 'APPROVED', 1,    NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 9 DAY),
(1, 2, 'GAP_SIZE',              10,   5, 'REJECTED', 1,    NOW() - INTERVAL  8 DAY, NOW() - INTERVAL 7 DAY);

-- 9b) promotion: 1 APPROVED + active today (its window contains CURDATE(), so it
--     grants an extra discount on Galilee Park visits right now) and 1 PENDING
--     (so the dept-manager approval queue shows a promotion to decide). Park/user
--     ids reuse the seeded rows: park 1 = Galilee (defined by user 2, galilee_mgr;
--     approved by user 1, dept_mgr); park 3 = Negev (defined by user 6, negev_mgr).
INSERT INTO promotion
    (park_id, name, discount_percent, start_date, end_date, status, defined_by, approved_by, created_at, decided_at) VALUES
(1, 'Summer Special',  20, CURDATE() - INTERVAL  5 DAY, CURDATE() + INTERVAL 25 DAY, 'APPROVED', 2, 1,    NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 5 DAY),
(3, 'Autumn Preview',  15, CURDATE() + INTERVAL 10 DAY, CURDATE() + INTERVAL 40 DAY, 'PENDING',  6, NULL, NOW() - INTERVAL 1 DAY, NULL);

-- 10) notification: several rows for subscribers (who log in to the notification
--     center) and for staff. acknowledged_at NULL = still UNREAD, so the center
--     shows unread badges. sent_at is stamped where delivered.
INSERT INTO notification
    (recipient_visitor_id, recipient_user_id, channel, body, scheduled_for, sent_at, acknowledged_at) VALUES
-- Vera (registered visitor 200000001): two unread, one read.
(200000001, NULL, 'SIM_EMAIL', 'Your reservation for today is confirmed. See you at the gate!', NULL, NOW() - INTERVAL  1 DAY,  NULL),
(200000001, NULL, 'SIM_SMS',   'Reminder: your visit is scheduled for today.',                  NULL, NOW() - INTERVAL  2 HOUR, NULL),
(200000001, NULL, 'POPUP',     'Welcome back to GoNature!',                                     NULL, NOW() - INTERVAL  5 DAY,  NOW() - INTERVAL 5 DAY),
-- Sam (subscriber 200000005): one unread, one read.
(200000005, NULL, 'SIM_EMAIL', 'Your family reservation for today is confirmed.',               NULL, NOW() - INTERVAL  3 HOUR, NULL),
(200000005, NULL, 'SIM_SMS',   'Thanks for visiting Carmel Park last month!',                   NULL, NOW() - INTERVAL 18 DAY,  NOW() - INTERVAL 18 DAY),
-- Sophie (subscriber 200000006): one unread.
(200000006, NULL, 'SIM_EMAIL', 'Your reservation in 3 days is confirmed.',                      NULL, NOW() - INTERVAL  4 HOUR, NULL),
-- Greta (200000004): the active waitlist grab offer for reservation 27.
(200000004, NULL, 'SIM_SMS',   'A spot opened up for your group! Grab it within the hour.',     NULL, NOW(),                     NULL),
-- Staff notifications (recipient_user_id), mix of read/unread.
(NULL, 1, 'POPUP',     'Parameter change requests are awaiting your review.', NULL, NOW() - INTERVAL 30 MINUTE, NULL),
(NULL, 2, 'SIM_EMAIL', 'Your gap-size change request was rejected.',          NULL, NOW() - INTERVAL  7 DAY,    NOW() - INTERVAL 6 DAY),
(NULL, 3, 'SIM_EMAIL', 'Your default-stay change request was approved.',      NULL, NOW() - INTERVAL  9 DAY,    NULL);

-- active_session intentionally left empty (populated at runtime by login).
