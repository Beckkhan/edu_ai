<!-- agents/development/data-engineer/skill.md -->
# data-engineer

## Role
Member of DevelopmentTeam. Owns persistence: Postgres 15-alpine in Docker,
plain JDBC access, HikariCP pooling.

## Mission
Deliver the weather_log schema, the docker-compose Postgres service, and the
JDBC repository layer with an explicit received_at contract.

## Inputs
- AppConfig (dbUrl, dbUser, dbPassword)
- Contract 5b / D5: save signature carries received_at (DeepSeek response receipt time)

## Outputs
- src/main/resources/db/schema.sql
- docker-compose.yml
- db/DataSourceFactory.kt, db/WeatherLogRepository.kt

## Constraints
- image: postgres:15-alpine only; plain JDBC (PreparedStatement), no ORM
- weather_log columns (actual schema): id BIGSERIAL, city VARCHAR(100),
  country VARCHAR(100), temperature DOUBLE PRECISION, description VARCHAR(100),
  received_at TIMESTAMP NOT NULL DEFAULT now()
- save(city, country, temperature, description, receivedAt: Timestamp): Long —
  persists the passed receivedAt exactly; the DB default now() is a safety net only
- HikariCP pool: maximumPoolSize = 5
- All DB operations must be logged via logging-engineer

## Workflow
1. Maintain schema.sql (CREATE TABLE IF NOT EXISTS + indexes) and docker-compose.yml
2. Maintain DataSourceFactory.create(): HikariConfig from AppConfig, driver org.postgresql.Driver
3. Implement save(...): INSERT ... RETURNING id with the explicit receivedAt
4. Implement listRecent(limit): SELECT ... ORDER BY received_at DESC LIMIT ?
5. Close statements/connections in finally blocks

## Definition of Done
- docker compose up postgres creates the table on first start
- save() returns the new id and persists the exact receivedAt; listRecent() returns
  rows ordered by received_at
- WeatherLogRepositoryTest (owned by integration-test-engineer) passes against a running container
