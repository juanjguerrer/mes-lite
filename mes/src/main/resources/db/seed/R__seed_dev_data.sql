-- Development seed data. Loaded only with the "dev" profile (see application-dev.yaml).
-- Repeatable and idempotent: Flyway re-runs it whenever this file changes.
-- Keep it in sync with the schema when columns are added.
-- Contents: lines, machines, state events (v0.2), shifts, reference machines and production counts (v0.3).

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

-- Shifts (v0.3): three 8 h shifts on ASM and PACK, in plant local time. SPARE has none.
INSERT INTO shifts (line_id, code, name, start_time, end_time)
SELECT l.id, s.code, s.name, s.start_time, s.end_time
FROM (VALUES
    ('A', 'Morning',   TIME '06:00', TIME '14:00'),
    ('B', 'Afternoon', TIME '14:00', TIME '22:00'),
    ('C', 'Night',     TIME '22:00', TIME '06:00')
) AS s(code, name, start_time, end_time)
CROSS JOIN production_lines l
WHERE l.code IN ('ASM', 'PACK')
ON CONFLICT (line_id, code) DO NOTHING;

-- Reference machines for the line OEE: the bottleneck of each line.
UPDATE production_lines pl
SET reference_machine_id = m.id
FROM machines m
WHERE m.line_id = pl.id
  AND ((pl.code = 'ASM' AND m.code = 'ROBOT-01') OR (pl.code = 'PACK' AND m.code = 'CAPPER-01'))
  AND pl.reference_machine_id IS NULL;

-- Production counts (v0.3): one count per machine and full hour, derived from the RUNNING time
-- of the state events above, at 92% of the ideal speed with 3% scrap. Machines without an ideal
-- cycle time assume 20 s per piece. event_id is deterministic (md5 of machine and hour), so re-runs
-- only add the hours that completed since the previous run.
WITH segments AS (
    SELECT e.machine_id, e.state, e.occurred_at AS seg_start,
           COALESCE(LEAD(e.occurred_at) OVER (PARTITION BY e.machine_id ORDER BY e.occurred_at, e.id),
                    date_trunc('hour', now())) AS seg_end
    FROM machine_state_events e
),
hours AS (
    SELECT generate_series(
               (SELECT date_trunc('hour', min(seg_start)) FROM segments),
               date_trunc('hour', now()) - INTERVAL '1 hour',
               INTERVAL '1 hour') AS hour_start
),
running AS (
    SELECT s.machine_id, h.hour_start,
           SUM(EXTRACT(EPOCH FROM (LEAST(s.seg_end, h.hour_start + INTERVAL '1 hour') - GREATEST(s.seg_start, h.hour_start))) * 1000) AS running_ms
    FROM segments s
    JOIN hours h ON s.seg_start < h.hour_start + INTERVAL '1 hour' AND s.seg_end > h.hour_start
    WHERE s.state = 'RUNNING'
    GROUP BY s.machine_id, h.hour_start
),
pieces AS (
    SELECT r.machine_id, r.hour_start, m.code,
           FLOOR(r.running_ms / COALESCE(m.ideal_cycle_time_ms, 20000) * 0.92)::BIGINT AS total
    FROM running r
    JOIN machines m ON m.id = r.machine_id
)
INSERT INTO production_counts (machine_id, event_id, good_count, scrap_count, interval_start, interval_end)
SELECT p.machine_id,
       md5('count/' || p.machine_id || '/' || p.hour_start)::uuid,
       p.total - FLOOR(p.total * 0.03)::BIGINT,
       FLOOR(p.total * 0.03)::BIGINT,
       p.hour_start,
       p.hour_start + INTERVAL '1 hour'
FROM pieces p
WHERE p.total > 0
ON CONFLICT (event_id) DO NOTHING;
