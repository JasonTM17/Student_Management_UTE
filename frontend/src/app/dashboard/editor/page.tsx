'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowUpDown,
  BookOpen,
  Check,
  ChevronDown,
  ChevronUp,
  Clock,
  Code2,
  Copy,
  Download,
  Eye,
  FileEdit,
  FileText,
  GripVertical,
  Layers,
  ListOrdered,
  Megaphone,
  Palette,
  Plus,
  RefreshCw,
  RotateCcw,
  Save,
  Send,
  Sliders,
  Sparkles,
  Trash2,
} from 'lucide-react';
import { toast } from 'sonner';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { announcementsApi, type AnnouncementRecord } from '@/lib/api';
import { AnnouncementReaderModal } from '@/components/announcements/AnnouncementReaderModal';
import { AnnouncementEditModal } from '@/components/announcements/AnnouncementEditModal';
import {
  DEFAULT_SITE_APPEARANCE,
  SITE_APPEARANCE_ACCENTS,
  orderByIds,
  type SiteAppearance,
  type SiteAppearanceAccent,
} from '@/lib/site-appearance';
import {
  broadcastSiteAppearance,
  fetchSiteAppearance,
  saveSiteAppearance,
} from '@/lib/site-appearance-client';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { LoadingState } from '@/components/ui/state-block';
import { RichTextEditor } from '@/components/ui/rich-text-editor';
import { TinyMceEditor } from '@/components/ui/tinymce-editor';
import { SortableList, DragHandle } from '@/components/ui/sortable-list';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { cn } from '@/lib/utils';

export interface ContentBlock {
  id: string;
  type: 'header' | 'recipient' | 'summary' | 'table' | 'clauses' | 'notice' | 'signoff';
  title: string;
  description: string;
  enabled: boolean;
  htmlContent: string;
}

const DEFAULT_BLOCKS: ContentBlock[] = [
  {
    id: 'block-header',
    type: 'header',
    title: '1. Tiêu ngữ Quốc hiệu & Tên Trường',
    description: 'Header chính thức: ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT TP. HỒ CHÍ MINH, Tiêu đề thông báo học vụ',
    enabled: true,
    htmlContent: `  <div style="text-align: center; border-bottom: 2px solid #0284c7; padding-bottom: 12px; margin-bottom: 20px;">
    <h4 style="margin: 0; text-transform: uppercase; color: #64748b; font-size: 13px; letter-spacing: 1px;">ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT THÀNH PHỐ HỒ CHÍ MINH</h4>
    <h2 style="margin: 8px 0 0 0; color: #0f172a; font-size: 22px; font-weight: 700;">THÔNG BÁO HỌC VỤ & HƯỚNG DẪN ĐÀO TẠO</h2>
    <p style="margin: 4px 0 0 0; color: #64748b; font-size: 13px;">Học kỳ I - Năm học 2026-2027 | Soạn thảo bởi Ban Giám Hiệu & Phòng Đào Tạo</p>
  </div>`,
  },
  {
    id: 'block-recipient',
    type: 'recipient',
    title: '2. Dòng Kính gửi Tiếp nhận',
    description: 'Dòng Kính gửi trang trọng đến Toàn thể Giảng viên, Cán bộ và Sinh viên',
    enabled: true,
    htmlContent: `  <div style="background-color: #eff6ff; border-left: 4px solid #3b82f6; padding: 14px 18px; border-radius: 6px; margin-bottom: 20px;">
    <strong style="color: #1d4ed8; font-size: 14px;">Kính gửi:</strong> Toàn thể Giảng viên, Cán bộ học vụ và Sinh viên hệ đào tạo chính quy.
  </div>`,
  },
  {
    id: 'block-summary',
    type: 'summary',
    title: '3. Căn cứ & Mục tiêu Kế hoạch',
    description: 'Căn cứ đề án đào tạo và thông báo các mốc triển khai học vụ',
    enabled: true,
    htmlContent: `  <h3 style="color: #0369a1; font-size: 16px; border-bottom: 1px solid #e2e8f0; padding-bottom: 6px;">1. Kế hoạch đào tạo & Đăng ký tín chỉ</h3>
  <p>Nhà trường thông báo kế hoạch tổ chức học vụ và thời gian mở cổng đăng ký tín chỉ học phần đợt mới:</p>`,
  },
  {
    id: 'block-table',
    type: 'table',
    title: '4. Bảng Lịch trình & Mốc thời gian',
    description: 'Bảng các đợt đăng ký tín chỉ, thời gian bắt đầu - kết thúc và đối tượng',
    enabled: true,
    htmlContent: `  <table style="width: 100%; border-collapse: collapse; margin: 16px 0;">
    <thead>
      <tr style="background-color: #f1f5f9; text-align: left;">
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Đợt đăng ký</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Thời gian bắt đầu</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Thời gian kết thúc</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Đối tượng</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Đợt 1</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">08:00 - 15/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">23:59 - 18/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Sinh viên năm cuối & Làm đồ án</td>
      </tr>
      <tr>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Đợt 2</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">08:00 - 19/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">17:00 - 22/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Sinh viên các khóa còn lại</td>
      </tr>
    </tbody>
  </table>`,
  },
  {
    id: 'block-clauses',
    type: 'clauses',
    title: '5. Các Điều khoản Quy chế & Hạn mức',
    description: 'Hạn mức tối đa 28 tín chỉ / kỳ và quy trình hủy/rút học phần hợp lệ',
    enabled: true,
    htmlContent: `  <div style="margin-bottom: 20px;">
    <h3 style="color: #0369a1; font-size: 16px; border-bottom: 1px solid #e2e8f0; padding-bottom: 6px;">2. Quy định thực hiện và Hạn mức tín chỉ</h3>
    <p><strong>- Hạn mức tín chỉ:</strong> Sinh viên không được phép đăng ký vượt quá 28 tín chỉ/học kỳ chính theo quy chế học vụ.</p>
    <p><strong>- Hủy học phần:</strong> Thời hạn xin rút/hủy học phần kết thúc vào tuần thứ 2 kể từ ngày bắt đầu học kỳ.</p>
  </div>`,
  },
  {
    id: 'block-notice',
    type: 'notice',
    title: '6. Hộp Cảnh báo & Lưu ý Quan trọng',
    description: 'Khung Callout màu vàng nổi bật cảnh báo điều kiện tiên quyết và địa điểm hỗ trợ',
    enabled: true,
    htmlContent: `  <div style="background-color: #fefce8; border-left: 4px solid #eab308; padding: 14px 18px; border-radius: 6px; margin-bottom: 20px;">
    <strong style="color: #a16207;">Lưu ý quan trọng:</strong> Sinh viên kiểm tra điều kiện tiên quyết và lịch học trước khi xác nhận. Mọi thắc mắc liên hệ Phòng Đào tạo (P. A1-201) trong giờ hành chính.
  </div>`,
  },
  {
    id: 'block-signoff',
    type: 'signoff',
    title: '7. Nơi nhận & Dấu Mộc Đỏ Điện Tử e-Office',
    description: 'Nơi nhận và con dấu điện tử chính thức của Hiệu trưởng PGS. TS. Lê Hiếu Giang',
    enabled: true,
    htmlContent: `  <table style="width: 100%; margin-top: 32px; border: none;">
    <tr>
      <td style="width: 50%; vertical-align: top; border: none;">
        <p style="font-size: 12px; margin: 0; line-height: 1.6; color: #64748b;">
          <strong><em>Nơi nhận:</em></strong><br/>
          - Như kính gửi;<br/>
          - Ban Giám hiệu (để b/c);<br/>
          - Phòng Đào tạo, VP các Khoa;<br/>
          - Lưu: VT, CTSV.
        </p>
      </td>
      <td style="width: 50%; text-align: right; vertical-align: top; border: none;">
        <p style="font-weight: bold; margin: 0; text-transform: uppercase; font-size: 13px; color: #334155;">HIỆU TRƯỞNG</p>
        <div style="display: inline-block; margin: 8px 0; padding: 4px 12px; border: 2px dashed #dc2626; border-radius: 6px; background-color: #fef2f2; color: #b91c1c; font-size: 11px; font-weight: bold; text-align: center;">
          [ĐÃ KÝ ĐIỆN TỬ - E-OFFICE]<br/>
          TRƯỜNG ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT TP.HCM
        </div>
        <p style="font-weight: bold; margin: 4px 0 0 0; color: #0284c7; font-size: 14px;">PGS. TS. Lê Hiếu Giang</p>
      </td>
    </tr>
  </table>`,
  },
];

const STORAGE_KEY = 'campuscore_editor_document';
const EDITOR_TYPE_KEY = 'campuscore_editor_engine';

interface StoredDocument {
  title: string;
  category: string;
  content: string;
  editorType?: 'tinymce' | 'markdown';
  updatedAt: string;
}

