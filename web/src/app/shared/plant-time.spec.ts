import { latestShiftDate, plantDate } from './plant-time';

// America/Mexico_City is UTC-6 all year since 2022.
describe('plantDate', () => {
  it('uses the plant date, not the UTC date', () => {
    expect(plantDate(new Date('2026-10-29T05:30:00Z'))).toBe('2026-10-28');
  });
});

describe('latestShiftDate', () => {
  it('is today once the shift has started', () => {
    // 14:00 in the plant
    expect(latestShiftDate('06:00:00', new Date('2026-10-28T20:00:00Z'))).toBe('2026-10-28');
  });

  it('is today at the exact start time', () => {
    // 06:00 in the plant
    expect(latestShiftDate('06:00:00', new Date('2026-10-28T12:00:00Z'))).toBe('2026-10-28');
  });

  it('is yesterday when the shift starts later than now (night shift after midnight)', () => {
    // 04:00 in the plant
    expect(latestShiftDate('22:00:00', new Date('2026-10-28T10:00:00Z'))).toBe('2026-10-27');
  });

  it('is yesterday for a shift that has not started yet today', () => {
    // 14:00 in the plant
    expect(latestShiftDate('22:00:00', new Date('2026-10-28T20:00:00Z'))).toBe('2026-10-27');
  });

  it('works just after midnight in the plant', () => {
    // 00:30 in the plant, the day after the UTC date flips
    expect(latestShiftDate('22:00:00', new Date('2026-10-28T06:30:00Z'))).toBe('2026-10-27');
    expect(latestShiftDate('00:00:00', new Date('2026-10-28T06:30:00Z'))).toBe('2026-10-28');
  });
});
