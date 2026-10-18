CREATE TABLE production_lines (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_production_lines_code UNIQUE (code)
);

CREATE TABLE machines (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    line_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description TEXT,
    ideal_cycle_time_ms INT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_machines_line_id_code UNIQUE (line_id, code),
    CONSTRAINT fk_machines_line_id FOREIGN KEY (line_id) REFERENCES production_lines (id) ON DELETE RESTRICT,
    CONSTRAINT ck_machines_ideal_cycle_time_ms CHECK (ideal_cycle_time_ms > 0)
);