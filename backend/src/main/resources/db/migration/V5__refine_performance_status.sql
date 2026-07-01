ALTER TABLE performance
    ADD COLUMN sales_status VARCHAR(30) NOT NULL DEFAULT 'BEFORE_SALE';

UPDATE performance
SET sales_status = CASE status
    WHEN 'ON_SALE' THEN 'ON_SALE'
    WHEN 'SALES_CLOSED' THEN 'CLOSED'
    WHEN 'CANCELLED' THEN 'CLOSED'
    ELSE 'BEFORE_SALE'
END;

UPDATE performance
SET status = CASE status
    WHEN 'CANCELLED' THEN 'CANCELLED'
    ELSE 'SCHEDULED'
END;

ALTER TABLE performance
    ALTER COLUMN sales_status DROP DEFAULT,
    DROP CONSTRAINT ck_performance_status,
    ADD CONSTRAINT ck_performance_status CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    ADD CONSTRAINT ck_performance_sales_status CHECK (sales_status IN ('BEFORE_SALE', 'ON_SALE', 'CLOSED'));

UPDATE performance_seat
SET status = 'AVAILABLE'
WHERE status = 'CLAIMING';

ALTER TABLE performance_seat
    RENAME COLUMN hold_owner_id TO hold_member_id;

ALTER TABLE performance_seat
    DROP CONSTRAINT ck_performance_seat_status,
    ADD CONSTRAINT ck_performance_seat_status CHECK (status IN ('AVAILABLE', 'HELD', 'RESERVED'));
