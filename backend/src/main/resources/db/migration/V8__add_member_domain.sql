CREATE TABLE member (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO member(id, name)
SELECT DISTINCT member_id, 'member-' || member_id::text
FROM reservation
WHERE member_id IS NOT NULL
UNION
SELECT DISTINCT hold_member_id, 'member-' || hold_member_id::text
FROM performance_seat
WHERE hold_member_id IS NOT NULL;

SELECT setval(
    pg_get_serial_sequence('member', 'id'),
    COALESCE((SELECT MAX(id) FROM member), 0) + 1,
    false
);

ALTER TABLE reservation
    DROP CONSTRAINT ck_reservation_member_id,
    ADD CONSTRAINT fk_reservation_member
        FOREIGN KEY (member_id) REFERENCES member(id);

ALTER TABLE performance_seat
    ADD CONSTRAINT fk_performance_seat_hold_member
        FOREIGN KEY (hold_member_id) REFERENCES member(id);
