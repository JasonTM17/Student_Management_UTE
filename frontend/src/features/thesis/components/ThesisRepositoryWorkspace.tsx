'use client';

import {
  Archive,
  BookOpen,
  Building2,
  ExternalLink,
  FileDown,
  FileStack,
  Search,
  Users,
  X,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { EmptyState, LoadingState } from '@/components/ui/state-block';
import { StatusBadge } from '@/components/thesis/StatusBadge';
import type { I18nMessages } from '@/i18n/messages';
import type { Locale } from '@/i18n/config';
import { type ThesisRepositoryReport } from '@/lib/thesis-api';
import { fillCopy, formatReportFileSize } from '@/features/thesis/components/MetricCard';

interface ThesisRepositoryWorkspaceProps {
  messages: I18nMessages;
  locale: Locale;
  formatDateTime: (value: string | number | Date) => string;
  reports: ThesisRepositoryReport[];
  filteredReports: ThesisRepositoryReport[];
  roundName?: string;
  isLoading: boolean;
  search: string;
  onSearchChange: (value: string) => void;
  onDownloadReport: (report: ThesisRepositoryReport) => void | Promise<void>;
}

/** Faculty archive of every report submitted in the selected round (server projection). */
export default function ThesisRepositoryWorkspace({
  messages,
  locale,
  formatDateTime,
  reports,
  filteredReports,
  roundName,
  isLoading,
  search,
  onSearchChange,
  onDownloadReport,
}: ThesisRepositoryWorkspaceProps) {
  const pageCopy = messages.thesisWorkflow.page;
  const totalReports = reports.length;
  const totalFiles = reports.filter((r) => r.fileName).length;
  const totalUrls = reports.filter((r) => r.url).length;

  return (
    <Card className="rounded-xl border-border/80 shadow-xs">
      <CardHeader>
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div className="space-y-1">
            <CardTitle className="flex items-center gap-2 text-lg">
              <Archive className="h-5 w-5 text-primary" />
              {pageCopy.repositoryTitle}
            </CardTitle>
            <CardDescription className="text-xs sm:text-sm">
              {pageCopy.repositoryDescription}
            </CardDescription>
          </div>
          {/* Quick Metrics */}
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center gap-1.5 rounded-lg border border-primary/20 bg-primary/5 px-3 py-1 text-xs font-semibold text-primary">
              <FileStack className="h-3.5 w-3.5" />
              {fillCopy(pageCopy.reportsCount, { count: totalReports })}
            </span>
            <span className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-500/20 bg-emerald-500/10 px-3 py-1 text-xs font-semibold text-emerald-700 dark:text-emerald-300">
              <FileDown className="h-3.5 w-3.5" />
              {totalFiles} {locale === 'vi' ? 'tài liệu đính kèm' : 'attached files'}
            </span>
            {totalUrls > 0 && (
              <span className="inline-flex items-center gap-1.5 rounded-lg border border-sky-500/20 bg-sky-500/10 px-3 py-1 text-xs font-semibold text-sky-700 dark:text-sky-300">
                <ExternalLink className="h-3.5 w-3.5" />
                {totalUrls} {locale === 'vi' ? 'liên kết ngoài' : 'external links'}
              </span>
            )}
          </div>
        </div>

        {/* Search bar */}
        <div className="pt-2">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              type="text"
              value={search}
              onChange={(e) => onSearchChange(e.target.value)}
              placeholder={pageCopy.repositorySearchPlaceholder}
              className="pl-9 pr-8 text-xs sm:text-sm h-10 rounded-xl"
            />
            {search ? (
              <button
                type="button"
                onClick={() => onSearchChange('')}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                <X className="h-4 w-4" />
              </button>
            ) : null}
          </div>
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        {isLoading ? (
          <div className="py-12 flex justify-center">
            <LoadingState label={messages.common.states.loading} />
          </div>
        ) : reports.length === 0 ? (
          <EmptyState
            icon={Archive}
            title={pageCopy.noRoundReportsYet}
            description={locale === 'vi' ? 'Các nhóm nghiên cứu sau khi hoàn thành sẽ nộp báo cáo tại đây để lưu trữ và thẩm định.' : 'Research groups will submit their thesis reports here once ready for archival.'}
            className="min-h-[220px]"
          />
        ) : filteredReports.length === 0 ? (
          <EmptyState
            icon={Search}
            title={pageCopy.noReportsFound}
            description={locale === 'vi' ? 'Thử thay đổi từ khóa tìm kiếm theo tên đề tài, mã số sinh viên hoặc giảng viên hướng dẫn.' : 'Try adjusting your search keywords by topic, student ID or supervisor.'}
            className="min-h-[200px]"
          />
        ) : (
          <div className="space-y-4">
            <div className="flex items-center justify-between text-xs text-muted-foreground px-1">
              <span>{fillCopy(pageCopy.allReports, { count: filteredReports.length })}</span>
              <span>{locale === 'vi' ? 'Đợt:' : 'Round:'} <strong className="text-foreground">{roundName}</strong></span>
            </div>

            <div className="grid grid-cols-1 gap-4">
              {filteredReports.map((report) => {
                return (
                  <div
                    key={report.reportId}
                    className="rounded-xl border border-border/70 bg-card p-5 transition-all hover:border-primary/40 hover:shadow-xs space-y-4"
                  >
                    {/* Header row: Topic title & status badges */}
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                      <div className="space-y-1 min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <StatusBadge status={report.groupStatus} />
                          <StatusBadge status={report.approvalStatus} variant="approval" />
                          <span className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-[11px] text-muted-foreground">
                            <Building2 className="h-3 w-3" /> {report.departmentName}
                          </span>
                        </div>
                        <h4 className="text-base font-bold text-foreground leading-snug">
                          {report.topicTitle || report.title || pageCopy.thesisDocumentLabel}
                        </h4>
                        {report.topicDescription && (
                          <p className="text-xs text-muted-foreground line-clamp-2">
                            {report.topicDescription}
                          </p>
                        )}
                      </div>

                      {/* Submission timestamp badge */}
                      <div className="flex flex-col sm:items-end text-xs text-muted-foreground shrink-0">
                        <span>
                          {pageCopy.submittedAtLabel} <strong className="text-foreground">{formatDateTime(report.submittedAt)}</strong>
                        </span>
                        {report.updatedAt && report.updatedAt !== report.submittedAt && (
                          <span className="text-[11px]">
                            {pageCopy.lastUpdatedLabel} {formatDateTime(report.updatedAt)}
                          </span>
                        )}
                      </div>
                    </div>

                    {/* Roster & Guidance grid */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3 rounded-lg border border-border/60 bg-muted/20 p-3 text-xs">
                      {/* Supervisors */}
                      <div className="space-y-1.5">
                        <span className="font-semibold text-foreground flex items-center gap-1.5">
                          <BookOpen className="h-3.5 w-3.5 text-primary" />
                          {pageCopy.archiveSupervisorLabel}
                        </span>
                        {report.supervisors.length === 0 ? (
                          <span className="text-muted-foreground italic">{locale === 'vi' ? 'Chưa phân công' : 'Not assigned'}</span>
                        ) : (
                          <div className="space-y-1">
                            {report.supervisors.map((s) => (
                              <div key={`${s.supervisorOrder}-${s.displayName}`} className="flex items-center gap-2">
                                <span className="font-medium text-foreground">
                                  {s.displayName || (locale === 'vi' ? 'Chưa có tên' : 'Unnamed supervisor')}
                                </span>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>

                      {/* Research group members */}
                      <div className="space-y-1.5">
                        <span className="font-semibold text-foreground flex items-center gap-1.5">
                          <Users className="h-3.5 w-3.5 text-primary" />
                          {pageCopy.membersLabel} ({report.members.length})
                        </span>
                        {report.members.length === 0 ? (
                          <span className="text-muted-foreground italic">{locale === 'vi' ? 'Chưa có thông tin thành viên' : 'No roster details'}</span>
                        ) : (
                          <div className="space-y-1">
                            {report.members.map((m) => {
                              const name = m.displayName || m.studentNumber || (locale === 'vi' ? 'Thành viên' : 'Member');
                              return (
                                <div key={`${m.studentNumber || name}-${m.isLeader ? 'leader' : 'member'}`} className="flex flex-wrap items-center gap-1.5">
                                  <span className="font-medium text-foreground">{name}</span>
                                  {m.studentNumber && (
                                    <span className="font-mono text-muted-foreground text-[11px] bg-background/80 px-1.5 py-0.5 rounded">
                                      MSSV: {m.studentNumber}
                                    </span>
                                  )}
                                  {m.isLeader && (
                                    <span className="rounded-md bg-primary/15 px-1.5 py-0.5 text-[10px] font-bold text-primary">
                                      {messages.thesis.leaderBadge}
                                    </span>
                                  )}
                                  {m.isExternal && (
                                    <span className="rounded-md bg-indigo-500/15 px-1.5 py-0.5 text-[10px] font-bold text-indigo-700 dark:text-indigo-300">
                                      {messages.thesis.externalBadge}
                                    </span>
                                  )}
                                </div>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Notes / Abstract */}
                    {report.note && (
                      <div className="rounded-lg border border-border/50 bg-background/50 p-3 text-xs">
                        <span className="font-semibold text-foreground block mb-1">
                          {pageCopy.reportNotesLabel}
                        </span>
                        <p className="text-muted-foreground whitespace-pre-wrap leading-relaxed">
                          {report.note}
                        </p>
                      </div>
                    )}

                    {/* Attached File Download & External Link */}
                    <div className="flex flex-wrap items-center justify-between gap-3 pt-1 border-t border-border/50">
                      <div className="flex flex-wrap items-center gap-2">
                        {report.fileName ? (
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            onClick={() => void onDownloadReport(report)}
                            className="gap-2 border-primary/30 hover:bg-primary/10 hover:border-primary font-semibold text-xs"
                          >
                            <FileDown className="h-4 w-4 text-primary" />
                            <span>{report.fileName}</span>
                            <span className="text-muted-foreground text-[11px] font-normal">
                              ({[report.fileType, formatReportFileSize(report.fileSize, locale)].filter(Boolean).join(' · ')})
                            </span>
                          </Button>
                        ) : (
                          <span className="text-xs text-muted-foreground italic">
                            {locale === 'vi' ? 'Không có file đính kèm' : 'No attached file'}
                          </span>
                        )}

                        {report.url && (
                          <a
                            href={report.url}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-1.5 text-xs font-semibold text-primary hover:underline bg-primary/5 border border-primary/20 rounded-lg px-3 py-1.5"
                          >
                            <ExternalLink className="h-3.5 w-3.5" />
                            <span>{pageCopy.openExternalLink}</span>
                          </a>
                        )}
                      </div>

                      <div className="text-[11px] text-muted-foreground">
                        {locale === 'vi' ? 'Người nộp:' : 'Submitted by:'}{' '}
                        <strong className="text-foreground">
                          {report.submittedByDisplayName || report.submittedByStudentNumber || (locale === 'vi' ? 'Không rõ' : 'Unavailable')}
                        </strong>
                        {report.submittedByDisplayName && report.submittedByStudentNumber && (
                          <span className="ml-1 font-mono">({report.submittedByStudentNumber})</span>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
