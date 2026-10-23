CREATE TABLE production_counts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    machine_id BIGINT NOT NULL,
    event_id UUID NOT NULL,
    good_count BIGINT NOT NULL,
    scrap_count BIGINT NOT NULL,
    interval_start TIMESTAMPTZ NOT NULL,
    interval_end TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_production_counts_event_id UNIQUE (event_id),
    CONSTRAINT fk_production_counts_machine_id FOREIGN KEY (machine_id) REFERENCES machines (id) ON DELETE RESTRICT,
    CONSTRAINT ck_production_counts_interval CHECK (interval_start < interval_end),
    CONSTRAINT ck_production_counts_counts CHECK (good_count >= 0 AND scrap_count >= 0)
);
CREATE INDEX idx_production_counts_machine_id_interval_end ON production_counts (machine_id, interval_end);