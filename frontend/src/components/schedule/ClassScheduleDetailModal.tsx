'use client';

import { useEffect, useRef } from 'react';
import {
  Award,
  Building,
  Calendar,
  Clock,
  GraduationCap,
  MapPin,
  Users,
  X,
} from 'lucide-react';
import { Button } from '@/components/ui/button';

export interface ScheduleDetailData {
  courseCode: string;
  courseName: string;
  courseNameEn?: string;
  courseNameVi?: string;
  sectionNumber: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  building?: string;
  roomNumber?: string;
  lecturerName?: string;
  credits?: number;
  departmentName?: string;
  departmentNameEn?: string;
  departmentNameVi?: string;
  enrolledCount?: number;
  capacity?: number;
  status?: string;
}

interface ClassScheduleDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  data: ScheduleDetailData | null;
  locale: 'vi' | 'en';
  isLecturer?: boolean;
}

const DAY_LABELS_VI: Record<number, string> = {
  1: 'Chủ Nhật',
  2: 'Thứ Hai',
  3: 'Thứ Ba',
  4: 'Thứ Tư',
  5: 'Thứ Năm',
  6: 'Thứ Sáu',
  7: 'Thứ Bảy',
};

const DAY_LABELS_EN: Record<number, string> = {
  1: 'Sunday',
  2: 'Monday',
  3: 'Tuesday',
  4: 'Wednesday',
  5: 'Thursday',
  6: 'Friday',
  7: 'Saturday',
};

