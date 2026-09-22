'use client';

import * as React from 'react';
import { useEffect, useMemo, useState } from 'react';
import {
  BarChart3,
  GraduationCap,
  Info,
  RefreshCw,
  School,
  TrendingUp,
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/state-block';
import { Skeleton } from '@/components/ui/skeleton';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';
import {
  campusDistributionApi,
  type AdminAnalyticsOverview,
} from '@/lib/api';

export interface AdminAnalyticsData {
  totalStudents: number;
  totalLecturers: number;
  totalCourses: number;
  totalEnrollments: number;
}

interface AdminAnalyticsChartsProps {
  stats: AdminAnalyticsData;
  className?: string;
}

// Monochromatic ramp built from theme status tokens (DESIGN.md: no raw
// Tailwind palettes on shared primitives).
const FACULTY_RANK_TOKENS = ['bg-status-info', 'bg-status-info/70', 'bg-status-info/40'] as const;
const FACULTY_STROKE_TOKENS = ['stroke-status-info', 'stroke-status-info/70', 'stroke-status-info/40'] as const;
const GRADE_BAND_TOKENS = [
  'bg-status-info',
  'bg-status-info/75',
  'bg-status-info/55',
  'bg-status-info/35',
  'bg-status-danger',
] as const;

export function AdminAnalyticsCharts({ stats, className }: AdminAnalyticsChartsProps) {
  const { messages, formatNumber, locale } = useI18n();
  const copy = messages.admin.analytics;
  const isVi = locale === 'vi';
  const [activeTab, setActiveTab] = useState<'departments' | 'enrollments' | 'faculty' | 'grades'>('departments');
  const [departmentMetric, setDepartmentMetric] = useState<'students' | 'courses' | 'sections'>('students');

  // Live analytics state from backend
  const [overview, setOverview] = useState<AdminAnalyticsOverview | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const fetchOverview = React.useCallback(() => {
    setIsLoading(true);
    campusDistributionApi
      .getOverview()
      .then((data) => {
        setOverview(data);
      })
      .catch(() => {
        setOverview(null);
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, []);

  useEffect(() => {
    fetchOverview();
  }, [fetchOverview]);

  const isLive = overview !== null;

  // 1. Departments data
  const departments = useMemo(() => {
    if (!overview?.departments || overview.departments.length === 0) {
      return [];
    }
    return overview.departments.map((dept) => {
      const localizedName = (copy.departments as Record<string, string>)[dept.code] || dept.name;
      return {
        ...dept,
        name: localizedName,
      };
    });
  }, [overview, copy]);

  // 2. Faculty ranks data
  const facultyRanks = useMemo(() => {
    if (!overview?.facultyRanks || overview.facultyRanks.length === 0) {
      return [];
    }
    const total = overview.facultyRanks.reduce((sum, item) => sum + item.count, 0) || 1;
    return overview.facultyRanks.map((item, idx) => {
      let localizedRank = item.rank;
      if (item.rank === 'PROFESSOR') localizedRank = copy.facultyRanks.professor;
      else if (item.rank === 'DOCTOR' || item.rank === 'PHD') localizedRank = copy.facultyRanks.doctor;
      else if (item.rank === 'MASTER') localizedRank = copy.facultyRanks.master;
      else if (!item.rank) localizedRank = isVi ? 'Giảng viên' : 'Lecturer';

      return {
        rank: localizedRank,
        count: item.count,
        tone: FACULTY_RANK_TOKENS[idx % FACULTY_RANK_TOKENS.length],
        strokeTone: FACULTY_STROKE_TOKENS[idx % FACULTY_STROKE_TOKENS.length],
        percentage: Math.round((item.count / total) * 100),
      };
    });
  }, [overview, copy, isVi]);

  // 3. Grade distribution data
  const gradeDistribution = useMemo(() => {
    if (!overview?.gradeDistribution || overview.gradeDistribution.length === 0) {
      return [];
    }
    const total = overview.gradeDistribution.reduce((sum, item) => sum + item.count, 0) || 1;
    return overview.gradeDistribution.map((item, idx) => ({
      grade: item.grade,
      count: item.count,
      tone: GRADE_BAND_TOKENS[Math.min(idx, GRADE_BAND_TOKENS.length - 1)],
      percentage: Math.round((item.count / total) * 100),
    }));
  }, [overview]);

  const gradeTotal = gradeDistribution.reduce((sum, item) => sum + item.count, 0);
  // Count by letter membership, not by list position: the backend appends
  // unknown/legacy letters after the canonical bands, so a positional slice
  // would overcount whenever fewer bands exist.
  const TOP_GRADE_BANDS = new Set(['A+', 'A', 'A-', 'B+']);
  const atLeastGoodCount = gradeDistribution
    .filter((item) => TOP_GRADE_BANDS.has(item.grade))
    .reduce((sum, item) => sum + item.count, 0);
  const atLeastGoodPercent = gradeTotal > 0 ? Math.round((atLeastGoodCount / gradeTotal) * 100) : 0;

  // 4. Semester trends
  const semesterTrends = useMemo(() => {
    return overview?.semesterTrends || [];
  }, [overview]);

  const averageCompletionRate = useMemo(() => {
    if (semesterTrends.length === 0) return 0;
    const sum = semesterTrends.reduce((acc, item) => acc + item.completionRate, 0);
    return sum / semesterTrends.length;
  }, [semesterTrends]);

  const currentTermLoad = semesterTrends[semesterTrends.length - 1];

  const maxDeptValue = Math.max(...departments.map((d) => d[departmentMetric]), 1);
  // Scale from the data only — an invented floor renders axis labels that
  // exist in no dataset and flattens the real line to the bottom.
  const maxTrendCount = Math.max(...semesterTrends.map((s) => s.count), 1);

  return (
    <Card className={cn('border-border/80 shadow-xs', className)}>
      <CardHeader className="border-b border-border/70 pb-4">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap items-center gap-2.5">
            <CardTitle className="text-base font-semibold text-foreground">
              {copy.title}
            </CardTitle>
            {isLive ? (
              <span
                title={copy.liveNotice}
                className="inline-flex items-center gap-1.5 rounded-md bg-status-success/15 px-2.5 py-0.5 text-xs font-medium text-status-success-foreground"
              >
                <Info className="h-3 w-3" aria-hidden="true" />
                {copy.liveBadge}
              </span>
            ) : null}
          </div>

          <div className="flex items-center gap-1 overflow-x-auto rounded-lg border border-border bg-secondary/30 p-1 max-w-full sm:gap-1.5 scrollbar-none">
            <Button
              type="button"
              size="sm"
              variant={activeTab === 'departments' ? 'default' : 'ghost'}
              className="h-7 shrink-0 text-xs px-2 sm:px-2.5"
              onClick={() => setActiveTab('departments')}
            >
              <BarChart3 className="mr-1.5 h-3.5 w-3.5" />
              {copy.tabs.departments}
            </Button>
            <Button
              type="button"
              size="sm"
              variant={activeTab === 'enrollments' ? 'default' : 'ghost'}
              className="h-7 shrink-0 text-xs px-2 sm:px-2.5"
              onClick={() => setActiveTab('enrollments')}
            >
              <TrendingUp className="mr-1.5 h-3.5 w-3.5" />
              {copy.tabs.enrollments}
            </Button>
            <Button
              type="button"
              size="sm"
              variant={activeTab === 'faculty' ? 'default' : 'ghost'}
              className="h-7 shrink-0 text-xs px-2 sm:px-2.5"
              onClick={() => setActiveTab('faculty')}
            >
              <School className="mr-1.5 h-3.5 w-3.5" />
              {copy.tabs.faculty}
            </Button>
            <Button
              type="button"
              size="sm"
              variant={activeTab === 'grades' ? 'default' : 'ghost'}
              className="h-7 shrink-0 text-xs px-2 sm:px-2.5"
              onClick={() => setActiveTab('grades')}
            >
              <GraduationCap className="mr-1.5 h-3.5 w-3.5" />
              {copy.tabs.grades}
            </Button>
          </div>
        </div>
        {isLive ? (
          <p className="mt-3 rounded-md border border-status-success/30 bg-status-success/10 px-3 py-2 text-xs leading-5 text-status-success-foreground">
            {copy.liveNotice}
          </p>
        ) : null}
      </CardHeader>

      <CardContent className="p-6">
        {isLoading ? (
          <div role="status" aria-live="polite" className="space-y-4 py-4">
            <div className="flex items-center justify-between">
              <Skeleton className="h-5 w-44" />
              <Skeleton className="h-7 w-56 rounded-md" />
            </div>
            <Skeleton className="h-52 w-full rounded-xl" />
          </div>
        ) : !overview ? (
          <EmptyState
            icon={BarChart3}
            title={isVi ? 'Không có dữ liệu thống kê phân bố' : 'Distribution statistics unavailable'}
            description={
              isVi
                ? 'Dữ liệu thống kê phân bố sinh viên, giảng viên và phổ điểm từ hệ thống hiện chưa sẵn sàng.'
                : 'Live campus distribution and grade statistics are currently unavailable from the distribution service.'
            }
            action={
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={fetchOverview}
                className="gap-2"
              >
                <RefreshCw className="h-3.5 w-3.5" />
                {messages.common.actions.retry}
              </Button>
            }
          />
        ) : (
          <>
            {/* TAB 1: DEPARTMENTS */}
        {activeTab === 'departments' && (
          <div className="space-y-6">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h4 className="text-sm font-semibold text-foreground">
                  {copy.departmentsTitle}
                </h4>
              </div>
              <div className="flex items-center gap-1 rounded-md border border-border/80 bg-background p-1 text-xs">
                <button
                  type="button"
                  onClick={() => setDepartmentMetric('students')}
                  className={cn(
                    'rounded px-2.5 py-1 font-medium transition',
                    departmentMetric === 'students' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
                  )}
                >
                  {copy.metricStudents} ({formatNumber(stats.totalStudents)})
                </button>
                <button
                  type="button"
                  onClick={() => setDepartmentMetric('courses')}
                  className={cn(
                    'rounded px-2.5 py-1 font-medium transition',
                    departmentMetric === 'courses' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
                  )}
                >
                  {copy.metricCourses} ({formatNumber(stats.totalCourses)})
                </button>
                <button
                  type="button"
                  onClick={() => setDepartmentMetric('sections')}
                  className={cn(
                    'rounded px-2.5 py-1 font-medium transition',
                    departmentMetric === 'sections' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
                  )}
                >
                  {copy.metricSections}
                </button>
              </div>
            </div>

            {/* SVG Monochromatic Bar Chart */}
            {departments.length === 0 ? (
              <div className="flex h-48 items-center justify-center rounded-xl border border-dashed border-border/70 text-xs text-muted-foreground">
                {isLoading ? (isVi ? 'Đang nạp dữ liệu thống kê khoa...' : 'Loading faculty statistics...') : (isVi ? 'Chưa có dữ liệu khoa' : 'No faculty data available')}
              </div>
            ) : (
              <div className="rounded-xl border border-border/70 bg-card/60 p-4">
                <svg viewBox="0 0 800 250" className="h-auto w-full" role="img" aria-label={copy.chartAria}>
                  <defs>
                    <linearGradient id="deptMonoGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#2563eb" stopOpacity="0.95" />
                      <stop offset="100%" stopColor="#1d4ed8" stopOpacity="0.80" />
                    </linearGradient>
                  </defs>

                  {/* Horizontal grid lines */}
                  {[0, 0.25, 0.5, 0.75, 1].map((ratio) => {
                    const y = 180 - ratio * 140;
                    const val = Math.round(ratio * maxDeptValue);
                    return (
                      <g key={ratio}>
                        <line x1={40} x2={780} y1={y} y2={y} stroke="currentColor" strokeOpacity={ratio === 0 ? 0.3 : 0.08} strokeWidth={1} />
                        <text x={34} y={y + 3} fontSize={10} fill="currentColor" fillOpacity={0.5} textAnchor="end">
                          {val}
                        </text>
                      </g>
                    );
                  })}

                  {/* Uniform Monochromatic Bars for each department */}
                  {departments.map((dept, index) => {
                    const barSlot = (740 - 20) / departments.length;
                    const barWidth = Math.min(44, barSlot * 0.7);
                    const x = 50 + index * barSlot + (barSlot - barWidth) / 2;
                    const value = dept[departmentMetric];
                    const barHeight = maxDeptValue === 0 ? 0 : (value / maxDeptValue) * 140;
                    const y = 180 - barHeight;
                    const unitLabel =
                      departmentMetric === 'students'
                        ? copy.studentsShort
                        : departmentMetric === 'courses'
                          ? copy.coursesShort
                          : copy.sectionsShort;

                    return (
                      <g key={dept.code} className="group cursor-pointer">
                        <rect
                          x={x}
                          y={y}
                          width={barWidth}
                          height={Math.max(barHeight, 2)}
                          rx={4}
                          fill="url(#deptMonoGradient)"
                          className="transition-all duration-200 group-hover:brightness-115 group-hover:opacity-100"
                        >
                          <title>{`${dept.name} (${dept.code}): ${formatNumber(value)} ${unitLabel}`}</title>
                        </rect>
                        {/* Value label on top of bar */}
                        <text
                          x={x + barWidth / 2}
                          y={y - 6}
                          fontSize={11}
                          fontWeight={600}
                          fill="currentColor"
                          textAnchor="middle"
                          className="transition-all group-hover:font-bold"
                        >
                          {value}
                        </text>
                        {/* Department code — rotated so long codes like
                            AUTO-ENG never collide at 16+ departments */}
                        <text
                          x={x + barWidth / 2}
                          y={206}
                          fontSize={10}
                          fontWeight={600}
                          fill="currentColor"
                          fillOpacity={0.9}
                          textAnchor="end"
                          transform={`rotate(-38 ${x + barWidth / 2} 206)`}
                        >
                          {dept.code}
                        </text>
                      </g>
                    );
                  })}
                </svg>
              </div>
            )}

            {/* Department stats summary tags - Monochromatic clean badges */}
            {departments.length > 0 && (
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                {departments.map((dept) => (
                  <div key={dept.code} className="flex items-center gap-2.5 rounded-lg border border-border/60 bg-secondary/20 p-2.5">
                    <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-primary/10 text-[11px] font-bold text-primary">
                      {dept.code}
                    </span>
                    <div className="min-w-0 flex-1 text-xs">
                      <p className="font-semibold text-foreground truncate">{dept.name}</p>
                      <p className="text-muted-foreground">
                        {formatNumber(dept.students)} {copy.studentShortBadge} &bull;{' '}
                        {formatNumber(dept.courses)} {copy.courseShortBadge}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* TAB 2: ENROLLMENT TRENDS */}
        {activeTab === 'enrollments' && (
          <div className="space-y-6">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h4 className="text-sm font-semibold text-foreground">
                  {copy.enrollmentsTitle}
                </h4>
              </div>
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span className="h-2 w-2 rounded-full bg-primary" />
                <span>{copy.enrollmentLegend}</span>
              </div>
            </div>

            {/* SVG Trend Line & Area Chart */}
            {semesterTrends.length === 0 ? (
              <div className="flex h-48 items-center justify-center rounded-xl border border-dashed border-border/70 text-xs text-muted-foreground">
                {isLoading ? (isVi ? 'Đang nạp xu hướng học kỳ...' : 'Loading semester trends...') : (isVi ? 'Chưa có dữ liệu học kỳ' : 'No semester trends recorded')}
              </div>
            ) : (
              <div className="rounded-xl border border-border/70 bg-card/60 p-4">
                <svg viewBox="0 0 760 220" className="h-auto w-full" role="img" aria-label={copy.enrollmentChartAria}>
                  <defs>
                    <linearGradient id="enrollmentGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#2563eb" stopOpacity="0.25" />
                      <stop offset="100%" stopColor="#2563eb" stopOpacity="0.0" />
                    </linearGradient>
                  </defs>

                  {/* Horizontal Grid lines */}
                  {[0, 0.33, 0.66, 1].map((ratio) => {
                    const level = Math.round(ratio * maxTrendCount);
                    const y = 170 - ratio * 130;
                    return (
                      <g key={ratio}>
                        <line x1={50} x2={720} y1={y} y2={y} stroke="currentColor" strokeOpacity={0.1} strokeWidth={1} />
                        <text x={44} y={y + 3} fontSize={10} fill="currentColor" fillOpacity={0.5} textAnchor="end">
                          {level}
                        </text>
                      </g>
                    );
                  })}

                  {/* Area and line coordinates */}
                  {(() => {
                    const trendPoints = semesterTrends.map((s, idx) => {
                      const x = semesterTrends.length === 1 ? 385 : 140 + idx * (480 / Math.max(semesterTrends.length - 1, 1));
                      const y = 170 - (Math.min(s.count, maxTrendCount) / Math.max(maxTrendCount, 1)) * 130;
                      return { x, y, s };
                    });

                    const polylineCoords = trendPoints.map((p) => `${p.x},${p.y}`).join(' ');
                    const areaCoords = `${trendPoints[0].x},170 ${polylineCoords} ${trendPoints[trendPoints.length - 1].x},170`;

                    return (
                      <>
                        {/* Area under curve */}
                        <polygon points={areaCoords} fill="url(#enrollmentGradient)" />

                        {/* Primary Trend Line */}
                        <polyline
                          points={polylineCoords}
                          fill="none"
                          stroke="#2563eb"
                          strokeWidth={3}
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        />

                        {/* Data Points */}
                        {trendPoints.map(({ x, y, s }) => (
                          <g key={s.semester}>
                            <circle cx={x} cy={y} r={6} fill="#2563eb" stroke="#ffffff" strokeWidth={2} />
                            <text x={x} y={y - 12} fontSize={12} fontWeight={700} fill="#2563eb" textAnchor="middle">
                              {formatNumber(s.count)}
                            </text>
                            <text x={x} y={192} fontSize={11} fontWeight={600} fill="currentColor" textAnchor="middle">
                              {s.semester}
                            </text>
                            <text x={x} y={208} fontSize={10} fill="currentColor" fillOpacity={0.6} textAnchor="middle">
                              {formatNumber(s.activeStudents)} {copy.studentsShort} &bull; {formatNumber(s.completionRate, { maximumFractionDigits: 1 })}%
                            </text>
                          </g>
                        ))}
                      </>
                    );
                  })()}
                </svg>
              </div>
            )}

            {/* Quick summary metrics */}
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <div className="rounded-lg border border-border/60 bg-secondary/20 p-3">
                <p className="text-xs text-muted-foreground">{copy.totalSubmissions}</p>
                <p className="mt-1 text-xl font-bold text-foreground">
                  {formatNumber(stats.totalEnrollments)} {copy.submissionsUnit}
                </p>
                <p className="mt-1 text-xs text-primary font-medium">
                  {copy.systemProcessed}
                </p>
              </div>
              <div className="rounded-lg border border-border/60 bg-secondary/20 p-3">
                <p className="text-xs text-muted-foreground">{copy.averagePassRate}</p>
                <p className="mt-1 text-xl font-bold text-foreground">
                  {formatNumber(averageCompletionRate, { maximumFractionDigits: 1 })}%
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  {copy.qualityStandard}
                </p>
              </div>
              <div className="rounded-lg border border-border/60 bg-secondary/20 p-3">
                <p className="text-xs text-muted-foreground">{copy.currentLoad}</p>
                <p className="mt-1 text-xl font-bold text-foreground">
                  {formatNumber(currentTermLoad?.count ?? stats.totalEnrollments)} {copy.loadsUnit}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  {currentTermLoad?.semester ?? (isVi ? 'Học kỳ hiện tại' : 'Current term')}
                </p>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: FACULTY COMPOSITION */}
        {activeTab === 'faculty' && (
          <div className="space-y-6">
            <div>
              <h4 className="text-sm font-semibold text-foreground">
                {copy.facultyTitle} ({formatNumber(stats.totalLecturers)} {copy.lecturersUnit})
              </h4>
            </div>

            {facultyRanks.length === 0 ? (
              <div className="flex h-48 items-center justify-center rounded-xl border border-dashed border-border/70 text-xs text-muted-foreground">
                {isLoading ? (isVi ? 'Đang nạp phân bố giảng viên...' : 'Loading faculty ranks...') : (isVi ? 'Chưa có dữ liệu học hàm' : 'No faculty rank data')}
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2 md:items-center">
                {/* SVG Monochromatic Donut Chart */}
                <div className="flex items-center justify-center p-4">
                  <svg viewBox="0 0 240 240" className="h-56 w-56" role="img" aria-label={copy.lecturersChartAria}>
                    <circle cx={120} cy={120} r={80} fill="none" strokeWidth={28} className="stroke-secondary" />

                    {(() => {
                      const circumference = 2 * Math.PI * 80;
                      let accumulatedOffset = 0;
                      return facultyRanks.map((item) => {
                        const strokeLen = (item.percentage / 100) * circumference;
                        const dashArray = `${strokeLen} ${circumference - strokeLen}`;
                        const offset = accumulatedOffset;
                        accumulatedOffset += strokeLen;
                        return (
                          <circle
                            key={item.rank}
                            cx={120}
                            cy={120}
                            r={80}
                            fill="none"
                            strokeWidth={28}
                            strokeDasharray={dashArray}
                            strokeDashoffset={-offset}
                            transform="rotate(-90 120 120)"
                            className={item.strokeTone}
                          />
                        );
                      });
                    })()}

                    {/* Center Text */}
                    <text x={120} y={114} fontSize={28} fontWeight={800} fill="currentColor" textAnchor="middle">
                      {formatNumber(stats.totalLecturers)}
                    </text>
                    <text x={120} y={134} fontSize={11} fill="currentColor" fillOpacity={0.6} textAnchor="middle">
                      {copy.lecturerShort}
                    </text>
                  </svg>
                </div>

                {/* Legend & Details */}
                <div className="space-y-4">
                  {facultyRanks.map((item) => (
                    <div key={item.rank} className="rounded-xl border border-border/70 bg-card/60 p-3.5 space-y-1.5">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className={cn('h-3 w-3 rounded-full', item.tone)} />
                          <span className="text-xs font-semibold text-foreground">{item.rank}</span>
                        </div>
                        <span className="text-xs font-bold text-foreground">
                          {formatNumber(item.count)} {copy.lecturersUnit} ({formatNumber(item.percentage)}%)
                        </span>
                      </div>
                      {/* Monochromatic Progress bar */}
                      <div className="h-2 w-full overflow-hidden rounded-full bg-secondary">
                        <div className={cn('h-full rounded-full', item.tone)} style={{ width: `${item.percentage}%` }} />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* TAB 4: GRADE PERFORMANCE */}
        {activeTab === 'grades' && (
          <div className="space-y-6">
            <div>
              <h4 className="text-sm font-semibold text-foreground">
                {copy.gradesTitle} ({formatNumber(stats.totalStudents)} {copy.studentsUnit})
              </h4>
            </div>

            {gradeDistribution.length === 0 ? (
              <div className="flex h-48 items-center justify-center rounded-xl border border-dashed border-border/70 text-xs text-muted-foreground">
                {isLoading ? (isVi ? 'Đang nạp phân bố điểm...' : 'Loading grade distribution...') : (isVi ? 'Chưa có dữ liệu điểm học phần' : 'No grade records')}
              </div>
            ) : (
              <>
                {/* Horizontal distribution bars - Monochromatic */}
                <div className="space-y-3.5">
                  {gradeDistribution.map((item) => (
                    <div key={item.grade} className="space-y-1">
                      <div className="flex items-center justify-between text-xs">
                        <span className="font-semibold text-foreground">{item.grade}</span>
                        <span className="text-muted-foreground">
                          <strong className="text-foreground">{formatNumber(item.count)}</strong> {copy.studentsUnit} ({formatNumber(item.percentage)}%)
                        </span>
                      </div>
                      <div className="h-3 w-full overflow-hidden rounded-full bg-secondary">
                        <div
                          className={cn('h-full rounded-full transition-all duration-300', item.tone)}
                          style={{ width: `${item.percentage}%` }}
                        />
                      </div>
                    </div>
                  ))}
                </div>

                <div className="rounded-lg border border-border/60 bg-secondary/15 p-3 flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between text-xs">
                  <span className="text-muted-foreground">
                    {copy.atLeastGoodLabel}
                  </span>
                  <span className="font-bold text-foreground text-sm">
                    {formatNumber(atLeastGoodPercent)}% ({formatNumber(atLeastGoodCount)} / {formatNumber(gradeTotal)} {copy.studentsUnit})
                  </span>
                </div>
              </>
            )}
          </div>
        )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
