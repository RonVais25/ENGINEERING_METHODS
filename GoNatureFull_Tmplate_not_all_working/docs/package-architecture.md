# GoNature Package Architecture

Boundary -> Client Net -> Server Net -> Control -> DAO -> DB.

ECB mapping:
- Boundary: client.boundary and server.boundary.
- Control: server.control.*.
- Entity: server.entity.*.

External libraries:
- JavaFX represented by client.view and external.javafx.
- JDBC represented by server.db and external.jdbc.
- OCSF represented by client.net/server.net and external.ocsf.

Design patterns:
- Strategy: server.pattern.pricingStrategy.
- Factory: server.pattern.reportFactory.
