'use client';

import {
  BarElement,
  CategoryScale,
  Chart as ChartJS,
  Filler,
  LinearScale,
  LineElement,
  PointElement,
  Tooltip,
  type ChartData,
  type ChartOptions,
} from 'chart.js';
import { Bar, Line } from 'react-chartjs-2';
import { cn } from '@/lib/utils';

export interface GpaTrendPoint {
  label: string;
  fullLabel: string;
  gpa: number;
}

export interface GradeDistributionBucket {
  letter: string;
  count: number;
}

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Filler,
  Tooltip,
);

const SEMESTER_COLOR = '#2563eb';
const CUMULATIVE_COLOR = '#10b981';
const TEN_SCALE_COLOR = '#f59e0b';
const GRID_COLOR = 'rgba(128, 128, 128, 0.16)';
const TICK_COLOR = 'rgba(128, 128, 128, 0.95)';

/** Draws the value above every point — the old SVG chart printed them inline. */
const valueLabelsPlugin = {
  id: 'valueLabels',
  afterDatasetsDraw(chart: ChartJS<'line'>) {
    const { ctx } = chart;
    chart.data.datasets.forEach((dataset, datasetIndex) => {
      const meta = chart.getDatasetMeta(datasetIndex);
      if (meta.hidden) return;
      const color = String(dataset.borderColor ?? '#333');
      ctx.save();
      ctx.fillStyle = color;
      ctx.font = '600 11px system-ui, sans-serif';
      ctx.textAlign = 'center';
      meta.data.forEach((element, index) => {
        const raw = dataset.data[index];
        if (typeof raw !== 'number') return;
        ctx.fillText(raw.toFixed(2), element.x, element.y - 8);
      });
      ctx.restore();
    });
  },
};

/**
 * The 4.0 trend used a fixed 0..4 axis, which flattened a 3.16 → 3.03 move
 * into an unreadable near-line. Chart.js gets a padded dynamic domain around
 * the actual data instead, so real movement is visible while the axis stays
 * honest (round ticks, no distortion).
 */
function paddedDomain(values: number[], fallback: [number, number], cap?: number): { min: number; max: number } {
  const real = values.filter((value) => Number.isFinite(value));
  if (real.length === 0) return { min: fallback[0], max: fallback[1] };
  const dataMin = Math.min(...real);
  const dataMax = Math.max(...real);
  let min = Math.floor((dataMin - 0.25) * 2) / 2;
  let max = Math.ceil((dataMax + 0.25) * 2) / 2;
  if (cap !== undefined) max = Math.min(max, cap);
  min = Math.max(min, 0);
  if (max - min < 0.5) max = min + 0.5;
  return { min, max };
}

function baseLineOptions(domain: { min: number; max: number }, stepSize: number): ChartOptions<'line'> {
  return {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'nearest', intersect: false },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: 'rgba(15, 23, 42, 0.92)',
        padding: 10,
        cornerRadius: 8,
        displayColors: true,
        boxWidth: 10,
        boxHeight: 10,
      },
    },
    scales: {
      x: {
        grid: { display: false },
        border: { color: GRID_COLOR },
        ticks: { color: TICK_COLOR, font: { size: 11 } },
      },
      y: {
        min: domain.min,
        max: domain.max,
        grid: { color: GRID_COLOR },
        border: { display: false },
        ticks: { color: TICK_COLOR, font: { size: 11 }, stepSize },
      },
    },
  };
}

/** Both trend charts share the same panel height so cards stay aligned. */
const CHART_HEIGHT_CLASS = 'h-60';

/**
 * Chart.js paints on a canvas, so a screen reader (or the e2e suite) sees
 * nothing of the plotted values behind the summary aria-label. Every chart
 * mirrors its plotted series into a visually-hidden table: the caption names
 * it after the chart, so the data stays reachable with assistive tech and
 * stays assertable in tests.
 */
function ChartDataTable({
  caption,
  columns,
  rows,
}: {
  caption: string;
  columns: [string, string, string];
  rows: Array<[string, string, string]>;
}) {
  return (
    // sr-only on a table element does not reliably clip — table intrinsic
    // layout can still push the document wider than the viewport (audit F06).
    // Wrap it: the div honours the 1px clip box regardless of table width.
    <div className="sr-only">
      <table>
        <caption>{caption}</caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column} scope="col">
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={`${row[0]}-${row[1]}-${index}`}>
              <th scope="row">{row[0]}</th>
              <td>{row[1]}</td>
              <td>{row[2]}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}


