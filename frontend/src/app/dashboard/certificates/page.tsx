'use client';

import React, { useState, useEffect, useMemo } from 'react';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { EmptyState, LoadingState } from '@/components/ui/state-block';
import { conductApi, curriculumApi } from '@/lib/api';
import { MyCurriculumResponse } from '@/types/api';
import { Button } from '@/components/ui/button';
import {
  Printer,
  Check,
  Award,
  FileCheck,
  Bus,
  Building,
  Briefcase,
  Info,
  ShieldX,
  TriangleAlert,
} from 'lucide-react';

type CertificatePurpose =
  | 'MILITARY_DEFERMENT'
  | 'STUDENT_LOAN'
  | 'BUS_PASS'
  | 'TAX_EXEMPTION'
  | 'INTERNSHIP';

interface PurposeOption {
  id: CertificatePurpose;
  icon: React.ElementType;
}

const PURPOSE_OPTIONS: PurposeOption[] = [
  { id: 'MILITARY_DEFERMENT', icon: ShieldCheckIcon },
  { id: 'STUDENT_LOAN', icon: Building },
  { id: 'BUS_PASS', icon: Bus },
  { id: 'TAX_EXEMPTION', icon: FileCheck },
  { id: 'INTERNSHIP', icon: Briefcase },
];

// Small local icon so the purpose list keeps its shield glyph without
// importing lucide's ShieldCheck (which read as a security endorsement on the
// old fabricated page).
function ShieldCheckIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

