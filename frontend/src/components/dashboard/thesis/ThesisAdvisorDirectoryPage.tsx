'use client';

import { useMemo, useState, useEffect } from 'react';
import { useSearchParams } from 'next/navigation';
import {
  Card,
  Input,
  Select,
  Tag,
  Button,
  Avatar,
  Badge,
  Progress,
  Modal,
  Table,
  Segmented,
  Tooltip,
  Empty,
  Spin,
  Alert,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  SearchOutlined,
  AppstoreOutlined,
  BarsOutlined,
  UserOutlined,
  MailOutlined,
  PhoneOutlined,
  HomeOutlined,
  BookOutlined,
  TeamOutlined,
  CheckCircleOutlined,
  ArrowRightOutlined,
  PlusOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { LocalizedLink } from '@/components/LocalizedLink';
import { LinkButton } from '@/components/ui/link-button';
import { useAuth, useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { LoadingState } from '@/components/ui/state-block';
import { lecturersApi, departmentsApi } from '@/lib/api';
import { thesisApi, type ThesisRound, type ThesisTopic } from '@/lib/thesis-api';
import type { Lecturer, Department } from '@/types/api';
import { FALLBACK_THESIS_ADVISORS } from '@/lib/thesis-advisors-data';

const { Search } = Input;

// Specialized academic domain mappings for HCMUTE professors
const ADVISOR_SPECIALIZATIONS: Record<string, string[]> = {
  default: ['Kỹ thuật phần mềm', 'Hệ thống thông tin', 'Phân tích dữ liệu'],
  cntt: ['Trí tuệ nhân tạo (AI)', 'Thị giác máy tính', 'Học sâu (Deep Learning)', 'Xử lý ngôn ngữ tự nhiên'],
  khmt: ['Khoa học dữ liệu', 'Thuật toán nâng cao', 'Tính toán hiệu năng cao', 'Machine Learning'],
  ktpm: ['Kiến trúc Microservices', 'DevOps & CI/CD', 'Phát triển Web/Mobile', 'Kiểm thử phần mềm tự động'],
  mmt: ['An toàn thông tin', 'Mạng máy tính & Điện toán đám mây', 'IoT & Hệ thống nhúng', 'Blockchain'],
};

function getAdvisorFullName(lec: Lecturer): string {
  const parts = [lec.user?.lastName, lec.user?.firstName].filter(Boolean);
  if (parts.length > 0) {
    return parts.join(' ').trim();
  }
  return lec.employeeId || 'Giảng viên';
}

export default function ThesisAdvisorDirectoryPage() {
  const { user, isLoading: authLoading, hasAccess, isForbidden } = useRequireAuth();
  const { messages, locale } = useI18n();
  const searchParams = useSearchParams();
  const preselectedRoundId = searchParams.get('roundId') || '';

  const [lecturers, setLecturers] = useState<Lecturer[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [rounds, setRounds] = useState<ThesisRound[]>([]);
  const [topics, setTopics] = useState<ThesisTopic[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Filtering states
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<string>('all');
  const [quotaFilter, setQuotaFilter] = useState<'all' | 'available' | 'full'>('all');
  const [viewMode, setViewMode] = useState<'grid' | 'table'>('grid');

  // Modal detail state
  const [selectedAdvisor, setSelectedAdvisor] = useState<Lecturer | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  // Load data
  useEffect(() => {
    let active = true;
    async function loadData() {
      setLoading(true);
      setError('');
      try {
        const [lecRes, deptRes, roundsList] = await Promise.all([
          lecturersApi.getAll({ limit: 100 }).catch(() => ({ data: [] })),
          departmentsApi.getAll({ limit: 50 }).catch(() => ({ data: [] })),
          thesisApi.listRounds().catch(() => []),
        ]);

        if (active) {
          const lecList = Array.isArray(lecRes?.data) && lecRes.data.length > 0 ? lecRes.data : FALLBACK_THESIS_ADVISORS;
          setLecturers(lecList);
          setDepartments(deptRes.data || []);
          setRounds(roundsList);

          // Find active or first round to count active supervision
          const targetRound = roundsList.find((r) => r.id === preselectedRoundId) || roundsList[0];
          if (targetRound) {
            const topicList = await thesisApi.listTopics(targetRound.id, 'PUBLISHED').catch(() => []);
            if (active) setTopics(topicList);
          }
        }
      } catch {
        if (active) setError(locale === 'vi' ? 'Không thể tải danh sách giảng viên.' : 'Failed to load lecturers.');
      } finally {
        if (active) setLoading(false);
      }
    }

    loadData();
    return () => {
      active = false;
    };
  }, [locale, preselectedRoundId]);

  // Advisor workload calculation (how many published topics each lecturer supervises)
  const advisorWorkloadMap = useMemo(() => {
    const map = new Map<string, { topicCount: number; maxTopics: number }>();
    topics.forEach((topic) => {
      // ThesisTopic payloads do not carry supervisors yet; the optional
      // projection keeps the workload map honest (empty when absent).
      (topic as ThesisTopic & { supervisors?: Array<{ lecturerId: string }> })
        .supervisors?.forEach((sup) => {
        const current = map.get(sup.lecturerId) || { topicCount: 0, maxTopics: 5 };
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
      const workload = advisorWorkloadMap.get(lec.id) || { topicCount: 0, maxTopics: 5 };
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
      const w = advisorWorkloadMap.get(l.id) || { topicCount: 0, maxTopics: 5 };
      return w.topicCount < w.maxTopics;
    }).length;
    return {
      total,
      available,
      departmentsCount: departments.length,
      activeTopicsCount: topics.length,
    };
  }, [lecturers, departments, topics, advisorWorkloadMap]);

  const userRoles = user?.roles || (user?.role ? [user.role] : []);
  const isStudent = userRoles.includes('STUDENT');

  if (authLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  const isVi = locale === 'vi';

  // Ant Design Table Columns Definition
  const columns: ColumnsType<Lecturer> = [
    {
      title: isVi ? 'Giảng viên hướng dẫn' : 'Lecturer & Advisor',
      key: 'name',
      render: (_, record) => {
        const titlePrefix = record.title ? `${record.title} ` : '';
        const name = getAdvisorFullName(record);
        return (
          <div className="flex items-center gap-3">
            <Avatar
              size={42}
              icon={<UserOutlined />}
              src={record.user?.avatar}
              className="bg-[#003f87] text-white shrink-0 font-bold"
            >
              {name.charAt(0).toUpperCase()}
            </Avatar>
            <div className="min-w-0">
              <div className="font-bold text-sm text-foreground hover:text-primary transition-colors">
                {titlePrefix}{name}
              </div>
              <div className="text-xs text-muted-foreground flex items-center gap-1 font-mono">
                <span>{record.employeeId}</span>
                {record.user?.email ? (
                  <>
                    <span>•</span>
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
      title: isVi ? 'Khoa / Bộ môn' : 'Department',
      dataIndex: 'departmentId',
      key: 'department',
      render: (deptId) => {
        const dept = departments.find((d) => d.id === deptId);
        const deptName = isVi ? (dept?.nameVi || dept?.name) : (dept?.nameEn || dept?.name);
        return <Tag color="blue" className="font-medium text-xs py-0.5 px-2">{deptName || deptId || '—'}</Tag>;
      },
    },
    {
      title: isVi ? 'Chuyên môn & Hướng nghiên cứu' : 'Research Specialization',
      key: 'specialization',
      render: (_, record) => {
        const spec = record.specialization || (isVi ? 'Công nghệ phần mềm & Hệ thống' : 'Software & Systems');
        return (
          <div className="text-xs text-slate-700 dark:text-slate-300 max-w-xs truncate" title={spec}>
            {spec}
          </div>
        );
      },
    },
    {
      title: isVi ? 'Tải hướng dẫn' : 'Supervision Quota',
      key: 'workload',
      render: (_, record) => {
        const workload = advisorWorkloadMap.get(record.id) || { topicCount: 0, maxTopics: 5 };
        const percent = Math.min(100, Math.round((workload.topicCount / workload.maxTopics) * 100));
        const isAvailable = workload.topicCount < workload.maxTopics;
        return (
          <div className="w-36 space-y-1">
            <div className="flex justify-between text-xs font-semibold">
              <span>{workload.topicCount}/{workload.maxTopics} {isVi ? 'đề tài' : 'topics'}</span>
              <span className={isAvailable ? 'text-emerald-600' : 'text-slate-400'}>
                {isAvailable ? (isVi ? 'Còn nhận' : 'Available') : (isVi ? 'Đủ' : 'Full')}
              </span>
            </div>
            <Progress
              percent={percent}
              size="small"
              status={isAvailable ? 'active' : 'normal'}
              strokeColor={isAvailable ? '#003f87' : '#94a3b8'}
              showInfo={false}
            />
          </div>
        );
      },
    },
    {
      title: isVi ? 'Thao tác' : 'Actions',
      key: 'action',
      align: 'right',
      render: (_, record) => (
        <div className="flex items-center justify-end gap-2">
          <Button
            size="small"
            onClick={() => {
              setSelectedAdvisor(record);
              setIsModalOpen(true);
            }}
          >
            {isVi ? 'Chi tiết' : 'Details'}
          </Button>
          <LinkButton
            href={`/dashboard/thesis?advisorId=${record.id}&action=propose`}
            size="sm"
            className="h-7 px-2.5 text-xs font-semibold bg-[#003f87] text-white hover:bg-[#002a5d]"
          >
            {isVi ? 'Chọn GV' : 'Select'}
          </LinkButton>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6 max-w-[1280px] mx-auto pb-12">
      {/* Top Header & Breadcrumb Navigation */}
      <PageHeader
        eyebrow={
          <SectionEyebrow>
            <LocalizedLink href="/dashboard/thesis" className="hover:underline flex items-center gap-1">
              <span>{messages.thesis.title}</span>
              <span>/</span>
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
              <ArrowRightOutlined rotate={180} />
              <span>{isVi ? 'Quay lại Khóa luận' : 'Back to Thesis'}</span>
            </LinkButton>
            {isStudent ? (
              <LinkButton
                href="/dashboard/thesis?action=propose"
                size="sm"
                className="bg-[#003f87] text-white hover:bg-[#002a5d] gap-1.5"
              >
                <PlusOutlined />
                <span>{isVi ? 'Đề xuất đề tài mới' : 'Propose Topic'}</span>
              </LinkButton>
            ) : null}
          </div>
        }
      />

      {error ? <Alert message={error} type="error" showIcon closable /> : null}

      {/* Executive Faculty & Defense Council Banner */}
      <div className="overflow-hidden rounded-2xl border border-border/80 bg-card text-card-foreground shadow-xs">
        <div className="grid grid-cols-1 lg:grid-cols-12">
          <div className="flex flex-col justify-center p-6 lg:col-span-8 space-y-2.5">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-md bg-primary/10 px-2.5 py-0.5 text-[11px] font-bold uppercase tracking-wider text-primary border border-primary/20">
                {isVi ? 'Hội đồng Khoa học & Đào tạo' : 'Academic Council & Faculty'}
              </span>
              <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-semibold text-muted-foreground">
                HCMUTE Thesis Committee 2024-2025
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
          <div className="relative h-48 lg:h-full lg:col-span-4 overflow-hidden border-t lg:border-t-0 lg:border-l border-border/60 min-h-[160px]">
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

      {/* Overview Stat Ribbon - Stitch Style */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="rounded-xl border border-slate-200/80 bg-white p-4 shadow-2xs dark:border-slate-800 dark:bg-slate-900">
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {isVi ? 'Tổng số Giảng viên' : 'Total Faculty'}
          </p>
          <p className="mt-1 text-2xl font-bold text-foreground">{lecturers.length}</p>
        </div>
        <div className="rounded-xl border border-slate-200/80 bg-white p-4 shadow-2xs dark:border-slate-800 dark:bg-slate-900">
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {isVi ? 'Khoa & Bộ môn' : 'Departments'}
          </p>
          <p className="mt-1 text-2xl font-bold text-primary">{departments.length}</p>
        </div>
        <div className="rounded-xl border border-slate-200/80 bg-white p-4 shadow-2xs dark:border-slate-800 dark:bg-slate-900">
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {isVi ? 'Đang mở nhận hướng dẫn' : 'Accepting Students'}
          </p>
          <p className="mt-1 text-2xl font-bold text-emerald-600">
            {
              lecturers.filter((l) => {
                const w = advisorWorkloadMap.get(l.id) || { topicCount: 0, maxTopics: 5 };
                return w.topicCount < w.maxTopics;
              }).length
            }
          </p>
        </div>
        <div className="rounded-xl border border-slate-200/80 bg-white p-4 shadow-2xs dark:border-slate-800 dark:bg-slate-900">
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {isVi ? 'Đề tài đang triển khai' : 'Active Theses'}
          </p>
          <p className="mt-1 text-2xl font-bold text-foreground">{topics.length}</p>
        </div>
      </div>

      {/* Ant Design Filter Bar (Stitch Screen 538cba8b58674dfc93d356072df75498) */}
      <div className="rounded-xl border border-slate-200/80 bg-white p-4 shadow-2xs dark:border-slate-800 dark:bg-slate-900 flex flex-col md:flex-row items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3 w-full md:w-auto flex-1">
          <div className="w-full sm:w-72">
            <Search
              placeholder={isVi ? 'Tìm tên, mã GV, email, hướng nghiên cứu...' : 'Search by name, email, keyword...'}
              allowClear
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              prefix={<SearchOutlined className="text-slate-400" />}
            />
          </div>

          <div className="w-full sm:w-60">
            <Select
              className="w-full"
              value={selectedDepartmentId}
              onChange={setSelectedDepartmentId}
              options={[
                { value: 'all', label: isVi ? 'Tất cả Khoa / Bộ môn' : 'All Departments' },
                ...departments.map((d) => ({
                  value: d.id,
                  label: isVi ? (d.nameVi || d.name) : (d.nameEn || d.name),
                })),
              ]}
            />
          </div>

          <div className="w-full sm:w-48">
            <Select
              className="w-full"
              value={quotaFilter}
              onChange={setQuotaFilter}
              options={[
                { value: 'all', label: isVi ? 'Tất cả chỉ tiêu' : 'All Quotas' },
                { value: 'available', label: isVi ? 'Còn nhận hướng dẫn' : 'Available' },
                { value: 'full', label: isVi ? 'Đã đủ chỉ tiêu' : 'Quota full' },
              ]}
            />
          </div>
        </div>

        <div className="flex items-center gap-2 shrink-0 self-end md:self-auto">
          <Segmented
            value={viewMode}
            onChange={(val) => setViewMode(val as 'grid' | 'table')}
            options={[
              { value: 'grid', icon: <AppstoreOutlined />, label: isVi ? 'Lưới thẻ' : 'Cards' },
              { value: 'table', icon: <BarsOutlined />, label: isVi ? 'Bảng' : 'Table' },
            ]}
          />
        </div>
      </div>

      {/* Main Content: Grid vs Table */}
      {loading ? (
        <div className="py-20 flex flex-col items-center justify-center gap-3">
          <Spin size="large" />
          <p className="text-sm text-muted-foreground">{isVi ? 'Đang tải danh sách giảng viên...' : 'Loading advisors...'}</p>
        </div>
      ) : filteredLecturers.length === 0 ? (
        <Empty
          description={
            <span className="text-muted-foreground">
              {isVi ? 'Không tìm thấy giảng viên nào phù hợp bộ lọc.' : 'No advisors matched your filters.'}
            </span>
          }
          className="my-16"
        />
      ) : viewMode === 'grid' ? (
        /* High-fidelity Card Grid matching Stitch Screen 538cba8b58674dfc93d356072df75498 */
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredLecturers.map((lec) => {
            const workload = advisorWorkloadMap.get(lec.id) || { topicCount: 0, maxTopics: 5 };
            const isAvailable = workload.topicCount < workload.maxTopics;
            const titlePrefix = lec.title ? `${lec.title} ` : '';
            const fullName = getAdvisorFullName(lec);
            const dept = departments.find((d) => d.id === lec.departmentId);
            const deptName = isVi ? (dept?.nameVi || dept?.name) : (dept?.nameEn || dept?.name);

            // Tags based on dept or specialization
            const tags =
              ADVISOR_SPECIALIZATIONS[dept?.code?.toLowerCase() || ''] ||
              (lec.specialization ? [lec.specialization] : ADVISOR_SPECIALIZATIONS.default);

            return (
              <Card
                key={lec.id}
                hoverable
                className="flex flex-col justify-between border-slate-200/90 shadow-2xs transition-all duration-200 hover:border-[#003f87] hover:shadow-md dark:border-slate-800 dark:bg-slate-900 rounded-xl overflow-hidden group"
                bodyStyle={{ padding: '1.25rem', height: '100%', display: 'flex', flexDirection: 'column' }}
              >
                <div>
                  {/* Top row: Avatar + Name + Department Tag */}
                  <div className="flex items-start gap-3.5">
                    <Avatar
                      size={54}
                      icon={<UserOutlined />}
                      src={lec.user?.avatar}
                      className="bg-[#003f87] text-white shrink-0 font-bold border-2 border-slate-100 dark:border-slate-800"
                    >
                      {fullName.charAt(0).toUpperCase()}
                    </Avatar>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between gap-1">
                        <h3 className="text-base font-bold text-foreground group-hover:text-primary transition-colors truncate">
                          {titlePrefix}{fullName}
                        </h3>
                      </div>
                      <p className="text-xs text-secondary font-medium mt-0.5 truncate">
                        {deptName || (isVi ? 'Khoa Công nghệ Thông tin' : 'Faculty of IT')}
                      </p>
                      <div className="mt-1.5 flex items-center gap-1.5 font-mono text-[11px] text-muted-foreground">
                        <Badge status={isAvailable ? 'success' : 'default'} />
                        <span>{isAvailable ? (isVi ? 'Nhận hướng dẫn' : 'Available') : (isVi ? 'Đã đủ chỉ tiêu' : 'Full quota')}</span>
                      </div>
                    </div>
                  </div>

                  {/* Contact Info */}
                  <div className="mt-4 pt-3 border-t border-slate-100 dark:border-slate-800 space-y-1.5 text-xs text-muted-foreground">
                    <div className="flex items-center gap-2 truncate">
                      <MailOutlined className="text-slate-400 shrink-0" />
                      <span className="truncate">{lec.user?.email || `${lec.employeeId}@hcmute.edu.vn`}</span>
                    </div>
                    {lec.office ? (
                      <div className="flex items-center gap-2 truncate">
                        <HomeOutlined className="text-slate-400 shrink-0" />
                        <span className="truncate">{lec.office}</span>
                      </div>
                    ) : null}
                  </div>

                  {/* Research Specialization Tags */}
                  <div className="mt-3 flex flex-wrap gap-1.5">
                    {tags.slice(0, 3).map((tag) => (
                      <Tag key={tag} className="m-0 text-[11px] font-medium border-slate-200 bg-slate-50 text-slate-700 dark:bg-slate-800 dark:border-slate-700 dark:text-slate-300">
                        {tag}
                      </Tag>
                    ))}
                  </div>

                  {/* Supervision Quota & Metrics */}
                  <div className="mt-4 rounded-lg bg-slate-50 dark:bg-slate-800/60 p-2.5 border border-slate-100 dark:border-slate-800">
                    <div className="flex items-center justify-between text-xs mb-1.5">
                      <span className="text-muted-foreground flex items-center gap-1">
                        <BookOutlined className="text-[#003f87]" />
                        <span>{isVi ? 'Tải hướng dẫn:' : 'Advising:'}</span>
                      </span>
                      <span className="font-bold text-foreground">
                        {workload.topicCount} / {workload.maxTopics} {isVi ? 'đề tài' : 'theses'}
                      </span>
                    </div>
                    <Progress
                      percent={Math.min(100, Math.round((workload.topicCount / workload.maxTopics) * 100))}
                      size="small"
                      status={isAvailable ? 'active' : 'normal'}
                      strokeColor={isAvailable ? '#003f87' : '#94a3b8'}
                      showInfo={false}
                    />
                  </div>
                </div>

                {/* Card Actions (Stitch Screen 538cba8b58674dfc93d356072df75498 line 350) */}
                <div className="mt-5 pt-3 border-t border-slate-100 dark:border-slate-800 flex gap-2">
                  <Button
                    className="flex-1 rounded-lg text-xs font-semibold"
                    onClick={() => {
                      setSelectedAdvisor(lec);
                      setIsModalOpen(true);
                    }}
                  >
                    {isVi ? 'Xem chi tiết' : 'View Bio'}
                  </Button>
                  <LinkButton
                    href={`/dashboard/thesis?advisorId=${lec.id}&action=propose`}
                    className="flex-1 w-full text-xs font-semibold bg-[#003f87] hover:bg-[#002a5d] text-white justify-center h-8"
                  >
                    {isVi ? 'Chọn hướng dẫn' : 'Select'}
                  </LinkButton>
                </div>
              </Card>
            );
          })}
        </div>
      ) : (
        /* Ant Design High-density Data Table */
        <div className="rounded-xl border border-slate-200/80 bg-white shadow-2xs overflow-hidden dark:border-slate-800 dark:bg-slate-900">
          <Table<Lecturer>
            columns={columns}
            dataSource={filteredLecturers}
            rowKey="id"
            pagination={{ pageSize: 12, showSizeChanger: true }}
            scroll={{ x: 900 }}
            className="ant-table-custom"
          />
        </div>
      )}

      {/* Advisor Detail Modal (Antd Modal) */}
      <Modal
        title={
          selectedAdvisor ? (
            <div className="flex items-center gap-3">
              <Avatar
                size={42}
                icon={<UserOutlined />}
                src={selectedAdvisor.user?.avatar}
                className="bg-[#003f87] text-white shrink-0 font-bold"
              >
                {getAdvisorFullName(selectedAdvisor).charAt(0).toUpperCase()}
              </Avatar>
              <div>
                <div className="text-base font-bold text-foreground">
                  {selectedAdvisor.title ? `${selectedAdvisor.title} ` : ''}
                  {getAdvisorFullName(selectedAdvisor)}
                </div>
                <div className="text-xs text-muted-foreground font-normal font-mono">
                  {selectedAdvisor.employeeId} • {isVi ? 'Giảng viên cơ hữu HCMUTE' : 'HCMUTE Faculty Member'}
                </div>
              </div>
            </div>
          ) : null
        }
        open={isModalOpen}
        onCancel={() => setIsModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setIsModalOpen(false)}>
            {isVi ? 'Đóng' : 'Close'}
          </Button>,
          selectedAdvisor ? (
            <LinkButton
              key="select"
              href={`/dashboard/thesis?advisorId=${selectedAdvisor.id}&action=propose`}
              className="bg-[#003f87] text-white hover:bg-[#002a5d]"
              onClick={() => setIsModalOpen(false)}
            >
              {isVi ? 'Đề xuất đề tài cùng Giảng viên này' : 'Propose Topic with Advisor'}
            </LinkButton>
          ) : null,
        ]}
        width={680}
      >
        {selectedAdvisor ? (
          <div className="space-y-4 py-2">
            <div className="grid grid-cols-2 gap-3 text-xs bg-slate-50 dark:bg-slate-800/60 p-3 rounded-lg border border-slate-100 dark:border-slate-800">
              <div>
                <span className="text-muted-foreground block">{isVi ? 'Khoa / Bộ môn:' : 'Department:'}</span>
                <span className="font-semibold text-foreground">
                  {departments.find((d) => d.id === selectedAdvisor.departmentId)?.name || 'Khoa Công nghệ Thông tin'}
                </span>
              </div>
              <div>
                <span className="text-muted-foreground block">{isVi ? 'Email học thuật:' : 'Email:'}</span>
                <span className="font-semibold text-foreground">{selectedAdvisor.user?.email || '—'}</span>
              </div>
              <div>
                <span className="text-muted-foreground block">{isVi ? 'Phòng làm việc:' : 'Office:'}</span>
                <span className="font-semibold text-foreground">{selectedAdvisor.office || 'Tòa nhà Trung tâm - HCMUTE'}</span>
              </div>
              <div>
                <span className="text-muted-foreground block">{isVi ? 'Điện thoại cơ quan:' : 'Phone:'}</span>
                <span className="font-semibold text-foreground">{selectedAdvisor.phone || '028 3896 8641'}</span>
              </div>
            </div>

            <div>
              <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground mb-1.5">
                {isVi ? 'Định hướng nghiên cứu & Chuyên môn' : 'Research Interests & Fields'}
              </h4>
              <p className="text-sm text-foreground leading-relaxed">
                {selectedAdvisor.specialization ||
                  (isVi
                    ? 'Chuyên sâu về kiến trúc phần mềm phân tán, ứng dụng trí tuệ nhân tạo, xử lý dữ liệu lớn và các giải pháp chuyển đổi số cho doanh nghiệp giáo dục.'
                    : 'Specialized in distributed software architectures, applied AI, big data systems, and digital transformation for higher education.')}
              </p>
            </div>

            <div>
              <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground mb-1.5">
                {isVi ? 'Quy định hướng dẫn & Đăng ký' : 'Supervision Guidelines'}
              </h4>
              <ul className="text-xs text-slate-600 dark:text-slate-300 space-y-1 list-disc pl-4">
                <li>{isVi ? 'Sinh viên cần chuẩn bị trước đề cương sơ bộ trước khi gặp giảng viên.' : 'Students should prepare an initial outline before scheduling an appointment.'}</li>
                <li>{isVi ? 'Nhóm thực hiện tối đa 2-3 sinh viên theo quy chế đào tạo hiện hành.' : 'Each team consists of 2-3 students according to academic regulations.'}</li>
                <li>{isVi ? 'Báo cáo tiến độ định kỳ vào thứ 3 và thứ 5 hàng tuần.' : 'Weekly milestone report submitted every Tuesday and Thursday.'}</li>
              </ul>
            </div>
          </div>
        ) : null}
      </Modal>
    </div>
  );
}
