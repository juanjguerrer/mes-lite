import { hourlyOptions } from './chart-options';

interface Options {
  xAxis: { data: string[] };
  series: { name: string; stack: string; data: number[] }[];
}

describe('hourlyOptions', () => {
  // 12:00Z and 13:00Z are 06:00 and 07:00 in the plant (UTC-6).
  const buckets = [
    { bucketStart: '2026-10-28T12:00:00Z', goodCount: 50, scrapCount: 2 },
    { bucketStart: '2026-10-28T13:00:00Z', goodCount: 0, scrapCount: 0 },
  ];
  const options = hourlyOptions(buckets) as unknown as Options;

  it('labels each column with the plant-local hour', () => {
    expect(options.xAxis.data).toEqual(['06:00', '07:00']);
  });

  it('stacks good and scrap pieces', () => {
    expect(options.series.map((s) => s.name)).toEqual(['Good', 'Scrap']);
    expect(new Set(options.series.map((s) => s.stack))).toEqual(new Set(['pieces']));
    expect(options.series[0].data).toEqual([50, 0]);
    expect(options.series[1].data).toEqual([2, 0]);
  });

  it('builds empty series for no buckets', () => {
    const empty = hourlyOptions([]) as unknown as Options;

    expect(empty.xAxis.data).toEqual([]);
    expect(empty.series.every((s) => s.data.length === 0)).toBe(true);
  });
});