const DEFAULT_TINYMCE_VI = `
<div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b;">
  <div style="text-align: center; border-bottom: 2px solid #0284c7; padding-bottom: 12px; margin-bottom: 20px;">
    <h4 style="margin: 0; text-transform: uppercase; color: #64748b; font-size: 13px; letter-spacing: 1px;">ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT THÀNH PHỐ HỒ CHÍ MINH</h4>
    <h2 style="margin: 8px 0 0 0; color: #0f172a; font-size: 22px; font-weight: 700;">THÔNG BÁO HỌC VỤ & HƯỚNG DẪN ĐÀO TẠO</h2>
    <p style="margin: 4px 0 0 0; color: #64748b; font-size: 13px;">Học kỳ I - Năm học 2026-2027 | Soạn thảo bởi Ban Giám Hiệu & Phòng Đào Tạo</p>
  </div>

  <div style="background-color: #eff6ff; border-left: 4px solid #3b82f6; padding: 14px 18px; border-radius: 6px; margin-bottom: 20px;">
    <strong style="color: #1d4ed8; font-size: 14px;">Kính gửi:</strong> Toàn thể Giảng viên, Cán bộ học vụ và Sinh viên hệ đào tạo chính quy.
  </div>

  <h3 style="color: #0369a1; font-size: 16px; border-bottom: 1px solid #e2e8f0; padding-bottom: 6px;">1. Kế hoạch đào tạo & Đăng ký tín chỉ</h3>
  <p>Nhà trường thông báo kế hoạch tổ chức học vụ và thời gian mở cổng đăng ký tín chỉ học phần đợt mới:</p>
  
  <table style="width: 100%; border-collapse: collapse; margin: 16px 0;">
    <thead>
      <tr style="background-color: #f1f5f9; text-align: left;">
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Đợt đăng ký</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Thời gian bắt đầu</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Thời gian kết thúc</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Đối tượng</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Đợt 1</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">08:00 - 15/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">23:59 - 18/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Sinh viên năm cuối & Làm đồ án</td>
      </tr>
      <tr>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Đợt 2</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">08:00 - 19/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">17:00 - 22/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Sinh viên các khóa còn lại</td>
      </tr>
    </tbody>
  </table>

  <div style="background-color: #fefce8; border-left: 4px solid #eab308; padding: 14px 18px; border-radius: 6px; margin-bottom: 20px;">
    <strong style="color: #a16207;">Lưu ý quan trọng:</strong> Sinh viên kiểm tra điều kiện tiên quyết và lịch học trước khi xác nhận.
  </div>
</div>
`;

const DEFAULT_TINYMCE_EN = `
<div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b;">
  <div style="text-align: center; border-bottom: 2px solid #0284c7; padding-bottom: 12px; margin-bottom: 20px;">
    <h4 style="margin: 0; text-transform: uppercase; color: #64748b; font-size: 13px; letter-spacing: 1px;">HCMC UNIVERSITY OF TECHNOLOGY AND ENGINEERING</h4>
    <h2 style="margin: 8px 0 0 0; color: #0f172a; font-size: 22px; font-weight: 700;">ACADEMIC NOTICE & GOVERNANCE POLICY</h2>
    <p style="margin: 4px 0 0 0; color: #64748b; font-size: 13px;">Term 1 - Academic Year 2026-2027 | Official Academic Affairs Notice</p>
  </div>

  <div style="background-color: #eff6ff; border-left: 4px solid #3b82f6; padding: 14px 18px; border-radius: 6px; margin-bottom: 20px;">
    <strong style="color: #1d4ed8; font-size: 14px;">Attention:</strong> All Faculty Members, Academic Staff, and Enrolled Students.
  </div>

  <h3 style="color: #0369a1; font-size: 16px; border-bottom: 1px solid #e2e8f0; padding-bottom: 6px;">1. Course Registration Milestones</h3>
  <table style="width: 100%; border-collapse: collapse; margin: 16px 0;">
    <thead>
      <tr style="background-color: #f1f5f9; text-align: left;">
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Batch</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Start Time</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">End Time</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Target Cohort</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Phase 1</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">08:00 - Sep 15, 2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">23:59 - Sep 18, 2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Graduating & Thesis Students</td>
      </tr>
      <tr>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Phase 2</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">08:00 - Sep 19, 2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">17:00 - Sep 22, 2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">All remaining cohorts</td>
      </tr>
    </tbody>
  </table>
</div>
`;

const DEFAULT_MARKDOWN_VI = `# ĐỀ CƯƠNG HỌC PHẦN & TÀI LIỆU HƯỚNG DẪN

> [!NOTE]
> Tài liệu này được soạn thảo trực tiếp trên **Trình soạn thảo học vụ CampusCore**. Hỗ trợ bảng biểu, công thức, mã nguồn và hộp cảnh báo chuẩn institutional.

### 1. Mục tiêu và Chuẩn đầu ra (CLO)
- [ ] Nắm vững kiến trúc hệ thống và nguyên lý thiết kế cơ sở dữ liệu phân tán.
- [ ] Xây dựng giải pháp đảm bảo tính sẵn sàng cao (High Availability).
- [ ] Triển khai kiểm thử hồi quy và đo lường hiệu năng.

### 2. Kế hoạch học tập và phân bổ thời lượng
| Tuần | Chủ đề đào tạo | Hình thức | Chuẩn đầu ra |
| :--- | :--- | :--- | :--- |
| Tuần 1-3 | Tổng quan kiến trúc hướng dịch vụ | Lý thuyết & Demo | CLO-1 |
| Tuần 4-7 | Thiết kế CSDL & Tối ưu hóa truy vấn | Thực hành Lab | CLO-2 |
| Tuần 8-12 | Báo cáo tiến độ đồ án & Phản biện | Thuyết trình nhóm | CLO-3 |
`;

const TEMPLATES = [
  {
    id: 'council-decision',
    nameVi: 'Quyết định Thành lập Hội đồng Bảo vệ KLTN',
    nameEn: 'Thesis Defense Council Establishment Decision',
    descVi: 'Mẫu quyết định bổ nhiệm Chủ tịch, Thư ký và Ủy viên theo quy chế R6.',
    contentVi: `
<div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b;">
  <div style="text-align: center; border-bottom: 2px solid #0d509d; padding-bottom: 12px; margin-bottom: 20px;">
    <h4 style="margin: 0; text-transform: uppercase; color: #64748b; font-size: 13px;">ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT THÀNH PHỐ HỒ CHÍ MINH</h4>
    <h2 style="margin: 8px 0 0 0; color: #0d509d; font-size: 20px; font-weight: bold;">QUYẾT ĐỊNH THÀNH LẬP HỘI ĐỒNG CHẤM BẢO VỆ KHÓA LUẬN TỐT NGHIỆP</h2>
    <p style="margin: 4px 0 0 0; color: #64748b; font-size: 13px;">Căn cứ Quy chế đào tạo đại học và Đề án tổ chức đánh giá tốt nghiệp</p>
  </div>
  <p><strong>HIỆU TRƯỞNG TRƯỜNG ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT TP. HỒ CHÍ MINH QUYẾT ĐỊNH:</strong></p>
  <p><strong>Điều 1.</strong> Thành lập Hội đồng chấm bảo vệ Khóa luận tốt nghiệp chuyên ngành Kỹ thuật Phần mềm gồm các thành viên:</p>
  <ul>
    <li><strong>Ghế 1 (Chủ tịch):</strong> ThS. Trần Văn Bình - Giảng viên chính</li>
    <li><strong>Ghế 2 (Thư ký):</strong> TS. Nguyễn Thị Hoa - Giảng viên</li>
    <li><strong>Ghế 3 (Ủy viên):</strong> ThS. Lê Minh Tuấn - Giảng viên</li>
    <li><strong>Ghế 4 (Ủy viên):</strong> TS. Phạm Thị Ngọc Lan - Giảng viên</li>
  </ul>
  <p><strong>Điều 2.</strong> Hội đồng có nhiệm vụ tổ chức chấm điểm bảo vệ công tâm, minh bạch theo Quy định R1–R9.</p>
  <table style="width: 100%; margin-top: 24px; border: none;">
    <tr>
      <td style="width: 50%; vertical-align: top; border: none;">
        <p style="font-size: 11px; margin: 0; line-height: 1.5;"><strong><em>Nơi nhận:</em></strong><br/>- Như Điều 1;<br/>- Ban Giám hiệu (để b/c);<br/>- Phòng ĐT, VP Khoa CNTT;<br/>- Lưu: VT.</p>
      </td>
      <td style="width: 50%; text-align: right; vertical-align: top; border: none;">
        <p style="font-weight: bold; margin: 0; text-transform: uppercase;">HIỆU TRƯỞNG</p>
        <div style="height: 48px;"></div>
        <p style="font-weight: bold; margin: 0; color: #0d509d;">PGS. TS. Lê Hiếu Giang</p>
      </td>
    </tr>
  </table>
</div>
`,
  },
  {
    id: 'registration-notice',
    nameVi: 'Thông báo Mở Cổng Đăng ký Tín chỉ & Hạn mức',
    nameEn: 'Course Registration & Credit Limit Official Notice',
    descVi: 'Thông báo khung giờ đăng ký, điều kiện tiên quyết và hạn mức tín chỉ tối đa.',
    contentVi: DEFAULT_TINYMCE_VI,
  },
  {
    id: 'scholarship-notice',
    nameVi: 'Thông báo Xét duyệt Học bổng Khuyến khích Học tập',
    nameEn: 'Merit-based Scholarship Evaluation Notice',
    descVi: 'Tiêu chuẩn xét cấp học bổng loại Xuất sắc, Giỏi và Khá cho sinh viên.',
    contentVi: `
<div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b;">
  <div style="text-align: center; border-bottom: 2px solid #16a34a; padding-bottom: 12px; margin-bottom: 20px;">
    <h4 style="margin: 0; text-transform: uppercase; color: #64748b; font-size: 13px;">ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT THÀNH PHỐ HỒ CHÍ MINH</h4>
    <h2 style="margin: 8px 0 0 0; color: #15803d; font-size: 20px; font-weight: bold;">THÔNG BÁO XÉT CẤP HỌC BỔNG KHUYẾN KHÍCH HỌC TẬP</h2>
    <p style="margin: 4px 0 0 0; color: #64748b; font-size: 13px;">Học kỳ 1 - Năm học 2026-2027</p>
  </div>
  <p>Phòng Đào tạo thông báo điều kiện và định mức xét học bổng khuyến khích học tập kỳ này:</p>
  <table style="width: 100%; border-collapse: collapse; margin: 16px 0;">
    <thead>
      <tr style="background-color: #f0fdf4;">
        <th style="border: 1px solid #bbf7d0; padding: 10px;">Xếp loại</th>
        <th style="border: 1px solid #bbf7d0; padding: 10px;">Điểm GPA (thang 4)</th>
        <th style="border: 1px solid #bbf7d0; padding: 10px;">Điểm rèn luyện</th>
        <th style="border: 1px solid #bbf7d0; padding: 10px;">Định mức</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td style="border: 1px solid #bbf7d0; padding: 10px;">Xuất sắc</td>
        <td style="border: 1px solid #bbf7d0; padding: 10px;">≥ 3.60</td>
        <td style="border: 1px solid #bbf7d0; padding: 10px;">≥ 90</td>
        <td style="border: 1px solid #bbf7d0; padding: 10px;">120% Học phí</td>
      </tr>
      <tr>
        <td style="border: 1px solid #bbf7d0; padding: 10px;">Giỏi</td>
        <td style="border: 1px solid #bbf7d0; padding: 10px;">3.20 - 3.59</td>
        <td style="border: 1px solid #bbf7d0; padding: 10px;">≥ 80</td>
        <td style="border: 1px solid #bbf7d0; padding: 10px;">100% Học phí</td>
      </tr>
    </tbody>
  </table>
</div>
`,
  },
];

