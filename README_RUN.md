# GoNature - Run Instructions

## Requirements

- Java JDK 21 with JavaFX support
- MySQL Server
- MySQL Connector/J
- Eclipse IDE or runnable JAR files
- Local network connection between the server computer and the client computer

## Database Setup

1. Open MySQL Workbench or another MySQL client.
2. Run the `setup.sql` file from the project.
3. Verify that the GoNature database was created successfully.
4. Verify that the demo data exists, including users, parks, visitors, subscribers, guides, reservations, and visits.

## Server Run

1. Start the server application.
2. Configure the server port.
3. Make sure the database connection details are correct.
4. Click **Start Server**.
5. Verify that the server displays that it is listening for client connections.

## Client Run

1. Start the client application.
2. Enter the server IP address and port.
3. Click **Connect**.
4. Login with one of the demo users according to the role that should be tested.

## Main Demo Flow

1. Login as a visitor and create a reservation.
2. Verify that a simulated confirmation notification is shown.
3. Login as a park employee and register park entry.
4. Verify that the current occupancy is updated.
5. Login as the visitor and record self-exit from **My Reservations**.
6. Login as a department manager and view the reports.

## Notes for Final Demo

- The system is designed to run in a client-server architecture over TCP/IP.
- Each user role can access only the screens that match its permissions.
- Email and SMS messages are simulated using system notifications or popup messages.
- QR code functionality is simulated using a numeric confirmation code.
- Actual payment is not performed inside the system; the system only calculates and displays the bill.
