ALTER TABLE performance_seat
    ALTER COLUMN hold_member_id TYPE BIGINT
    USING CASE
        WHEN hold_member_id ~ '^[0-9]+$' THEN hold_member_id::BIGINT
        ELSE NULL
    END;

ALTER TABLE reservation
    RENAME COLUMN user_id TO member_id;

ALTER TABLE reservation
    ALTER COLUMN member_id TYPE BIGINT
    USING CASE
        WHEN member_id ~ '^[0-9]+$' THEN member_id::BIGINT
        ELSE NULL
    END;

ALTER TABLE reservation
    ADD CONSTRAINT ck_reservation_member_id CHECK (member_id > 0);
