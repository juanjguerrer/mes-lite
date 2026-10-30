import type { components } from '../api/schema';
import type { MachineState } from './machine-state';

type StateDurationsResponse = components['schemas']['StateDurationsResponse'];
export type StateDurations = Omit<StateDurationsResponse, 'durationsMs'> & { durationsMs: Record<MachineState, number> };
