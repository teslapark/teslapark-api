CREATE TABLE garage (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(64)  NOT NULL,
    timezone   VARCHAR(64)  NOT NULL DEFAULT 'America/Sao_Paulo',
    currency   CHAR(3)      NOT NULL DEFAULT 'BRL',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_garage_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE garage_state (
    garage_id          BIGINT        NOT NULL,
    total_capacity     INT           NOT NULL DEFAULT 0,
    occupied_spots     INT           NOT NULL DEFAULT 0,
    occupancy_rate     DECIMAL(5, 4) NOT NULL DEFAULT 0.0000,
    closed_by_capacity BOOLEAN       NOT NULL DEFAULT FALSE,
    last_sync_at       TIMESTAMP(3)  NULL,
    config_status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    version            BIGINT        NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (garage_id),
    CONSTRAINT fk_garage_state_garage FOREIGN KEY (garage_id) REFERENCES garage (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sector (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    garage_id              BIGINT         NOT NULL,
    code                   VARCHAR(16)    NOT NULL,
    base_price             DECIMAL(10, 2) NOT NULL,
    max_capacity           INT            NOT NULL,
    open_hour              TIME           NOT NULL,
    close_hour             TIME           NOT NULL,
    duration_limit_minutes INT            NOT NULL,
    created_at             TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sector_garage_code (garage_id, code),
    CONSTRAINT fk_sector_garage FOREIGN KEY (garage_id) REFERENCES garage (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE vehicle (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    license_plate VARCHAR(16)  NOT NULL,
    first_seen_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_seen_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_vehicle_license_plate (license_plate)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE parking_spot (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    external_id        BIGINT        NOT NULL,
    sector_id          BIGINT        NOT NULL,
    lat                DECIMAL(9, 6) NOT NULL,
    lng                DECIMAL(9, 6) NOT NULL,
    occupied           BOOLEAN       NOT NULL DEFAULT FALSE,
    current_session_id BIGINT        NULL,
    created_at         TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_parking_spot_external_id (external_id),
    UNIQUE KEY uk_parking_spot_coordinates (lat, lng),
    UNIQUE KEY uk_parking_spot_current_session (current_session_id),
    KEY ix_parking_spot_sector_occupied (sector_id, occupied),
    CONSTRAINT fk_parking_spot_sector FOREIGN KEY (sector_id) REFERENCES sector (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE parking_session (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    vehicle_id              BIGINT         NOT NULL,
    license_plate           VARCHAR(16)    NOT NULL,
    sector_id               BIGINT         NULL,
    spot_id                 BIGINT         NULL,
    status                  VARCHAR(16)    NOT NULL,
    entry_time              TIMESTAMP(3)   NOT NULL,
    parked_time             TIMESTAMP(3)   NULL,
    exit_time               TIMESTAMP(3)   NULL,
    duration_minutes        INT            NULL,
    base_price_applied      DECIMAL(10, 2) NULL,
    occupancy_rate_at_entry DECIMAL(5, 4)  NOT NULL,
    price_multiplier        DECIMAL(4, 3)  NOT NULL,
    billed_hours            INT            NULL,
    amount_charged          DECIMAL(10, 2) NULL,
    currency                CHAR(3)        NOT NULL DEFAULT 'BRL',
    revenue_date            DATE           NULL,
    version                 BIGINT         NOT NULL DEFAULT 0,
    created_at              TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    active_plate            VARCHAR(16) GENERATED ALWAYS AS (
        CASE WHEN status <> 'EXITED' THEN license_plate END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_parking_session_active_plate (active_plate),
    KEY ix_parking_session_sector_revenue_date (sector_id, revenue_date),
    KEY ix_parking_session_status_entry_time (status, entry_time),
    CONSTRAINT fk_parking_session_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id) ON DELETE RESTRICT,
    CONSTRAINT fk_parking_session_sector FOREIGN KEY (sector_id) REFERENCES sector (id) ON DELETE RESTRICT,
    CONSTRAINT fk_parking_session_spot FOREIGN KEY (spot_id) REFERENCES parking_spot (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE parking_spot
    ADD CONSTRAINT fk_parking_spot_current_session
        FOREIGN KEY (current_session_id) REFERENCES parking_session (id) ON DELETE RESTRICT;

CREATE TABLE sector_daily_revenue (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    sector_id           BIGINT         NOT NULL,
    revenue_date        DATE           NOT NULL,
    total_amount        DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    sessions_count      INT            NOT NULL DEFAULT 0,
    free_sessions_count INT            NOT NULL DEFAULT 0,
    currency            CHAR(3)        NOT NULL DEFAULT 'BRL',
    updated_at          TIMESTAMP(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sector_daily_revenue_sector_date (sector_id, revenue_date),
    CONSTRAINT fk_sector_daily_revenue_sector FOREIGN KEY (sector_id) REFERENCES sector (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE webhook_event (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    idempotency_key   CHAR(64)     NOT NULL,
    event_type        VARCHAR(16)  NOT NULL,
    license_plate     VARCHAR(16)  NULL,
    session_id        BIGINT       NULL,
    event_time        TIMESTAMP(3) NULL,
    received_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at      TIMESTAMP(3) NULL,
    processing_status VARCHAR(16)  NOT NULL DEFAULT 'RECEIVED',
    raw_payload       JSON         NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_webhook_event_idempotency_key (idempotency_key),
    KEY ix_webhook_event_received_at (received_at),
    CONSTRAINT fk_webhook_event_session FOREIGN KEY (session_id) REFERENCES parking_session (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE session_anomaly (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    session_id       BIGINT       NULL,
    webhook_event_id BIGINT       NULL,
    anomaly_type     VARCHAR(32)  NOT NULL,
    description      VARCHAR(255) NULL,
    detected_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved         BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY ix_session_anomaly_type_detected_at (anomaly_type, detected_at),
    CONSTRAINT fk_session_anomaly_session FOREIGN KEY (session_id) REFERENCES parking_session (id) ON DELETE RESTRICT,
    CONSTRAINT fk_session_anomaly_webhook_event FOREIGN KEY (webhook_event_id) REFERENCES webhook_event (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