export function GpaTrendChart({
  points,
  cumulativePoints,
  mode = 'semester',
  selectedLabel,
  ariaLabel,
}: {
  points: GpaTrendPoint[];
  cumulativePoints?: GpaTrendPoint[];
  mode?: 'semester' | 'cumulative' | 'both';
  selectedLabel?: string;
  ariaLabel: string;
}) {
  const activePoints = mode === 'cumulative' && cumulativePoints?.length ? cumulativePoints : points;
  const labels = activePoints.map((point) => point.label);
  const fullLabels = activePoints.map((point) => point.fullLabel);

  const showSemester = mode === 'semester' || mode === 'both';
  const showCumulative =
    (mode === 'cumulative' || mode === 'both') && cumulativePoints && cumulativePoints.length > 0;

  const domainValues = [
    ...(showSemester ? points.map((point) => point.gpa) : []),
    ...(showCumulative ? (cumulativePoints ?? []).map((point) => point.gpa) : []),
  ];
  const domain = paddedDomain(domainValues, [0, 4], 4);

  const datasets: ChartData<'line'>['datasets'] = [];
  if (showSemester) {
    datasets.push({
      label: 'GPA Học kỳ',
      data: points.map((point) => point.gpa),
      borderColor: SEMESTER_COLOR,
      backgroundColor: SEMESTER_COLOR,
      borderWidth: 2.5,
      tension: 0.25,
      pointRadius: points.map((point) =>
        isSelected(point, selectedLabel) ? 6 : 4,
      ),
      pointHoverRadius: 7,
      pointBorderColor: points.map((point) =>
        isSelected(point, selectedLabel) ? SEMESTER_COLOR : 'rgba(255,255,255,0.9)',
      ),
      pointBorderWidth: points.map((point) => (isSelected(point, selectedLabel) ? 3 : 2)),
    });
  }
  if (showCumulative) {
    datasets.push({
      label: 'GPA Tích lũy (Tổng hợp)',
      data: (cumulativePoints ?? []).map((point) => point.gpa),
      borderColor: CUMULATIVE_COLOR,
      backgroundColor: CUMULATIVE_COLOR,
      borderWidth: 2.5,
      borderDash: mode === 'both' ? [6, 4] : undefined,
      tension: 0.25,
      pointRadius: 4,
      pointHoverRadius: 7,
      pointBorderColor: 'rgba(255,255,255,0.9)',
      pointBorderWidth: 2,
    });
  }

  const options = baseLineOptions(domain, 0.5);
  // Single-series view keeps the 0.01 precision; both-series view shows the
  // same precision for both lines.
  options.plugins = {
    ...options.plugins,
    tooltip: {
      ...options.plugins?.tooltip,
      callbacks: {
        title: (items) => fullLabels[items[0]?.dataIndex ?? 0] ?? '',
        label: (item) =>
          `${item.dataset.label}: ${Number(item.raw).toFixed(2)}${mode === 'both' ? '' : ' / 4.0'}`,
      },
    },
  };

  return (
    <div className="space-y-3">
      <div className={CHART_HEIGHT_CLASS} role="img" aria-label={ariaLabel}>
        {/* aria-hidden on the canvas: it is an implicit img without a name
            (axe role-img-alt); the wrapper carries the label and the data
            table below carries the values. */}
        <Line data={{ labels, datasets }} options={options} plugins={[valueLabelsPlugin]} aria-hidden="true" />
      </div>
      <ChartDataTable
        caption={ariaLabel}
        columns={['Kỳ', 'Chuỗi dữ liệu', 'Giá trị']}
        rows={datasets.flatMap((dataset) =>
          dataset.data.map((value, index) => [
            fullLabels[index] ?? labels[index] ?? '',
            String(dataset.label ?? ''),
            Number(value).toFixed(2),
          ] as [string, string, string]),
        )}
      />
      <div className="flex flex-wrap items-center justify-center gap-4 text-xs">
        {datasets.map((dataset) => (
          <div key={dataset.label} className="flex items-center gap-1.5 text-muted-foreground">
            <span
              className="inline-block h-2.5 w-4 rounded"
              style={{ backgroundColor: String(dataset.borderColor) }}
            />
            <span>{dataset.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function isSelected(point: GpaTrendPoint, selectedLabel?: string) {
  return Boolean(selectedLabel) &&
    (point.label === selectedLabel ||
      point.fullLabel === selectedLabel ||
      selectedLabel?.includes(point.label));
}

/**
 * 0..10 per-semester average on its own axis (split from the 4.0 plot so the
 * two series stay readable). Chart.js with a padded dynamic domain replaces
 * the fixed 0..10 axis that made a 7.6 → 7.5 move look flat.
 */
export function TenScaleTrendChart({
  points,
  ariaLabel,
}: {
  points: GpaTrendPoint[];
  ariaLabel: string;
}) {
  const labels = points.map((point) => point.label);
  const fullLabels = points.map((point) => point.fullLabel);
  const domain = paddedDomain(points.map((point) => point.gpa), [0, 10], 10);

  const options = baseLineOptions(domain, 0.5);
  options.plugins = {
    ...options.plugins,
    tooltip: {
      ...options.plugins?.tooltip,
      callbacks: {
        title: (items) => fullLabels[items[0]?.dataIndex ?? 0] ?? '',
        label: (item) => `${Number(item.raw).toFixed(1)} / 10`,
      },
    },
  };

  const data: ChartData<'line'> = {
    labels,
    datasets: [
      {
        label: 'ĐTB hệ 10',
        data: points.map((point) => point.gpa),
        borderColor: TEN_SCALE_COLOR,
        backgroundColor: TEN_SCALE_COLOR,
        borderWidth: 2.5,
        tension: 0.25,
        pointRadius: 4,
        pointHoverRadius: 7,
        pointBorderColor: 'rgba(255,255,255,0.9)',
        pointBorderWidth: 2,
        fill: false,
      },
    ],
  };

  return (
    <div className="space-y-3">
      <div className={CHART_HEIGHT_CLASS} role="img" aria-label={ariaLabel}>
        <Line data={data} options={options} plugins={[valueLabelsPlugin]} aria-hidden="true" />
      </div>
      <ChartDataTable
        caption={ariaLabel}
        columns={['Kỳ', 'Chuỗi dữ liệu', 'Giá trị']}
        rows={points.map((point, index) => [
          fullLabels[index] ?? labels[index] ?? '',
          'ĐTB hệ 10',
          `${Number(point.gpa).toFixed(1)}/10`,
        ] as [string, string, string])}
      />
    </div>
  );
}

/**
 * Letter-grade distribution as a Chart.js bar chart with rounded bars and
 * count labels above each bar.
 */
const countLabelsPlugin = {
  id: 'countLabels',
  afterDatasetsDraw(chart: ChartJS<'bar'>) {
    const { ctx } = chart;
    const meta = chart.getDatasetMeta(0);
    if (meta.hidden) return;
    ctx.save();
    ctx.fillStyle = '#2563eb';
    ctx.font = '600 11px system-ui, sans-serif';
    ctx.textAlign = 'center';
    meta.data.forEach((element, index) => {
      const raw = chart.data.datasets[0].data[index];
      if (typeof raw !== 'number') return;
      ctx.fillText(String(raw), element.x, element.y - 6);
    });
    ctx.restore();
  },
};

export function GradeDistributionChart({
  buckets,
  ariaLabel,
}: {
  buckets: GradeDistributionBucket[];
  ariaLabel: string;
}) {
  const max = buckets.reduce((acc, bucket) => Math.max(acc, bucket.count), 0);
  const options: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: 'rgba(15, 23, 42, 0.92)',
        padding: 10,
        cornerRadius: 8,
        callbacks: { label: (item) => `${item.parsed.y} học phần` },
      },
    },
    scales: {
      x: {
        grid: { display: false },
        border: { color: GRID_COLOR },
        ticks: { color: TICK_COLOR, font: { size: 11 } },
      },
      y: {
        beginAtZero: true,
        suggestedMax: Math.max(max + 1, 3),
        grid: { color: GRID_COLOR },
        border: { display: false },
        ticks: { color: TICK_COLOR, precision: 0 },
      },
    },
  };

  const data: ChartData<'bar'> = {
    labels: buckets.map((bucket) => bucket.letter),
    datasets: [
      {
        label: 'Số học phần',
        data: buckets.map((bucket) => bucket.count),
        backgroundColor: 'rgba(37, 99, 235, 0.78)',
        hoverBackgroundColor: '#2563eb',
        borderRadius: 6,
        maxBarThickness: 36,
      },
    ],
  };

  return (
    <div className="space-y-3">
      <div className="h-56" role="img" aria-label={ariaLabel}>
        <Bar data={data} options={options} plugins={[countLabelsPlugin]} className={cn('[&>*]:!bg-transparent')} aria-hidden="true" />
      </div>
      <ChartDataTable
        caption={ariaLabel}
        columns={['Xếp loại', 'Chuỗi dữ liệu', 'Số học phần']}
        rows={buckets.map((bucket) => [
          bucket.letter,
          'Số học phần',
          String(bucket.count),
        ] as [string, string, string])}
      />
    </div>
  );
}
