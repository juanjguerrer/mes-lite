import { formatShiftTime } from './plant-clock';

describe('formatShiftTime', () => {
  it('drops the seconds', () => {
    expect(formatShiftTime('06:00:00')).toBe('06:00');
    expect(formatShiftTime('22:30:00')).toBe('22:30');
  });

  it('keeps a value that already has no seconds', () => {
    expect(formatShiftTime('14:00')).toBe('14:00');
  });
});
