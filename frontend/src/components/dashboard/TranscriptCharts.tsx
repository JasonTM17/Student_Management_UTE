'use client';

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

const CHART_WIDTH = 320;
const CHART_HEIGHT = 150;
const PLOT_TOP = 16;
const PLOT_BOTTOM = 118;

function formatAxisLabel(label: string) {
  return label.length > 10 ? `${label.slice(0, 9)}…` : label;
}

/**
 * Dependency-free SVG line chart for the per-semester GPA trend on the 4.0
 * scale. The scale is fixed at 0..4 so two students' charts are visually
 * comparable. The 0..10 average lives in `TenScaleTrendChart` instead of being
 * overlaid here: on a shared plot the two series sat at nearly the same height
 * and the 10-scale line was unreadable.
 */
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
  const step = activePoints.length > 1 ? (CHART_WIDTH - 48) / (activePoints.length - 1) : 0;
  const yFor = (gpa: number) =>
    PLOT_BOTTOM - (Math.min(Math.max(gpa, 0), 4) / 4) * (PLOT_BOTTOM - PLOT_TOP);
  const xFor = (index: number) =>
    activePoints.length === 1 ? CHART_WIDTH / 2 : 24 + index * step;

  const polyline = points
    .map((point, index) => `${xFor(index)},${yFor(point.gpa).toFixed(1)}`)
    .join(' ');

  const cumulativePolyline = cumulativePoints
    ? cumulativePoints
        .map((point, index) => `${xFor(index)},${yFor(point.gpa).toFixed(1)}`)
        .join(' ')
    : '';

  return (
    <div className="space-y-3">
      <svg
        viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
        className="h-auto w-full text-primary"
        role="img"
        aria-label={ariaLabel}
      >
        {[0, 1, 2, 3, 4].map((gpa) => (
          <g key={gpa}>
            <line
              x1={20}
              x2={CHART_WIDTH - 8}
              y1={yFor(gpa)}
              y2={yFor(gpa)}
              stroke="currentColor"
              strokeOpacity={gpa === 0 ? 0.35 : 0.12}
              strokeWidth={1}
            />
            <text x={2} y={yFor(gpa) + 3} fontSize={8} fill="currentColor" fillOpacity={0.6}>
              {gpa}
            </text>
          </g>
        ))}

        {/* Semester GPA line */}
        {(mode === 'semester' || mode === 'both') && points.length > 1 ? (
          <polyline
            points={polyline}
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            strokeLinejoin="round"
          />
        ) : null}

        {/* Cumulative Overall GPA line */}
        {(mode === 'cumulative' || mode === 'both') &&
        cumulativePoints &&
        cumulativePoints.length > 1 ? (
          <polyline
            points={cumulativePolyline}
            fill="none"
            stroke="rgb(16, 185, 129)"
            strokeWidth={2}
            strokeDasharray={mode === 'both' ? '4 3' : undefined}
            strokeLinejoin="round"
          />
        ) : null}

        {/* Points for active view */}
        {activePoints.map((point, index) => {
          const x = xFor(index);
          const y = yFor(point.gpa);
          const isCum = mode === 'cumulative';
          const isSelected =
            Boolean(selectedLabel) &&
            (point.label === selectedLabel ||
              point.fullLabel === selectedLabel ||
              selectedLabel?.includes(point.label));

          return (
            <g key={`${point.label}-${index}`}>
              {isSelected ? (
                <circle
                  cx={x}
                  cy={y}
                  r={6.5}
                  fill="none"
                  stroke={isCum ? 'rgb(16, 185, 129)' : 'currentColor'}
                  strokeWidth={2}
                  strokeDasharray="2 2"
                />
              ) : null}
              <circle
                cx={x}
                cy={y}
                r={isSelected ? 4.5 : 3.5}
                fill={isCum ? 'rgb(16, 185, 129)' : 'currentColor'}
              >
                <title>{`${point.fullLabel}: ${point.gpa.toFixed(2)}`}</title>
              </circle>
              <text
                x={x}
                y={y - 7}
                fontSize={9}
                textAnchor="middle"
                fill={isCum ? 'rgb(16, 185, 129)' : 'currentColor'}
                fontWeight={600}
              >
                {point.gpa.toFixed(2)}
              </text>
              <text
                x={x}
                y={CHART_HEIGHT - 4}
                fontSize={8}
                textAnchor="middle"
                fill="currentColor"
                fillOpacity={isSelected ? 1 : 0.7}
                fontWeight={isSelected ? 700 : 400}
              >
                {formatAxisLabel(point.label)}
              </text>
            </g>
          );
        })}
      </svg>

      {/* Chart Legend */}
      <div className="flex flex-wrap items-center justify-center gap-4 text-xs">
        {(mode === 'semester' || mode === 'both') && (
          <div className="flex items-center gap-1.5 text-muted-foreground">
            <span className="inline-block h-2.5 w-4 rounded bg-primary" />
            <span>GPA Học kỳ</span>
          </div>
        )}
        {(mode === 'cumulative' || mode === 'both') && cumulativePoints && (
          <div className="flex items-center gap-1.5 text-muted-foreground">
            <span className="inline-block h-2.5 w-4 rounded bg-emerald-500" />
            <span>GPA Tích lũy (Tổng hợp)</span>
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * Dependency-free SVG line chart for the per-semester 0..10 average, on its own
 * 0..10 axis. Split out of the GPA trend chart so the 4.0 lines and the 10-point
 * average are each readable instead of overlapping on one plot.
 */
export function TenScaleTrendChart({
  points,
  ariaLabel,
}: {
  points: GpaTrendPoint[];
  ariaLabel: string;
}) {
  const step = points.length > 1 ? (CHART_WIDTH - 48) / (points.length - 1) : 0;
  const yFor = (value: number) =>
    PLOT_BOTTOM - (Math.min(Math.max(value, 0), 10) / 10) * (PLOT_BOTTOM - PLOT_TOP);
  const xFor = (index: number) => (points.length === 1 ? CHART_WIDTH / 2 : 24 + index * step);
  const polyline = points
    .map((point, index) => `${xFor(index)},${yFor(point.gpa).toFixed(1)}`)
    .join(' ');

  return (
    <div className="space-y-3">
      <svg
        viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
        className="h-auto w-full text-amber-500"
        role="img"
        aria-label={ariaLabel}
      >
        {[0, 2, 4, 6, 8, 10].map((value) => (
          <g key={value}>
            <line
              x1={20}
              x2={CHART_WIDTH - 8}
              y1={yFor(value)}
              y2={yFor(value)}
              stroke="currentColor"
              strokeOpacity={value === 0 ? 0.35 : 0.12}
              strokeWidth={1}
            />
            <text x={2} y={yFor(value) + 3} fontSize={8} fill="currentColor" fillOpacity={0.6}>
              {value}
            </text>
          </g>
        ))}

        {points.length > 1 ? (
          <polyline points={polyline} fill="none" stroke="currentColor" strokeWidth={2} strokeLinejoin="round" />
        ) : null}

        {points.map((point, index) => {
          const x = xFor(index);
          const y = yFor(point.gpa);
          return (
            <g key={`${point.label}-${index}`}>
              <circle cx={x} cy={y} r={3.5} fill="currentColor">
                <title>{`${point.fullLabel}: ${point.gpa.toFixed(1)}/10`}</title>
              </circle>
              <text x={x} y={y - 7} fontSize={9} textAnchor="middle" fill="currentColor" fontWeight={600}>
                {point.gpa.toFixed(1)}
              </text>
              <text
                x={x}
                y={CHART_HEIGHT - 4}
                fontSize={8}
                textAnchor="middle"
                fill="currentColor"
                fillOpacity={0.7}
              >
                {formatAxisLabel(point.label)}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}

/**
 * Dependency-free SVG bar chart for the letter-grade distribution of the
 * currently selected transcript scope.
 */
export function GradeDistributionChart({
  buckets,
  ariaLabel,
}: {
  buckets: GradeDistributionBucket[];
  ariaLabel: string;
}) {
  const max = buckets.reduce((acc, bucket) => Math.max(acc, bucket.count), 0);
  const slot = (CHART_WIDTH - 16) / Math.max(buckets.length, 1);
  const barWidth = Math.min(34, slot * 0.6);

  return (
    <svg
      viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
      className={cn('h-auto w-full text-primary')}
      role="img"
      aria-label={ariaLabel}
    >
      {buckets.map((bucket, index) => {
        const height =
          max === 0 ? 0 : (bucket.count / max) * (PLOT_BOTTOM - PLOT_TOP);
        const x = 8 + index * slot + (slot - barWidth) / 2;
        const y = PLOT_BOTTOM - height;
        return (
          <g key={bucket.letter}>
            <rect
              x={x}
              y={y}
              width={barWidth}
              height={Math.max(height, 1)}
              rx={3}
              fill="currentColor"
              fillOpacity={0.75}
            >
              <title>{`${bucket.letter}: ${bucket.count}`}</title>
            </rect>
            <text
              x={x + barWidth / 2}
              y={y - 4}
              fontSize={9}
              textAnchor="middle"
              fill="currentColor"
            >
              {bucket.count}
            </text>
            <text
              x={x + barWidth / 2}
              y={CHART_HEIGHT - 4}
              fontSize={9}
              textAnchor="middle"
              fill="currentColor"
              fillOpacity={0.7}
            >
              {bucket.letter}
            </text>
          </g>
        );
      })}
    </svg>
  );
}
