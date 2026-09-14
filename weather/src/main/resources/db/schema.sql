-- src/main/resources/db/schema.sql (auto-applied by postgres:15-alpine on first start)
-- NOTE: init scripts run only on an EMPTY volume. After changing the schema,
-- recreate it with: docker compose down -v && docker compose up -d
DROP TABLE IF EXISTS weather_log;

CREATE TABLE IF NOT EXISTS weather_log (
    id          BIGSERIAL        PRIMARY KEY,
    city        VARCHAR(100)     NOT NULL,
    country     VARCHAR(100)     NOT NULL,
    temperature DOUBLE PRECISION NOT NULL,
    description VARCHAR(100)     NOT NULL,
    received_at TIMESTAMP        NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_weather_log_city        ON weather_log (city);
CREATE INDEX IF NOT EXISTS idx_weather_log_received_at ON weather_log (received_at DESC);
