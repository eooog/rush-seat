CREATE TABLE seat_event (
    id BIGINT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE display_sector (
    event_id BIGINT NOT NULL REFERENCES seat_event(id) ON DELETE CASCADE,
    display_sector_id VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, display_sector_id)
);

CREATE TABLE subscription_tile (
    event_id BIGINT NOT NULL REFERENCES seat_event(id) ON DELETE CASCADE,
    tile_id VARCHAR(50) NOT NULL,
    display_sector_id VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, tile_id),
    FOREIGN KEY (event_id, display_sector_id)
        REFERENCES display_sector(event_id, display_sector_id)
        ON DELETE CASCADE
);

CREATE TABLE asset (
    id BIGINT PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES seat_event(id) ON DELETE CASCADE,
    display_sector_id VARCHAR(50) NOT NULL,
    tile_id VARCHAR(50) NOT NULL,
    code VARCHAR(100) NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    hold_owner_id VARCHAR(100),
    hold_token VARCHAR(120),
    hold_expires_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (event_id, display_sector_id)
        REFERENCES display_sector(event_id, display_sector_id),
    FOREIGN KEY (event_id, tile_id)
        REFERENCES subscription_tile(event_id, tile_id),
    CONSTRAINT ck_asset_status CHECK (status IN ('AVAILABLE', 'HELD', 'RESERVED'))
);

CREATE TABLE reservation (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES seat_event(id) ON DELETE CASCADE,
    asset_id BIGINT NOT NULL REFERENCES asset(id),
    user_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    hold_token VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    expires_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_reservation_status CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX idx_asset_event_tile
ON asset(event_id, tile_id);

CREATE INDEX idx_asset_event_tile_status
ON asset(event_id, tile_id, status);

CREATE INDEX idx_asset_hold_expires
ON asset(status, hold_expires_at)
WHERE status = 'HELD';

CREATE UNIQUE INDEX uq_asset_hold_token
ON asset(hold_token)
WHERE hold_token IS NOT NULL;

CREATE UNIQUE INDEX uq_reservation_idempotency
ON reservation(event_id, user_id, idempotency_key);

CREATE INDEX idx_reservation_hold_token
ON reservation(hold_token);
