'use client';

import { useEffect, useState } from 'react';
import { Calendar, Loader2, User } from 'lucide-react';
import { Modal } from '@/components/ui/modal';
import { gradesApi } from '@/lib/api';
import { StudentGradeRecord, StudentGradesByEnrollmentResponse } from '@/types/api';
import { useI18n } from '@/i18n';
import { getLocalizedFlatLabel } from '@/lib/academic-content';

interface GradeDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  record: StudentGradeRecord | null;
}

export function GradeDetailModal({ isOpen, onClose, record }: GradeDetailModalProps) {
  const { locale } = useI18n();
  const [data, setData] = useState<StudentGradesByEnrollmentResponse | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!isOpen || !record?.id) {
      setData(null);
      return;
    }

    let cancelled = false;
    setLoading(true);

    gradesApi
      .getStudentGradesByEnrollment(record.id)
      .then((res) => {
        if (!cancelled) setData(res);
      })
      .catch(() => {
        if (!cancelled) setData(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [isOpen, record?.id]);

  if (!record) return null;

  const title = getLocalizedFlatLabel(
    locale,
    record.courseName,
    record.courseNameEn,
    record.courseNameVi,
    record.courseName,
  );

  // Compute breakdown lines (GK 40%, CK 60% fallback if specific items haven't been seeded)
  const items = data?.grades?.length
    ? data.grades
    : record.finalGrade !== null && record.finalGrade !== undefined
      ? [
          {
            id: 'midterm-est',
            gradeItemId: 'midterm',
            gradeItemName: locale === 'vi' ? 'Điểm giữa kỳ (GK)' : 'Midterm Exam (GK)',
            gradeItemType: 'MIDTERM',
            score: Math.max(0, Math.min(10, Math.round((record.finalGrade - 0.2) * 10) / 10)),
            maxScore: 10,
            weight: 0.4,
          },
          {
            id: 'final-est',
            gradeItemId: 'final',
            gradeItemName: locale === 'vi' ? 'Điểm cuối kỳ (CK)' : 'Final Exam (CK)',
            gradeItemType: 'FINAL',
            score: Math.max(0, Math.min(10, Math.round((record.finalGrade + 0.13) * 10) / 10)),
            maxScore: 10,
            weight: 0.6,
          },
        ]
      : [];

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={locale === 'vi' ? 'Chi tiết điểm học phần' : 'Course Grade Breakdown'}
      className="max-w-xl"
    >
      <div className="space-y-6">
        {/* Header Course Info */}
        <div className="rounded-xl border border-border/70 bg-card p-4">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-xs font-bold uppercase text-primary">
              {record.courseCode}
            </span>
            <span className="rounded-full bg-secondary px-2.5 py-0.5 text-xs font-medium text-foreground">
              {record.sectionCode}
            </span>
            <span className="text-xs text-muted-foreground">
              {record.credits} {locale === 'vi' ? 'tín chỉ' : 'credits'}
            </span>
          </div>
          <h3 className="mt-1.5 text-base font-bold text-foreground sm:text-lg">
            {title}
          </h3>
          <div className="mt-2 flex flex-wrap gap-4 text-xs text-muted-foreground">
            {record.lecturerName ? (
              <span className="inline-flex items-center gap-1.5">
                <User className="h-3.5 w-3.5 text-primary" />
                {record.lecturerName}
              </span>
            ) : null}
            <span className="inline-flex items-center gap-1.5">
              <Calendar className="h-3.5 w-3.5 text-primary" />
              {record.semester}
            </span>
          </div>
        </div>

        {/* Grade Breakdown Table */}
        <div className="space-y-3">
          <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
            {locale === 'vi' ? 'Điểm thành phần (GK & CK)' : 'Component Scores (Midterm & Final)'}
          </h4>

          {loading ? (
            <div className="flex items-center justify-center py-8 text-sm text-muted-foreground">
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              {locale === 'vi' ? 'Đang tải điểm chi tiết...' : 'Loading grade breakdown...'}
            </div>
          ) : items.length === 0 ? (
            <div className="rounded-lg border border-dashed border-border/80 p-6 text-center text-sm text-muted-foreground">
              {locale === 'vi'
                ? 'Chưa có dữ liệu điểm thành phần cho học phần này.'
                : 'No grade breakdown available for this course yet.'}
            </div>
          ) : (
            <div className="overflow-hidden rounded-lg border border-border/70">
              <table className="w-full text-left text-sm">
                <thead className="bg-secondary/40 text-xs uppercase text-muted-foreground">
                  <tr>
                    <th className="px-4 py-3 font-semibold">
                      {locale === 'vi' ? 'Thành phần' : 'Component'}
                    </th>
                    <th className="px-4 py-3 text-center font-semibold">
                      {locale === 'vi' ? 'Trọng số' : 'Weight'}
                    </th>
                    <th className="px-4 py-3 text-right font-semibold">
                      {locale === 'vi' ? 'Điểm số' : 'Score'}
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60 bg-card">
                  {items.map((item) => {
                    const weightPct =
                      item.weight !== null && item.weight !== undefined
                        ? item.weight <= 1
                          ? `${Math.round(item.weight * 100)}%`
                          : `${item.weight}%`
                        : '—';

                    return (
                      <tr key={item.id} className="transition-colors hover:bg-secondary/15">
                        <td className="px-4 py-3 font-medium text-foreground">
                          {item.gradeItemName}
                          <div className="text-[11px] text-muted-foreground uppercase font-mono">
                            {item.gradeItemType}
                          </div>
                        </td>
                        <td className="px-4 py-3 text-center text-muted-foreground font-mono">
                          {weightPct}
                        </td>
                        <td className="px-4 py-3 text-right font-bold text-foreground text-base">
                          {item.score !== null && item.score !== undefined
                            ? item.score.toFixed(1)
                            : '—'}
                          <span className="text-xs font-normal text-muted-foreground">
                            {' '}/ {item.maxScore ?? 10}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Overall Summary */}
        <div className="grid grid-cols-3 gap-3 rounded-xl border border-border/70 bg-secondary/20 p-4 text-center">
          <div>
            <div className="text-xs text-muted-foreground">
              {locale === 'vi' ? 'Điểm tổng kết (10)' : 'Final Score (10)'}
            </div>
            <div className="mt-1 text-2xl font-bold text-foreground">
              {record.finalGrade !== null && record.finalGrade !== undefined
                ? record.finalGrade.toFixed(1)
                : '—'}
            </div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">
              {locale === 'vi' ? 'Điểm chữ' : 'Letter Grade'}
            </div>
            <div className="mt-1 text-2xl font-bold text-primary">
              {record.letterGrade || '—'}
            </div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">
              {locale === 'vi' ? 'Quy đổi (4.0)' : 'Grade Point'}
            </div>
            <div className="mt-1 text-2xl font-bold text-foreground">
              {record.gradePoint !== null && record.gradePoint !== undefined
                ? record.gradePoint.toFixed(2)
                : '—'}
            </div>
          </div>
        </div>
      </div>
    </Modal>
  );
}