export default function CertificatesPage() {
  // The generator mints a student certificate from student identity claims, so a
  // staff account reaching it by URL used to render a sheet prefilled with blank
  // student attributes instead of a forbidden state.
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['STUDENT']);
  const { locale, messages, formatDate } = useI18n();
  const certCopy = messages.certificates;

  const [selectedPurpose, setSelectedPurpose] = useState<CertificatePurpose>('MILITARY_DEFERMENT');
  const [customRecipient, setCustomRecipient] = useState<string>('');
  const [curriculumData, setCurriculumData] = useState<MyCurriculumResponse | null>(null);
  // The office-issued MSSV lives on the student record, not the auth session
  // (whose `studentId` is the internal profile id, e.g. "student-profile").
  // Same source the transcript page uses: the conduct summary's studentCode.
  const [studentCode, setStudentCode] = useState('');

  useEffect(() => {
    let active = true;
    async function loadCurriculum() {
      try {
        const res = await curriculumApi.getMyCurriculum();
        if (active) setCurriculumData(res);
      } catch {
        // No curriculum data: the sheet renders an em-dash, never a made-up major.
      }
    }
    void loadCurriculum();
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!hasAccess) return;
    let cancelled = false;
    conductApi
      .getMyConduct()
      .then((summary) => {
        if (cancelled) return;
        const code = summary?.studentCode?.trim();
        // The backend stringifies the column, so a missing record arrives as
        // the literal "null"; treat that as unresolved rather than as an MSSV.
        if (code && code !== 'null' && code !== 'undefined') setStudentCode(code);
      })
      .catch(() => {
        // Unavailable MSSV stays blank — never falls back to the internal id.
      });
    return () => {
      cancelled = true;
    };
  }, [hasAccess]);

  const activeOption = useMemo(
    () => PURPOSE_OPTIONS.find((p) => p.id === selectedPurpose) || PURPOSE_OPTIONS[0],
    [selectedPurpose]
  );
  const activePurposeCopy = certCopy.purposes[activeOption.id];

  // Identity comes only from the signed-in session. When a field is missing we
  // render an em-dash — never another student's name or MSSV.
  const studentName = useMemo(() => {
    if (user?.firstName || user?.lastName) {
      return `${user.lastName ?? ''} ${user.firstName ?? ''}`.trim().toUpperCase();
    }
    return null;
  }, [user]);

  const studentId = studentCode || null;

  const cohort = useMemo(() => {
    if (!studentId) return null;
    const match = studentId.match(/^(\d{2})/);
    if (!match) return null;
    return `20${match[1]} (K${match[1]})`;
  }, [studentId]);

  const departmentName = curriculumData?.curriculum?.name || null;

  const today = useMemo(() => new Date(), []);

  const handlePrint = () => {
    window.print();
  };

  const genderValue = (() => {
    if (user?.gender === 'MALE') return certCopy.genderMale;
    if (user?.gender === 'FEMALE') return certCopy.genderFemale;
    if (user?.gender === 'OTHER') return certCopy.genderOther;
    return null;
  })();

  const missing = certCopy.missingValue;

  // The vi template carries {day}/{month}/{year}; the en template carries {date}
  // through the locale formatter. Replacing every placeholder on the active
  // template is a no-op for the ones it does not use.
  const cityDateLine = certCopy.cityDateLine
    .replace('{day}', String(today.getDate()))
    .replace('{month}', String(today.getMonth() + 1))
    .replace('{year}', String(today.getFullYear()))
    .replace('{date}', formatDate(today, { month: 'long', day: 'numeric', year: 'numeric' }));

  // Without any identity claim there is nothing to prefill, so the page says so
  // instead of rendering a sheet of em-dashes.
  const hasIdentity = Boolean(studentName || studentId);

  if (authLoading) {
    return (
      <div className="min-h-screen py-6 px-4 max-w-7xl mx-auto">
        <LoadingState label={messages.common.states.loadingContent} />
      </div>
    );
  }
  if (!hasAccess) {
    return <WorkspaceForbiddenState />;
  }

  return (
    <div className="min-h-screen py-6 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{certCopy.eyebrow}</SectionEyebrow>}
        title={certCopy.pageTitle}
        tabLabel={certCopy.tabLabel}
        description={certCopy.pageDescription}
        actions={
          <Button
            type="button"
            onClick={handlePrint}
            className="flex items-center gap-2 font-medium shadow-sm"
          >
            <Printer className="h-4 w-4" />
            <span>{certCopy.printAction}</span>
          </Button>
        }
      />

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Purpose Selector Panel - Screen Only */}
        <div className="print:hidden lg:col-span-5 space-y-6">
          <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded-md text-xs font-semibold bg-amber-50 text-amber-800 dark:bg-amber-950/40 dark:text-amber-300 border border-amber-200 dark:border-amber-800">
            <TriangleAlert className="h-3.5 w-3.5" />
            <span>{certCopy.serviceBadge}</span>
          </div>

          <div className="bg-card border border-border rounded-xl p-5 shadow-sm">
            <h2 className="text-base font-semibold text-foreground flex items-center gap-2 mb-3">
              <Award className="h-4 w-4 text-primary" />
              {certCopy.selectPurposeTitle}
            </h2>
            <div className="space-y-2.5">
              {PURPOSE_OPTIONS.map((opt) => {
                const Icon = opt.icon;
                const isSelected = opt.id === selectedPurpose;
                const optionCopy = certCopy.purposes[opt.id];
                return (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => setSelectedPurpose(opt.id)}
                    className={`w-full text-left p-3.5 rounded-lg border transition-all flex items-start gap-3.5 ${
                      isSelected
                        ? 'border-primary bg-primary/5 ring-1 ring-primary/20 shadow-sm'
                        : 'border-border/70 hover:border-border hover:bg-accent/40'
                    }`}
                  >
                    <div
                      className={`p-2 rounded-md shrink-0 mt-0.5 ${
                        isSelected ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground'
                      }`}
                    >
                      <Icon className="h-4 w-4" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="text-sm font-semibold text-foreground flex items-center justify-between">
                        <span>{optionCopy.title}</span>
                        {isSelected && <Check className="h-4 w-4 text-primary shrink-0 ml-2" />}
                      </div>
                      <p className="text-xs text-muted-foreground mt-0.5 line-clamp-2">
                        {optionCopy.decree}
                      </p>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="bg-card border border-border rounded-xl p-5 shadow-sm space-y-4">
            <h2 className="text-base font-semibold text-foreground flex items-center gap-2">
              <Building className="h-4 w-4 text-primary" />
              {certCopy.recipientTitle}
            </h2>
            <div>
              <label htmlFor="custom-recipient" className="block text-xs font-medium text-muted-foreground mb-1.5">
                {certCopy.recipientCustomLabel}
              </label>
              <textarea
                id="custom-recipient"
                rows={2}
                value={customRecipient}
                onChange={(e) => setCustomRecipient(e.target.value)}
                placeholder={activePurposeCopy.defaultRecipient}
                className="w-full text-xs sm:text-sm p-3 rounded-lg border border-input bg-background focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <p className="text-[11px] text-muted-foreground mt-1">{certCopy.recipientHelper}</p>
            </div>
          </div>

          <div className="bg-muted/40 border border-border/80 rounded-xl p-4 text-xs space-y-2 text-muted-foreground">
            <div className="flex items-start gap-2">
              <Info className="h-4 w-4 text-primary shrink-0 mt-0.5" />
              <p>
                <strong className="text-foreground">{certCopy.issuedByLabel} </strong>
                {certCopy.issuedByValue}. {certCopy.issuedByNote}
              </p>
            </div>
            {!curriculumData && (
              <div className="flex items-start gap-2 border-t border-border/60 pt-2">
                <TriangleAlert className="h-4 w-4 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
                <p>{certCopy.curriculumHint}</p>
              </div>
            )}
          </div>
        </div>

        {/* Printable Preview Document Sheet */}
        <div className="lg:col-span-7">
          {!hasIdentity ? (
            <EmptyState
              icon={ShieldX}
              title={certCopy.identityMissingTitle}
              description={certCopy.identityMissingDescription}
            />
          ) : (
          <div className="bg-white text-slate-900 border border-slate-200 rounded-xl shadow-md p-6 sm:p-10 font-serif leading-relaxed text-sm print:p-0 print:border-none print:shadow-none print:m-0 print:w-full">
            {/* Honest preview banner: the first thing any reader sees, on screen
                and on paper. This page never mints an official document. */}
            <div className="mb-6 rounded-lg border-2 border-dashed border-amber-500 bg-amber-50 px-4 py-3 text-center font-sans">
              <p className="text-sm sm:text-base font-black uppercase tracking-wide text-amber-800">
                {certCopy.previewBanner}
              </p>
              <p className="mt-1 text-[11px] sm:text-xs text-amber-700">{certCopy.previewBannerNote}</p>
            </div>

            {/* Header: National Header & University Header */}
            <div className="grid grid-cols-2 gap-4 pb-6 border-b border-slate-300">
              <div className="text-center font-sans">
                <p className="text-[11px] uppercase tracking-wider font-medium text-slate-600">
                  {certCopy.nationalHeader}
                </p>
                <p className="text-xs sm:text-sm font-bold uppercase text-slate-900 mt-0.5">
                  {certCopy.universityHeader}
                </p>
                <div className="w-16 h-[1.5px] bg-slate-900 mx-auto my-1"></div>
                <p className="text-[11px] font-mono text-slate-600 mt-1">
                  {certCopy.serialLabel} {missing}
                </p>
              </div>

              <div className="text-center font-sans">
                <p className="text-xs sm:text-sm font-bold uppercase text-slate-900">
                  {certCopy.socialistHeader}
                </p>
                <p className="text-xs sm:text-sm font-semibold text-slate-800 mt-0.5">
                  {certCopy.independenceHeader}
                </p>
                <div className="w-24 h-[1.5px] bg-slate-900 mx-auto my-1"></div>
                <p className="text-[11px] italic text-slate-600 mt-1">{cityDateLine}</p>
              </div>
            </div>

            {/* Document Title */}
            <div className="text-center my-6">
              <h2 className="text-xl sm:text-2xl font-bold uppercase text-slate-950 font-sans tracking-wide">
                {certCopy.docTitle}
              </h2>
              <p className="text-xs text-slate-600 italic font-sans mt-1">{certCopy.docSubtitle}</p>
            </div>

            {/* Document Body */}
            <div className="space-y-4 my-6 text-[13px] sm:text-sm text-slate-800">
              <p className="font-semibold">{certCopy.declarationIntro}</p>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-y-2 pt-1 font-sans">
                <div>
                  <span className="text-slate-600">{certCopy.nameLabel}</span>
                  <strong className="text-slate-950">{studentName || missing}</strong>
                </div>
                <div>
                  <span className="text-slate-600">{certCopy.idLabel}</span>
                  <strong className="text-slate-950 font-mono">{studentId || missing}</strong>
                </div>
                <div>
                  <span className="text-slate-600">{certCopy.dobLabel}</span>
                  <span>{user?.dateOfBirth ? formatDate(user.dateOfBirth) : missing}</span>
                </div>
                <div>
                  <span className="text-slate-600">{certCopy.genderLabel}</span>
                  <span>{genderValue || missing}</span>
                </div>
                <div>
                  <span className="text-slate-600">{certCopy.cohortLabel}</span>
                  <strong className="text-slate-900">{cohort || missing}</strong>
                </div>
                <div>
                  <span className="text-slate-600">{certCopy.programLevelLabel}</span>
                  <strong className="text-slate-900">{certCopy.programLevelValue}</strong>
                </div>
                <div className="sm:col-span-2">
                  <span className="text-slate-600">{certCopy.majorLabel}</span>
                  <strong className="text-slate-900">{departmentName || missing}</strong>
                </div>
              </div>

              {/* No academic-standing claim: the session supplies no enrollment /
                  discipline field, so the draft asserts nothing about standing. */}

              <div className="pt-3 border-t border-slate-200 space-y-2">
                <p>
                  <strong className="text-slate-950">{certCopy.purposeLabel} </strong>
                  <span>{activePurposeCopy.body}</span>
                </p>
                <p className="text-xs italic text-slate-600">
                  <span>{certCopy.legalBasisLabel} </span>
                  {activePurposeCopy.decree}
                </p>
                <p>
                  <strong className="text-slate-950">{certCopy.recipientLineLabel} </strong>
                  <span>{customRecipient.trim() || activePurposeCopy.defaultRecipient}</span>
                </p>
              </div>
            </div>

            {/* Issuance Guidance (replaces the simulated QR / signature area) */}
            <div className="grid grid-cols-2 gap-6 pt-6 border-t border-slate-300 items-start">
              <div className="space-y-3 font-sans">
                <div>
                  <p className="text-[11px] font-bold uppercase text-slate-800">{certCopy.receiverTitle}</p>
                  <p className="text-[11px] text-slate-600 leading-tight">{certCopy.receiverFirst}</p>
                  <p className="text-[11px] text-slate-600 leading-tight">{certCopy.receiverSecond}</p>
                </div>
              </div>

              <div className="font-sans space-y-2 text-center">
                <p className="text-[11px] font-bold uppercase text-slate-800 leading-tight">
                  {certCopy.issuedByLabel}
                </p>
                <p className="text-xs font-bold uppercase text-slate-900 leading-tight">
                  {certCopy.issuedByValue}
                </p>
                <p className="text-[11px] italic text-slate-600 leading-snug">{certCopy.issuedByNote}</p>
              </div>
            </div>
          </div>
          )}
        </div>
      </div>
    </div>
  );
}
