<div align="center">

# 🌿 GoNature — Nature Park Visit Management System

> Course 61756 – Engineering Methods for Software Development · Spring 2026 · Group 10

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://www.java.com/)
[![JavaFX](https://img.shields.io/badge/JavaFX-UI-007396?style=flat-square&logo=java&logoColor=white)](https://openjfx.io/)
[![MySQL](https://img.shields.io/badge/MySQL-9.x-4479A1?style=flat-square&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Status](https://img.shields.io/badge/Status-Prototype-yellow?style=flat-square)]()

</div>

---

A distributed client-server prototype for managing national park visits. The final system will support advance bookings, group reservations, visitor quotas, entry control, and monthly reports — this submission covers the **25% prototype** milestone.

---

## Current features

- **Read** — client looks up an existing order by its `order_number`
- **Update** — client edits an existing order's `order_date` and `number_of_visitors`
- **Insert** — client creates a new booking; server assigns the next order number and a confirmation code
- **Server GUI** — shows the connected client's IP, host name, and live connection status
- **Verification** — every write is checked directly in MySQL Workbench (bypassing the prototype) to confirm it persisted

Additional convenience screens (Dashboard, History) show mock data — they're UI placeholders for the full system, not part of the prototype operations.

---

## Architecture

```
JavaFX Client  ◄──── TCP socket ────►  Java Server  ◄── JDBC ──►  MySQL
        (login → main UI)   (port 5555)        (GUI: status, clients, log)
```

Standard Boundary–Control–Entity layering:

| Layer | Package | Responsibility |
|---|---|---|
| Boundary | `client.boundary`, `server.boundary` | JavaFX GUI (login + main on the client, controls + clients table + log on the server) |
| Net | `client.net`, `server.net` | Socket lifecycle, message dispatch (`RequestRouter` switches on `RequestType`) |
| Control | `server.control` | Business-logic layer between dispatcher and DAO |
| DAO | `server.dao` | All SQL — `OrderDAO` |
| DB | `server.db` | JDBC connection management |
| Common | `common.dto` | Shared `ClientRequest`, `ServerResponse`, `OrderDTO`, `RequestType` |

---

## Project structure

```
GoNaturePrototype/
├── src/
│   ├── client/
│   │   ├── boundary/GoNatureClientFX.java   ← JavaFX client (login + main UI)
│   │   └── net/ClientConnection.java        ← persistent socket
│   ├── common/dto/
│   │   ├── ClientRequest.java
│   │   ├── ServerResponse.java
│   │   ├── OrderDTO.java
│   │   └── RequestType.java                 ← PING, GET_ORDER, UPDATE_ORDER, INSERT_ORDER
│   └── server/
│       ├── app/StartServer.java             ← launches the FX GUI
│       ├── boundary/ServerGUI.java          ← controls, clients table, activity log
│       ├── net/
│       │   ├── OrderServer.java             ← accept loop + per-client read loop
│       │   ├── ServerListener.java          ← event interface for the GUI
│       │   └── RequestRouter.java           ← dispatch on RequestType
│       ├── control/OrderController.java
│       ├── dao/OrderDAO.java                ← all SQL lives here
│       └── db/DBConnection.java
├── lib/mysql-connector-j-9.6.0.jar          ← JDBC driver, bundled
├── dist/                                    ← built JARs (committed for convenience)
│   ├── GoNatureServer.jar
│   └── GoNatureClient.jar
├── launchers/                               ← double-click launchers
│   ├── Run Server.command   (macOS)
│   ├── Run Server.bat       (Windows)
│   ├── Run Client.command
│   └── Run Client.bat
├── build_jars.sh                            ← rebuilds the JARs from source
└── setup.sql                                ← creates database + Order table
```

---

## Running

### Requirements

- **JDK:** Liberica JDK 21 Full (bundles JavaFX). Download from [bell-sw.com/pages/downloads/](https://bell-sw.com/pages/downloads/) — pick "JDK 21 LTS" → "Full" → your OS.
- **Database:** MySQL 8.0 or 9.x running locally on port 3306.

### One-time setup (server machine only)

Create the database and seed three sample orders:
```bash
mysql -u root -p --host=127.0.0.1 < GoNaturePrototype/setup.sql
```

### Start the server (one machine)

Double-click `GoNaturePrototype/launchers/Run Server.command` (macOS) or `Run Server.bat` (Windows).

In the server window:
1. Enter the MySQL root password
2. Click **Start Server**
3. Note the IP shown under **REACHABLE AT** — clients on the same network connect to this address

### Start the client (any machine on the same network)

Double-click `GoNaturePrototype/launchers/Run Client.command` (macOS) or `Run Client.bat` (Windows).

In the login window:
1. **Server Host** — the IP from the server's REACHABLE AT (or `localhost` if running on the same machine)
2. **Port** — `5555`
3. Click **Connect**

The login window closes and the main UI appears. The sidebar shows the current connection status; the server's Connected Clients table now shows your machine's IP and host name with status **Connected**.

---

## Database schema

```sql
CREATE TABLE `Order` (
    order_number          INT PRIMARY KEY,
    order_date            DATE NOT NULL,
    number_of_visitors    INT  NOT NULL,
    confirmation_code     INT  NOT NULL,
    subscriber_id         INT  NOT NULL,
    date_of_placing_order DATE NOT NULL
);
```

---

## Planned pricing model

| Visit Type | Discount |
|---|---|
| Individual / Family – Pre-booked | 15% off |
| Individual / Family – Walk-in | Full price |
| Organized Group – Pre-booked | 25% off (+12% if prepaid, guide free) |
| Organized Group – Walk-in | 10% off |
| Subscriber Club Member | Extra 10% on top |

---

## Academic Integrity

This project is submitted as part of an academic course. All code and design decisions are original group work. Copying or reusing this project in other submissions violates the college's academic regulations.

---

<div align="center">

*Built with care for nature and clean code 🌲*

</div>
