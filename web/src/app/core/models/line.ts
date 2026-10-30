import type { components } from '../api/schema';

/** A production line as the MES returns it. `referenceMachineId` is null until a reference machine is set. */
export type Line = components['schemas']['LineResponse'];
