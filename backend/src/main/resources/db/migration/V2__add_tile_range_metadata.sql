ALTER TABLE tile
    DROP COLUMN version,
    ADD COLUMN row_start_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN row_end_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN column_start_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN column_end_no INTEGER NOT NULL DEFAULT 1;

ALTER TABLE tile
    ADD CONSTRAINT ck_tile_row_range CHECK (
        row_start_no > 0
        AND row_end_no >= row_start_no
    ),
    ADD CONSTRAINT ck_tile_column_range CHECK (
        column_start_no > 0
        AND column_end_no >= column_start_no
    );

ALTER TABLE tile
    ALTER COLUMN row_start_no DROP DEFAULT,
    ALTER COLUMN row_end_no DROP DEFAULT,
    ALTER COLUMN column_start_no DROP DEFAULT,
    ALTER COLUMN column_end_no DROP DEFAULT;

ALTER TABLE seat
    ADD CONSTRAINT ck_seat_row_no CHECK (row_no > 0),
    ADD CONSTRAINT ck_seat_column_no CHECK (column_no > 0);
