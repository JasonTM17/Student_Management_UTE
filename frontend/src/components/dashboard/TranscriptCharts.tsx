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
 * Dependency-free SVG line chart for the per-semester GPA trend. The GPA scale
 * is fixed at 0..4 so two students' charts are visually comparable.
 */
export function GpaTrendChart({
  points,
  ariaLabel,
}: {
  points: GpaTrendPoint[];
  ariaLabel: string;
}) {
  const step = points.length > 1 ? (CHART_WIDTH - 48) / (points.length - 1) : 0;
  const yFor = (gpa: number) =>
    PLOT_BOTTOM - (Math.min(Math.max(gpa, 0), 4) / 4) * (PLOT_BOTTOM - PLOT_TOP);
  const xFor = (index: number) =>
    points.length === 1 ? CHART_WIDTH / 2 : 24 + index * step;
  const polyline = points
    .map((point, index) => `${xFor(index)},${yFor(point.gpa).toFixed(1)}`)
    .join(' ');

  return (
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
      {points.length > 1 ? (
        <polyline
          points={polyline}
          fill="none"
          stroke="currentColor"
          strokeWidth={2}
          strokeLinejoin="round"
        />
      ) : null}
      {points.map((point, index) => {
        const x = xFor(index);
        const y = yFor(point.gpa);
        return (
          <g key={`${point.label}-${index}`}>
            <circle cx={x} cy={y} r={3.5} fill="currentColor">
              <title>{`${point.fullLabel}: ${point.gpa.toFixed(2)}`}</title>
            </circle>
            <text x={x} y={y - 7} fontSize={9} textAnchor="middle" fill="currentColor">
              {point.gpa.toFixed(2)}
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
