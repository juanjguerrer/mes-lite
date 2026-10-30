import type { EChartsCoreOption } from 'echarts/core';
import { HourlyBucket } from '../../core/models/production';
import { formatPlantTime } from '../../shared/plant-time';

const GOOD_COLOR = '#2e9e4f';
const SCRAP_COLOR = '#d32f2f';

/** Stacked columns, one per plant-local hour: good pieces and scrap. */
export function hourlyOptions(buckets: HourlyBucket[]): EChartsCoreOption {
  return {
    animation: false,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0 },
    grid: { top: 40, left: 8, right: 24, bottom: 8, containLabel: true },
    xAxis: { type: 'category', data: buckets.map((b) => formatPlantTime(b.bucketStart)) },
    yAxis: { type: 'value', name: 'pieces' },
    series: [
      {
        name: 'Good',
        type: 'bar',
        stack: 'pieces',
        itemStyle: { color: GOOD_COLOR },
        data: buckets.map((b) => b.goodCount),
      },
      {
        name: 'Scrap',
        type: 'bar',
        stack: 'pieces',
        itemStyle: { color: SCRAP_COLOR },
        data: buckets.map((b) => b.scrapCount),
      },
    ],
  };
}
