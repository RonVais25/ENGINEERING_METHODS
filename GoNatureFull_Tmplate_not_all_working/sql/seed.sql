USE gonature;

INSERT INTO parks (park_id, park_name, location, maximum_capacity, reservation_gap, estimated_stay_duration, current_visitors)
VALUES
(1, 'Carmel Forest', 'North', 100, 20, 120, 0),
(2, 'Ein Gedi', 'Dead Sea', 80, 15, 90, 0),
(3, 'Banias', 'Golan', 120, 25, 150, 0);

INSERT INTO users (user_id, username, password_hash, first_name, last_name, email, role, park_id)
VALUES
(1, 'worker', '1234', 'Park', 'Worker', 'worker@gonature.com', 'PARK_WORKER', 1),
(2, 'manager', '1234', 'Park', 'Manager', 'manager@gonature.com', 'PARK_MANAGER', 1),
(3, 'department', '1234', 'Department', 'Manager', 'department@gonature.com', 'DEPARTMENT_MANAGER', NULL),
(4, 'service', '1234', 'Service', 'Representative', 'service@gonature.com', 'SERVICE_REPRESENTATIVE', NULL);

INSERT INTO visitors (visitor_id, id_number, first_name, last_name, phone, email)
VALUES
(1, '123456789', 'Demo', 'Visitor', '0501234567', 'visitor@gonature.com'),
(2, '987654321', 'Group', 'Guide', '0507654321', 'guide@gonature.com');

INSERT INTO family_subscriptions (subscription_id, visitor_id, family_members_count, payment_method)
VALUES (1, 1, 4, 'CASH');

INSERT INTO authorized_guides (guide_id, visitor_id, registered_by)
VALUES (1, 2, 4);

INSERT INTO reservations (reservation_id, visitor_id, park_id, subscription_id, guide_id, visit_date, arrival_time, number_of_visitors, visitor_type, status, qr_code)
VALUES
(1, 1, 1, NULL, NULL, CURDATE(), '10:00:00', 3, 'INDIVIDUAL_PREBOOKED', 'APPROVED', 'QR-1'),
(2, 2, 1, NULL, 1, CURDATE(), '11:00:00', 12, 'GROUP_PREBOOKED', 'APPROVED', 'QR-2');
