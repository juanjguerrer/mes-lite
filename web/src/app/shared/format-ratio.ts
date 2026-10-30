const percentFormat = new Intl.NumberFormat('en-GB', {
  style: 'percent',
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

/** "87.3%" from a ratio such as 0.8734; "—" when the MES could not compute it (null). */
export function formatRatio(ratio: number | null): string {
  return ratio === null ? '—' : percentFormat.format(ratio);
}
