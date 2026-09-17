'use client';

import * as React from 'react';
import { useState } from 'react';
import {
  BarChart3,
  GraduationCap,
  Info,
  School,
  TrendingUp,
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';

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

// Illustrative sample datasets. The headline totals passed in through `stats`
// come from live campus records; everything below only demonstrates the chart
// layout and is labelled as sample data in the UI (see `illustrativeNotice`).
type DepartmentCode = 'CNTT' | 'ĐĐT' | 'CKM' | 'CKĐ' | 'KT' | 'XD' | 'CNHH-TP' | 'NN';

const DEPARTMENTS: Array<{
  code: DepartmentCode;
  students: number;
  courses: number;
  sections: number;
}> = [
  { code: 'CNTT', students: 68, courses: 24, sections: 18 },
  { code: 'ĐĐT', students: 42, courses: 16, sections: 12 },
  { code: 'CKM', students: 38, courses: 14, sections: 10 },
  { code: 'CKĐ', students: 34, courses: 12, sections: 9 },
  { code: 'KT', students: 28, courses: 11, sections: 8 },
  { code: 'XD', students: 20, courses: 9, sections: 7 },
  { code: 'CNHH-TP', students: 14, courses: 8, sections: 6 },
  { code: 'NN', students: 10, courses: 6, sections: 5 },
];

const SEMESTER_TRENDS = [
  { semester: 'HK1 2025-2026', count: 1120, completionRate: 95.2, activeStudents: 238 },
  { semester: 'HK2 2025-2026', count: 1185, completionRate: 96.8, activeStudents: 246 },
  { semester: 'HK1 2026-2027', count: 1076, completionRate: 94.5, activeStudents: 254 },
];

// Monochromatic ramp built from theme status tokens (DESIGN.md: no raw
// Tailwind palettes on shared primitives).
const FACULTY_RANK_TOKENS = ['bg-status-info', 'bg-status-info/70', 'bg-status-info/40'] as const;
const GRADE_BAND_TOKENS = [
  'bg-status-info',
  'bg-status-info/75',
  'bg-status-info/55',
  'bg-status-info/35',
  'bg-status-danger',
] as const;

export function AdminAnalyticsCharts({ stats, className }: AdminAnalyticsChartsProps) {
  const { messages, formatNumber } = useI18n();
  const copy = messages.admin.analytics;
  const [activeTab, setActiveTab] = useState<'departments' | 'enrollments' | 'faculty' | 'grades'>('departments');
  const [departmentMetric, setDepartmentMetric] = useState<'students' | 'courses' | 'sections'>('students');

  const departments = DEPARTMENTS.map((dept) => ({
    ...dept,
    name: copy.departments[dept.code],
  }));

  const facultyRanks = [
    { rank: copy.facultyRanks.professor, count: 7, tone: FACULTY_RANK_TOKENS[0] },
    { rank: copy.facultyRanks.doctor, count: 11, tone: FACULTY_RANK_TOKENS[1] },
    { rank: copy.facultyRanks.master, count: 7, tone: FACULTY_RANK_TOKENS[2] },
  ].map((item) => {
    const total = 25; // sample total; see illustrativeNotice
    return { ...item, percentage: Math.round((item.count / total) * 100) };
  });

  const gradeDistribution = [
    { grade: copy.gradeBands.excellent, count: 56, tone: GRADE_BAND_TOKENS[0] },
    { grade: copy.gradeBands.veryGood, count: 114, tone: GRADE_BAND_TOKENS[1] },
    { grade: copy.gradeBands.good, count: 66, tone: GRADE_BAND_TOKENS[2] },
    { grade: copy.gradeBands.average, count: 15, tone: GRADE_BAND_TOKENS[3] },
    { grade: copy.gradeBands.warning, count: 3, tone: GRADE_BAND_TOKENS[4] },
  ].map((item) => ({ ...item, percentage: Math.round((item.count / 254) * 100) }));

  const gradeTotal = gradeDistribution.reduce((sum, item) => sum + item.count, 0);
  const atLeastGoodCount = gradeDistribution
    .slice(0, 4)
    .reduce((sum, item) => sum + item.count, 0);
  const atLeastGoodPercent = Math.round((atLeastGoodCount / gradeTotal) * 100);

  const averageCompletionRate =
    SEMESTER_TRENDS.reduce((sum, item) => sum + item.completionRate, 0) /
    SEMESTER_TRENDS.length;
  const currentTermLoad = SEMESTER_TRENDS[SEMESTER_TRENDS.length - 1];

  const maxDeptValue = Math.max(...departments.map((d) => d[departmentMetric]));

  return (
    <Card className={cn('border-border/80 shadow-xs', className)}>
      <CardHeader className="border-b border-border/70 pb-4">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap items-center gap-2.5">
            <CardTitle className="text-base font-semibold text-foreground">
              {copy.title}
            </CardTitle>
            <span
              title={copy.illustrativeNotice}
              className="inline-flex items-center gap-1.5 rounded-full bg-status-warning/15 px-2.5 py-0.5 text-xs font-medium text-status-warning-foreground"
            >
              <Info className="h-3 w-3" aria-hidden="true" />
              {copy.illustrativeBadge}
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-1.5 rounded-lg border border-border bg-secondary/30 p-1">
            <Button
              type="button"
              size="sm"
              variant={activeTab === 'departments' ? 'default' : 'ghost'}
              className="h-7 text-xs px-2.5"
              onClick={() => setActiveTab('departments')}
            >
              <BarChart3 className="mr-1.5 h-3.5 w-3.5" />
              {copy.tabs.departments}
            </Button>
            <Button
              type="button"
              size="sm"
              variant={activeTab === 'enrollments' ? 'default' : 'ghost'}
              className="h-7 text-xs px-2.5"
              onClick={() => setActiveTab('enrollments')}
            >
              <TrendingUp className="mr-1.5 h-3.5 w-3.5" />
              {copy.tabs.enrollments}
            </Button>
            <Button
              type="button"
              size="sm"
              variant={activeTab === 'faculty' ? 'default' : 'ghost'}
              className="h-7 text-xs px-2.5"
              onClick={() => setActiveTab('faculty')}
            >
              <School className="mr-1.5 h-3.5 w-3.5" />
              {copy.tabs.faculty}
            </Button>
            <Button
              type="button"
              size="sm"
              variant={activeTab === 'grades' ? 'default' : 'ghost'}
              className="h-7 text-xs px-2.5"
              onClick={() => setActiveTab('grades')}
            >
              <GraduationCap className="mr-1.5 h-3.5 w-3.5" />
              {copy.tabs.grades}
            </Button>
          </div>
        </div>
        <p className="mt-3 rounded-md border border-status-warning/30 bg-status-warning/10 px-3 py-2 text-xs leading-5 text-status-warning-foreground">
          {copy.illustrativeNotice}
        </p>
      </CardHeader>

      <CardContent className="p-6">
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
            <div className="rounded-xl border border-border/70 bg-card/60 p-4">
              <svg viewBox="0 0 800 230" className="h-auto w-full" role="img" aria-label={copy.chartAria}>
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
                  const barWidth = 44;
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
                      {/* Department code */}
                      <text
                        x={x + barWidth / 2}
                        y={204}
                        fontSize={11}
                        fontWeight={600}
                        fill="currentColor"
                        fillOpacity={0.9}
                        textAnchor="middle"
                      >
                        {dept.code}
                      </text>
                    </g>
                  );
                })}
              </svg>
            </div>

            {/* Department stats summary tags - Monochromatic clean badges */}
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
            <div className="rounded-xl border border-border/70 bg-card/60 p-4">
              <svg viewBox="0 0 760 220" className="h-auto w-full" role="img" aria-label={copy.enrollmentChartAria}>
                <defs>
                  <linearGradient id="enrollmentGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#2563eb" stopOpacity="0.25" />
                    <stop offset="100%" stopColor="#2563eb" stopOpacity="0.0" />
                  </linearGradient>
                </defs>

                {/* Horizontal Grid lines */}
                {[0, 400, 800, 1200].map((level) => {
                  const y = 170 - (level / 1400) * 130;
                  return (
                    <g key={level}>
                      <line x1={50} x2={720} y1={y} y2={y} stroke="currentColor" strokeOpacity={0.1} strokeWidth={1} />
                      <text x={44} y={y + 3} fontSize={10} fill="currentColor" fillOpacity={0.5} textAnchor="end">
                        {level}
                      </text>
                    </g>
                  );
                })}

                {/* Area under curve */}
                <polygon
                  points="140,170 140,66 380,60 620,70 620,170"
                  fill="url(#enrollmentGradient)"
                />

                {/* Primary Trend Line */}
                <polyline
                  points="140,66 380,60 620,70"
                  fill="none"
                  stroke="#2563eb"
                  strokeWidth={3}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />

                {/* Data Points */}
                {SEMESTER_TRENDS.map((s, idx) => {
                  const x = 140 + idx * 240;
                  const y = 170 - (s.count / 1400) * 130;
                  return (
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
                  );
                })}
              </svg>
            </div>

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
                  {formatNumber(currentTermLoad.count)} {copy.loadsUnit}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  {currentTermLoad.semester}
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

            <div className="grid grid-cols-1 gap-6 md:grid-cols-2 md:items-center">
              {/* SVG Monochromatic Donut Chart */}
              <div className="flex items-center justify-center p-4">
                <svg viewBox="0 0 240 240" className="h-56 w-56" role="img" aria-label={copy.lecturersChartAria}>
                  <circle cx={120} cy={120} r={80} fill="none" strokeWidth={28} className="stroke-secondary" />

                  {/* PGS.TS (28% -> strokeDasharray: 140.7 362) */}
                  <circle
                    cx={120}
                    cy={120}
                    r={80}
                    fill="none"
                    strokeWidth={28}
                    strokeDasharray="140.7 362"
                    strokeDashoffset="0"
                    transform="rotate(-90 120 120)"
                    className="stroke-status-info"
                  />
                  {/* TS (44% -> strokeDasharray: 221.1 281) */}
                  <circle
                    cx={120}
                    cy={120}
                    r={80}
                    fill="none"
                    strokeWidth={28}
                    strokeDasharray="221.1 281"
                    strokeDashoffset="-140.7"
                    transform="rotate(-90 120 120)"
                    className="stroke-status-info/70"
                  />
                  {/* ThS (28% -> strokeDasharray: 140.7 362) */}
                  <circle
                    cx={120}
                    cy={120}
                    r={80}
                    fill="none"
                    strokeWidth={28}
                    strokeDasharray="140.7 362"
                    strokeDashoffset="-361.8"
                    transform="rotate(-90 120 120)"
                    className="stroke-status-info/40"
                  />

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
          </div>
        )}
      </CardContent>
    </Card>
  );
}
