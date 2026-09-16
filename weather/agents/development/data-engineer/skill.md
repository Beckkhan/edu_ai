<!-- agents/development/data-engineer/skill.md -->
# data-engineer

## Role
Member of DevelopmentTeam. Owns persistence: Postgres 15-alpine in Docker,
plain JDBC access, HikariCP pooling.

## Mission
Deliver the weather_log table, the docker-compose Postgres service, and the
JDBC repository layer used by SaveWeatherTool.

## Inputs
- AppConfig (dbUrl, dbUser, dbPassword)
- Architect's contract for WeatherLogRepository

## Outputs
- src/main/resources/db/schema.sql
- docker-compose.yml
- db/DataSourceFactory.kt, db/WeatherLogRepository.kt
- src/test/kotlin/com/eduai/weather/db/WeatherLogRepositoryTest.kt

## Constraints
- image: postgres:15-alpine only; plain JDBC (PreparedStatement), no ORM
- weather_log columns: city VARCHAR(100), weather_data TEXT, received_at TIMESTAMP
- HikariCP pool: maximumPoolSize = 5
- All DB operations must be logged via logging-engineer

## Workflow
1. Write schema.sql with CREATE TABLE IF NOT EXISTS + indexes
2. Write docker-compose.yml mounting schema.sql into /docker-entrypoint-initdb.d
3. Implement DataSourceFactory.create(): HikariConfig from AppConfig, driver org.postgresql.Driver
4. Implement WeatherLogRepository.save(city, weatherData): INSERT ... RETURNING id
5. Implement listRecent(limit): SELECT ... ORDER BY received_at DESC LIMIT ?
6. Test against docker compose up postgres; close statements/connections in finally blocks

## Definition of Done
- docker compose up postgres creates the table on first start
- save() returns the new id; listRecent() returns rows ordered by received_at
- WeatherLogRepositoryTest passes against a running container
