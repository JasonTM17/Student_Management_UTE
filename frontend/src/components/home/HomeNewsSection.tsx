'use client';

import React, { useEffect, useState } from 'react';
import {
  ArrowRight,
  BookOpen,
  Calendar,
  Clock,
  GraduationCap,
  Newspaper,
  Sparkles,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { announcementsApi, type AnnouncementRecord } from '@/lib/api';
import { AnnouncementReaderModal } from '@/components/announcements/AnnouncementReaderModal';
import { AnnouncementFeedCard } from '@/components/announcements/feed/AnnouncementFeedCard';
import { SectionEyebrow } from '@/components/ui/page-header';
import { LocalizedLink } from '@/components/LocalizedLink';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';

const HOMEPAGE_FALLBACK_NEWS: AnnouncementRecord[] = [
  {
    id: 'notice-home-01',
    title: 'Khởi động Phòng Nghiên cứu Dữ liệu lớn (Big Data) & Trí tuệ Nhân tạo Khoa CNTT HCMUTE',
    content: `<p>Khoa Công nghệ Thông tin - Trường Đại học Sư phạm Kỹ thuật TP.HCM trân trọng thông báo đưa vào vận hành cụm máy chủ điện toán hiệu năng cao phục vụ nghiên cứu Big Data và Trí tuệ nhân tạo.</p>
<ul>
  <li><strong>Hạ tầng kỹ thuật:</strong> Cụm máy chủ 8x NVIDIA A100 GPU Tensor Core, hệ thống lưu trữ phân tán Ceph 500TB và mạng InfiniBand 200Gbps.</li>
  <li><strong>Đối tượng khai thác:</strong> Toàn thể giảng viên, học viên cao học, nhóm nghiên cứu sinh viên (Lab AI & Data Science) và đề tài Khóa luận tốt nghiệp chuyên sâu.</li>
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Khoa Công nghệ Thông tin & Phòng Đào Tạo',
    createdAt: '2026-09-08T08:00:00Z',
    publishAt: '2026-09-08T08:00:00Z',
  },
  {
    id: 'notice-home-02',
    title: 'Ngày hội việc làm và Kết nối doanh nghiệp công nghệ thông tin UTE Tech Career Expo 2026',
    content: `<p>Nhà trường phối hợp cùng hơn 60 tập đoàn công nghệ đa quốc gia và doanh nghiệp phần mềm hàng đầu tổ chức Ngày hội Việc làm UTE Tech Career Expo.</p>
<ul>
  <li>Hơn 1.200 vị trí tuyển dụng thực tập sinh và kỹ sư chính thức (Fresher/Junior Software Engineer, Data Engineer, AI Engineer).</li>
  <li>Phỏng vấn tuyển dụng trực tiếp tại gian hàng (On-site Fast-track Interview).</li>
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Trung Tâm Hướng Nghiệp & Quan Hệ Doanh Nghiệp',
    createdAt: '2026-09-09T10:00:00Z',
    publishAt: '2026-09-09T10:00:00Z',
  },
  {
    id: 'notice-home-03',
    title: 'Danh sách sinh viên đủ điều kiện xét Học bổng khuyến khích học tập Học kỳ vừa qua',
    content: `<p>Hội đồng xét học bổng Nhà trường thông báo kết quả rà soát điểm học tập (GPA) kết hợp điểm rèn luyện (ĐRL):</p>
<ul>
  <li>Sinh viên có ĐRL từ <strong>80 điểm trở lên</strong> (Xếp loại Tốt và Xuất sắc) và không nợ môn học đủ điều kiện xét cấp các mức học bổng loại A, B, C.</li>
  <li>Thời gian tiếp nhận phản hồi và đối soát minh chứng: đến hết 17:00 ngày 20/09/2026.</li>
</ul>`,
    priority: 'NORMAL',
    publishedBy: 'Phòng Công Tác Sinh Viên & Đào Tạo',
    createdAt: '2026-09-05T09:00:00Z',
    publishAt: '2026-09-05T09:00:00Z',
  },
  {
    id: 'notice-home-04',
    title: 'Kế hoạch mở cổng Đăng ký học phần chính thức Học kỳ 1 năm học 2026-2027',
    content: `<p>Phòng Đào tạo thông báo kế hoạch đăng ký học phần chính thức dành cho toàn thể sinh viên các khóa như sau:</p>
<ul>
  <li><strong>Hạn mức tín chỉ:</strong> Tối đa 28 tín chỉ/học kỳ theo đúng quy chế đào tạo tín chỉ UTE.</li>
  <li><strong>Thời gian mở cổng:</strong> Từ 08:00 ngày 25/08/2026 đến 17:00 ngày 15/09/2026.</li>
  <li><strong>Lưu ý:</strong> Sinh viên kiểm tra điều kiện tiên quyết và trùng lịch trước khi xác nhận lưu đăng ký.</li>
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Phòng Đào Tạo UTE',
    createdAt: '2026-08-25T08:00:00Z',
    publishAt: '2026-08-25T08:00:00Z',
  },
];

export function HomeNewsSection() {
  const { user } = useAuth();
  const { locale } = useI18n();
  const [items, setItems] = useState<AnnouncementRecord[]>(HOMEPAGE_FALLBACK_NEWS);
  const [selectedNotice, setSelectedNotice] = useState<AnnouncementRecord | null>(null);

  const isVi = locale === 'vi';

  useEffect(() => {
    let isMounted = true;
    if (!user) {
      return;
    }
    announcementsApi
      .getMy({ page: 1, limit: 6 })
      .then((res) => {
        if (isMounted && res.data && res.data.length > 0) {
          setItems(res.data);
        }
      })
      .catch(() => {
        // Fallback to static editorial notices
      });
    return () => {
      isMounted = false;
    };
  }, [user]);

  const featured = items[0] || HOMEPAGE_FALLBACK_NEWS[0];
  const restItems = items.slice(1, 4);

  return (
    <section className="border-t border-border/70 bg-secondary/15 py-12 sm:py-16">
      <div className="mx-auto max-w-[1280px] px-4 sm:px-6 lg:px-12 space-y-8">
        {/* Section Header */}
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div className="space-y-1.5">
            <SectionEyebrow>
              {isVi ? 'TIN TỨC & SỰ KIỆN HỌC THUẬT' : 'CAMPUS NEWS & EVENTS'}
            </SectionEyebrow>
            <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
              {isVi ? 'Bản Tin Đại Học HCMUTE' : 'HCMUTE University News & Press'}
            </h2>
            <p className="text-sm text-muted-foreground max-w-2xl">
              {isVi
                ? 'Cập nhật các thành tựu nghiên cứu khoa học, chuyển đổi số đào tạo, hoạt động khởi nghiệp và công văn học vụ mới nhất.'
                : 'Latest updates on scientific research, academic digital transformation, career fairs, and administrative dispatches.'}
            </p>
          </div>

          <LocalizedLink
            href="/dashboard/announcements"
            className="group inline-flex items-center gap-1.5 font-bold text-sm text-primary hover:underline self-start sm:self-end"
          >
            <span>{isVi ? 'Xem tất cả thông báo' : 'View all announcements'}</span>
            <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
          </LocalizedLink>
        </div>

        {/* News Grid */}
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12 items-stretch">
          {/* Featured Article (7 columns on desktop) */}
          <div className="lg:col-span-7 flex flex-col">
            <AnnouncementFeedCard
              announcement={featured}
              onClick={() => setSelectedNotice(featured)}
              variant="featured"
              locale={locale}
              className="h-full"
            />
          </div>

          {/* Secondary News Stream (5 columns on desktop) */}
          <div className="lg:col-span-5 flex flex-col justify-between gap-4">
            {restItems.map((ann) => (
              <AnnouncementFeedCard
                key={ann.id}
                announcement={ann}
                onClick={() => setSelectedNotice(ann)}
                variant="standard"
                locale={locale}
              />
            ))}
          </div>
        </div>
      </div>

      {/* Embedded Announcement Reader Modal */}
      <AnnouncementReaderModal
        announcement={selectedNotice}
        isOpen={Boolean(selectedNotice)}
        onClose={() => setSelectedNotice(null)}
        allAnnouncements={items}
        onSelectAnnouncement={(ann) => setSelectedNotice(ann)}
      />
    </section>
  );
}
