'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import type { KeyboardEvent as ReactKeyboardEvent, ReactNode } from 'react';
import { useSearchParams } from 'next/navigation';
import {
  ArrowLeft,
  BookOpen,
  Building2,
  ChevronLeft,
  ChevronRight,
  GraduationCap,
  LayoutGrid,
  List,
  Mail,
  MapPin,
  Plus,
  Search,
  Users,
  UsersRound,
} from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import { LinkButton } from '@/components/ui/link-button';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Modal } from '@/components/ui/modal';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { statusToneClass, type StatusTone } from '@/components/ui/status';
import { useAuth, useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { lecturersApi, departmentsApi } from '@/lib/api';
import { thesisApi, type ThesisRound, type ThesisTopic } from '@/lib/thesis-api';
import type { Lecturer, Department } from '@/types/api';
import { cn } from '@/lib/utils';

/** Cards rendered before the reader asks for more. */
const GRID_PAGE_SIZE = 12;
/** Supervision ceiling the directory assumes when a topic projection is absent. */
const DEFAULT_MAX_TOPICS = 5;

interface AdvisorWorkload {
  topicCount: number;
  maxTopics: number;
}

function getAdvisorFullName(lec: Lecturer): string {
  const parts = [lec.user?.lastName, lec.user?.firstName].filter(Boolean);
  if (parts.length > 0) {
    return parts.join(' ').trim();
  }
  // The record's own identifier, never a guessed name.
  return lec.employeeId || '—';
}

function getInitials(name: string): string {
  const letters = name
    .split(/\s+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase());
  if (letters.length === 0) return '—';
  return `${letters[0]}${letters[letters.length - 1]}`;
}

function workloadFor(map: Map<string, AdvisorWorkload>, lecturerId: string): AdvisorWorkload {
  return map.get(lecturerId) || { topicCount: 0, maxTopics: DEFAULT_MAX_TOPICS };
}

// ---------------------------------------------------------------------------
// Local presentation primitives (single design language, no vendor UI kit)
// ---------------------------------------------------------------------------

function StatusPill({ tone, children }: { tone: StatusTone; children: ReactNode }) {
  return (
    <span className={cn('inline-flex items-center rounded-md px-2 py-0.5 text-[11px] font-semibold', statusToneClass(tone))}>
      {children}
    </span>
  );
}

function AdvisorAvatar({ lecturer, size = 'md' }: { lecturer: Lecturer; size?: 'sm' | 'md' | 'lg' }) {
  const src = lecturer.user?.avatar || '';
  const name = getAdvisorFullName(lecturer);
  const sizeClass =
    size === 'lg' ? 'h-14 w-14 text-lg' : size === 'sm' ? 'h-9 w-9 text-xs' : 'h-11 w-11 text-sm';

  return (
    <span
      className={cn(
        'flex shrink-0 items-center justify-center overflow-hidden rounded-full border border-border bg-primary font-bold text-primary-foreground',
        sizeClass,
      )}
    >
      {src ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={src} alt="" className="h-full w-full object-cover" />
      ) : (
        <span aria-hidden="true">{getInitials(name)}</span>
      )}
    </span>
  );
}

function QuotaBar({ topicCount, maxTopics }: AdvisorWorkload) {
  const isAvailable = topicCount < maxTopics;
  const percent = maxTopics > 0 ? Math.min(100, Math.round((topicCount / maxTopics) * 100)) : 0;

  return (
    <div
      role="progressbar"
      aria-valuenow={percent}
      aria-valuemin={0}
      aria-valuemax={100}
      className="h-1.5 w-full overflow-hidden rounded-full bg-secondary"
    >
      <div
        className={cn('h-full rounded-full transition-[width]', isAvailable ? 'bg-primary' : 'bg-status-neutral')}
        style={{ width: `${percent}%` }}
      />
    </div>
  );
}

interface SegmentedOption<T extends string> {
  value: T;
  label: string;
  icon?: ReactNode;
}

interface SegmentedProps<T extends string> {
  value: T;
  onChange: (value: T) => void;
  options: SegmentedOption<T>[];
  ariaLabel: string;
}

function Segmented<T extends string>({ value, onChange, options, ariaLabel }: SegmentedProps<T>) {
  const handleKeyDown = (event: ReactKeyboardEvent<HTMLButtonElement>, index: number) => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    const offset = event.key === 'ArrowRight' ? 1 : -1;
    const nextIndex = (index + offset + options.length) % options.length;
    onChange(options[nextIndex].value);
  };

  return (
    <div
      role="radiogroup"
      aria-label={ariaLabel}
      className="inline-flex items-center gap-1 rounded-lg border border-border bg-secondary/40 p-1"
    >
      {options.map((option, index) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(option.value)}
            onKeyDown={(event) => handleKeyDown(event, index)}
            className={cn(
              'inline-flex h-9 items-center gap-1.5 rounded-md px-3 text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
              selected
                ? 'bg-primary text-primary-foreground'
                : 'text-muted-foreground hover:bg-background hover:text-foreground',
            )}
          >
            {option.icon ? <span aria-hidden="true">{option.icon}</span> : null}
            <span>{option.label}</span>
          </button>
        );
      })}
    </div>
  );
}

