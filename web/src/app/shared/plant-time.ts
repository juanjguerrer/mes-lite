import { PLANT_ZONE_ID } from '../core/plant-config';

const timeFormat = new Intl.DateTimeFormat('en-GB', {
  timeZone: PLANT_ZONE_ID,
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

const dateTimeFormat = new Intl.DateTimeFormat('en-GB', {
  timeZone: PLANT_ZONE_ID,
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

// The en-CA locale prints dates as yyyy-MM-dd, the format the MES expects in `date=`.
const dateFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: PLANT_ZONE_ID,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** "14:05" in plant time, from an ISO-8601 instant. */
export function formatPlantTime(iso: string): string {
  return timeFormat.format(new Date(iso));
}

/** "22/10, 14:05" in plant time, from an ISO-8601 instant. */
export function formatPlantDateTime(iso: string): string {
  return dateTimeFormat.format(new Date(iso));
}

/** Plant-local date as yyyy-MM-dd; defaults to today, which may differ from the browser's date. */
export function plantDate(instant: Date = new Date()): string {
  return dateFormat.format(instant);
}

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * The `date` the MES expects for the most recent occurrence of a shift that has already started:
 * the shift's start date, which is yesterday when the shift starts later in the day than now
 * (a night shift after midnight, or a shift that has not started yet today).
 * `startTime` is the plant-local "HH:mm[:ss]" of the shift.
 */
export function latestShiftDate(startTime: string, now: Date = new Date()): string {
  const started = formatPlantTime(now.toISOString()) >= startTime.slice(0, 5);
  return plantDate(started ? now : new Date(now.getTime() - DAY_MS));
}
