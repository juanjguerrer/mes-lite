-- Development seed data. Loaded only with the "dev" profile (see application-dev.yaml).
-- Repeatable and idempotent: Flyway re-runs it whenever this file changes.
-- Keep it in sync with the schema when columns are added.

-- Lines (SPARE has no machines on purpose)
INSERT INTO production_lines (name, code, description) VALUES
    ('Assembly line', 'ASM', 'Final assembly and testing'),
    ('Packaging line', 'PACK', 'Filling, capping and labeling'),
    ('Spare line', 'SPARE', 'Line without machines')
ON CONFLICT (code) DO NOTHING;

-- Machines (SCREW-01 has no ideal cycle time on purpose)
INSERT INTO machines (line_id, name, code, description, ideal_cycle_time_ms)
SELECT l.id, m.name, m.code, m.description, m.ideal_cycle_time_ms
FROM (VALUES
    ('ASM',  'Robot arm 1',  'ROBOT-01',   'Pick and place',        12000),
    ('ASM',  'Robot arm 2',  'ROBOT-02',   'Pick and place',        12000),
    ('ASM',  'Final tester', 'TESTER-01',  'End of line test',       8000),
    ('ASM',  'Screwdriver',  'SCREW-01',   'Manual screw station',   NULL),
    ('PACK', 'Filler',       'FILLER-01',  'Bottle filler',          1500),
    ('PACK', 'Capper',       'CAPPER-01',  'Bottle capper',          1500),
    ('PACK', 'Labeler',      'LABELER-01', 'Label applicator',       2000)
) AS m(line_code, name, code, description, ideal_cycle_time_ms)
JOIN production_lines l ON l.code = m.line_code
ON CONFLICT (line_id, code) DO NOTHING;

-- State events, relative to the current hour (all in the past).
-- event_id is deterministic (md5 of line/machine/seq), so re-runs never duplicate events.
-- Edge cases covered:
--   ROBOT-01  full history with every state
--   ROBOT-02  first event only 5h ago (unknown time before it) + two events at the same instant
--   TESTER-01 no events at all (current state is null)
--   SCREW-01  currently DOWN
INSERT INTO machine_state_events (machine_id, event_id, state, occurred_at, reason)
SELECT m.id,
       md5(e.line_code || '/' || e.machine_code || '/' || e.seq)::uuid,
       e.state,
       date_trunc('hour', now()) - e.ago,
       e.reason
FROM (VALUES
    ('ASM',  'ROBOT-01',   1, 'SETUP',        INTERVAL '30 hours',    'Product changeover'),
    ('ASM',  'ROBOT-01',   2, 'RUNNING',      INTERVAL '29 hours',    NULL),
    ('ASM',  'ROBOT-01',   3, 'DOWN',         INTERVAL '26 hours',    'Gripper jam'),
    ('ASM',  'ROBOT-01',   4, 'RUNNING',      INTERVAL '25 hours',    NULL),
    ('ASM',  'ROBOT-01',   5, 'PLANNED_STOP', INTERVAL '22 hours',    'Shift change'),
    ('ASM',  'ROBOT-01',   6, 'RUNNING',      INTERVAL '21 hours',    NULL),
    ('ASM',  'ROBOT-01',   7, 'IDLE',         INTERVAL '10 hours',    'Waiting for material'),
    ('ASM',  'ROBOT-01',   8, 'RUNNING',      INTERVAL '9 hours',     NULL),

    ('ASM',  'ROBOT-02',   1, 'RUNNING',      INTERVAL '5 hours',     NULL),
    ('ASM',  'ROBOT-02',   2, 'DOWN',         INTERVAL '3 hours',     'Sensor fault'),
    ('ASM',  'ROBOT-02',   3, 'IDLE',         INTERVAL '1 hour',      'Waiting for operator'),
    ('ASM',  'ROBOT-02',   4, 'RUNNING',      INTERVAL '1 hour',      NULL),

    ('ASM',  'SCREW-01',   1, 'SETUP',        INTERVAL '4 hours',     NULL),
    ('ASM',  'SCREW-01',   2, 'DOWN',         INTERVAL '2 hours',     'Motor failure'),

    ('PACK', 'FILLER-01',  1, 'RUNNING',      INTERVAL '12 hours',    NULL),

    ('PACK', 'CAPPER-01',  1, 'RUNNING',      INTERVAL '12 hours',    NULL),
    ('PACK', 'CAPPER-01',  2, 'IDLE',         INTERVAL '6 hours',     'Upstream starved'),
    ('PACK', 'CAPPER-01',  3, 'RUNNING',      INTERVAL '5 hours',     NULL),

    ('PACK', 'LABELER-01', 1, 'SETUP',        INTERVAL '3 hours',     NULL),
    ('PACK', 'LABELER-01', 2, 'PLANNED_STOP', INTERVAL '1 hour',      'Preventive maintenance')
) AS e(line_code, machine_code, seq, state, ago, reason)
JOIN production_lines l ON l.code = e.line_code
JOIN machines m ON m.line_id = l.id AND m.code = e.machine_code
ORDER BY e.line_code, e.machine_code, e.seq
ON CONFLICT (event_id) DO NOTHING;