interface TableColumn<T> {
  key: string;
  title: string;
  align?: 'left' | 'right';
  render: (row: T) => ReactNode;
}

interface DataTableProps<T> {
  columns: TableColumn<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  caption: string;
}

function DataTable<T>({ columns, rows, rowKey, caption }: DataTableProps<T>) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[880px] border-collapse text-left text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b border-border bg-secondary/40">
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={cn(
                  'px-4 py-3 text-xs font-bold uppercase tracking-wide text-muted-foreground',
                  column.align === 'right' && 'text-right',
                )}
              >
                {column.title}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)} className="border-b border-border/70 last:border-b-0 hover:bg-secondary/30">
              {columns.map((column) => (
                <td
                  key={column.key}
                  className={cn('px-4 py-3 align-middle', column.align === 'right' && 'text-right')}
                >
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------

export default function ThesisAdvisorDirectoryPage() {
  const { user, isLoading: authLoading, hasAccess, isForbidden } = useRequireAuth();
  const { messages, locale } = useI18n();
  const searchParams = useSearchParams();
  const preselectedRoundId = searchParams.get('roundId') || '';

  const userRoles = user?.roles || (user?.role ? [user.role] : []);
  const isStudent = userRoles.includes('STUDENT');

  const [lecturers, setLecturers] = useState<Lecturer[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [topics, setTopics] = useState<ThesisTopic[]>([]);
  // 'failed' is an outage; 'ready' with an empty list is a genuinely
  // unpopulated directory. The two never render the same message.
  const [loadState, setLoadState] = useState<'loading' | 'ready' | 'failed'>('loading');
  const [error, setError] = useState('');
  const [reloadToken, setReloadToken] = useState(0);

  // Filtering states
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<string>('all');
  const [quotaFilter, setQuotaFilter] = useState<'all' | 'available' | 'full'>('all');
  const [viewMode, setViewMode] = useState<'grid' | 'table'>('grid');

  // Pagination states
  const [tablePage, setTablePage] = useState(1);
  const [tablePageSize, setTablePageSize] = useState(10);
  const [gridVisibleCount, setGridVisibleCount] = useState(GRID_PAGE_SIZE);

  // Modal detail state
  const [selectedAdvisor, setSelectedAdvisor] = useState<Lecturer | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const loadFailedMessage = messages.thesis.loadFailed;

  useEffect(() => {
    let active = true;

    // The directory endpoint is intentionally staff-only (PeopleReadController
    // pins STUDENT -> 403 in its boundary tests). Firing it for a student would
    // manufacture a 403 and an outage-looking error state, so students get the
    // honest restricted panel instead (rendered below, before any data is used).
    if (isStudent) {
      return () => {
        active = false;
      };
    }

    async function loadData() {
      setLoadState('loading');
      setError('');

      const [lecResult, deptRes, roundsList] = await Promise.all([
        // The directory is the one required input. Its failure is reported
        // below; it is never silently replaced with invented faculty.
        lecturersApi
          .getAll({ limit: 100 })
          .then((res) => ({
            ok: true as const,
            data: Array.isArray(res?.data) ? res.data : [],
          }))
          .catch(() => ({ ok: false as const, data: [] as Lecturer[] })),
        // Departments, rounds and topics only enrich the directory; losing
        // them degrades the enrichment, it does not invent a person.
        departmentsApi.getAll({ limit: 50 }).catch(() => ({ data: [] as Department[] })),
        thesisApi.listRounds().catch(() => [] as ThesisRound[]),
      ]);

      if (!active) return;

      setDepartments(Array.isArray(deptRes?.data) ? deptRes.data : []);

      if (!lecResult.ok) {
        setLecturers([]);
        setError(loadFailedMessage);
        setLoadState('failed');
        return;
      }

      setLecturers(lecResult.data);
      setLoadState('ready');

      const targetRound =
        roundsList.find((r) => r.id === preselectedRoundId) || roundsList[0];
      if (targetRound) {
        const topicList = await thesisApi
          .listTopics(targetRound.id, 'PUBLISHED')
          .catch(() => [] as ThesisTopic[]);
        if (active) setTopics(topicList);
      }
    }

    void loadData();
    return () => {
      active = false;
    };
  }, [locale, preselectedRoundId, reloadToken, loadFailedMessage, isStudent]);

  // Advisor workload calculation (how many published topics each lecturer supervises)
  const advisorWorkloadMap = useMemo(() => {
    const map = new Map<string, AdvisorWorkload>();
    topics.forEach((topic) => {
      // ThesisTopic payloads do not carry supervisors yet; the optional
      // projection keeps the workload map honest (empty when absent).
      (topic as ThesisTopic & { supervisors?: Array<{ lecturerId: string }> })
        .supervisors?.forEach((sup) => {
          const current = map.get(sup.lecturerId) || { topicCount: 0, maxTopics: DEFAULT_MAX_TOPICS };
          map.set(sup.lecturerId, { ...current, topicCount: current.topicCount + 1 });
        });
    });
    return map;
  }, [topics]);

  // Filtered lecturers list based on search, department, and quota
  const filteredLecturers = useMemo(() => {
    return lecturers.filter((lec) => {
      // 1. Search filter
      const q = searchQuery.toLowerCase().trim();
      const fullName = getAdvisorFullName(lec).toLowerCase();
      const empId = (lec.employeeId || '').toLowerCase();
      const email = (lec.user?.email || '').toLowerCase();
      const spec = (lec.specialization || '').toLowerCase();
      const matchQuery = !q || fullName.includes(q) || empId.includes(q) || email.includes(q) || spec.includes(q);

      // 2. Department filter
      const matchDept = selectedDepartmentId === 'all' || lec.departmentId === selectedDepartmentId;

      // 3. Quota filter
      const workload = workloadFor(advisorWorkloadMap, lec.id);
      const isAvailable = workload.topicCount < workload.maxTopics;
      const matchQuota =
        quotaFilter === 'all' ||
        (quotaFilter === 'available' && isAvailable) ||
        (quotaFilter === 'full' && !isAvailable);

      return matchQuery && matchDept && matchQuota;
    });
  }, [lecturers, searchQuery, selectedDepartmentId, quotaFilter, advisorWorkloadMap]);

  // Statistics counters
  const stats = useMemo(() => {
    const total = lecturers.length;
    const available = lecturers.filter((l) => {
      const w = workloadFor(advisorWorkloadMap, l.id);
      return w.topicCount < w.maxTopics;
    }).length;
    return {
      total,
      available,
      departmentsCount: departments.length,
      activeTopicsCount: topics.length,
    };
  }, [lecturers, departments, topics, advisorWorkloadMap]);

  // A narrowing filter re-pages the result set from the top.
  useEffect(() => {
    setTablePage(1);
    setGridVisibleCount(GRID_PAGE_SIZE);
  }, [searchQuery, selectedDepartmentId, quotaFilter]);

  const retryLoad = useCallback(() => {
    setReloadToken((token) => token + 1);
  }, []);

  const isVi = locale === 'vi';
  const st = messages.common.states;

  const departmentName = (departmentId?: string) => {
    if (!departmentId) return '—';
    const dept = departments.find((d) => d.id === departmentId);
    if (!dept) return departmentId;
    return (isVi ? dept.nameVi || dept.name : dept.nameEn || dept.name) || departmentId;
  };

  const pageCount = Math.max(1, Math.ceil(filteredLecturers.length / tablePageSize));
  const currentTablePage = Math.min(tablePage, pageCount);
  const pagedLecturers = filteredLecturers.slice(
    (currentTablePage - 1) * tablePageSize,
    currentTablePage * tablePageSize,
  );
  const visibleLecturers = filteredLecturers.slice(0, gridVisibleCount);
  const filtersActive =
    searchQuery.trim() !== '' || selectedDepartmentId !== 'all' || quotaFilter !== 'all';

  const resetFilters = () => {
    setSearchQuery('');
    setSelectedDepartmentId('all');
    setQuotaFilter('all');
  };

  const openAdvisor = (lecturer: Lecturer) => {
    setSelectedAdvisor(lecturer);
    setIsModalOpen(true);
  };

  if (authLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  if (isStudent) {
    return (
      <div className="space-y-6">
        <PageHeader
          eyebrow={<SectionEyebrow>{isVi ? 'Khóa luận' : 'Thesis'}</SectionEyebrow>}
          title={isVi ? 'Danh bạ giảng viên hướng dẫn' : 'Advisor directory'}
          description={
            isVi
              ? 'Danh bạ liên hệ chi tiết của giảng viên do Phòng Đào tạo quản lý và dành cho giảng viên, quản trị viên.'
              : 'The detailed faculty contact directory is maintained by the Academic Office and is available to lecturers and administrators.'
          }
        />
        <EmptyState
          icon={UsersRound}
          title={isVi ? 'Mục này dành cho giảng viên và quản trị viên' : 'This section is for lecturers and administrators'}
          description={
            isVi
              ? 'Thông tin người hướng dẫn theo từng đề tài có trong Danh mục đề tài — nơi bạn chọn đề tài và xem giảng viên phụ trách.'
              : 'Per-topic supervisor information lives in the topic catalog, where you pick a topic and see its supervising lecturer.'
          }
        />
        <div>
          <LinkButton href="/dashboard/thesis/topics">
            {isVi ? 'Mở danh mục đề tài' : 'Open the topic catalog'}
          </LinkButton>
        </div>
      </div>
    );
  }

  const tableColumns: TableColumn<Lecturer>[] = [
    {
      key: 'name',
      title: isVi ? 'Giảng viên hướng dẫn' : 'Lecturer & Advisor',
      render: (record) => {
        const titlePrefix = record.title ? `${record.title} ` : '';
        const name = getAdvisorFullName(record);
        return (
          <div className="flex items-center gap-3">
            <AdvisorAvatar lecturer={record} />
            <div className="min-w-0">
              <div className="text-sm font-bold text-foreground">
                {titlePrefix}{name}
              </div>
              <div className="flex items-center gap-1 font-mono text-xs text-muted-foreground">
                <span>{record.employeeId || '—'}</span>
                {record.user?.email ? (
                  <>
                    <span aria-hidden="true">•</span>
                    <span className="truncate">{record.user.email}</span>
                  </>
                ) : null}
              </div>
            </div>
          </div>
        );
      },
    },
    {
      key: 'department',
      title: isVi ? 'Khoa / Bộ môn' : 'Department',
      render: (record) => (
        <StatusPill tone="info">{departmentName(record.departmentId)}</StatusPill>
      ),
    },
    {
      key: 'specialization',
      title: isVi ? 'Chuyên môn & Hướng nghiên cứu' : 'Research Specialization',
      render: (record) => (
        <div className="max-w-xs truncate text-xs text-foreground" title={record.specialization || undefined}>
          {record.specialization || '—'}
        </div>
      ),
    },
    {
      key: 'workload',
      title: isVi ? 'Tải hướng dẫn' : 'Supervision Quota',
      render: (record) => {
        const workload = workloadFor(advisorWorkloadMap, record.id);
        const isAvailable = workload.topicCount < workload.maxTopics;
        return (
          <div className="w-36 space-y-1">
            <div className="flex justify-between text-xs font-semibold">
              <span>{workload.topicCount}/{workload.maxTopics} {isVi ? 'đề tài' : 'topics'}</span>
              <span className={isAvailable ? 'text-status-success-foreground' : 'text-muted-foreground'}>
                {isAvailable ? (isVi ? 'Còn nhận' : 'Available') : (isVi ? 'Đủ' : 'Full')}
              </span>
            </div>
            <QuotaBar {...workload} />
          </div>
        );
      },
    },
    {
      key: 'action',
      title: isVi ? 'Thao tác' : 'Actions',
      align: 'right',
      render: (record) => (
        <div className="flex items-center justify-end gap-2">
          <Button type="button" variant="outline" size="sm" className="h-9 px-3 text-xs" onClick={() => openAdvisor(record)}>
            {isVi ? 'Chi tiết' : 'Details'}
          </Button>
          <LinkButton
            href={`/dashboard/thesis?advisorId=${record.id}&action=propose`}
            size="sm"
            className="h-9 px-3 text-xs"
          >
            {isVi ? 'Chọn GV' : 'Select'}
          </LinkButton>
        </div>
      ),
    },
  ];

  return (
    <div className="mx-auto max-w-[1280px] space-y-6 pb-12">
      {/* Top Header & Breadcrumb Navigation */}
      <PageHeader
        eyebrow={
          <SectionEyebrow>
            <LocalizedLink href="/dashboard/thesis" className="flex items-center gap-1 hover:underline">
              <span>{messages.thesis.title}</span>
              <span aria-hidden="true">/</span>
              <span>{isVi ? 'Giảng viên hướng dẫn' : 'Advisors'}</span>
            </LocalizedLink>
          </SectionEyebrow>
        }
        title={isVi ? 'Danh sách Giảng viên Hướng dẫn' : 'Faculty Thesis Advisors'}
        tabLabel={isVi ? 'Giảng viên hướng dẫn' : 'Advisors'}
        description={
          isVi
            ? 'Tra cứu thông tin học hàm, hướng nghiên cứu và liên hệ đăng ký giảng viên hướng dẫn khóa luận tốt nghiệp (HCMUTE).'
            : 'Explore faculty research interests, academic rank, and contact advisors for thesis supervision.'
        }
        actions={
          <div className="flex items-center gap-3">
            <LinkButton href="/dashboard/thesis" variant="outline" size="sm" className="gap-1.5">
              <ArrowLeft className="h-4 w-4" aria-hidden="true" />
              <span>{isVi ? 'Quay lại Khóa luận' : 'Back to Thesis'}</span>
            </LinkButton>
            {isStudent ? (
              <LinkButton href="/dashboard/thesis?action=propose" size="sm" className="gap-1.5">
                <Plus className="h-4 w-4" aria-hidden="true" />
                <span>{isVi ? 'Đề xuất đề tài mới' : 'Propose Topic'}</span>
              </LinkButton>
            ) : null}
          </div>
        }
      />

      {/* Executive Faculty & Defense Council Banner */}
      <div className="overflow-hidden rounded-2xl border border-border/80 bg-card text-card-foreground shadow-xs">
        <div className="grid grid-cols-1 lg:grid-cols-12">
          <div className="flex flex-col justify-center space-y-2.5 p-6 lg:col-span-8">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-md border border-primary/20 bg-primary/10 px-2.5 py-0.5 text-[11px] font-bold uppercase tracking-wider text-primary">
                {isVi ? 'Hội đồng Khoa học & Đào tạo' : 'Academic Council & Faculty'}
              </span>
              <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-semibold text-muted-foreground">
                HCMUTE Thesis Committee
              </span>
            </div>
            <h2 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
              {isVi ? 'Danh bạ Giảng viên Hướng dẫn & Hội đồng Bảo vệ' : 'Faculty Advisor Directory & Defense Council'}
            </h2>
            <p className="text-xs leading-relaxed text-muted-foreground sm:text-sm">
              {isVi
                ? 'Tra cứu thông tin học hàm, học vị, hướng nghiên cứu mũi nhọn, chỉ tiêu nhận sinh viên khóa luận và liên hệ trực tiếp với các Thầy/Cô thuộc các Khoa viện.'
                : 'Browse faculty profiles, academic degrees, research specializations, thesis student capacity, and direct contact details.'}
            </p>
          </div>
          <div className="relative h-48 min-h-[160px] overflow-hidden border-t border-border/60 lg:col-span-4 lg:h-full lg:border-l lg:border-t-0">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src="/images/banners/thesis_evaluation_council.jpg"
              alt="HCMUTE Academic Thesis Evaluation Council"
              className="h-full w-full object-cover object-center transition duration-500 hover:scale-105"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/50 via-transparent to-transparent lg:hidden" />
            <div className="absolute bottom-2 left-3 rounded-md bg-background/85 px-2 py-0.5 text-[11px] font-semibold text-foreground backdrop-blur-xs">
              {isVi ? 'Hội đồng Bảo vệ Khóa luận HCMUTE' : 'HCMUTE Thesis Defense Council'}
            </div>
          </div>
        </div>
      </div>

      {loadState === 'loading' ? (
        <LoadingState label={messages.thesis.loading} />
      ) : loadState === 'failed' ? (
        // An outage is an outage: the directory is not rendered at all, so a
        // failure can never be mistaken for "no advisors exist".
        <ErrorState
          title={isVi ? 'Không thể tải danh bạ giảng viên' : 'Advisor directory unavailable'}
          description={`${error} ${isVi ? 'Danh bạ bên dưới chưa được tải nên không hiển thị.' : 'Nothing below is rendered, because the directory did not load.'}`}
          onRetry={retryLoad}
        />
      ) : (
        <>
          {/* Overview Stat Ribbon - Stitch Style */}
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <div className="rounded-xl border border-border/80 bg-card p-4 shadow-2xs">
              <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {isVi ? 'Tổng số Giảng viên' : 'Total Faculty'}
              </p>
              <p className="mt-1 text-2xl font-bold text-foreground">{stats.total}</p>
            </div>
            <div className="rounded-xl border border-border/80 bg-card p-4 shadow-2xs">
              <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {isVi ? 'Khoa & Bộ môn' : 'Departments'}
              </p>
              <p className="mt-1 text-2xl font-bold text-primary">{stats.departmentsCount}</p>
            </div>
            <div className="rounded-xl border border-border/80 bg-card p-4 shadow-2xs">
              <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {isVi ? 'Đang mở nhận hướng dẫn' : 'Accepting Students'}
              </p>
              <p className="mt-1 text-2xl font-bold text-status-success-foreground">{stats.available}</p>
            </div>
            <div className="rounded-xl border border-border/80 bg-card p-4 shadow-2xs">
              <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {isVi ? 'Đề tài đang triển khai' : 'Active Theses'}
              </p>
              <p className="mt-1 text-2xl font-bold text-foreground">{stats.activeTopicsCount}</p>
            </div>
          </div>

          {/* Filter Bar */}
          <div className="flex flex-col items-center justify-between gap-3 rounded-xl border border-border/80 bg-card p-4 shadow-2xs md:flex-row">
            <div className="flex w-full flex-1 flex-wrap items-center gap-3 md:w-auto">
              <div className="w-full sm:w-72">
                <Input
                  type="search"
                  aria-label={isVi ? 'Tìm giảng viên hướng dẫn' : 'Search advisors'}
                  placeholder={isVi ? 'Tìm tên, mã GV, email, hướng nghiên cứu...' : 'Search by name, email, keyword...'}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  icon={<Search className="h-4 w-4" aria-hidden="true" />}
                  endAction={
                    searchQuery ? (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="h-8 px-2 text-xs"
                        onClick={() => setSearchQuery('')}
                      >
                        {isVi ? 'Xóa' : 'Clear'}
                      </Button>
                    ) : undefined
                  }
                />
              </div>

              <div className="w-full sm:w-60">
                <Select
                  aria-label={isVi ? 'Lọc theo khoa' : 'Filter by department'}
                  value={selectedDepartmentId}
                  onChange={(e) => setSelectedDepartmentId(e.target.value)}
                  options={[
                    { value: 'all', label: isVi ? 'Tất cả Khoa / Bộ môn' : 'All Departments' },
                    ...departments.map((d) => ({
                      value: d.id,
                      label: (isVi ? d.nameVi || d.name : d.nameEn || d.name) || d.name,
                    })),
                  ]}
                />
              </div>

              <div className="w-full sm:w-48">
                <Select
                  aria-label={isVi ? 'Lọc theo chỉ tiêu' : 'Filter by quota'}
                  value={quotaFilter}
                  onChange={(e) => setQuotaFilter(e.target.value as 'all' | 'available' | 'full')}
                  options={[
                    { value: 'all', label: isVi ? 'Tất cả chỉ tiêu' : 'All Quotas' },
                    { value: 'available', label: isVi ? 'Còn nhận hướng dẫn' : 'Available' },
                    { value: 'full', label: isVi ? 'Đã đủ chỉ tiêu' : 'Quota full' },
                  ]}
                />
              </div>
            </div>

            <div className="flex shrink-0 items-center gap-2 self-end md:self-auto">
              <Segmented
                ariaLabel={isVi ? 'Chế độ hiển thị' : 'Display mode'}
                value={viewMode}
                onChange={setViewMode}
                options={[
                  { value: 'grid', icon: <LayoutGrid className="h-4 w-4" />, label: isVi ? 'Lưới thẻ' : 'Cards' },
                  { value: 'table', icon: <List className="h-4 w-4" />, label: isVi ? 'Bảng' : 'Table' },
                ]}
              />
            </div>
          </div>

          {lecturers.length === 0 ? (
            <EmptyState
              icon={GraduationCap}
              title={isVi ? 'Chưa có giảng viên trong danh bạ' : 'No advisors in the directory yet'}
              description={
                isVi
                  ? 'Nhà trường chưa công bố giảng viên hướng dẫn nào. Vui lòng liên hệ Khoa để được hỗ trợ.'
                  : 'The faculty directory publishes no advisors yet. Contact your department office for assistance.'
              }
            />
          ) : filteredLecturers.length === 0 ? (
            <EmptyState
              icon={Search}
              title={isVi ? 'Không tìm thấy giảng viên nào phù hợp bộ lọc' : 'No advisors matched your filters'}
              description={
                isVi
                  ? 'Thử đổi từ khóa tìm kiếm hoặc bỏ bộ lọc khoa/chỉ tiêu.'
                  : 'Try a different keyword, or clear the department and quota filters.'
              }
              action={
                filtersActive ? (
                  <Button type="button" variant="outline" onClick={resetFilters}>
                    {isVi ? 'Bỏ tất cả bộ lọc' : 'Clear all filters'}
                  </Button>
                ) : undefined
              }
            />
          ) : viewMode === 'grid' ? (
            /* High-fidelity Card Grid */
            <div className="space-y-5">
              <div className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3">
                {visibleLecturers.map((lec) => {
                  const workload = workloadFor(advisorWorkloadMap, lec.id);
                  const isAvailable = workload.topicCount < workload.maxTopics;
                  const titlePrefix = lec.title ? `${lec.title} ` : '';
                  const fullName = getAdvisorFullName(lec);

                  return (
                    <Card
                      key={lec.id}
                      className="flex flex-col justify-between rounded-xl border-border/90 shadow-2xs transition-all duration-200 hover:border-primary hover:shadow-md"
                    >
                      <div className="flex h-full flex-col p-5">
                        <div>
                          {/* Top row: Avatar + Name + Department */}
                          <div className="flex items-start gap-3.5">
                            <AdvisorAvatar lecturer={lec} size="lg" />
                            <div className="min-w-0 flex-1">
                              <h3 className="truncate text-base font-bold text-foreground">
                                {titlePrefix}{fullName}
                              </h3>
                              <p className="mt-0.5 flex items-center gap-1.5 truncate text-xs font-medium text-muted-foreground">
                                <Building2 className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                                <span className="truncate">{departmentName(lec.departmentId)}</span>
                              </p>
                              <div className="mt-1.5">
                                <StatusPill tone={isAvailable ? 'success' : 'neutral'}>
                                  {isAvailable
                                    ? (isVi ? 'Nhận hướng dẫn' : 'Available')
                                    : (isVi ? 'Đã đủ chỉ tiêu' : 'Full quota')}
                                </StatusPill>
                              </div>
                            </div>
                          </div>

                          {/* Contact Info */}
                          <div className="mt-4 space-y-1.5 border-t border-border/70 pt-3 text-xs text-muted-foreground">
                            <div className="flex items-center gap-2 truncate">
                              <Mail className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                              <span className="truncate">{lec.user?.email || '—'}</span>
                            </div>
                            <div className="flex items-center gap-2 truncate">
                              <MapPin className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                              <span className="truncate">{lec.office || '—'}</span>
                            </div>
                          </div>

                          {/* Research Specialization */}
                          <div className="mt-3 flex flex-wrap gap-1.5">
                            {lec.specialization ? (
                              <StatusPill tone="neutral">{lec.specialization}</StatusPill>
                            ) : (
                              <span className="text-[11px] text-muted-foreground">—</span>
                            )}
                          </div>

                          {/* Supervision Quota & Metrics */}
                          <div className="mt-4 rounded-lg border border-border/70 bg-secondary/40 p-2.5">
                            <div className="mb-1.5 flex items-center justify-between text-xs">
                              <span className="flex items-center gap-1 text-muted-foreground">
                                <BookOpen className="h-3.5 w-3.5 text-primary" aria-hidden="true" />
                                <span>{isVi ? 'Tải hướng dẫn:' : 'Advising:'}</span>
                              </span>
                              <span className="font-bold text-foreground">
                                {workload.topicCount} / {workload.maxTopics} {isVi ? 'đề tài' : 'theses'}
                              </span>
                            </div>
                            <QuotaBar {...workload} />
                          </div>
                        </div>

                        {/* Card Actions */}
                        <div className="mt-5 flex gap-2 border-t border-border/70 pt-3">
                          <Button
                            type="button"
                            variant="outline"
                            className="h-9 flex-1 rounded-lg text-xs font-semibold"
                            onClick={() => openAdvisor(lec)}
                          >
                            {isVi ? 'Xem chi tiết' : 'View Bio'}
                          </Button>
                          <LinkButton
                            href={`/dashboard/thesis?advisorId=${lec.id}&action=propose`}
                            className="h-9 flex-1 justify-center rounded-lg text-xs font-semibold"
                          >
                            {isVi ? 'Chọn hướng dẫn' : 'Select'}
                          </LinkButton>
                        </div>
                      </div>
                    </Card>
                  );
                })}
              </div>

              {filteredLecturers.length > visibleLecturers.length ? (
                <div className="flex justify-center">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setGridVisibleCount((count) => count + GRID_PAGE_SIZE)}
                  >
                    {isVi
                      ? `Tải thêm giảng viên (${visibleLecturers.length}/${filteredLecturers.length})`
                      : `Load more advisors (${visibleLecturers.length}/${filteredLecturers.length})`}
                  </Button>
                </div>
              ) : null}
            </div>
          ) : (
            /* High-density data table */
            <div className="overflow-hidden rounded-xl border border-border/80 bg-card shadow-2xs">
              <DataTable
                columns={tableColumns}
                rows={pagedLecturers}
                rowKey={(row) => row.id}
                caption={isVi ? 'Danh sách giảng viên hướng dẫn' : 'Faculty thesis advisor directory'}
              />

              <div className="flex flex-col items-center justify-between gap-3 border-t border-border/70 px-4 py-3 sm:flex-row">
                <p className="text-xs text-muted-foreground">
                  {st.showingResults}{' '}
                  {(currentTablePage - 1) * tablePageSize + 1}
                  {' – '}
                  {(currentTablePage - 1) * tablePageSize + pagedLecturers.length} {st.of}{' '}
                  {filteredLecturers.length} {st.results}
                </p>

                <div className="flex items-center gap-2">
                  <div className="w-36">
                    <Select
                      aria-label={isVi ? 'Số dòng mỗi trang' : 'Rows per page'}
                      value={String(tablePageSize)}
                      onChange={(e) => {
                        setTablePageSize(Number(e.target.value) || 10);
                        setTablePage(1);
                      }}
                      options={[
                        { value: '10', label: st.perPage10 },
                        { value: '25', label: st.perPage25 },
                        { value: '50', label: st.perPage50 },
                      ]}
                    />
                  </div>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    className="h-9 w-9"
                    aria-label={st.goToPreviousPage}
                    disabled={currentTablePage <= 1}
                    onClick={() => setTablePage((page) => Math.max(1, page - 1))}
                  >
                    <ChevronLeft className="h-4 w-4" aria-hidden="true" />
                  </Button>
                  <span className="text-xs font-semibold text-foreground">
                    {st.page} {currentTablePage} / {pageCount}
                  </span>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    className="h-9 w-9"
                    aria-label={st.goToNextPage}
                    disabled={currentTablePage >= pageCount}
                    onClick={() => setTablePage((page) => Math.min(pageCount, page + 1))}
                  >
                    <ChevronRight className="h-4 w-4" aria-hidden="true" />
                  </Button>
                </div>
              </div>
            </div>
          )}
        </>
      )}

      {/* Advisor Detail Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={
          selectedAdvisor
            ? `${selectedAdvisor.title ? `${selectedAdvisor.title} ` : ''}${getAdvisorFullName(selectedAdvisor)}`
            : undefined
        }
        className="max-w-2xl"
      >
        {selectedAdvisor ? (
          <div className="space-y-4 py-2">
            <div className="flex items-center gap-3">
              <AdvisorAvatar lecturer={selectedAdvisor} size="lg" />
              <div className="min-w-0">
                <div className="font-mono text-xs text-muted-foreground">
                  {selectedAdvisor.employeeId || '—'} • {isVi ? 'Giảng viên cơ hữu HCMUTE' : 'HCMUTE Faculty Member'}
                </div>
                <div className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
                  <Users className="h-3.5 w-3.5" aria-hidden="true" />
                  <span>{departmentName(selectedAdvisor.departmentId)}</span>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3 rounded-lg border border-border/70 bg-secondary/40 p-3 text-xs">
              <div>
                <span className="block text-muted-foreground">{isVi ? 'Khoa / Bộ môn:' : 'Department:'}</span>
                <span className="font-semibold text-foreground">{departmentName(selectedAdvisor.departmentId)}</span>
              </div>
              <div>
                <span className="block text-muted-foreground">{isVi ? 'Email học thuật:' : 'Email:'}</span>
                <span className="font-semibold text-foreground">{selectedAdvisor.user?.email || '—'}</span>
              </div>
              <div>
                <span className="block text-muted-foreground">{isVi ? 'Phòng làm việc:' : 'Office:'}</span>
                <span className="font-semibold text-foreground">{selectedAdvisor.office || '—'}</span>
              </div>
              <div>
                <span className="block text-muted-foreground">{isVi ? 'Điện thoại cơ quan:' : 'Phone:'}</span>
                <span className="font-semibold text-foreground">{selectedAdvisor.phone || '—'}</span>
              </div>
            </div>

            <div>
              <h4 className="mb-1.5 text-xs font-bold uppercase tracking-wider text-muted-foreground">
                {isVi ? 'Định hướng nghiên cứu & Chuyên môn' : 'Research Interests & Fields'}
              </h4>
              <p className="text-sm leading-relaxed text-foreground">
                {selectedAdvisor.specialization || '—'}
              </p>
            </div>

            <div>
              <h4 className="mb-1.5 text-xs font-bold uppercase tracking-wider text-muted-foreground">
                {isVi ? 'Tải hướng dẫn hiện tại' : 'Current supervision load'}
              </h4>
              <div className="space-y-2 rounded-lg border border-border/70 bg-secondary/40 p-3">
                <div className="flex items-center justify-between text-xs font-semibold">
                  <span className="text-muted-foreground">
                    {isVi ? 'Số đề tài đang hướng dẫn' : 'Supervised topics'}
                  </span>
                  <span className="text-foreground">
                    {workloadFor(advisorWorkloadMap, selectedAdvisor.id).topicCount} /{' '}
                    {workloadFor(advisorWorkloadMap, selectedAdvisor.id).maxTopics}
                  </span>
                </div>
                <QuotaBar {...workloadFor(advisorWorkloadMap, selectedAdvisor.id)} />
              </div>
            </div>

            <div>
              <h4 className="mb-1.5 text-xs font-bold uppercase tracking-wider text-muted-foreground">
                {isVi ? 'Quy định hướng dẫn & Đăng ký' : 'Supervision Guidelines'}
              </h4>
              <ul className="list-disc space-y-1 pl-4 text-xs text-muted-foreground">
                <li>
                  {isVi
                    ? 'Mỗi đề tài có 1–2 giảng viên hướng dẫn (hướng dẫn chính và đồng hướng dẫn).'
                    : 'Each topic has 1–2 supervising lecturers (primary and co-supervisor).'}
                </li>
                <li>
                  {isVi
                    ? 'Mỗi nhóm đăng ký đúng một đề tài; giảng viên hướng dẫn phê duyệt hoặc từ chối kèm lý do.'
                    : 'Each group registers exactly one topic; the supervisor approves or rejects it with a reason.'}
                </li>
                <li>
                  {isVi
                    ? 'Sinh viên nên chuẩn bị đề cương sơ bộ trước khi liên hệ giảng viên.'
                    : 'Students should prepare an initial outline before contacting a supervisor.'}
                </li>
              </ul>
            </div>

            <div className="flex justify-end gap-2 border-t border-border/70 pt-4">
              <Button type="button" variant="outline" onClick={() => setIsModalOpen(false)}>
                {isVi ? 'Đóng' : 'Close'}
              </Button>
              <LinkButton
                href={`/dashboard/thesis?advisorId=${selectedAdvisor.id}&action=propose`}
                onClick={() => setIsModalOpen(false)}
              >
                {isVi ? 'Đề xuất đề tài cùng Giảng viên này' : 'Propose Topic with Advisor'}
              </LinkButton>
            </div>
          </div>
        ) : null}
      </Modal>
    </div>
  );
}
