# ENGINEERING_METHODS
Braude Course - שיטות הנדסיות
<div align="center">

<img src="https://img.shields.io/badge/-%F0%9F%8C%BF%20GoNature-228B22?style=for-the-badge&labelColor=1a5c1a&color=2d8a2d" alt="GoNature" height="50"/>

# 🌿 GoNature
### Nature Park Visit Management System

*A full-stack Java application for managing national park visits, reservations, and visitor flow*

---

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://www.java.com/)
[![Eclipse](https://img.shields.io/badge/Eclipse-IDE-2C2255?style=flat-square&logo=eclipseide&logoColor=white)](https://www.eclipse.org/)
[![MySQL](https://img.shields.io/badge/MySQL-Database-4479A1?style=flat-square&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![JavaFX](https://img.shields.io/badge/JavaFX-UI-007396?style=flat-square&logo=java&logoColor=white)](https://openjfx.io/)
[![License](https://img.shields.io/badge/License-Academic-green?style=flat-square)](LICENSE)
[![Status](https://img.shields.io/badge/Status-In%20Development-yellow?style=flat-square)]()

</div>

---

## 📖 About

**GoNature** is a distributed park visit management system developed for the *Parks and Recreation Department* as part of course **61756 – Engineering Methods for Software Development** at our college.

The system helps the department manage visitor activity across multiple nature parks — handling reservations, group visits, visitor quotas, entry control, and monthly reports — all through a centralized yet distributed client-server architecture.

---

## ✨ Features

### 🧭 For Visitors (Travelers)
| Feature | Description |
|--------|-------------|
| 📅 **Advance Booking** | Reserve a visit by date, time, park, and number of visitors |
| 👥 **Group Reservations** | Guides can book for organized groups (up to 15 participants) |
| ⏳ **Waiting List** | Join a queue if the park is full — get notified when a spot opens |
| 🔔 **Reminders** | Automatic email & SMS reminder sent 24 hours before the visit |
| 🏷️ **Subscriber Club** | Family subscriptions with exclusive discounted rates |
| ❌ **Cancellations** | Cancel or modify bookings to free up spots for others |

### 🏕️ For Park Staff
| Feature | Description |
|--------|-------------|
| 🚪 **Entry Control** | Validate visitors by ID or QR code at the gate |
| 🔢 **Live Visitor Count** | Real-time count of visitors currently inside the park |
| 💳 **Billing** | Automatic invoice generation based on visitor type and pricing model |
| 📊 **Monthly Reports** | Visitor counts by type, usage patterns, cancellation statistics |

### 🛠️ For Management
| Feature | Description |
|--------|-------------|
| 📈 **Visual Reports** | Graphical visit and cancellation reports across all parks |
| ⚙️ **Quota Control** | Set and update max visitor caps per park (with department approval) |
| 🎟️ **Promotions** | Define time-limited discounts (with department head approval) |
| 👤 **User Management** | Manage staff accounts, roles, and permissions |

---

## 💰 Pricing Model

| Visit Type | Discount | Notes |
|-----------|----------|-------|
| Individual / Family – Pre-booked | 15% off | — |
| Individual / Family – Walk-in | Full price | — |
| Organized Group – Pre-booked | 25% off | +12% for prepaid; guide enters free |
| Organized Group – Walk-in | 10% off | Guide pays full price |
| Subscriber (Club Member) | Extra 10% | Stacks with other discounts |

---

## 🏗️ Architecture

GoNature is built on a **Full-Stack Client-Server** architecture:

```
┌─────────────────────────────────────────────────────────┐
│                     GoNature System                     │
│                                                         │
│  ┌─────────────────┐        ┌──────────────────────┐    │
│  │   Front End     │◄──────►│      Back End        │    │
│  │                 │  LAN   │                      │    │
│  │  JavaFX GUI     │ TCP/IP │  Business Logic      │    │
│  │  (Workstations) │        │  MySQL Database      │    │
│  │                 │        │  Server              │    │
│  └─────────────────┘        └──────────────────────┘    │
│                                                         │
│  Phase 1: Local Network (LAN)                           │
│  Phase 2: Internet / Web / Mobile App (future)          │
└─────────────────────────────────────────────────────────┘
```

### Tech Stack
- **Language:** Java
- **UI:** JavaFX
- **IDE:** Eclipse
- **Database:** MySQL (relational)
- **Networking:** TCP/IP over LAN (client-server)
- **Communication:** Simulated SMS/Email notifications (Phase 1)

---

## 📁 Project Structure

```
GoNature/
│
├── 📂 GoNatureServer/         # Server-side application
│   ├── src/
│   │   ├── server/            # Server logic & DB connection
│   │   └── entities/          # Shared data models
│
├── 📂 GoNatureClient/         # Client-side application
│   ├── src/
│   │   ├── gui/               # JavaFX screens & controllers
│   │   ├── client/            # Client networking
│   │   └── logic/             # Client-side business rules
│
├── 📂 GoNatureCommon/         # Shared classes (entities, messages)
│
├── 📂 db/                     # SQL scripts
│   └── schema.sql
│
└── 📂 docs/                   # UML diagrams, reports
```

---

## 👥 Group 10
> 📚 **Course:** 61756 – Engineering Methods for Software Development  
> 🏫 **Semester:** Spring 2026

---

## 📌 Development Phases

- [x] **Phase 1** — Full-stack prototype over LAN with simulated notifications and QR codes
- [ ] **Phase 2** — Web access (browser) + smartphone app over the internet *(future)*

---

## 📜 Academic Integrity Notice

This project is submitted as part of an academic course. All code and design decisions are original group work. External AI assistance was used as a learning aid only. Copying or reusing this project in other submissions is a disciplinary violation under the college's academic regulations.

---

<div align="center">

🌲 *Built with care for nature and clean code* 🌲

</div>
