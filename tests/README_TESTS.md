# GoNature Tests - Mockito + Database

This folder contains JUnit 5 tests for the GoNature project.

## What was added

### Mockito unit tests
These tests check functionality without using a real database.

Covered examples:
- `ReservationController`
- `AvailabilityController`
- `CancellationController`
- `EntryController`
- `ExitController`
- `BillingController`
- `ReportController`
- `RequestRouter`
- `ParkDAO`
- `ReservationDAO`
- `VisitDAO`
- DTOs and pricing strategies

Mockito is used to:
- mock DAO objects
- mock controller dependencies
- mock JDBC `Connection`, `PreparedStatement`, and `ResultSet`
- mock static calls to `DBConnection.getConnection()`
- mock constructors for classes that create DAOs inside methods

### Database integration tests
These tests are marked with `@Disabled`.
They are intended to be enabled only when a local MySQL database is ready.

They check:
- the database connection works
- required tables exist
- seed data exists
- reservation insert/select works
- park capacity query works
- visit entry/exit flow works
- report data can be queried

## Required libraries

Use JUnit 5 and Mockito.

Recommended dependencies:
```xml
<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.2</version>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.11.0</version>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-junit-jupiter</artifactId>
        <version>5.11.0</version>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <version>8.4.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

If your Mockito version requires a separate inline artifact for static mocking, add:
```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-inline</artifactId>
    <version>5.2.0</version>
    <scope>test</scope>
</dependency>
```

## Suggested commit message

```text
Add Mockito and database tests
```

## Suggested commit description

```text
Added a tests folder with Mockito-based unit tests for controllers, request routing,
DAO database access logic, DTOs, and pricing strategies. Added disabled MySQL integration
tests for checking schema, seed data, reservation flow, entry/exit flow, and report queries.
```