export function ClassScheduleDetailModal({
  isOpen,
  onClose,
  data,
  locale,
  isLecturer = false,
}: ClassScheduleDetailModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };
    if (isOpen) {
      window.addEventListener('keydown', handleKeyDown);
      document.body.style.overflow = 'hidden';
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = '';
    };
  }, [isOpen, onClose]);

  if (!isOpen || !data) return null;

  const dayName =
    locale === 'vi'
      ? DAY_LABELS_VI[data.dayOfWeek] ?? `Thứ ${data.dayOfWeek}`
      : DAY_LABELS_EN[data.dayOfWeek] ?? `Day ${data.dayOfWeek}`;

  const displayName =
    locale === 'vi'
      ? data.courseNameVi ?? data.courseName
      : data.courseNameEn ?? data.courseName;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 p-4 backdrop-blur-sm animate-in fade-in-0"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="schedule-modal-title"
    >
      <div
        ref={modalRef}
        className="w-full max-w-lg rounded-2xl border border-border bg-card p-6 shadow-2xl transition-all zoom-in-95"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-start justify-between gap-4 border-b border-border/80 pb-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="rounded-md bg-primary/10 px-2.5 py-1 font-mono text-xs font-bold text-primary">
                {data.courseCode}
              </span>
              <span className="rounded-md bg-secondary px-2 py-0.5 text-xs font-semibold text-foreground">
                {locale === 'vi' ? 'Lớp HP' : 'Section'} {data.sectionNumber}
              </span>
              {data.status ? (
                <span className="rounded-full bg-emerald-500/15 px-2 py-0.5 text-[11px] font-medium text-emerald-700 dark:text-emerald-300">
                  {data.status}
                </span>
              ) : null}
            </div>
            <h3
              id="schedule-modal-title"
              className="mt-2 text-lg font-bold leading-snug text-foreground"
            >
              {displayName}
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label={locale === 'vi' ? 'Đóng' : 'Close'}
            className="rounded-lg p-1.5 text-muted-foreground transition hover:bg-secondary hover:text-foreground"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Content details grid */}
        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          <div className="flex items-start gap-3 rounded-xl border border-border/60 bg-secondary/30 p-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <Calendar className="h-4 w-4" />
            </div>
            <div>
              <div className="text-xs text-muted-foreground">
                {locale === 'vi' ? 'Ngày học / Giảng dạy' : 'Day of Week'}
              </div>
              <div className="mt-0.5 text-sm font-semibold text-foreground">{dayName}</div>
            </div>
          </div>

          <div className="flex items-start gap-3 rounded-xl border border-border/60 bg-secondary/30 p-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-sky-500/10 text-sky-600 dark:text-sky-400">
              <Clock className="h-4 w-4" />
            </div>
            <div>
              <div className="text-xs text-muted-foreground">
                {locale === 'vi' ? 'Khung giờ học' : 'Meeting Time'}
              </div>
              <div className="mt-0.5 text-sm font-semibold text-foreground">
                {data.startTime} - {data.endTime}
              </div>
            </div>
          </div>

          <div className="flex items-start gap-3 rounded-xl border border-border/60 bg-secondary/30 p-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
              <MapPin className="h-4 w-4" />
            </div>
            <div>
              <div className="text-xs text-muted-foreground">
                {locale === 'vi' ? 'Phòng học' : 'Classroom'}
              </div>
              <div className="mt-0.5 text-sm font-semibold text-foreground">
                {data.roomNumber ? (
                  <>
                    {data.building ? `${data.building} - ` : ''}
                    {data.roomNumber}
                  </>
                ) : (
                  <span className="italic text-muted-foreground">
                    {locale === 'vi' ? 'Chưa xếp phòng' : 'Pending room assignment'}
                  </span>
                )}
              </div>
            </div>
          </div>

          {data.lecturerName ? (
            <div className="flex items-start gap-3 rounded-xl border border-border/60 bg-secondary/30 p-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-purple-500/10 text-purple-600 dark:text-purple-400">
                <GraduationCap className="h-4 w-4" />
              </div>
              <div>
                <div className="text-xs text-muted-foreground">
                  {locale === 'vi' ? 'Giảng viên' : 'Instructor'}
                </div>
                <div className="mt-0.5 text-sm font-semibold text-foreground">
                  {data.lecturerName}
                </div>
              </div>
            </div>
          ) : null}

          {data.credits ? (
            <div className="flex items-start gap-3 rounded-xl border border-border/60 bg-secondary/30 p-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400">
                <Award className="h-4 w-4" />
              </div>
              <div>
                <div className="text-xs text-muted-foreground">
                  {locale === 'vi' ? 'Số tín chỉ' : 'Credits'}
                </div>
                <div className="mt-0.5 text-sm font-semibold text-foreground">
                  {data.credits} {locale === 'vi' ? 'tín chỉ' : 'credits'}
                </div>
              </div>
            </div>
          ) : null}

          {isLecturer && data.enrolledCount !== undefined ? (
            <div className="flex items-start gap-3 rounded-xl border border-border/60 bg-secondary/30 p-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-teal-500/10 text-teal-600 dark:text-teal-400">
                <Users className="h-4 w-4" />
              </div>
              <div>
                <div className="text-xs text-muted-foreground">
                  {locale === 'vi' ? 'Sĩ số đăng ký' : 'Enrolled Students'}
                </div>
                <div className="mt-0.5 text-sm font-semibold text-foreground">
                  {data.enrolledCount} {locale === 'vi' ? 'sinh viên' : 'students'}
                </div>
              </div>
            </div>
          ) : null}

          {data.departmentName || data.departmentNameVi || data.departmentNameEn ? (
            <div className="flex items-start gap-3 rounded-xl border border-border/60 bg-secondary/30 p-3 sm:col-span-2">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400">
                <Building className="h-4 w-4" />
              </div>
              <div>
                <div className="text-xs text-muted-foreground">
                  {locale === 'vi' ? 'Khoa / Bộ môn' : 'Department'}
                </div>
                <div className="mt-0.5 text-sm font-semibold text-foreground">
                  {locale === 'vi'
                    ? data.departmentNameVi || data.departmentName || data.departmentNameEn
                    : data.departmentNameEn || data.departmentName || data.departmentNameVi}
                </div>
              </div>
            </div>
          ) : null}
        </div>

        {/* Footer actions */}
        <div className="mt-6 flex justify-end gap-3 border-t border-border/80 pt-4">
          <Button variant="outline" onClick={onClose}>
            {locale === 'vi' ? 'Đóng' : 'Close'}
          </Button>
        </div>
      </div>
    </div>
  );
}
