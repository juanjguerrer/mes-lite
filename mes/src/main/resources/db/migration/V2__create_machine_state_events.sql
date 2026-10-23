CREATE TABLE machine_state_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    machine_id BIGINT NOT NULL,
    event_id UUID NOT NULL,
    state VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    reason VARCHAR(255),
    CONSTRAINT uq_machine_state_events_event_id UNIQUE (event_id),
    CONSTRAINT fk_machine_state_events_machine_id FOREIGN KEY (machine_id) REFERENCES machines (id) ON DELETE RESTRICT,
    CONSTRAINT ck_machine_state_events_state CHECK (state IN ('RUNNING', 'IDLE', 'DOWN', 'SETUP', 'PLANNED_STOP'))
);

CREATE INDEX idx_machine_state_events_machine_id_occurred_at ON machine_state_events (machine_id, occurred_at);