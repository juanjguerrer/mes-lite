import { StateDurations } from '../../core/models/state-durations';
import { DurationsRow, durationsOptions } from './chart-options';

const machine = (id: number, code: string) =>
  ({ id, code, name: code }) as DurationsRow['machine'];

const durations = (durationsMs: Partial<StateDurations['durationsMs']>, unknownMs = 0): StateDurations => ({
  machineId: 1,
  from: '2026-10-28T12:00:00Z',
  to: '2026-10-28T20:00:00Z',
  durationsMs: { RUNNING: 0, IDLE: 0, DOWN: 0, SETUP: 0, PLANNED_STOP: 0, ...durationsMs },
  unknownMs,
});

interface Series {
  name: string;
  stack: string;
  data: number[];
  itemStyle: { color: string };
}

function seriesOf(rows: DurationsRow[]): Series[] {
  return (durationsOptions(rows) as { series: Series[] }).series;
}

describe('durationsOptions', () => {
  const rows: DurationsRow[] = [
    { machine: machine(1, 'ROBOT-01'), durations: durations({ RUNNING: 90 * 60_000, DOWN: 30_000 }, 5 * 60_000) },
    { machine: machine(2, 'ROBOT-02'), durations: null },
  ];

  it('has one stacked series per state plus the time without data', () => {
    const series = seriesOf(rows);

    expect(series.map((s) => s.name)).toEqual([
      'Running',
      'Idle',
      'Down',
      'Setup',
      'Planned stop',
      'No data',
    ]);
    expect(new Set(series.map((s) => s.stack))).toEqual(new Set(['time']));
  });

  it('converts milliseconds to minutes, one value per machine', () => {
    const series = seriesOf(rows);
    const byName = (name: string) => series.find((s) => s.name === name)?.data;

    expect(byName('Running')).toEqual([90, 0]);
    expect(byName('Down')).toEqual([0.5, 0]);
    expect(byName('No data')).toEqual([5, 0]);
  });

  it('uses the state colors of the cards', () => {
    const running = seriesOf(rows).find((s) => s.name === 'Running');

    expect(running?.itemStyle.color).toBe('#00FF00');
  });

  it('labels a machine whose call failed', () => {
    const yAxis = (durationsOptions(rows) as { yAxis: { data: string[] } }).yAxis;

    expect(yAxis.data).toEqual(['ROBOT-01', 'ROBOT-02 (failed)']);
  });

  it('builds empty series for no machines', () => {
    expect(seriesOf([]).every((s) => s.data.length === 0)).toBe(true);
  });
});
