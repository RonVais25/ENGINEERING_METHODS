# GoNature — Park Reservation System

A client–server system for managing nature-park visits: reservations, entry/exit,
payment, a members club, waiting lists, automated reminders, and management reports.
Built for course 61756 (Software Engineering Methods).

- **Client** — JavaFX desktop app (`client.boundary.GoNatureClientApp`)
- **Server** — TCP server with a JavaFX control console (`server.app.StartServer`)
- **Database** — MySQL (`gonature`)
- **Real-time** — the server pushes live updates to subscribed clients (a change one
  client makes appears on the others within ~1 second), and simulates email/SMS
  notifications as on-screen popups.

---

## Prerequisites

- **Liberica JDK 21 *Full*** (bundles JavaFX). The build script auto-detects it, or set
  `JAVA_HOME`. Any JDK 21 that bundles JavaFX works.
- **MySQL** running on the **server** machine.
- The MySQL Connector/J jar is included under `lib/`.

> The **client** needs only the client jar + the server's address — it talks to the
> server over TCP. **MySQL, `setup.sql`, and `.env` live on the server machine only.**

---

## Setup (server machine)

1. **Create the database + seed data:**
   ```
   mysql -u root -p < setup.sql
   ```
   This creates the `gonature` schema (12 tables) and loads a month of demo data.
   Re-run it any time to reset to a clean, known state.

2. **Configure the DB password:**
   ```
   cp .env.example .env
   ```
   Edit `.env` and set `DB_PASSWORD` to your local MySQL `root` password.
   (`.env` is gitignored; it's resolved next to the jar, in the working dir, via
   `-Dgonature.env=<path>`, or `~/.gonature.env`.)

The server connects to `jdbc:mysql://127.0.0.1:3306/gonature` as `root`.

---

## Build

```
./build_jars.sh
```
Produces:
- `dist/GoNatureServer.jar`
- `dist/GoNatureClient.jar`

(The build compiles all sources with JavaFX modules and bundles client resources +
the MySQL connector reference into the manifests.)

---

## Run

**Start the server first:**
```
java -jar dist/GoNatureServer.jar   
```
In the server console, set the port and click **Start**. (Keep `.env` next to the jar
or run from a folder that contains it.)

**Then the client(s):**
```
java -jar dist/GoNatureClient.jar    
```
Connect to the server's **host + port**, then log in.

### Two-machine setup
1. Server PC: MySQL up, `setup.sql` loaded, `.env` set, run the **server** jar.
2. Client PC(s): run the **client** jar, connect to the **server PC's IP** + port.
3. Both DBs (if any teammate also runs a server) must be rebuilt from this `setup.sql`
   — the schema has evolved, so a stale DB will throw SQL errors.

---

## Roles & login

- **Staff** log in with **username + password** (park employee, park manager,
  department manager, service representative).
- **Visitors** log in with their **national ID** only.
- The same account cannot be logged in twice at once (single session lock).

---

## Features

- **Reservations** — individual / family / organized-group bookings, with a capacity
  check, cancel, confirm, and live editing (reschedule with price re-settlement).
- **Payment** — 5-tier pricing (pre-order, group, prepay, member discounts); the
  group guide is free on pre-ordered visits, pays on casual walk-ins.
- **Members & guides** — subscriber club registration and guide registration (by a
  service rep); members get an extra discount.
- **Parks** — multi-park; park managers request parameter changes, the department
  manager approves them.
- **Gate** — entry/exit by confirmation code or ID, plus casual walk-in admission and
  exit, with live occupancy.
- **Waiting list** — join when full; a freed slot is offered FIFO with a 1-hour grab
  window; auto-advances on expiry.
- **Notifications** — simulated email/SMS shown as popups; pushed live to online
  recipients, or fetched from the notification center on next login.
- **Reports** (department manager) — visits by visitor type (chart) and cancellations
  by day (chart).

---

## Project structure

```
src/
  client/
    app/         MainApp wiring, navigation, session
    net/         TCP connection, event bus (real-time push)
    service/     async request wrapper
    view/        FXML controllers (one per screen) + shared widgets
    resources/   *.fxml + client.css
  common/dto/    request/response/event DTOs + enums (shared client+server)
  server/
    app/         server entry point
    boundary/    server control console (JavaFX)
    control/     domain controllers (auth, reservation, park, visit, report,
                 notification) + pricing
    dao/         JDBC data-access objects
    db/          DB connection + .env loader
    net/         TCP server, per-client session, request router
    scheduler/   timed jobs (reminder, confirm-timeout, grab-expiry, no-show)
    subscription/ real-time subscription registry
setup.sql        schema + seed data
build_jars.sh    build script -> dist/*.jar
.env.example     config template (copy to .env)
launchers/       Run Server / Run Client (.command / .bat)
```
