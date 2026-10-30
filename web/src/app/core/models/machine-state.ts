import type { components } from '../api/schema';

export type MachineState = NonNullable<components['schemas']['MachineCurrentStateResponse']['state']>;

export const MachineStates: readonly MachineState[] = ['RUNNING', 'IDLE', 'DOWN', 'SETUP', 'PLANNED_STOP'];

/** Current state of a machine; `state` and `since` are null when it has no events yet. */
export type MachineCurrentState = components['schemas']['MachineCurrentStateResponse'];

export const MachineStateColors: Record<MachineState, string> = {
  RUNNING: '#00FF00', // Green
  IDLE: '#FFFF00',    // Yellow
  DOWN: '#FF0000',    // Red
  SETUP: '#0000FF',   // Blue
  PLANNED_STOP: '#FFA500', // Orange
};

export const MachineStateLabels: Record<MachineState, string> = {
  RUNNING: 'Running',
  IDLE: 'Idle',
  DOWN: 'Down',
  SETUP: 'Setup',
  PLANNED_STOP: 'Planned stop',
};
