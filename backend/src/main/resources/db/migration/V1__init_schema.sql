CREATE TABLE venue (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE hall (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venue(id),
    name VARCHAR(200) NOT NULL,
    capacity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_hall_capacity CHECK (capacity > 0)
);

CREATE TABLE seat_map (
    id BIGSERIAL PRIMARY KEY,
    hall_id BIGINT NOT NULL REFERENCES hall(id),
    name VARCHAR(200) NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_seat_map_version CHECK (version > 0),
    CONSTRAINT ck_seat_map_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uq_seat_map_hall_version
ON seat_map(hall_id, version);

CREATE TABLE sector (
    id BIGSERIAL PRIMARY KEY,
    seat_map_id BIGINT NOT NULL REFERENCES seat_map(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_sector_seat_map_code
ON sector(seat_map_id, code);

CREATE TABLE tile (
    id BIGSERIAL PRIMARY KEY,
    seat_map_id BIGINT NOT NULL REFERENCES seat_map(id) ON DELETE CASCADE,
    sector_id BIGINT NOT NULL REFERENCES sector(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    seat_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_tile_seat_count CHECK (seat_count >= 0)
);

CREATE UNIQUE INDEX uq_tile_seat_map_code
ON tile(seat_map_id, code);

CREATE INDEX idx_tile_sector
ON tile(sector_id);

CREATE TABLE seat (
    id BIGSERIAL PRIMARY KEY,
    seat_map_id BIGINT NOT NULL REFERENCES seat_map(id) ON DELETE CASCADE,
    sector_id BIGINT NOT NULL REFERENCES sector(id),
    tile_id BIGINT NOT NULL REFERENCES tile(id),
    code VARCHAR(100) NOT NULL,
    row_label VARCHAR(50),
    seat_no VARCHAR(50),
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_seat_seat_map_code
ON seat(seat_map_id, code);

CREATE INDEX idx_seat_seat_map_sector
ON seat(seat_map_id, sector_id);

CREATE INDEX idx_seat_seat_map_tile
ON seat(seat_map_id, tile_id);

CREATE TABLE performer (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_performer_type CHECK (type IN ('SOLO', 'GROUP', 'CAST', 'ORCHESTRA', 'ETC'))
);

CREATE TABLE show_info (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    running_minutes INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_show_running_minutes CHECK (running_minutes IS NULL OR running_minutes > 0)
);

CREATE TABLE show_performer (
    show_id BIGINT NOT NULL REFERENCES show_info(id) ON DELETE CASCADE,
    performer_id BIGINT NOT NULL REFERENCES performer(id),
    role VARCHAR(100),
    display_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (show_id, performer_id)
);

CREATE TABLE performance (
    id BIGSERIAL PRIMARY KEY,
    show_id BIGINT NOT NULL REFERENCES show_info(id),
    hall_id BIGINT NOT NULL REFERENCES hall(id),
    seat_map_id BIGINT NOT NULL REFERENCES seat_map(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    sales_open_at TIMESTAMPTZ NOT NULL,
    sales_close_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_performance_time CHECK (starts_at < ends_at),
    CONSTRAINT ck_performance_sales_time CHECK (sales_open_at < sales_close_at),
    CONSTRAINT ck_performance_status CHECK (status IN ('SCHEDULED', 'ON_SALE', 'SALES_CLOSED', 'CANCELLED'))
);

CREATE INDEX idx_performance_show
ON performance(show_id);

CREATE INDEX idx_performance_hall_starts_at
ON performance(hall_id, starts_at);

CREATE TABLE performance_seat (
    id BIGSERIAL PRIMARY KEY,
    performance_id BIGINT NOT NULL REFERENCES performance(id) ON DELETE CASCADE,
    seat_id BIGINT NOT NULL REFERENCES seat(id),
    sector_id BIGINT NOT NULL REFERENCES sector(id),
    tile_id BIGINT NOT NULL REFERENCES tile(id),
    code VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    hold_owner_id VARCHAR(100),
    hold_token VARCHAR(120),
    hold_expires_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_performance_seat_status CHECK (status IN ('AVAILABLE', 'CLAIMING', 'HELD', 'RESERVED'))
);

CREATE UNIQUE INDEX uq_performance_seat
ON performance_seat(performance_id, seat_id);

CREATE INDEX idx_performance_seat_tile
ON performance_seat(performance_id, tile_id);

CREATE INDEX idx_performance_seat_tile_status
ON performance_seat(performance_id, tile_id, status);

CREATE INDEX idx_performance_seat_sector_status
ON performance_seat(performance_id, sector_id, status);

CREATE INDEX idx_performance_seat_hold_expires
ON performance_seat(status, hold_expires_at)
WHERE status = 'HELD';

CREATE UNIQUE INDEX uq_performance_seat_hold_token
ON performance_seat(hold_token)
WHERE hold_token IS NOT NULL;

CREATE TABLE reservation (
    id BIGSERIAL PRIMARY KEY,
    performance_id BIGINT NOT NULL REFERENCES performance(id) ON DELETE CASCADE,
    performance_seat_id BIGINT NOT NULL REFERENCES performance_seat(id),
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

CREATE UNIQUE INDEX uq_reservation_idempotency
ON reservation(performance_id, user_id, idempotency_key);

CREATE UNIQUE INDEX uq_reservation_active_performance_seat
ON reservation(performance_seat_id)
WHERE status IN ('HELD', 'CONFIRMED');

CREATE INDEX idx_reservation_performance_user
ON reservation(performance_id, user_id);

CREATE INDEX idx_reservation_hold_token
ON reservation(hold_token);

CREATE INDEX idx_reservation_expires
ON reservation(status, expires_at)
WHERE status = 'HELD';
