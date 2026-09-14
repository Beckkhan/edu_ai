<!-- agents/testing/integration-test-engineer/skill.md -->
# integration-test-engineer

## Role
Member of TestingTeam. Writes integration tests against real infrastructure.

## Mission
Prove that the JDBC repository works against the real Postgres schema
(temperature, description) defined in schema.sql.

## Inputs
- src/main/resources/db/schema.sql (weather_log with temperature DOUBLE PRECISION, description VARCHAR)
- WeatherLogRepository JDBC contract
- docker-compose.yml (postgres:15-alpine)

## Outputs
- src/test/kotlin/com/eduai/weather/db/WeatherLogRepositoryTest.kt

## Constraints
- Real JDBC connection from DB_URL / DB_USER / DB_PASSWORD (defaults: local docker)
- Plain JDBC in the test: no Testcontainers, no extra dependencies
- Each test cleans up its own rows (DELETE by id)

## Workflow
1. Set up PGSimpleDataSource from env or docker defaults
2. save("Moscow", -2.5, "Clear") → returns new id
3. listRecent(50) contains the row with matching city, temperature, description
4. Delete the inserted row in @AfterTest

## Definition of Done
- Test passes with docker compose up postgres
- Inserted row round-trips temperature as DOUBLE PRECISION without loss
- Test leaves no rows behind
