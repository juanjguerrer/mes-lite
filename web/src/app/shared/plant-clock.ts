/**
 * "06:00" from a plant-local time of day such as "06:00:00".
 * These values are not instants: do not convert them with Date or Intl.
 */
export function formatShiftTime(time: string): string {
  return time.slice(0, 5);
}
