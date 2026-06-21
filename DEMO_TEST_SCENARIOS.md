# GoNature - Demo Test Scenarios

## Scenario 1: Visitor Reservation

1. Login as a visitor.
2. Open the reservation screen.
3. Create a new reservation for an available park and time.
4. Verify that the reservation is saved successfully.
5. Verify that a simulated confirmation notification is displayed.

## Scenario 2: Family Reservation

1. Login as a subscriber.
2. Create a family visit reservation.
3. Verify that the system allows the reservation only for a registered subscriber.
4. Verify that the party size does not exceed the subscriber family size.
5. Verify that the subscriber discount is applied in the bill calculation.

## Scenario 3: Group Reservation

1. Login with a registered guide.
2. Create an organized group reservation.
3. Verify that the system checks that the guide is registered.
4. Verify that the group size is limited to 15 visitors.
5. Verify that the correct group discount is applied.
6. Verify that the guide is not charged for a pre-booked group visit.

## Scenario 4: Waiting List

1. Try to create a reservation for a time slot that has no available capacity.
2. Add the visitor to the waiting list.
3. Cancel another reservation for the same park and time.
4. Verify that the first visitor in the waiting list receives a simulated notification.
5. Confirm the waiting list offer and verify that a reservation is created.

## Scenario 5: Park Entry and Exit by Park Employee

1. Login as a park employee.
2. Open the gate screen.
3. Register visitor entry using the reservation code or visitor ID.
4. Verify that the current occupancy increases.
5. Register visitor exit manually.
6. Verify that the current occupancy decreases.

## Scenario 6: Visitor Self Exit

1. Login as a visitor.
2. Open **My Reservations**.
3. Select an active reservation that already entered the park.
4. Click the exit action.
5. Verify that the whole reservation group is marked as exited.
6. Verify that the park occupancy is updated.

## Scenario 7: Casual Visit

1. Login as a park employee.
2. Register a casual visit at the gate.
3. Verify that the system allows the visit only if the park has available capacity.
4. Verify that a casual visit ticket or numeric code is created.
5. Register exit for the casual visit and verify that occupancy is updated.

## Scenario 8: No-Show Handling

1. Use a confirmed reservation whose planned arrival time has passed.
2. Make sure no entry was registered for that reservation.
3. Run or trigger the no-show handling process.
4. Verify that the reservation status becomes `NO_SHOW`.
5. Verify that the reservation appears in the cancellations report.

## Scenario 9: Reports

1. Login as a department manager.
2. Open the visits report.
3. Verify that visits are displayed by visitor type.
4. Open the cancellations report.
5. Verify that cancelled reservations and no-show reservations are displayed.
6. Open the usage report.
7. Verify that the report displays periods or days when the park was not fully occupied.

## Scenario 10: Park Parameter Approval

1. Login as a park manager.
2. Submit a park parameter change request, such as capacity, gap, visit duration, or special discount.
3. Login as a department manager.
4. Open the approvals screen.
5. Approve or reject the request.
6. Verify that the park parameter is updated only after approval.

## Scenario 11: Role-Based Screens

1. Login as each role separately: visitor, service representative, park employee, park manager, and department manager.
2. Verify that each user sees only the screens that match their role.
3. Verify that restricted screens are not visible in the navigation menu.

## Scenario 12: Duplicate Login Prevention

1. Login with a staff user on one client.
2. Try to login with the same staff user from another client.
3. Verify that the system prevents the same user from being connected more than once at the same time.
