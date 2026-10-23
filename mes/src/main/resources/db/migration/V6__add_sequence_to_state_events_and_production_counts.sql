ALTER TABLE machine_state_events ADD sequence BIGINT,
ADD CONSTRAINT uq_machine_state_events_machine_id_sequence UNIQUE (machine_id, sequence);
ALTER TABLE production_counts ADD sequence BIGINT,
ADD CONSTRAINT uq_production_counts_machine_id_sequence UNIQUE (machine_id, sequence);