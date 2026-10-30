import { formatRatio } from './format-ratio';

describe('formatRatio', () => {
  it('formats a ratio as a percentage with one decimal', () => {
    expect(formatRatio(0.8734)).toBe('87.3%');
    expect(formatRatio(1)).toBe('100.0%');
    expect(formatRatio(0)).toBe('0.0%');
  });

  it('shows a dash when the ratio is null', () => {
    expect(formatRatio(null)).toBe('—');
  });
});
