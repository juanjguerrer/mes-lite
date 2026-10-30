import type { EChartsCoreOption } from 'echarts/core';
import { Machine } from '../../core/models/machine';
import { MachineStateColors, MachineStateLabels, MachineStates } from '../../core/models/machine-state';
import { StateDurations } from '../../core/models/state-durations';

const MS_PER_MINUTE = 60_000;
const UNKNOWN_COLOR = '#999999';

export interface DurationsRow {
  machine: Machine;
  /** null when the call for this machine failed. */
  durations: StateDurations | null;
}

const minutes = (ms: number) => Math.round((ms / MS_PER_MINUTE) * 10) / 10;

/** Stacked horizontal bars, one per machine, one segment per state plus the time without data. */
export function durationsOptions(rows: DurationsRow[]): EChartsCoreOption {
  const stateSeries = MachineStates.map((state) => ({
    name: MachineStateLabels[state],
    type: 'bar',
    stack: 'time',
    itemStyle: { color: MachineStateColors[state] },
    data: rows.map((row) => minutes(row.durations?.durationsMs[state] ?? 0)),
  }));
  const unknownSeries = {
    name: 'No data',
    type: 'bar',
    stack: 'time',
    itemStyle: { color: UNKNOWN_COLOR },
    data: rows.map((row) => minutes(row.durations?.unknownMs ?? 0)),
  };

  return {
    animation: false,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (value: unknown) => `${value} min`,
    },
    legend: { top: 0 },
    grid: { top: 40, left: 8, right: 24, bottom: 8, containLabel: true },
    xAxis: { type: 'value', name: 'min' },
    yAxis: {
      type: 'category',
      inverse: true,
      data: rows.map((row) => (row.durations ? row.machine.code : `${row.machine.code} (failed)`)),
    },
    series: [...stateSeries, unknownSeries],
  };
}
