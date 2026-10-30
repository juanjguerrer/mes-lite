import type { components } from '../api/schema';

/** A production as the MES returns it. */
export type HourlyProduction = components['schemas']['HourlyProductionResponse'];

export type ProductionCount = components['schemas']['ProductionCountResponse'];

export type HourlyBucket = components['schemas']['Bucket'];
