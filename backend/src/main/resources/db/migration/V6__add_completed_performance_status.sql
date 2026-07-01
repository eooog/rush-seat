ALTER TABLE performance
    DROP CONSTRAINT ck_performance_status,
    ADD CONSTRAINT ck_performance_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED'));
