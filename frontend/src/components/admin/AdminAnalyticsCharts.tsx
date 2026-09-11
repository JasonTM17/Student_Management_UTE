'use client';

import * as React from 'react';
import { useState } from 'react';
import {
  BarChart3,
  GraduationCap,
  School,
  TrendingUp,
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
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

export function AdminAnalyticsCharts({ stats, className }: AdminAnalyticsChartsProps) {
  const [activeTab, setActiveTab] = useState<'departments' | 'enrollments' | 'faculty' | 'grades'>('departments');
  const [departmentMetric, setDepartmentMetric] = useState<'students' | 'courses' | 'sections'>('students');

  // Department distribution data (aligned with HCMUTE official faculties)
  const departments = [
    { code: 'CNTT', name: 'Khoa Công nghệ Thông tin', students: 68, courses: 24, sections: 18 },
    { code: 'ĐĐT', name: 'Khoa Điện - Điện tử', students: 42, courses: 16, sections: 12 },
    { code: 'CKM', name: 'Khoa Cơ khí Chế tạo máy', students: 38, courses: 14, sections: 10 },
    { code: 'CKĐ', name: 'Khoa Cơ khí Động lực', students: 34, courses: 12, sections: 9 },
    { code: 'KT', name: 'Khoa Kinh tế', students: 28, courses: 11, sections: 8 },
    { code: 'XD', name: 'Khoa Xây dựng', students: 20, courses: 9, sections: 7 },
    { code: 'CNHH-TP', name: 'Khoa Công nghệ Hóa học & Thực phẩm', students: 14, courses: 8, sections: 6 },
    { code: 'NN', name: 'Khoa Ngoại ngữ', students: 10, courses: 6, sections: 5 },
  ];

  // Semester enrollment trend data
  const semesterTrends = [
    { semester: 'HK1 2025-2026', count: 1120, completionRate: 95.2, activeStudents: 238 },
    { semester: 'HK2 2025-2026', count: 1185, completionRate: 96.8, activeStudents: 246 },
    { semester: 'HK1 2026-2027', count: 1076, completionRate: 94.5, activeStudents: 254 },
  ];

  // Faculty composition data (total: 25 lecturers) - Monochromatic blue scale
  const facultyRanks = [
    { rank: 'Giáo sư & Phó Giáo sư (PGS.TS)', count: 7, percentage: 28, color: '#1e40af', bgClass: 'bg-blue-800 dark:bg-blue-700' },
    { rank: 'Tiến sĩ (TS)', count: 11, percentage: 44, color: '#2563eb', bgClass: 'bg-blue-600 dark:bg-blue-500' },
    { rank: 'Thạc sĩ (ThS)', count: 7, percentage: 28, color: '#93c5fd', bgClass: 'bg-blue-300 dark:bg-blue-400' },
  ];

  // Grade performance distribution data (total: 254 students) - Monochromatic blue scale
  const gradeDistribution = [
    { grade: 'Xuất sắc (A/A+)', count: 56, percentage: 22, bgClass: 'bg-blue-800 dark:bg-blue-700' },
    { grade: 'Giỏi (B/B+)', count: 114, percentage: 45, bgClass: 'bg-blue-700 dark:bg-blue-600' },
    { grade: 'Khá (C/C+)', count: 66, percentage: 26, bgClass: 'bg-blue-600 dark:bg-blue-500' },
    { grade: 'Trung bình (D/D+)', count: 15, percentage: 6, bgClass: 'bg-blue-500 dark:bg-blue-400' },
    { grade: 'Cảnh báo (F)', count: 3, percentage: 1, bgClass: 'bg-slate-400 dark:bg-slate-500' },
  ];

  const maxDeptValue = Math.max(...departments.map((d) => d[departmentMetric]));

  return (
    <Card className={cn('border-border/80 shadow-xs', className)}>
      <CardHeader className="border-b border-border/70 pb-4">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2.5">
            <CardTitle className="text-base font-semibold text-foreground">
              Trung Tâm Phân Tích & Đồ Thị Giám Sát Học Vụ
            </CardTitle>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary">
              <span className="h-1.5 w-1.5 rounded-full bg-primary animate-pulse" />
              Thời gian thực
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
              Theo Khoa / Viện
            </Button>
            <Button
              type="button"
              size="sm"
              variant={activeTab === 'enrollments' ? 'default' : 'ghost'}
              className="h-7 text-xs px-2.5"
              onClick={() => setActiveTab('enrollments')}
            >
              <TrendingUp className="mr-1.5 h-3.5 w-3.5" />
              Xu hướng ĐKHP
            </Button>
            <Button
              type="button"
              size="sm"
              variant={activeTab === 'faculty' ? 'default' : 'ghost'}
              className="h-7 text-xs px-2.5"
              onClick={() => setActiveTab('faculty')}
            >
              <School className="mr-1.5 h-3.5 w-3.5" />
              Đội ngũ Giảng viên
            </Button>
            <Button
              type="button"
              size="sm"
              variant={activeTab === 'grades' ? 'default' : 'ghost'}
              className="h-7 text-xs px-2.5"
              onClick={() => setActiveTab('grades')}
            >
              <GraduationCap className="mr-1.5 h-3.5 w-3.5" />
              Xếp loại Học lực
            </Button>
          </div>
        </div>
      </CardHeader>

      <CardContent className="p-6">
        {/* TAB 1: DEPARTMENTS */}
        {activeTab === 'departments' && (
          <div className="space-y-6">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h4 className="text-sm font-semibold text-foreground">
                  Phân bổ Quy mô Đào tạo theo 8 Khoa & Bộ môn trọng điểm
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
                  Sinh viên ({stats.totalStudents})
                </button>
                <button
                  type="button"
                  onClick={() => setDepartmentMetric('courses')}
                  className={cn(
                    'rounded px-2.5 py-1 font-medium transition',
                    departmentMetric === 'courses' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
                  )}
                >
                  Môn học ({stats.totalCourses})
                </button>
                <button
                  type="button"
                  onClick={() => setDepartmentMetric('sections')}
                  className={cn(
                    'rounded px-2.5 py-1 font-medium transition',
                    departmentMetric === 'sections' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
                  )}
                >
                  Lớp mở
                </button>
              </div>
            </div>

            {/* SVG Monochromatic Bar Chart */}
            <div className="rounded-xl border border-border/70 bg-card/60 p-4">
              <svg viewBox="0 0 800 230" className="h-auto w-full" role="img" aria-label="Biểu đồ phân bổ quy mô đào tạo theo khoa">
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
                        <title>{`${dept.name} (${dept.code}): ${value} ${departmentMetric === 'students' ? 'sinh viên' : departmentMetric === 'courses' ? 'môn học' : 'lớp mở'}`}</title>
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
                    <p className="text-muted-foreground">{dept.students} SV &bull; {dept.courses} Môn</p>
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
                  Tiến độ & Xu hướng Đăng Ký Học Phần Qua Các Học Kỳ
                </h4>
              </div>
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span className="h-2 w-2 rounded-full bg-primary" />
                <span>Số lượt đăng ký học phần</span>
              </div>
            </div>

            {/* SVG Trend Line & Area Chart */}
            <div className="rounded-xl border border-border/70 bg-card/60 p-4">
              <svg viewBox="0 0 760 220" className="h-auto w-full" role="img" aria-label="Biểu đồ xu hướng đăng ký học phần">
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
                {semesterTrends.map((s, idx) => {
                  const x = 140 + idx * 240;
                  const y = 170 - (s.count / 1400) * 130;
                  return (
                    <g key={s.semester}>
                      <circle cx={x} cy={y} r={6} fill="#2563eb" stroke="#ffffff" strokeWidth={2} />
                      <text x={x} y={y - 12} fontSize={12} fontWeight={700} fill="#2563eb" textAnchor="middle">
                        {s.count.toLocaleString('vi-VN')}
                      </text>
                      <text x={x} y={192} fontSize={11} fontWeight={600} fill="currentColor" textAnchor="middle">
                        {s.semester}
                      </text>
                      <text x={x} y={208} fontSize={10} fill="currentColor" fillOpacity={0.6} textAnchor="middle">
                        {s.activeStudents} sinh viên &bull; Hoàn tất {s.completionRate}%
                      </text>
                    </g>
                  );
                })}
              </svg>
            </div>

            {/* Quick summary metrics */}
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <div className="rounded-lg border border-border/60 bg-secondary/20 p-3">
                <p className="text-xs text-muted-foreground">Tổng lượt đăng ký tích lũy</p>
                <p className="mt-1 text-xl font-bold text-foreground">
                  {stats.totalEnrollments.toLocaleString('vi-VN')} hồ sơ
                </p>
                <p className="mt-1 text-xs text-primary font-medium">
                  100% xử lý trên hệ thống
                </p>
              </div>
              <div className="rounded-lg border border-border/60 bg-secondary/20 p-3">
                <p className="text-xs text-muted-foreground">Tỷ lệ qua môn trung bình</p>
                <p className="mt-1 text-xl font-bold text-foreground">
                  95.5%
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  Chuẩn bảo đảm chất lượng đào tạo
                </p>
              </div>
              <div className="rounded-lg border border-border/60 bg-secondary/20 p-3">
                <p className="text-xs text-muted-foreground">Tải đăng ký kỳ hiện tại</p>
                <p className="mt-1 text-xl font-bold text-foreground">
                  1.076 lượt
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  Học kỳ 1 năm học 2026-2027
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
                Cơ Cấu Học Hàm & Trình Độ Đội Ngũ Giảng Viên ({stats.totalLecturers} Giảng viên)
              </h4>
            </div>

            <div className="grid grid-cols-1 gap-6 md:grid-cols-2 md:items-center">
              {/* SVG Monochromatic Donut Chart */}
              <div className="flex items-center justify-center p-4">
                <svg viewBox="0 0 240 240" className="h-56 w-56" role="img" aria-label="Biểu đồ cơ cấu giảng viên">
                  <circle cx={120} cy={120} r={80} fill="none" stroke="#f1f5f9" strokeWidth={28} className="dark:stroke-slate-800" />
                  
                  {/* PGS.TS (28% -> strokeDasharray: 140.7 362) - Deep Navy */}
                  <circle
                    cx={120}
                    cy={120}
                    r={80}
                    fill="none"
                    stroke="#1e40af"
                    strokeWidth={28}
                    strokeDasharray="140.7 362"
                    strokeDashoffset="0"
                    transform="rotate(-90 120 120)"
                  />
                  {/* TS (44% -> strokeDasharray: 221.1 281) - Primary Blue */}
                  <circle
                    cx={120}
                    cy={120}
                    r={80}
                    fill="none"
                    stroke="#2563eb"
                    strokeWidth={28}
                    strokeDasharray="221.1 281"
                    strokeDashoffset="-140.7"
                    transform="rotate(-90 120 120)"
                  />
                  {/* ThS (28% -> strokeDasharray: 140.7 362) - Soft Blue */}
                  <circle
                    cx={120}
                    cy={120}
                    r={80}
                    fill="none"
                    stroke="#93c5fd"
                    strokeWidth={28}
                    strokeDasharray="140.7 362"
                    strokeDashoffset="-361.8"
                    transform="rotate(-90 120 120)"
                  />

                  {/* Center Text */}
                  <text x={120} y={114} fontSize={28} fontWeight={800} fill="currentColor" textAnchor="middle">
                    {stats.totalLecturers}
                  </text>
                  <text x={120} y={134} fontSize={11} fill="currentColor" fillOpacity={0.6} textAnchor="middle">
                    Thầy Cô
                  </text>
                </svg>
              </div>

              {/* Legend & Details */}
              <div className="space-y-4">
                {facultyRanks.map((item) => (
                  <div key={item.rank} className="rounded-xl border border-border/70 bg-card/60 p-3.5 space-y-1.5">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className={cn('h-3 w-3 rounded-full', item.bgClass)} />
                        <span className="text-xs font-semibold text-foreground">{item.rank}</span>
                      </div>
                      <span className="text-xs font-bold text-foreground">
                        {item.count} giảng viên ({item.percentage}%)
                      </span>
                    </div>
                    {/* Monochromatic Progress bar */}
                    <div className="h-2 w-full overflow-hidden rounded-full bg-secondary">
                      <div className={cn('h-full rounded-full', item.bgClass)} style={{ width: `${item.percentage}%` }} />
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
                Phân Bố Xếp Loại Kết Quả Học Vụ Toàn Trường ({stats.totalStudents} Sinh viên)
              </h4>
            </div>

            {/* Horizontal distribution bars - Monochromatic */}
            <div className="space-y-3.5">
              {gradeDistribution.map((item) => (
                <div key={item.grade} className="space-y-1">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-foreground">{item.grade}</span>
                    <span className="text-muted-foreground">
                      <strong className="text-foreground">{item.count}</strong> sinh viên ({item.percentage}%)
                    </span>
                  </div>
                  <div className="h-3 w-full overflow-hidden rounded-full bg-secondary">
                    <div
                      className={cn('h-full rounded-full transition-all duration-300', item.bgClass)}
                      style={{ width: `${item.percentage}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>

            <div className="rounded-lg border border-border/60 bg-secondary/15 p-3 flex items-center justify-between text-xs">
              <span className="text-muted-foreground">
                Tỷ lệ sinh viên đạt loại Khá trở lên (GPA &ge; 2.5):
              </span>
              <span className="font-bold text-foreground text-sm">
                93.0% (236 / 254 sinh viên)
              </span>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
