<div align="center">

# 🌿 GoNature — Nature Park Visit Management System

> Course 61756 – Engineering Methods for Software Development · Spring 2026 · Group 10

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://www.java.com/)
[![JavaFX](https://img.shields.io/badge/JavaFX-UI-007396?style=flat-square&logo=java&logoColor=white)](https://openjfx.io/)
[![MySQL](https://img.shields.io/badge/MySQL-9.5-4479A1?style=flat-square&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Status](https://img.shields.io/badge/Status-In%20Development-yellow?style=flat-square)]()

</div>
---

A distributed client-server system for managing national park visits — bookings, group reservations, visitor quotas, entry control, and monthly reports.

---

## Architecture

```
JavaFX Client  →  (TCP :5555)  →  Java Server  →  (JDBC :3306)  →  MySQL
```

Single codebase, three layers:

| Layer | Package | Responsibility |
|---|---|---|
| Client | `client.boundary`, `client.net` | JavaFX UI + server communication |
| Common | `common.dto` | Shared data objects (Client ↔ Server) |
| Server | `server.net`, `server.control`, `server.dao`, `server.db` | Request handling, business logic, DB |

---

## Project Structure

```
GoNaturePrototype/
├── src/
│   ├── client/
│   │   ├── boundary/
│   │   │   └── GoNatureClientFX.java   ← JavaFX client 
│   │   └── net/
│   │       └── ClientConnection.java
│   ├── common/dto/
│   │   ├── ClientRequest.java
│   │   ├── ServerResponse.java
│   │   ├── OrderDTO.java
│   │   └── RequestType.java
│   └── server/
│       ├── app/StartServer.java
│       ├── net/
│       │   ├── OrderServer.java
│       │   └── RequestRouter.java
│       ├── control/OrderController.java
│       ├── dao/OrderDAO.java
│       └── db/DBConnection.java
├── setup.sql          ← creates DB and seeds dummy data
├── run_server.sh
└── run_client_fx.sh
```

---

## Running Locally

**Requirements:** Liberica JDK 21 Full, MySQL 9.x

**1. Start MySQL** (MySQL Server locally)

**2. Set up the database** (once per machine)
```bash
mysql -u root -p --host=127.0.0.1 < setup.sql
```

**3. Open two terminals**
```bash
# Terminal 1 — server
./run_server.sh yourpassword

# Terminal 2 — client
./run_client_fx.sh
```

The DB password is passed at runtime and never stored in code.

---

## Client Screens

| Screen | Description |
|---|---|
| Dashboard | Recent orders and subscriber stats |
| Get Order | Look up any order by number |
| Update Order | Step-by-step edit flow with live result panel |
| New Booking | Submit a new park visit reservation |
| History | Full order table for the subscriber |

---

## Features

**Visitors**
- Advance booking by date and number of visitors
- Group reservations for organized tours
- Waiting list with automatic notifications
- Subscriber club with discounted rates
- Booking cancellation and modification

**Park Staff**
- Entry validation by ID or confirmation code
- Live visitor count
- Automatic billing and invoice generation
- Monthly usage and cancellation reports

**Management**
- Visual reports across all parks
- Visitor quota control per park
- Time-limited promotions
- Staff account and role management

---

## Pricing

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


