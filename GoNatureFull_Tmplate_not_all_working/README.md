# GoNatureImplementation

Clean Java client-server implementation foundation for the GoNature final project.

## Main entry points

- Server GUI: `server.boundary.ServerGUI`
- Server console: `server.app.StartServer`
- Client GUI: `client.app.GoNatureClientApp`

## Before running

1. Run `sql/schema.sql` in MySQL Workbench.
2. Run `sql/seed.sql` in MySQL Workbench.
3. Add MySQL Connector/J to the Eclipse Build Path.
4. Update DB credentials in `server.db.DBConfig` if needed.

## Architecture

Client Boundary -> Client Net -> Server Net -> RequestRouter -> Control -> DAO -> DBConnection -> MySQL.

The code follows ECB:
- Boundary: GUI screens.
- Control: use-case controllers.
- Entity: domain objects.
- DAO: SQL access only.

Design patterns:
- Strategy Pattern: `server.pattern.pricingStrategy`
- Factory Pattern: `server.pattern.reportFactory`
