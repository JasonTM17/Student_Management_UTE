'use client';

import { useRef, useState } from 'react';
import { FileUp } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';
import { parseGradeSheet, type GradeImportRow, type GradeImportStudent, type ParsedGradeSheet } from '@/lib/grade-sheet';

export type { GradeImportRow };

interface SkippedRow {
  line: number;
  reason: string;
}

export function GradeImportPanel({
  students,
  disabled,
  onApply,
}: {
  students: GradeImportStudent[];
  disabled?: boolean;
  onApply: (rows: GradeImportRow[]) => void;
}) {
  const { locale } = useI18n();
  const [open, setOpen] = useState(false);
  const [pasted, setPasted] = useState('');
  const [fileName, setFileName] = useState('');
  const [result, setResult] = useState<ParsedGradeSheet | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const vi = locale === 'vi';

  const evaluate = (text: string) => {
    const parsed = parseGradeSheet(text, students);
    setResult(parsed);
    return parsed;
  };

  const onFile = (file: File | null) => {
    setFileName(file?.name ?? '');
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => evaluate(String(reader.result ?? ''));
    reader.readAsText(file, 'utf-8');
  };

  const apply = () => {
    if (!result || result.applied.length === 0) return;
    onApply(result.applied);
    setResult(null);
    setPasted('');
    setFileName('');
    if (fileRef.current) fileRef.current.value = '';
  };

  return (
    <div className="rounded-lg border border-border/70 bg-card">
      <button
        type="button"
        onClick={() => setOpen((current) => !current)}
        aria-expanded={open}
        disabled={disabled}
        className="inline-flex items-center gap-2 px-3 py-2 text-sm font-semibold text-primary hover:underline disabled:opacity-50"
      >
        <FileUp className="h-4 w-4" />
        {vi ? 'Nhập điểm từ tệp CSV/TSV' : 'Import scores from a CSV/TSV file'}
      </button>

      {open ? (
        <div className="space-y-3 border-t border-border/60 p-3">
          <p className="text-xs leading-5 text-muted-foreground">
            {vi
              ? 'Mỗi dòng gồm: mã sinh viên (hoặc email), điểm quá trình, điểm cuối kỳ — phân cách bởi dấu tab, chấm phẩy hoặc phẩy. Có thể sao chép vùng dữ liệu từ Excel/Google Sheets rồi dán vào tệp CSV, hệ thống không đọc trực tiếp tệp .xlsx.'
              : 'Each row is: student code (or email), process score, final score — separated by tab, semicolon or comma. Copy a range from Excel/Google Sheets into a CSV file; .xlsx files are not read directly.'}
          </p>

          <label className="inline-flex cursor-pointer items-center gap-2 text-xs font-semibold text-foreground">
            <input
              type="file"
              accept=".csv,text/csv,.txt,text/plain"
              className="hidden"
              onChange={(event) => onFile(event.target.files?.[0] ?? null)}
              disabled={disabled}
              ref={fileRef}
            />
            <span className="rounded-lg border border-border/80 px-2.5 py-1.5 hover:bg-secondary/60">
              {vi ? 'Chọn tệp CSV' : 'Choose a CSV file'}
            </span>
            {fileName ? <span className="font-normal text-muted-foreground">{fileName}</span> : null}
          </label>

          <textarea
            value={pasted}
            onChange={(event) => {
              setPasted(event.target.value);
              evaluate(event.target.value);
            }}
            rows={4}
            disabled={disabled}
            aria-label={vi ? 'Dán bảng điểm' : 'Paste grade rows'}
            placeholder={vi ? 'Hoặc dán các dòng điểm vào đây…' : 'Or paste grade rows here…'}
            className="w-full rounded-lg border border-input bg-background px-3 py-2 font-mono text-xs text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />

          {result ? (
            <div className="space-y-1 text-xs">
              <p
                className={cn(
                  'font-semibold',
                  result.applied.length > 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-muted-foreground',
                )}
              >
                {vi
                  ? `Khớp ${result.applied.length} dòng, bỏ qua ${result.skipped.length}.`
                  : `${result.applied.length} row(s) matched, ${result.skipped.length} skipped.`}
              </p>
              {result.skipped.slice(0, 5).map((item) => (
                <p key={item.line} className="text-destructive">
                  {vi
                    ? `Dòng ${item.line}: ${
                        item.reason === 'unknown-student'
                          ? 'không tìm thấy sinh viên'
                          : item.reason === 'invalid-score'
                            ? 'điểm không hợp lệ (0-10)'
                            : item.reason === 'duplicate'
                              ? 'trùng sinh viên'
                              : 'thiếu mã sinh viên'
                      }`
                    : `Line ${item.line}: ${
                        item.reason === 'unknown-student'
                          ? 'student not found'
                          : item.reason === 'invalid-score'
                            ? 'score out of range (0-10)'
                            : item.reason === 'duplicate'
                              ? 'duplicate student'
                              : 'missing student code'
                      }`}
                </p>
              ))}
            </div>
          ) : null}

          <Button
            type="button"
            size="sm"
            onClick={apply}
            disabled={disabled || !result || result.applied.length === 0}
          >
            {vi ? 'Điền vào bảng điểm' : 'Fill the grade table'}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
