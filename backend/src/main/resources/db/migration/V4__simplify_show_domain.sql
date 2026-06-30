ALTER TABLE performer
    DROP CONSTRAINT ck_performer_type,
    DROP COLUMN type;

ALTER TABLE show_info
    DROP COLUMN category;

ALTER TABLE show_performer
    DROP CONSTRAINT show_performer_pkey;

CREATE SEQUENCE show_performer_id_seq;

ALTER TABLE show_performer
    ADD COLUMN id BIGINT;

UPDATE show_performer
SET id = nextval('show_performer_id_seq');

ALTER TABLE show_performer
    ALTER COLUMN id SET DEFAULT nextval('show_performer_id_seq'),
    ALTER COLUMN id SET NOT NULL,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD CONSTRAINT pk_show_performer PRIMARY KEY (id),
    ADD CONSTRAINT ck_show_performer_display_order CHECK (display_order >= 0);

ALTER SEQUENCE show_performer_id_seq
OWNED BY show_performer.id;

SELECT setval(
    'show_performer_id_seq',
    COALESCE((SELECT MAX(id) FROM show_performer), 0) + 1,
    false
);

CREATE UNIQUE INDEX uq_show_performer
ON show_performer(show_id, performer_id);
