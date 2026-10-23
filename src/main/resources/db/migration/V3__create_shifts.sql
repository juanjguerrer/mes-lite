CREATE TABLE shifts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    line_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_shifts_line_id_code UNIQUE (line_id, code),
    CONSTRAINT ck_shifts_times_differ CHECK (start_time <> end_time),
    CONSTRAINT fk_shifts_line_id FOREIGN KEY (line_id) REFERENCES production_lines (id) ON DELETE RESTRICT
);