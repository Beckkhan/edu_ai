-- docs/schema.sql — DDL-first contract (D11, T21)
-- Structure only, READ-ONLY: this file never carries data, INSERT/UPDATE/DELETE,
-- credentials, or connection strings. The AI receives only this DDL to understand
-- data structures; tool handlers execute SQL on the backend with credentials from
-- .env (via data-engineer).
-- Column types and nullability from information_schema discovery 2026-09-23
-- (docs/project-specification.md §6). Maintained by the architect on re-discovery.

-- =====================================================================
-- Database `fueling` (D7: three separate databases — fueling, payment, vendors)
-- =====================================================================

-- Table fuelings: the core fueling record, PARTITIONED by month in the real DB
-- (fuelings_YYYY_MM / fuelings_part_* partitions); fueling_id is TEXT (UUID).
-- Relationship: fuelings.user_id -> payments.user_id is the only verified link to
-- the payment database (D8). fuelings.vendor_fueling_order_id <-> vendor tables:
-- 0 matches (unverified link, spec §6).
CREATE TABLE fuelings (
    fueling_id              text PRIMARY KEY,
    vendor_fueling_order_id text NULL,
    user_id                 text,
    status                  text,
    amount                  numeric,
    fuel_type               text,
    gas_station_id          text,
    gas_pump_id             text,
    refueling_gun_id        text,
    fuel_reservation_key    text,
    created_at              numeric,
    updated_at              numeric,
    actual_amount           numeric NULL,
    vendor_transaction_date text NULL,
    failed_reason           text NULL,
    vendor_fuel_price       numeric NULL,
    fueled_orders           jsonb NULL,
    discount_fuel_price     numeric NULL,
    fueling_type            text,
    extra                   jsonb NULL,
    fueling_payment_type    text,
    finished_at             numeric NULL
);

-- =====================================================================
-- Database `payment` (D7)
-- =====================================================================

-- Table payments: user-scoped (D8 — the tool returns the fueling user's latest 10
-- payments, LIMIT 10, created_at DESC).
-- Relationship: payments.order_id is UUID-shaped but verified NOT a fueling id
-- (0/10 samples) — never join on it; fuelings.user_id -> payments.user_id is the
-- only working link.
CREATE TABLE payments (
    payment_id            text PRIMARY KEY,
    order_id              text,
    user_id               text,
    external_payment_id   text NULL,
    status                jsonb NULL,
    created_at            bigint NULL,
    updated_at            bigint NULL,
    payment_system        varchar,
    payment_method        varchar,
    payment_type          text,
    purpose               text,
    amount                numeric NULL,
    actual_amount         numeric NULL,
    card_binding_id       text NULL,
    card_binding_type     text NULL,
    sbp_subscription_id   text NULL
);

-- =====================================================================
-- Database `vendors` (D7)
-- =====================================================================

-- Table fueling_orders: vendor-side order for a fueling.
-- Relationship: fueling_orders.fueling_id = fuelings.fueling_id (verified 1:1).
CREATE TABLE fueling_orders (
    fueling_id         text PRIMARY KEY,
    volume             numeric,
    price              numeric,
    fuel_type          text,
    station_id         text,
    pump_id            text,
    created_at         bigint,
    data               jsonb,
    fuel_description   text NULL,
    brand              text
);

-- Table fueling_events: vendor-side events for a fueling.
-- Relationship: fueling_events.fueling_id = fuelings.fueling_id (verified,
-- ~4 events per fueling).
CREATE TABLE fueling_events (
    event_offset   bigint PRIMARY KEY,
    fueling_id     text,
    created_at     bigint,
    status         text,
    data           jsonb,
    brand          text
);

-- Table vendor_fueling_orders: best-effort lookup by fueling_id (D9) — the table
-- covers a subset (possibly one vendor); a valid query may return zero rows.
-- Note: one known non-hex fueling_id exists here
-- (gpn7a264-61ab-4622-983b-1ed62961a679); ids stay String end-to-end (D6).
CREATE TABLE vendor_fueling_orders (
    fueling_id             text PRIMARY KEY,
    fuel_type              text,
    quantity               numeric,
    total_sum              numeric,
    status                 text,
    gas_pump_number        numeric,
    vendor_gas_station_id  text,
    vendor_fuel_price      numeric,
    email                  text NULL,
    phone                  text NULL,
    date_create            text,
    created_at             numeric
);