export default function AcademicEditorPage() {
  const { user, hasAccess, isLoading: authLoading, isForbidden } = useRequireAuth([
    'ADMIN',
    'SUPER_ADMIN',
  ]);
  const { locale } = useI18n();
  const isVi = locale === 'vi';

  // Navigation tab state: 'announcement' (Editor) | 'hero' (Site Controls) | 'templates' (Document Library)
  const [activeTab, setActiveTab] = useState<'announcement' | 'hero' | 'templates'>('announcement');

  // Editor states
  const [editorType, setEditorType] = useState<'tinymce' | 'markdown'>('tinymce');
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState('notice');
  const [content, setContent] = useState('');
  const [priority, setPriority] = useState<'URGENT' | 'HIGH' | 'NORMAL' | 'LOW'>('NORMAL');
  const [targetRole, setTargetRole] = useState<'ALL' | 'STUDENT' | 'LECTURER'>('ALL');
  const [isPublishingNotice, setIsPublishingNotice] = useState(false);
  const [lastSaved, setLastSaved] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingVersion, setEditingVersion] = useState<number>(0);
  const [publishedNotices, setPublishedNotices] = useState<AnnouncementRecord[]>([]);
  const [loadingNotices, setLoadingNotices] = useState(false);
  const [previewingNotice, setPreviewingNotice] = useState<AnnouncementRecord | null>(null);
  const [editingNoticeModal, setEditingNoticeModal] = useState<AnnouncementRecord | null>(null);

  // Sortable Content Blocks Builder State
  const [showBlockBuilder, setShowBlockBuilder] = useState(true);
  const [blocks, setBlocks] = useState<ContentBlock[]>(DEFAULT_BLOCKS);
  const [hasUnsavedNoticeOrder, setHasUnsavedNoticeOrder] = useState(false);

  // Site Appearance (Hero / Banner control)
  const [siteAppearance, setSiteAppearance] = useState<SiteAppearance>(DEFAULT_SITE_APPEARANCE);
  const [heroEyebrow, setHeroEyebrow] = useState('ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT THÀNH PHỐ HỒ CHÍ MINH');
  const [heroTitle, setHeroTitle] = useState('Cổng Thông Tin Đào Tạo & Học Vụ Trực Tuyến');
  const [heroDescription, setHeroDescription] = useState('Hệ thống quản lý học vụ số tập trung dành cho Sinh viên, Giảng viên và Cán bộ Quản trị.');
  const [heroAccent, setHeroAccent] = useState<SiteAppearanceAccent>('ute-yellow');
  const [isSavingHero, setIsSavingHero] = useState(false);

  // Copy text definitions
  const copy = useMemo(
    () =>
      isVi
        ? {
            eyebrow: 'TRUNG TÂM SOẠN THẢO VĂN BẢN & QUẢN TRỊ NỘI DUNG',
            title: 'Trình Soạn Thảo & Quản Trị Nội Dung Học Vụ',
            description:
              'Bộ công cụ dành riêng cho Ban Quản trị: Soạn thảo văn bản học vụ điện tử, phát hành thông báo đào tạo và điều hành cổng thông tin trường.',
            tabs: {
              announcement: 'Soạn thảo & Phát hành Thông báo',
              hero: 'Điều khiển Banners & Trang chủ',
              templates: 'Thư viện Mẫu Văn bản Chuẩn',
            },
            docTitleLabel: 'Tiêu đề tài liệu / Thông báo',
            docTitlePlaceholder: 'Nhập tiêu đề thông báo học vụ...',
            categoryLabel: 'Thể loại',
            priorityLabel: 'Mức độ ưu tiên',
            targetRoleLabel: 'Đối tượng nhận tin',
            categories: {
              notice: 'Thông báo học vụ',
              syllabus: 'Đề cương môn học',
              thesis: 'Đề tài tốt nghiệp & Hội đồng',
              notes: 'Quyết định / Quy chế đào tạo',
            },
            editorEngine: 'Chế độ soạn thảo',
            tinymceMode: 'Soạn thảo trực quan',
            markdownMode: 'Soạn thảo ký hiệu / Markdown',
            newDoc: 'Tạo mới',
            saveDraft: 'Lưu nháp',
            copyContent: 'Sao chép nội dung',
            downloadDoc: 'Tải tệp về máy',
            publishAnnouncement: 'Đăng lên Bảng tin Học vụ',
            copiedToast: 'Đã sao chép nội dung vào bộ nhớ tạm',
            savedToast: 'Đã lưu bản nháp vào trình duyệt',
            newDocConfirm: 'Bạn có chắc chắn muốn làm mới toàn bộ nội dung tài liệu?',
            savedAt: 'Lưu gần nhất',
            statsTitle: 'Thống kê tài liệu',
            words: 'Từ',
            characters: 'Ký tự',
            readingTime: 'Thời gian đọc ước tính',
            minutes: 'phút',
            loading: 'Đang tải trung tâm điều khiển...',
          }
        : {
            eyebrow: 'ADMIN SITE CONTROL & ACADEMIC EDITOR',
            title: 'Academic Document Studio & Content Management',
            description:
              'Admin publishing workstation: Compose official academic documents, publish live announcements, and configure portal appearance.',
            tabs: {
              announcement: 'Compose & Publish Notices',
              hero: 'Hero Banner & Site Appearance',
              templates: 'Official Templates Library',
            },
            docTitleLabel: 'Document / Announcement Title',
            docTitlePlaceholder: 'Enter official academic announcement title...',
            categoryLabel: 'Category',
            priorityLabel: 'Priority',
            targetRoleLabel: 'Target Audience',
            categories: {
              notice: 'Academic Notice',
              syllabus: 'Course Syllabus',
              thesis: 'Thesis & Defense Council',
              notes: 'Regulations & Policies',
            },
            editorEngine: 'Editor Mode',
            tinymceMode: 'Rich Visual Editor',
            markdownMode: 'Markdown Editor',
            newDoc: 'New Document',
            saveDraft: 'Save Draft',
            copyContent: 'Copy Content',
            downloadDoc: 'Export File',
            publishAnnouncement: 'Publish to Campus Feed',
            copiedToast: 'Content copied to clipboard',
            savedToast: 'Draft saved to browser storage',
            newDocConfirm: 'Reset and create a new document?',
            savedAt: 'Last saved',
            statsTitle: 'Document Statistics',
            words: 'Words',
            characters: 'Characters',
            readingTime: 'Estimated reading time',
            minutes: 'min',
            loading: 'Loading control studio...',
          },
    [isVi],
  );

  // Initialize from storage or default
  useEffect(() => {
    try {
      const savedEngine = localStorage.getItem(EDITOR_TYPE_KEY) as 'tinymce' | 'markdown' | null;
      if (savedEngine === 'tinymce' || savedEngine === 'markdown') {
        setEditorType(savedEngine);
      }

      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as StoredDocument;
        setTitle(parsed.title || '');
        setCategory(parsed.category || 'notice');
        setContent(parsed.content || '');
        setLastSaved(parsed.updatedAt || null);
        if (parsed.editorType) {
          setEditorType(parsed.editorType);
        }
        return;
      }
    } catch {
      // ignore
    }

    setTitle(isVi ? 'Thông báo kế hoạch tổ chức học vụ học kỳ mới' : 'Official Academic Schedule Notice');
    setContent(isVi ? DEFAULT_TINYMCE_VI : DEFAULT_TINYMCE_EN);
  }, [isVi]);

  // Load site appearance for Hero control tab
  useEffect(() => {
    fetchSiteAppearance()
      .then((data) => {
        setSiteAppearance(data);
        const heroData = data.hero[locale] || data.hero.vi;
        if (heroData.eyebrow) setHeroEyebrow(heroData.eyebrow);
        if (heroData.title) setHeroTitle(heroData.title);
        if (heroData.description) setHeroDescription(heroData.description);
        if (data.accent) setHeroAccent(data.accent);
      })
      .catch(() => {});
  }, [locale]);

  const handleSwitchEditorType = (type: 'tinymce' | 'markdown') => {
    setEditorType(type);
    try {
      localStorage.setItem(EDITOR_TYPE_KEY, type);
    } catch {
      // ignore
    }
  };

  const saveDraft = useCallback(() => {
    const timestamp = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const payload: StoredDocument = {
      title,
      category,
      content,
      editorType,
      updatedAt: timestamp,
    };
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
      setLastSaved(timestamp);
      toast.success(copy.savedToast);
    } catch {
      // ignore
    }
  }, [category, content, copy.savedToast, editorType, title]);

  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(content);
      setCopied(true);
      toast.success(copy.copiedToast);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error('Could not copy to clipboard');
    }
  }, [content, copy.copiedToast]);

  const handleDownload = useCallback(() => {
    const isHtml = editorType === 'tinymce';
    const ext = isHtml ? 'html' : 'md';
    const mime = isHtml ? 'text/html;charset=utf-8' : 'text/markdown;charset=utf-8';
    const filename = `${(title || 'academic-document').toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-_]/g, '')}.${ext}`;

    const blob = new Blob([content], { type: mime });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }, [content, editorType, title]);

  const handleReset = useCallback(() => {
    if (window.confirm(copy.newDocConfirm)) {
      setTitle('');
      setContent(editorType === 'tinymce' ? (isVi ? DEFAULT_TINYMCE_VI : DEFAULT_TINYMCE_EN) : DEFAULT_MARKDOWN_VI);
      setLastSaved(null);
      localStorage.removeItem(STORAGE_KEY);
    }
  }, [copy.newDocConfirm, editorType, isVi]);

  // Reorder content blocks via SortableJS
  const handleReorderBlocks = (newBlocks: ContentBlock[]) => {
    setBlocks(newBlocks);
  };

  // Toggle single block inclusion
  const handleToggleBlock = (id: string) => {
    setBlocks((prev) =>
      prev.map((b) => (b.id === id ? { ...b, enabled: !b.enabled } : b))
    );
  };

  // Compile all enabled blocks into TinyMCE
  const handleCompileBlocksToTinyMce = () => {
    const active = blocks.filter((b) => b.enabled);
    if (active.length === 0) {
      toast.error(isVi ? 'Vui lòng chọn ít nhất một khối để biên dịch!' : 'Please enable at least one block!');
      return;
    }
    const compiled = `<div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b;">\n${active.map((b) => b.htmlContent).join('\n\n')}\n</div>`;
    setContent(compiled);
    toast.success(
      isVi
        ? `Đã áp dụng ${active.length} khối cấu trúc vào văn bản đang soạn!`
        : `Applied ${active.length} structured blocks into document!`
    );
  };

  // Insert a single block into TinyMCE
  const handleInsertSingleBlock = (block: ContentBlock) => {
    setContent((prev) => `${prev}\n\n${block.htmlContent}`);
    toast.success(
      isVi ? `Đã chèn khối "${block.title}" vào nội dung!` : `Inserted "${block.title}" into editor!`
    );
  };

  // Reset blocks to default order
  const handleResetBlocks = () => {
    setBlocks(DEFAULT_BLOCKS);
    toast.info(isVi ? 'Đã khôi phục các khối cấu trúc mẫu mặc định.' : 'Reset blocks to default.');
  };

  // Reorder announcements via SortableJS
  const handleSortableNoticeReorder = (newNotices: AnnouncementRecord[]) => {
    setPublishedNotices(newNotices);
    setHasUnsavedNoticeOrder(true);
  };

  // Save announcements order to site appearance
  const handleSaveNoticeOrder = async () => {
    try {
      const newOrder = publishedNotices.map((n) => n.id);
      const updated: SiteAppearance = {
        ...siteAppearance,
        postOrder: newOrder,
      };
      await saveSiteAppearance(updated);
      broadcastSiteAppearance(updated);
      setSiteAppearance(updated);
      setHasUnsavedNoticeOrder(false);
      toast.success(
        isVi
          ? 'Đã lưu và đồng bộ thứ tự ghim bài viết lên Bảng tin toàn trường!'
          : 'Saved and broadcast announcement feed order!'
      );
    } catch {
      toast.error(
        isVi
          ? 'Không thể lưu thứ tự ghim bài viết.'
          : 'Could not save announcement order.'
      );
    }
  };

  // Fetch published announcements from campus database
  const fetchPublishedNotices = useCallback(async () => {
    setLoadingNotices(true);
    try {
      const res = await announcementsApi.getAll({ page: 1, limit: 15 });
      const raw = res.data || [];
      const ordered = siteAppearance.postOrder?.length > 0 ? orderByIds(raw, siteAppearance.postOrder) : raw;
      setPublishedNotices(ordered);
      setHasUnsavedNoticeOrder(false);
    } catch {
      // fallback
    } finally {
      setLoadingNotices(false);
    }
  }, [siteAppearance.postOrder]);

  useEffect(() => {
    if (hasAccess) {
      void fetchPublishedNotices();
    }
  }, [fetchPublishedNotices, hasAccess]);

  // Save modal edit changes directly
  const handleSaveModalEdit = async (id: string, payload: any) => {
    const updated = await announcementsApi.update(id, payload);
    toast.success(isVi ? 'Đã lưu thay đổi thông báo thành công!' : 'Notice updated successfully!');
    setPublishedNotices((prev) =>
      prev.map((n) => (n.id === id ? { ...n, ...updated, ...payload } : n))
    );
  };

  // Load existing announcement into TinyMCE editor
  const handleLoadAnnouncement = useCallback((ann: AnnouncementRecord) => {
    setEditingId(ann.id);
    setEditingVersion(ann.version ?? 0);
    setTitle(ann.title);
    setContent(ann.content);
    if (ann.priority === 'URGENT' || ann.priority === 'HIGH' || ann.priority === 'NORMAL' || ann.priority === 'LOW') {
      setPriority(ann.priority as any);
    }
    if (ann.isGlobal || !ann.targetRoles || ann.targetRoles.length === 0 || (ann.targetRoles.includes('STUDENT') && ann.targetRoles.includes('LECTURER'))) {
      setTargetRole('ALL');
    } else if (ann.targetRoles.includes('STUDENT')) {
      setTargetRole('STUDENT');
    } else if (ann.targetRoles.includes('LECTURER')) {
      setTargetRole('LECTURER');
    }
    setEditorType('tinymce');
    setActiveTab('announcement');
    setTimeout(() => {
      const workspaceEl = document.getElementById('editor-workspace');
      if (workspaceEl) {
        workspaceEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }, 100);
    toast.success(
      isVi ? `Đang chỉnh sửa bài viết: "${ann.title.slice(0, 32)}..."` : `Editing notice: "${ann.title.slice(0, 32)}..."`
    );
  }, [isVi]);

  // Auto load announcement from query param ?editId=... or ?id=...
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams(window.location.search);
    const targetId = params.get('editId') || params.get('id');
    if (!targetId) return;

    announcementsApi
      .getAll({ page: 1, limit: 50 })
      .then((res) => {
        const found = (res.data || []).find((a) => a.id === targetId);
        if (found) {
          handleLoadAnnouncement(found);
        }
      })
      .catch(() => {});
  }, [handleLoadAnnouncement]);

  // Preview current draft in Official Administrative modal
  const handlePreviewCurrentDraft = () => {
    const authorName = user
      ? (`${user.lastName || ''} ${user.firstName || ''}`.trim() || `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.email)
      : 'Phòng Đào Tạo & Ban Giám Hiệu';
    setPreviewingNotice({
      id: editingId || 'draft-preview',
      title: title || (isVi ? 'THÔNG BÁO HỌC VỤ & HƯỚNG DẪN ĐÀO TẠO' : 'Official Academic Notice'),
      content: content,
      priority: priority,
      createdAt: new Date().toISOString(),
      publishAt: new Date().toISOString(),
      publishedBy: authorName,
      targetRoles: targetRole === 'ALL' ? ['STUDENT', 'LECTURER', 'ADMIN'] : [targetRole],
      isGlobal: targetRole === 'ALL',
    });
  };

  // Cancel edit mode and start new
  const handleCancelEdit = () => {
    setEditingId(null);
    setEditingVersion(0);
    setTitle('');
    setContent(editorType === 'tinymce' ? (isVi ? DEFAULT_TINYMCE_VI : DEFAULT_TINYMCE_EN) : DEFAULT_MARKDOWN_VI);
    toast.info(isVi ? 'Đã hủy chế độ sửa, bắt đầu soạn thảo văn bản mới' : 'Cancelled edit mode');
  };

  // Publish announcement to backend feed
  const handlePublishAnnouncement = async () => {
    if (!title.trim()) {
      toast.error(isVi ? 'Vui lòng nhập tiêu đề thông báo!' : 'Please enter announcement title!');
      return;
    }

    setIsPublishingNotice(true);
    try {
      const targetRoles = targetRole === 'ALL'
        ? ['STUDENT', 'LECTURER', 'ADMIN']
        : [targetRole];

      await announcementsApi.create({
        title,
        content,
        priority,
        targetRoles,
        targetYears: [],
        isGlobal: targetRole === 'ALL',
      });

      toast.success(
        isVi
          ? 'Đã phát hành thông báo thành công lên toàn trường!'
          : 'Announcement successfully published to campus feed!',
      );
      void fetchPublishedNotices();
    } catch (err) {
      toast.error(
        isVi
          ? 'Có lỗi khi phát hành thông báo. Vui lòng kiểm tra lại.'
          : 'Could not publish announcement.',
      );
    } finally {
      setIsPublishingNotice(false);
    }
  };

  // Update existing announcement in backend
  const handleUpdateAnnouncement = async () => {
    if (!editingId || !title.trim()) {
      toast.error(isVi ? 'Vui lòng nhập tiêu đề bài viết!' : 'Please enter title!');
      return;
    }

    setIsPublishingNotice(true);
    try {
      const targetRoles = targetRole === 'ALL'
        ? ['STUDENT', 'LECTURER', 'ADMIN']
        : [targetRole];

      await announcementsApi.update(editingId, {
        title,
        content,
        priority,
        targetRoles,
        targetYears: [],
        isGlobal: targetRole === 'ALL',
        reason: 'Cập nhật nội dung văn bản học vụ',
        expectedVersion: editingVersion,
      });

      setEditingVersion((prev) => prev + 1);
      toast.success(
        isVi ? 'Đã cập nhật bài viết thành công vào cơ sở dữ liệu!' : 'Announcement updated in database!'
      );
      void fetchPublishedNotices();
    } catch {
      toast.error(isVi ? 'Không thể cập nhật thông báo.' : 'Failed to update announcement.');
    } finally {
      setIsPublishingNotice(false);
    }
  };

  // Publish Hero banner changes to site appearance
  const handlePublishHero = async () => {
    setIsSavingHero(true);
    try {
      const updated: SiteAppearance = {
        ...siteAppearance,
        accent: heroAccent,
        hero: {
          ...siteAppearance.hero,
          [locale]: {
            eyebrow: heroEyebrow,
            title: heroTitle,
            description: heroDescription,
          },
        },
      };

      await saveSiteAppearance(updated);
      broadcastSiteAppearance(updated);
      setSiteAppearance(updated);
      toast.success(
        isVi
          ? 'Đã cập nhật và xuất bản trực tiếp lên Trang chủ!'
          : 'Homepage appearance updated and published live!',
      );
    } catch {
      toast.error(
        isVi
          ? 'Không thể lưu cấu hình trang chủ.'
          : 'Failed to update homepage appearance.',
      );
    } finally {
      setIsSavingHero(false);
    }
  };

  // Load selected template into editor
  const applyTemplate = (templateContent: string, templateTitle: string) => {
    setContent(templateContent);
    setTitle(templateTitle);
    setActiveTab('announcement');
    toast.success(isVi ? 'Đã nạp mẫu văn bản vào trình soạn thảo!' : 'Template applied to editor!');
  };

  const stats = useMemo(() => {
    const cleanText = editorType === 'tinymce'
      ? content.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim()
      : content.trim();
    const chars = cleanText.length;
    const words = cleanText.length === 0 ? 0 : cleanText.split(/\s+/).filter(Boolean).length;
    const minutes = Math.max(1, Math.ceil(words / 200));
    return { chars, words, minutes };
  }, [content, editorType]);

  if (authLoading) {
    return <LoadingState label={copy.loading} />;
  }

  // Strict Admin Gate: Only Admins can access!
  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  return (
    <div className="space-y-6 pb-12">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
      />

      {/* Main Tab Controller */}
      <div className="flex flex-wrap items-center gap-2 border-b border-border/70 pb-3">
        <Button
          type="button"
          variant={activeTab === 'announcement' ? 'default' : 'outline'}
          onClick={() => setActiveTab('announcement')}
          className="gap-2 font-semibold"
        >
          <FileEdit className="h-4 w-4" />
          {copy.tabs.announcement}
        </Button>
        <Button
          type="button"
          variant={activeTab === 'hero' ? 'default' : 'outline'}
          onClick={() => setActiveTab('hero')}
          className="gap-2 font-semibold"
        >
          <Sliders className="h-4 w-4" />
          {copy.tabs.hero}
        </Button>
        <Button
          type="button"
          variant={activeTab === 'templates' ? 'default' : 'outline'}
          onClick={() => setActiveTab('templates')}
          className="gap-2 font-semibold"
        >
          <BookOpen className="h-4 w-4" />
          {copy.tabs.templates}
        </Button>
      </div>

      {/* TAB 1: HERO & SITE APPEARANCE CONTROL */}
      {activeTab === 'hero' && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <Sliders className="h-5 w-5 text-primary" />
                {isVi ? 'Cấu Hình Banner & Tên Trường Trên Trang Chủ' : 'Homepage Hero Banner & University Identity'}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-5">
              <div>
                <label className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {isVi ? 'Tên trường / Nhãn Eyebrow (Hiển thị đầu trang chủ)' : 'University Name / Eyebrow Badge'}
                </label>
                <Input
                  value={heroEyebrow}
                  onChange={(e) => setHeroEyebrow(e.target.value)}
                  placeholder="ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT THÀNH PHỐ HỒ CHÍ MINH"
                  className="font-bold text-primary"
                />
              </div>

              <div>
                <label className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {isVi ? 'Tiêu đề chính (Hero Title)' : 'Hero Title'}
                </label>
                <Input
                  value={heroTitle}
                  onChange={(e) => setHeroTitle(e.target.value)}
                  placeholder="Cổng Thông Tin Đào Tạo & Học Vụ Trực Tuyến"
                  className="font-semibold"
                />
              </div>

              <div>
                <label className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {isVi ? 'Đoạn giới thiệu (Hero Description)' : 'Hero Description'}
                </label>
                <Textarea
                  value={heroDescription}
                  onChange={(e) => setHeroDescription(e.target.value)}
                  rows={3}
                  className="leading-relaxed"
                />
              </div>

              <div>
                <label className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {isVi ? 'Màu sắc điểm nhấn (Accent Theme)' : 'Accent Theme'}
                </label>
                <div className="flex flex-wrap gap-3">
                  {SITE_APPEARANCE_ACCENTS.map((accent) => (
                    <button
                      key={accent}
                      type="button"
                      onClick={() => setHeroAccent(accent)}
                      className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold transition-all ${
                        heroAccent === accent
                          ? 'border-primary bg-primary/10 text-primary ring-2 ring-primary/20'
                          : 'border-border bg-card text-muted-foreground hover:bg-muted/40'
                      }`}
                    >
                      <Palette className="h-3.5 w-3.5" />
                      <span>{accent}</span>
                    </button>
                  ))}
                </div>
              </div>

              {/* Live Preview Box */}
              <div className="rounded-xl border border-border/80 bg-muted/30 p-5">
                <div className="text-xs font-bold uppercase tracking-wider text-muted-foreground mb-3">
                  {isVi ? 'Xem trước giao diện Banner Trang chủ:' : 'Homepage Live Preview:'}
                </div>
                <div className="border-l-4 border-[var(--portal-yellow)] pl-5 py-2 space-y-2">
                  <SectionEyebrow>{heroEyebrow}</SectionEyebrow>
                  <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground">
                    {heroTitle}
                  </h1>
                  <p className="text-sm text-foreground/80 max-w-xl">
                    {heroDescription}
                  </p>
                </div>
              </div>

              <div className="flex justify-end pt-2">
                <Button
                  type="button"
                  onClick={handlePublishHero}
                  disabled={isSavingHero}
                  className="gap-2 bg-primary text-primary-foreground font-semibold px-6 shadow-sm"
                >
                  <Save className="h-4 w-4" />
                  {isSavingHero ? (isVi ? 'Đang xuất bản...' : 'Publishing...') : (isVi ? 'Xuất bản lên Trang chủ' : 'Publish to Homepage')}
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* TAB 2: ANNOUNCEMENT & DOCUMENT WYSIWYG EDITOR */}
      {activeTab === 'announcement' && (
        <div id="editor-workspace" className="space-y-5">
          <Card>
            <CardContent className="p-4 sm:p-5">
              {editingId && (
                <div className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 px-4 py-2.5 text-xs text-amber-800 dark:text-amber-200">
                  <div className="flex items-center gap-2 font-medium">
                    <Sparkles className="h-4 w-4 text-amber-600 dark:text-amber-400 shrink-0" />
                    <span>
                      {isVi
                        ? `Đang ở chế độ chỉnh sửa bài viết đã lưu: "${title || editingId}"`
                        : `Editing saved notice: "${title || editingId}"`}
                    </span>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={handleCancelEdit}
                    className="h-7 text-xs font-semibold text-amber-700 hover:bg-amber-500/20 dark:text-amber-300"
                  >
                    {isVi ? 'Hủy sửa / Tạo bài mới' : 'Cancel Edit'}
                  </Button>
                </div>
              )}

              {/* Row 1: Document Metadata Configuration (Full-Width Responsive Grid) */}
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-12 items-end">
                <div className="sm:col-span-2 lg:col-span-6">
                  <label className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground whitespace-nowrap">
                    {copy.docTitleLabel}
                  </label>
                  <Input
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder={copy.docTitlePlaceholder}
                    className="font-medium h-10 w-full"
                  />
                </div>
                <div className="sm:col-span-1 lg:col-span-3">
                  <label className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground whitespace-nowrap">
                    {copy.priorityLabel}
                  </label>
                  <select
                    value={priority}
                    onChange={(e) => setPriority(e.target.value as any)}
                    className="h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    <option value="URGENT">{isVi ? 'Khẩn cấp (Urgent)' : 'Urgent'}</option>
                    <option value="HIGH">{isVi ? 'Ưu tiên cao (High)' : 'High'}</option>
                    <option value="NORMAL">{isVi ? 'Bình thường (Normal)' : 'Normal'}</option>
                    <option value="LOW">{isVi ? 'Thông tin chung (Low)' : 'Low'}</option>
                  </select>
                </div>
                <div className="sm:col-span-1 lg:col-span-3">
                  <label className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground whitespace-nowrap">
                    {copy.targetRoleLabel}
                  </label>
                  <select
                    value={targetRole}
                    onChange={(e) => setTargetRole(e.target.value as any)}
                    className="h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    <option value="ALL">{isVi ? 'Toàn trường (All)' : 'All'}</option>
                    <option value="STUDENT">{isVi ? 'Chỉ Sinh viên (Students)' : 'Students'}</option>
                    <option value="LECTURER">{isVi ? 'Chỉ Giảng viên (Lecturers)' : 'Lecturers'}</option>
                  </select>
                </div>
              </div>

              {/* Row 2: Dedicated Action Toolbar */}
              <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-border/60 pt-3">
                <div className="flex flex-wrap items-center gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handlePreviewCurrentDraft}
                    className="gap-1.5 border-primary/40 text-primary hover:bg-primary/10"
                    title={isVi ? 'Xem trước công văn chuẩn e-Office' : 'Preview document'}
                  >
                    <Eye className="h-4 w-4" />
                    {isVi ? 'Xem trước' : 'Preview'}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handleCopy}
                    className="gap-1.5"
                  >
                    {copied ? <Check className="h-4 w-4 text-emerald-600" /> : <Copy className="h-4 w-4" />}
                    {copy.copyContent}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handleDownload}
                    className="gap-1.5"
                  >
                    <Download className="h-4 w-4" />
                    {editorType === 'tinymce' ? (isVi ? 'Tải HTML' : 'Export HTML') : (isVi ? 'Tải .md' : 'Export .md')}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={saveDraft}
                    className="gap-1.5"
                  >
                    <FileText className="h-4 w-4" />
                    {copy.saveDraft}
                  </Button>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  {editingId ? (
                    <Button
                      type="button"
                      onClick={handleUpdateAnnouncement}
                      disabled={isPublishingNotice}
                      size="sm"
                      className="gap-1.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold shadow-xs"
                      title={isVi ? 'Lưu nội dung cập nhật vào cơ sở dữ liệu' : 'Save changes to database'}
                    >
                      <Save className="h-4 w-4" />
                      {isPublishingNotice ? (isVi ? 'Đang lưu...' : 'Saving...') : (isVi ? 'Lưu cập nhật' : 'Save Changes')}
                    </Button>
                  ) : null}
                  <Button
                    type="button"
                    onClick={handlePublishAnnouncement}
                    disabled={isPublishingNotice}
                    size="sm"
                    className="gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold shadow-xs"
                  >
                    <Send className="h-4 w-4" />
                    {isPublishingNotice
                      ? (isVi ? 'Đang gửi...' : 'Publishing...')
                      : editingId
                      ? (isVi ? 'Đăng thành bài mới' : 'Publish as New')
                      : copy.publishAnnouncement}
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={handleReset}
                    className="gap-1.5 text-muted-foreground hover:text-destructive"
                  >
                    <RotateCcw className="h-4 w-4" />
                    {copy.newDoc}
                  </Button>
                </div>
              </div>

              {/* Engine Selector & Statistics Bar */}
              <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-border/70 pt-3 text-xs text-muted-foreground">
                <div className="inline-flex rounded-lg border border-border bg-secondary/40 p-1">
                  <button
                    type="button"
                    onClick={() => handleSwitchEditorType('tinymce')}
                    className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 font-medium transition-all ${
                      editorType === 'tinymce'
                        ? 'bg-primary text-primary-foreground shadow-xs'
                        : 'text-muted-foreground hover:text-foreground'
                    }`}
                  >
                    <Sparkles className="h-3.5 w-3.5" />
                    <span>{copy.tinymceMode}</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => handleSwitchEditorType('markdown')}
                    className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 font-medium transition-all ${
                      editorType === 'markdown'
                        ? 'bg-primary text-primary-foreground shadow-xs'
                        : 'text-muted-foreground hover:text-foreground'
                    }`}
                  >
                    <Code2 className="h-3.5 w-3.5" />
                    <span>{copy.markdownMode}</span>
                  </button>
                </div>

                <div className="flex flex-wrap items-center gap-4">
                  <span className="flex items-center gap-1.5">
                    <FileEdit className="h-3.5 w-3.5 text-primary" />
                    <strong>{stats.words.toLocaleString()}</strong> {copy.words}
                  </span>
                  <span>•</span>
                  <span className="flex items-center gap-1.5">
                    <BookOpen className="h-3.5 w-3.5 text-primary" />
                    <strong>{stats.chars.toLocaleString()}</strong> {copy.characters}
                  </span>
                  <span>•</span>
                  <span className="flex items-center gap-1.5">
                    <Clock className="h-3.5 w-3.5 text-primary" />
                    ~<strong>{stats.minutes}</strong> {copy.minutes} {copy.readingTime}
                  </span>
                  {lastSaved && (
                    <>
                      <span>•</span>
                      <span className="inline-flex items-center gap-1 text-muted-foreground">
                        <Sparkles className="h-3.5 w-3.5 text-amber-500" />
                        {copy.savedAt}: <strong>{lastSaved}</strong>
                      </span>
                    </>
                  )}
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Sortable Content Blocks Builder Toggle & Workspace */}
          <Card className="border-border/80 shadow-xs">
            <CardHeader className="py-3 px-4 border-b border-border/60 bg-muted/20">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2.5">
                  <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <Layers className="h-4 w-4" />
                  </div>
                  <div>
                    <CardTitle className="text-sm font-bold text-foreground flex items-center gap-2">
                      <span>{isVi ? 'Khung Lắp Ghép Khối Cấu Trúc Văn Bản' : 'Institutional Content Blocks Builder'}</span>
                      <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[10.5px] font-semibold text-primary">
                        {isVi ? 'Sắp xếp trực quan' : 'Drag & Drop'}
                      </span>
                    </CardTitle>
                    <p className="text-[11.5px] text-muted-foreground">
                      {isVi
                        ? 'Kéo thả các khối cấu trúc chuẩn hành chính (Quốc hiệu, Kính gửi, Kế hoạch, Bảng biểu, Điều khoản, Con dấu) rồi đưa vào văn bản soạn thảo'
                        : 'Drag & drop institutional blocks to insert directly into official document'}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    type="button"
                    variant={showBlockBuilder ? 'default' : 'outline'}
                    size="sm"
                    onClick={() => setShowBlockBuilder(!showBlockBuilder)}
                    className="h-8 gap-1.5 text-xs font-semibold"
                  >
                    <ListOrdered className="h-3.5 w-3.5" />
                    <span>
                      {showBlockBuilder
                        ? isVi
                          ? 'Thu gọn khung khối'
                          : 'Collapse Blocks'
                        : isVi
                          ? 'Mở khung cấu trúc khối'
                          : 'Open Content Blocks'}
                    </span>
                    {showBlockBuilder ? (
                      <ChevronUp className="h-3.5 w-3.5 ml-0.5" />
                    ) : (
                      <ChevronDown className="h-3.5 w-3.5 ml-0.5" />
                    )}
                  </Button>
                </div>
              </div>
            </CardHeader>
            {showBlockBuilder && (
              <CardContent className="p-4 space-y-4">
                <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
                  <div className="text-xs text-muted-foreground flex items-center gap-1.5">
                    <Sparkles className="h-3.5 w-3.5 text-amber-500 shrink-0" />
                    <span>
                      {isVi
                        ? 'Giữ chuột vào biểu tượng tay cầm ⠿ để kéo thả đổi vị trí các khối. Tích chọn để đưa vào văn bản.'
                        : 'Drag handle ⠿ to reorder blocks. Check boxes to include in compiled document.'}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={handleResetBlocks}
                      className="h-7 text-xs text-muted-foreground hover:text-foreground"
                    >
                      <RotateCcw className="h-3.5 w-3.5 mr-1" />
                      {isVi ? 'Khôi phục thứ tự mẫu' : 'Reset Blocks'}
                    </Button>
                    <Button
                      type="button"
                      variant="default"
                      size="sm"
                      onClick={handleCompileBlocksToTinyMce}
                      className="h-7 gap-1 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold shadow-xs"
                    >
                      <Sparkles className="h-3.5 w-3.5" />
                      {isVi ? 'Chèn tất cả khối vào văn bản' : 'Insert All Blocks'}
                    </Button>
                  </div>
                </div>

                <SortableList<ContentBlock>
                  tag="div"
                  itemTag="div"
                  items={blocks}
                  keyExtractor={(b) => b.id}
                  onOrderChange={handleReorderBlocks}
                  className="space-y-2.5"
                  renderItem={(block, index) => (
                    <div
                      className={cn(
                        'flex items-center justify-between gap-3 rounded-lg border p-3 transition-colors',
                        block.enabled
                          ? 'border-border bg-card shadow-xs'
                          : 'border-dashed border-border/60 bg-muted/30 opacity-60'
                      )}
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <DragHandle
                          className="cursor-grab hover:text-primary active:cursor-grabbing"
                          title={isVi ? 'Kéo để đổi thứ tự khối' : 'Drag to reorder'}
                        />
                        <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-[11px] font-bold text-primary">
                          {String(index + 1).padStart(2, '0')}
                        </span>
                        <input
                          type="checkbox"
                          checked={block.enabled}
                          onChange={() => handleToggleBlock(block.id)}
                          className="h-4 w-4 rounded border-input text-primary focus:ring-primary cursor-pointer"
                          title={isVi ? 'Bật/tắt khối này' : 'Toggle block'}
                        />
                        <div className="min-w-0">
                          <p className="text-xs font-semibold text-foreground truncate">
                            {block.title}
                          </p>
                          <p className="text-[11px] text-muted-foreground truncate">
                            {block.description}
                          </p>
                        </div>
                      </div>

                      <div className="flex shrink-0 items-center gap-1.5">
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => handleInsertSingleBlock(block)}
                          className="h-6 px-2 text-[11px] font-medium"
                          title={isVi ? 'Chèn riêng khối này vào cuối nội dung' : 'Append this block'}
                        >
                          <Plus className="h-3 w-3 mr-0.5" />
                          {isVi ? 'Chèn khối' : 'Append'}
                        </Button>
                      </div>
                    </div>
                  )}
                />
              </CardContent>
            )}
          </Card>

          {/* Editor Workspace */}
          <div className="rounded-xl border border-border/80 bg-card p-1 shadow-sm sm:p-2">
            {editorType === 'tinymce' ? (
              <TinyMceEditor
                value={content}
                onChange={setContent}
                height={580}
                locale={isVi ? 'vi' : 'en'}
                showTemplates={true}
              />
            ) : (
              <RichTextEditor
                value={content}
                onChange={setContent}
                minHeight="540px"
                locale={isVi ? 'vi' : 'en'}
                showTemplates={true}
              />
            )}
          </div>

          {/* Section: Live Announcements Database & One-Click Load into TinyMCE */}
          <Card className="mt-6 border-border/80 shadow-xs">
            <CardHeader className="border-b border-border/60 pb-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <CardTitle className="flex items-center gap-2 text-base font-bold text-foreground">
                    <Megaphone className="h-4 w-4 text-primary" />
                    {isVi
                      ? 'Kho Bài Viết & Thông Báo Đang Lưu Trong Cơ Sở Dữ Liệu'
                      : 'Live Announcements in Campus Records'}
                    <span className="rounded-full bg-blue-500/10 px-2 py-0.5 text-[10.5px] font-semibold text-blue-600 border border-blue-500/20">
                      {isVi ? 'Kéo thả thứ tự' : 'Custom Order'}
                    </span>
                  </CardTitle>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    {isVi
                      ? 'Kéo thả các dòng bài viết ⠿ để thiết lập thứ tự ghim ưu tiên trên Bảng tin toàn trường. Bấm nút "Chỉnh sửa" để mở văn bản trong trình soạn thảo.'
                      : 'Drag and drop rows ⠿ to set priority feed display order. Click "Edit" to open in document editor.'}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {hasUnsavedNoticeOrder && (
                    <Button
                      type="button"
                      variant="default"
                      size="sm"
                      onClick={handleSaveNoticeOrder}
                      className="h-8 gap-1.5 text-xs font-semibold bg-blue-600 hover:bg-blue-700 text-white shadow-xs animate-pulse"
                    >
                      <Save className="h-3.5 w-3.5" />
                      <span>{isVi ? 'Lưu thứ tự ghim bài viết' : 'Save Feed Order'}</span>
                    </Button>
                  )}
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={fetchPublishedNotices}
                    disabled={loadingNotices}
                    className="h-8 gap-1.5 text-xs"
                  >
                    <RefreshCw className={cn('h-3.5 w-3.5', loadingNotices && 'animate-spin')} />
                    <span>{isVi ? 'Làm mới' : 'Refresh'}</span>
                  </Button>
                </div>
              </div>
            </CardHeader>

            {hasUnsavedNoticeOrder && (
              <div className="bg-blue-500/10 border-b border-blue-500/20 px-4 py-2.5 flex items-center justify-between gap-3 text-xs text-blue-700 dark:text-blue-300">
                <div className="flex items-center gap-2">
                  <ArrowUpDown className="h-4 w-4 shrink-0 text-blue-600" />
                  <span>
                    {isVi
                      ? 'Bạn vừa kéo thả sắp xếp lại thứ tự bài viết. Bấm nút "Lưu thứ tự ghim bài viết" để đồng bộ ngay lập tức lên Bảng tin sinh viên & giảng viên.'
                      : 'You changed the order of announcements. Click "Save Feed Order" to broadcast.'}
                  </span>
                </div>
                <Button
                  type="button"
                  size="sm"
                  onClick={handleSaveNoticeOrder}
                  className="h-7 px-3 text-xs bg-blue-600 hover:bg-blue-700 text-white font-semibold shrink-0 shadow-xs"
                >
                  <Save className="h-3.5 w-3.5 mr-1" />
                  {isVi ? 'Lưu ngay' : 'Save Now'}
                </Button>
              </div>
            )}

            <CardContent className="p-0">
              {loadingNotices ? (
                <div className="py-8 text-center text-xs text-muted-foreground">
                  <div className="flex items-center justify-center gap-2">
                    <RefreshCw className="h-4 w-4 animate-spin text-primary" />
                    <span>{isVi ? 'Đang tải danh sách bài viết từ CSDL...' : 'Loading announcements...'}</span>
                  </div>
                </div>
              ) : publishedNotices.length === 0 ? (
                <div className="py-8 text-center text-xs text-muted-foreground">
                  {isVi ? 'Chưa có thông báo nào trong cơ sở dữ liệu.' : 'No announcements in database.'}
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[920px] text-left text-xs border-collapse">
                    <thead className="border-b border-border/60 bg-muted/40 text-muted-foreground font-semibold">
                      <tr>
                        <th className="py-2.5 px-3 w-10 text-center">#</th>
                        <th className="py-2.5 px-3 w-14 text-center">{isVi ? 'Ghim' : 'Pin'}</th>
                        <th className="py-2.5 px-4 min-w-[200px]">{isVi ? 'Tiêu đề văn bản' : 'Title'}</th>
                        <th className="py-2.5 px-3 w-24 whitespace-nowrap">{isVi ? 'Ưu tiên' : 'Priority'}</th>
                        <th className="py-2.5 px-3 w-28 whitespace-nowrap">{isVi ? 'Đối tượng' : 'Audience'}</th>
                        <th className="py-2.5 px-3 max-w-[160px] truncate">{isVi ? 'Người đăng' : 'Publisher'}</th>
                        <th className="py-2.5 px-3 w-24 whitespace-nowrap">{isVi ? 'Ngày ban hành' : 'Date'}</th>
                        <th className="sticky right-0 z-20 bg-muted/95 backdrop-blur-xs py-2.5 px-4 min-w-[190px] text-right shadow-[-4px_0_6px_-2px_rgba(0,0,0,0.08)]">{isVi ? 'Thao tác' : 'Actions'}</th>
                      </tr>
                    </thead>
                    <SortableList<AnnouncementRecord>
                      tag="tbody"
                      itemTag="tr"
                      items={publishedNotices}
                      keyExtractor={(ann) => ann.id}
                      onOrderChange={handleSortableNoticeReorder}
                      itemClassName="hover:bg-muted/30 transition-colors border-b border-border/50"
                      renderItem={(ann, index) => (
                        <>
                          <td className="py-3 px-3 text-center">
                            <DragHandle
                              className="mx-auto cursor-grab hover:text-primary active:cursor-grabbing"
                              title={isVi ? 'Kéo thả để sắp xếp thứ tự hiển thị' : 'Drag to reorder notice'}
                            />
                          </td>
                          <td className="py-3 px-3 text-center whitespace-nowrap">
                            <span
                              className={cn(
                                'inline-flex items-center justify-center rounded px-1.5 py-0.5 font-bold text-[10.5px]',
                                index === 0
                                  ? 'bg-amber-500/15 text-amber-700 dark:text-amber-300 border border-amber-500/30'
                                  : 'bg-muted text-muted-foreground'
                              )}
                            >
                              {index === 0 ? (isVi ? 'Top 1' : 'Top 1') : `#${index + 1}`}
                            </span>
                          </td>
                          <td className="py-3 px-4 min-w-[200px] max-w-sm">
                            <div className="font-semibold text-foreground line-clamp-2">
                              {ann.title}
                            </div>
                            <div className="text-[11px] text-muted-foreground truncate mt-0.5">
                              {ann.isGlobal
                                ? (isVi ? 'Phạm vi: Toàn trường' : 'Scope: Campus-wide')
                                : (isVi ? 'Phạm vi: Phân quyền đối tượng' : 'Scope: Targeted audience')}
                            </div>
                          </td>
                          <td className="py-3 px-3 whitespace-nowrap">
                            <span
                              className={cn(
                                'inline-flex items-center rounded-full px-2 py-0.5 text-[10.5px] font-semibold',
                                ann.priority === 'URGENT'
                                  ? 'bg-red-500/10 text-red-600 border border-red-500/30'
                                  : ann.priority === 'HIGH'
                                  ? 'bg-amber-500/10 text-amber-600 border border-amber-500/30'
                                  : 'bg-blue-500/10 text-blue-600 border border-blue-500/30'
                              )}
                            >
                              {ann.priority}
                            </span>
                          </td>
                          <td className="py-3 px-3 whitespace-nowrap">
                            <span className="inline-flex items-center rounded bg-secondary/80 px-2 py-0.5 text-[11px] font-medium text-foreground">
                              {ann.isGlobal
                                ? (isVi ? 'Toàn trường' : 'All')
                                : ann.targetRoles?.includes('STUDENT')
                                ? (isVi ? 'Sinh viên' : 'Students')
                                : (isVi ? 'Giảng viên' : 'Lecturers')}
                            </span>
                          </td>
                          <td className="py-3 px-3 whitespace-nowrap text-muted-foreground text-[11.5px] max-w-[160px] truncate" title={ann.publishedBy || 'Phòng Đào tạo'}>
                            {ann.publishedBy || 'Phòng Đào tạo'}
                          </td>
                          <td className="py-3 px-3 whitespace-nowrap text-muted-foreground text-[11.5px]">
                            {ann.createdAt ? new Date(ann.createdAt).toLocaleDateString('vi-VN') : '—'}
                          </td>
                          <td className="sticky right-0 z-10 bg-card/95 backdrop-blur-xs py-3 px-4 min-w-[190px] text-right whitespace-nowrap shadow-[-4px_0_6px_-2px_rgba(0,0,0,0.08)]">
                            <div className="flex items-center justify-end gap-2">
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={() => setPreviewingNotice(ann)}
                                className="h-7 px-2.5 text-[11.5px] gap-1 hover:bg-primary/10 hover:text-primary"
                                title={isVi ? 'Xem trước công văn chuẩn e-Office' : 'Preview document'}
                              >
                                <Eye className="h-3.5 w-3.5 text-primary" />
                                <span>{isVi ? 'Xem trước' : 'Preview'}</span>
                              </Button>
                              <Button
                                type="button"
                                variant="default"
                                size="sm"
                                onClick={() => setEditingNoticeModal(ann)}
                                className="h-7 px-2.5 text-[11.5px] gap-1 bg-blue-600 hover:bg-blue-700 text-white font-semibold shadow-xs"
                                title={isVi ? 'Chỉnh sửa nhanh bài viết này' : 'Edit announcement'}
                              >
                                <FileEdit className="h-3.5 w-3.5" />
                                <span>{isVi ? 'Chỉnh sửa' : 'Edit'}</span>
                              </Button>
                            </div>
                          </td>
                        </>
                      )}
                    />
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      {/* TAB 3: TEMPLATES REPOSITORY */}
      {activeTab === 'templates' && (
        <div className="grid gap-5 md:grid-cols-2">
          {TEMPLATES.map((tmpl) => (
            <Card key={tmpl.id} className="flex flex-col justify-between hover:border-primary/50 transition-colors">
              <CardHeader>
                <CardTitle className="text-base font-bold text-slate-800 flex items-center gap-2">
                  <BookOpen className="h-4 w-4 text-primary" />
                  {isVi ? tmpl.nameVi : tmpl.nameEn}
                </CardTitle>
                <p className="text-xs text-muted-foreground mt-1">
                  {isVi ? tmpl.descVi : tmpl.descVi}
                </p>
              </CardHeader>
              <CardContent className="pt-2 flex justify-end gap-2 border-t border-border/50">
                <Button
                  type="button"
                  variant="default"
                  size="sm"
                  onClick={() => applyTemplate(tmpl.contentVi, isVi ? tmpl.nameVi : tmpl.nameEn)}
                  className="gap-1.5 text-xs font-semibold"
                >
                  <Send className="h-3.5 w-3.5" />
                  {isVi ? 'Nạp vào Trình soạn thảo' : 'Load into Editor'}
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Official Administrative Reader Modal */}
      {previewingNotice && (
        <AnnouncementReaderModal
          announcement={previewingNotice}
          isOpen={Boolean(previewingNotice)}
          onClose={() => setPreviewingNotice(null)}
          onEdit={(notice) => {
            setPreviewingNotice(null);
            setEditingNoticeModal(notice);
          }}
        />
      )}

      {/* In-Place Administrative Edit Modal */}
      {editingNoticeModal && (
        <AnnouncementEditModal
          announcement={editingNoticeModal}
          isOpen={Boolean(editingNoticeModal)}
          onClose={() => setEditingNoticeModal(null)}
          onSave={handleSaveModalEdit}
          onOpenStudio={(notice) => {
            setEditingNoticeModal(null);
            handleLoadAnnouncement(notice);
          }}
        />
      )}
    </div>
  );
}
