/* eslint-disable @next/next/no-img-element */
'use client';

import React, { useState, useMemo, useRef } from 'react';
import { toast } from 'sonner';
import {
  Award,
  BarChart2,
  LineChart,
  Download,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { authApi } from '@/lib/api';
import { LocalizedLink } from '@/components/LocalizedLink';
import { isDemoUser } from '@/lib/login-portal';
import { StudentTranscriptSemester, MyCurriculumResponse } from '@/types/api';

interface StudentUteProfileGradeViewProps {
  transcriptSemesters?: StudentTranscriptSemester[];
  curriculumData?: MyCurriculumResponse | null;
  selectedSemesterId?: string;
  onSemesterChange?: (semesterId: string) => void;
  availableSemesters?: Array<{ id: string; name: string }>;
}

export function StudentUteProfileGradeView({
  transcriptSemesters = [],
  curriculumData,
  selectedSemesterId,
  onSemesterChange,
  availableSemesters = [],
}: StudentUteProfileGradeViewProps) {
  const { user, refreshUser } = useAuth();
  const { messages, locale } = useI18n();
  const card = messages.studentCard;
  const [chartType, setChartType] = useState<'combo' | 'bar' | 'line'>('combo');
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      toast.error(card.toastPhotoTooLarge);
      return;
    }
    const dataUrl = await new Promise<string | null>((resolve) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => resolve(null);
      reader.readAsDataURL(file);
    });
    // The profile API accepts inline data URLs up to ~200k characters.
    if (!dataUrl || dataUrl.length > 200_000) {
      toast.error(card.toastPhotoTooLarge);
      return;
    }
    setAvatarPreview(dataUrl);
    try {
      await authApi.updateProfile({ avatar: dataUrl });
      await refreshUser();
      toast.success(card.toastPhotoUpdated);
    } catch {
      // Never keep a preview of a change the server refused to store.
      setAvatarPreview(null);
      toast.error(card.toastPhotoFailed);
    }
  };

  const handleDownloadChart = () => {
    const svg = document.getElementById('grade-combo-chart');
    if (svg) {
      const serializer = new XMLSerializer();
      const source = serializer.serializeToString(svg);
      const blob = new Blob([source], { type: 'image/svg+xml;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `bieu-do-hoc-tap-${studentInfo.studentId}.svg`;
      link.click();
      URL.revokeObjectURL(url);
      toast.success(card.toastDownloaded);
    } else {
      window.print();
    }
  };

  // Official student identity comes from the authenticated profile and the
  // curriculum record. Missing values stay visibly unavailable — never filled
  // with another person's data.
  const studentInfo = useMemo(() => {
    const empty = card.notAvailable;
    return {
      name: user ? `${user.lastName ?? ''} ${user.firstName ?? ''}`.trim() || empty : empty,
      studentId: user?.studentId || empty,
      dateOfBirth: user?.dateOfBirth ? String(user.dateOfBirth).slice(0, 10) : empty,
      address: user?.address || empty,
      gender: user?.gender || empty,
      curriculumName: curriculumData?.curriculum?.name || empty,
    };
  }, [card.notAvailable, curriculumData, user]);

  // Credit progress (Đã học / Còn lại). Numbers render only when the real
  // curriculum and completed coursework agree — no sample values, ever.
  const curriculumTotal = curriculumData?.curriculum?.totalCredits;
  const earnedCredits = useMemo(() => {
    if (!curriculumData?.courses) return null;
    return curriculumData.courses
      .filter((c) => c.status === 'COMPLETED')
      .reduce((acc, c) => acc + c.credits, 0);
  }, [curriculumData]);
  const totalCredits =
    typeof curriculumTotal === 'number' && curriculumTotal > 0 ? curriculumTotal : null;
  const creditsKnown = totalCredits !== null && earnedCredits !== null;
  const remainingCredits = creditsKnown ? Math.max(0, totalCredits! - earnedCredits!) : 0;
  const earnedRatio = creditsKnown && totalCredits! > 0 ? earnedCredits! / totalCredits! : 0;

  // Only real transcript records feed the chart. A 4.0 grade point is never
  // converted into an invented 10-scale score.
  const sampleCourses = useMemo(() => {
    const records = transcriptSemesters.flatMap((s) => s.records || []);
    return records
      .filter((r) => typeof r.finalGrade === 'number')
      .slice(0, 10)
      .map((r) => ({
        code: r.courseCode || '',
        name: r.courseName || '',
        studentScore: r.finalGrade as number,
      }));
  }, [transcriptSemesters]);

  // SVG Chart dimensions
  const chartWidth = 520;
  const chartHeight = 220;
  const padding = { top: 25, right: 35, bottom: 40, left: 35 };
  const plotWidth = chartWidth - padding.left - padding.right;
  const plotHeight = chartHeight - padding.top - padding.bottom;

  const barWidth = Math.min(
    24,
    Math.max(14, Math.floor(plotWidth / Math.max(1, sampleCourses.length) - 12)),
  );

  // SVG Pie calculations (radius = 65, center = 80, 80)
  const pieCenter = 80;
  const pieRadius = 65;
  const startAngleRad = -Math.PI / 2;
  const endAngleRad = startAngleRad + (earnedRatio * 2 * Math.PI);

  const x1 = pieCenter + pieRadius * Math.cos(startAngleRad);
  const y1 = pieCenter + pieRadius * Math.sin(startAngleRad);
  const x2 = pieCenter + pieRadius * Math.cos(endAngleRad);
  const y2 = pieCenter + pieRadius * Math.sin(endAngleRad);
  const largeArcFlag = earnedRatio > 0.5 ? 1 : 0;

  const earnedPath = `M ${pieCenter} ${pieCenter} L ${x1} ${y1} A ${pieRadius} ${pieRadius} 0 ${largeArcFlag} 1 ${x2} ${y2} Z`;
  const remainingPath = `M ${pieCenter} ${pieCenter} L ${x2} ${y2} A ${pieRadius} ${pieRadius} 0 ${1 - largeArcFlag} 1 ${x1} ${y1} Z`;

  return (
    <div className="w-full bg-muted/30 text-foreground rounded-xl border border-border shadow-xs overflow-hidden mb-8 font-sans">
      {/* 1. Official UTE Header Ribbon */}
      <div className="relative flex items-center bg-[#0d509d] text-white px-5 py-2.5 shadow-xs">
        <div className="flex items-center gap-2 font-extrabold text-sm uppercase tracking-wider">
          <span className="inline-block w-2.5 h-2.5 bg-[#f59e0b] rounded-xs" />
          {card.ribbonTitle}
        </div>
        <div
          className="absolute right-[-10px] top-0 bottom-0 w-4 bg-[#0d509d]"
          style={{ clipPath: 'polygon(0 0, 100% 50%, 0 100%)' }}
        />
      </div>

      {/* 2. Main Content Grid */}
      <div className="p-4 sm:p-6 grid grid-cols-1 lg:grid-cols-12 gap-5">
        {/* Left Column: Avatar & Student Basic Info (4 cols) */}
        <div className="lg:col-span-4 space-y-4">
          {/* Avatar card */}
          <div className="bg-card rounded-lg border border-border p-5 flex flex-col items-center justify-center shadow-2xs">
            <div className="relative mb-3">
              <div className="w-28 h-28 rounded-full overflow-hidden border-2 border-[#0d509d]/30 bg-muted flex items-center justify-center shadow-inner">
                {avatarPreview || user?.avatar ? (
                  <img
                    src={avatarPreview || user?.avatar || ''}
                    alt={studentInfo.name}
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <span className="text-2xl font-bold text-muted-foreground" aria-hidden="true">
                    {(user?.lastName?.[0] ?? '') + (user?.firstName?.[0] ?? '') || '?'}
                  </span>
                )}
              </div>
            </div>
            <h3 className="font-bold text-base text-foreground tracking-tight text-center">
              {studentInfo.name}
            </h3>
            {isDemoUser(user) && (
              <div className="mt-1 flex justify-center">
                <span className="inline-flex items-center gap-1 rounded bg-amber-500/15 border border-amber-500/30 px-2 py-0.5 text-[10.5px] font-medium text-amber-700 dark:text-amber-300">
                  <span className="h-1.5 w-1.5 rounded-full bg-amber-500 animate-pulse" />
                  {locale === 'vi' ? 'Tài khoản demo để trải nghiệm' : 'Demo account'}
                </span>
              </div>
            )}
            <input
              type="file"
              ref={fileInputRef}
              accept="image/*"
              className="hidden"
              onChange={handleAvatarChange}
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="mt-2 inline-flex min-h-8 items-center text-xs text-[#0d509d] dark:text-sky-400 hover:underline font-medium cursor-pointer"
            >
              {card.updatePhoto}
            </button>
          </div>

          {/* Student details table */}
          <div className="bg-card rounded-lg border border-border shadow-2xs overflow-hidden">
            <div className="px-4 py-2.5 bg-muted/60 border-b border-border font-bold text-xs uppercase text-foreground tracking-wide flex items-center justify-between">
              <span>{card.infoTitle}</span>
              <span className="text-[10px] text-muted-foreground font-semibold bg-muted px-1.5 py-0.5 rounded-sm">{card.semesterBadge}</span>
            </div>
            <div className="divide-y divide-border text-xs">
              <div className="grid grid-cols-1 gap-0.5 sm:grid-cols-5 px-4 py-2.5">
                <span className="sm:col-span-2 text-muted-foreground font-medium">{card.fieldsStudentId}</span>
                <span className="sm:col-span-3 font-semibold text-foreground">{studentInfo.studentId}</span>
              </div>
              <div className="grid grid-cols-1 gap-0.5 sm:grid-cols-5 px-4 py-2.5">
                <span className="sm:col-span-2 text-muted-foreground font-medium">{card.fieldsFullName}</span>
                <span className="sm:col-span-3 font-semibold text-foreground">{studentInfo.name}</span>
              </div>
              <div className="grid grid-cols-1 gap-0.5 sm:grid-cols-5 px-4 py-2.5">
                <span className="sm:col-span-2 text-muted-foreground font-medium">{card.fieldsDateOfBirth}</span>
                <span className="sm:col-span-3 text-foreground">{studentInfo.dateOfBirth}</span>
              </div>
              <div className="grid grid-cols-1 gap-0.5 sm:grid-cols-5 px-4 py-2.5">
                <span className="sm:col-span-2 text-muted-foreground font-medium">{card.fieldsGender}</span>
                <span className="sm:col-span-3 text-foreground">{studentInfo.gender}</span>
              </div>
              <div className="grid grid-cols-1 gap-0.5 sm:grid-cols-5 px-4 py-2.5">
                <span className="sm:col-span-2 text-muted-foreground font-medium">{card.curriculum}</span>
                <span className="sm:col-span-3 text-foreground">{studentInfo.curriculumName}</span>
              </div>
              <div className="grid grid-cols-1 gap-0.5 sm:grid-cols-5 px-4 py-2.5 bg-emerald-500/10 border-t border-emerald-500/20">
                <span className="sm:col-span-2 text-emerald-700 dark:text-emerald-400 font-semibold flex items-center gap-1">
                  <Award className="h-3.5 w-3.5 shrink-0" />
                  {card.fieldsConductPoints}
                </span>
                <span className="sm:col-span-3 font-semibold text-emerald-700 dark:text-emerald-400 flex items-center justify-between">
                  <span>{card.conductSeeRecord}</span>
                  <LocalizedLink href="/dashboard/conduct" className="inline-flex min-h-8 items-center text-[11px] hover:underline font-normal">{card.fieldsDetails}</LocalizedLink>
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Right Area: Charts & Contact Information (8 cols) */}
        <div className="lg:col-span-8 space-y-5">
          {/* Top Row: Academic Result Combo Chart + Progress Pie Chart */}
          <div className="grid grid-cols-1 xl:grid-cols-12 gap-5">
            {/* Center: Filters & Academic Results Combo Chart (8 cols) */}
            <div className="xl:col-span-8 bg-card rounded-lg border border-border p-4 shadow-2xs flex flex-col justify-between">
              {/* Selectors Bar — only data-bound controls; curriculum and
                  academic-year decoration was removed because it changed no
                  data and contradicted the student's real curriculum. */}
              <div className="flex flex-wrap items-center gap-3 mb-3 text-xs">
                {/* Semester Dropdown */}
                <div className="w-[150px]">
                  <label className="block text-[11px] text-muted-foreground mb-0.5 font-medium">
                    {card.semester}
                  </label>
                  <select
                    value={selectedSemesterId || ''}
                    onChange={(e) => onSemesterChange?.(e.target.value)}
                    className="w-full bg-background border border-border rounded px-2 py-1 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-[#0d509d]"
                  >
                    {availableSemesters.length > 0 ? (
                      availableSemesters.map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.name}
                        </option>
                      ))
                    ) : (
                      <>
                        <option value="hk1">{card.hk1}</option>
                        <option value="hk2">{card.hk2}</option>
                        <option value="hk-he">{card.hkHe}</option>
                      </>
                    )}
                  </select>
                </div>
              </div>

              {/* Chart Header & Action Tools */}
              <div className="flex items-center justify-between border-b border-border/70 pb-2 mb-2">
                <h4 className="font-bold text-sm text-foreground tracking-tight">
                  {card.chartTitle}
                </h4>
                <div className="flex items-center gap-1.5 text-muted-foreground/70">
                  <button
                    type="button"
                    onClick={() => setChartType('line')}
                    className={cn(
                      'p-1 rounded hover:bg-muted transition-colors',
                      chartType === 'line' && 'text-[#0d509d] dark:text-sky-400 bg-blue-500/10',
                    )}
                    title={card.chartLine}
                    aria-label={card.chartLine}
                    aria-pressed={chartType === 'line'}
                  >
                    <LineChart className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setChartType('bar')}
                    className={cn(
                      'p-1 rounded hover:bg-muted transition-colors',
                      chartType === 'bar' && 'text-[#0d509d] dark:text-sky-400 bg-blue-500/10',
                    )}
                    title={card.chartBar}
                    aria-label={card.chartBar}
                    aria-pressed={chartType === 'bar'}
                  >
                    <BarChart2 className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setChartType('combo')}
                    className={cn(
                      'p-1 rounded hover:bg-muted transition-colors',
                      chartType === 'combo' && 'text-[#0d509d] dark:text-sky-400 bg-blue-500/10',
                    )}
                    title={card.chartCombo}
                    aria-label={card.chartCombo}
                    aria-pressed={chartType === 'combo'}
                  >
                    <span className="text-[10px] font-bold px-1">Combo</span>
                  </button>
                  <button
                    type="button"
                    onClick={handleDownloadChart}
                    className="inline-flex h-8 w-8 items-center justify-center rounded hover:bg-muted transition-colors"
                    title={card.chartDownload}
                    aria-label={card.chartDownload}
                  >
                    <Download className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

              {/* SVG Responsive Bar Chart — rendered only from real records */}
              {sampleCourses.length === 0 ? (
                <p className="rounded-lg border border-dashed border-border px-4 py-10 text-center text-xs text-muted-foreground">
                  {card.chartNoData}
                </p>
              ) : (
              <div className="w-full overflow-x-auto">
                <svg
                  id="grade-combo-chart"
                  viewBox={`0 0 ${chartWidth} ${chartHeight}`}
                  className="w-full h-auto"
                >
                  {/* Grid lines (0, 2.5, 5, 7.5, 10) */}
                  {[0, 2.5, 5, 7.5, 10].map((val) => {
                    const y = padding.top + plotHeight - (val / 10) * plotHeight;
                    return (
                      <g key={val}>
                        <line
                          x1={padding.left}
                          y1={y}
                          x2={chartWidth - padding.right}
                          y2={y}
                          className="stroke-border"
                          strokeDasharray="3 3"
                        />
                        <text
                          x={padding.left - 6}
                          y={y + 3}
                          fontSize="9"
                          className="fill-muted-foreground"
                          textAnchor="end"
                        >
                          {val}
                        </text>
                      </g>
                    );
                  })}

                  {/* Right Axis Label */}
                  <text
                    x={chartHeight / 2}
                    y={-(chartWidth - 10)}
                    transform="rotate(90)"
                    fontSize="9"
                    className="fill-muted-foreground"
                    textAnchor="middle"
                  >
                    {card.axisYourScore}
                  </text>

                  {/* Bars: Điểm của bạn */}
                  {(chartType === 'combo' || chartType === 'bar') &&
                    sampleCourses.map((c, i) => {
                      const stepX = plotWidth / sampleCourses.length;
                      const x = padding.left + i * stepX + (stepX - barWidth) / 2;
                      const barH = (c.studentScore / 10) * plotHeight;
                      const y = padding.top + plotHeight - barH;

                      return (
                        <g key={c.code}>
                          {/* Bar */}
                          <rect
                            x={x}
                            y={y}
                            width={barWidth}
                            height={barH}
                            fill="#3b82f6"
                            rx="2"
                            className="transition-all hover:fill-[#2563eb]"
                          />
                          {/* Score on top of bar */}
                          <text
                            x={x + barWidth / 2}
                            y={y - 4}
                            fontSize="9"
                            fontWeight="bold"
                            className="fill-foreground"
                            textAnchor="middle"
                          >
                            {c.studentScore}
                          </text>
                          {/* Course Code below axis */}
                          <text
                            x={x + barWidth / 2}
                            y={padding.top + plotHeight + 15}
                            fontSize="8"
                            className="fill-muted-foreground"
                            textAnchor="middle"
                          >
                            {c.code}
                          </text>
                        </g>
                      );
                    })}

                  {/* Class-average series intentionally omitted: the API does
                      not expose class aggregates, so none is invented. */}
                </svg>
              </div>
              )}

              {/* Legend Footer */}
              <div className="flex items-center justify-center gap-6 pt-2 border-t border-border/70 text-xs text-muted-foreground font-medium">
                <div className="flex items-center gap-1.5">
                  <span className="w-3 h-3 rounded bg-[#3b82f6] inline-block" />
                  <span>{card.axisYourScore}</span>
                </div>
              </div>
            </div>

            {/* Right: Tiến độ học tập Pie Chart (4 cols) */}
            <div className="xl:col-span-4 bg-card rounded-lg border border-border p-4 shadow-2xs flex flex-col justify-between items-center text-center">
              <div className="w-full">
                <h4 className="font-bold text-sm text-foreground tracking-tight mb-0.5 text-left">
                  {card.progressTitle}
                </h4>
                {creditsKnown ? (
                  <p className="text-xs text-muted-foreground text-left mb-3">
                    {card.totalCreditsLabel}: <span className="font-bold text-foreground">{earnedCredits}/{totalCredits}</span>
                  </p>
                ) : (
                  <p className="text-xs text-muted-foreground text-left mb-3">
                    {card.notAvailable}
                  </p>
                )}

                {/* Legend badges */}
                <div className="flex items-center justify-center gap-4 text-xs font-semibold mb-2">
                  <div className="flex items-center gap-1.5">
                    <span className="w-3 h-3 rounded-xs bg-[#3b82f6] inline-block" />
                    <span className="text-muted-foreground">{card.earned}</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="w-3 h-3 rounded-xs bg-[#84cc16] inline-block" />
                    <span className="text-muted-foreground">{card.remaining}</span>
                  </div>
                </div>
              </div>

              {/* SVG Pie Chart — only with real credit numbers */}
              <div className="py-2">
                {creditsKnown ? (
                  <svg width="160" height="160" viewBox="0 0 160 160">
                    {/* Đã học Slice (Blue) */}
                    <path d={earnedPath} fill="#3b82f6" className="stroke-card" strokeWidth="2" />
                    {/* Còn lại Slice (Green) */}
                    <path d={remainingPath} fill="#84cc16" className="stroke-card" strokeWidth="2" />
                    {/* Center percentage badge */}
                    <circle cx="80" cy="80" r="28" className="fill-card" />
                    <text
                      x="80"
                      y="84"
                      fontSize="13"
                      fontWeight="bold"
                      className="fill-foreground"
                      textAnchor="middle"
                    >
                      {Math.round(earnedRatio * 100)}%
                    </text>
                  </svg>
                ) : (
                  <div className="w-[160px] h-[160px] flex items-center justify-center rounded-full border border-dashed border-border text-xs text-muted-foreground px-4">
                    {card.notAvailable}
                  </div>
                )}
              </div>

              {creditsKnown ? (
                <div className="w-full pt-2 border-t border-border/70 flex justify-between text-xs text-muted-foreground">
                  <span>{card.accumulated}: <strong>{earnedCredits} TC</strong></span>
                  <span>{card.needMore}: <strong>{remainingCredits} TC</strong></span>
                </div>
              ) : null}
            </div>
          </div>

          {/* Bottom Row: Contact Information Card */}
          <div className="bg-card rounded-lg border border-border shadow-2xs overflow-hidden">
            <div className="px-4 py-2.5 bg-muted/60 border-b border-border font-bold text-xs uppercase text-foreground tracking-wide">
              {card.contactTitle}
            </div>
            <div className="divide-y divide-border text-xs">
              <div className="grid grid-cols-1 gap-0.5 sm:grid-cols-4 px-4 py-2.5">
                <span className="text-muted-foreground font-medium">{card.address}</span>
                <span className="sm:col-span-3 text-foreground">{studentInfo.address}</span>
              </div>
              <div className="grid grid-cols-1 gap-0.5 sm:grid-cols-4 px-4 py-2.5">
                <span className="text-muted-foreground font-medium">{card.phone}</span>
                <span className="sm:col-span-3 text-foreground">{user?.phone || card.notAvailable}</span>
              </div>
              <div className="grid grid-cols-1 gap-0.5 sm:grid-cols-4 px-4 py-2.5">
                <span className="text-muted-foreground font-medium">{card.fieldsEmail}</span>
                <span className="sm:col-span-3 text-foreground">{user?.email || card.notAvailable}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
