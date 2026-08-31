'use client';

import { useState } from 'react';
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, Search, Download } from 'lucide-react';
import { Button } from './button';
import { Input } from './input';
import { Select } from './select';
import { useI18n } from '@/i18n';

interface Column<T> {
  key: string;
  header: string;
  render?: (item: T) => React.ReactNode;
  sortable?: boolean;
}

interface DataTableProps<T> {
  data: T[];
  columns: Column<T>[];
  keyField: keyof T;
  searchPlaceholder?: string;
  searchKeys?: (keyof T)[];
  onExport?: () => void;
  loading?: boolean;
  emptyMessage?: string;
}

export function DataTable<T extends Record<string, unknown>>({
  data,
  columns,
  keyField,
  searchPlaceholder,
  searchKeys = [],
  onExport,
  loading = false,
  emptyMessage,
}: DataTableProps<T>) {
  const { messages } = useI18n();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc');

  const filteredData = data.filter((item) => {
    if (!search) return true;
    const searchLower = search.toLowerCase();
    return searchKeys.some((key) =>
      String(item[key]).toLowerCase().includes(searchLower)
    );
  });

  const sortedData = [...filteredData].sort((a, b) => {
    if (!sortKey) return 0;
    const aVal = normalizeSortValue(a[sortKey]);
    const bVal = normalizeSortValue(b[sortKey]);
    if (aVal < bVal) return sortOrder === 'asc' ? -1 : 1;
    if (aVal > bVal) return sortOrder === 'asc' ? 1 : -1;
    return 0;
  });

  const totalPages = Math.ceil(sortedData.length / pageSize);
  const paginatedData = sortedData.slice((page - 1) * pageSize, page * pageSize);

  const handleSort = (key: string) => {
    if (sortKey === key) {
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setSortKey(key);
      setSortOrder('asc');
    }
  };

  function normalizeSortValue(value: unknown): string | number {
    if (typeof value === 'number') {
      return value;
    }

    if (typeof value === 'string') {
      const parsed = Number(value);
      return Number.isFinite(parsed) && value.trim() !== '' ? parsed : value.toLowerCase();
    }

    if (typeof value === 'boolean') {
      return value ? 1 : 0;
    }

    return String(value ?? '');
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-col sm:flex-row gap-4 justify-between">
        <div className="w-full sm:w-72">
          <Input
            type="text"
            placeholder={searchPlaceholder || messages.common.states.searchPlaceholder}
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(1);
            }}
            icon={<Search className="h-4 w-4" />}
          />
        </div>
        {onExport && (
          <Button variant="outline" onClick={onExport}>
            <Download className="h-4 w-4 mr-2" />
            {messages.common.states.export}
          </Button>
        )}
      </div>

      <div className="rounded-md border overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-muted">
              <tr>
                {columns.map((column) => (
                  <th
                    key={column.key}
                    aria-sort={
                      column.sortable && sortKey === column.key
                        ? sortOrder === 'asc' ? 'ascending' : 'descending'
                        : 'none'
                    }
                    className="px-4 py-3 text-left text-sm font-medium text-muted-foreground cursor-pointer hover:bg-secondary"
                  >
                    {column.sortable ? (
                      <button
                        type="button"
                        className="flex min-h-10 w-full items-center gap-1 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                        onClick={() => handleSort(column.key)}
                        aria-label={`Sort by ${column.header}`}
                      >
                        {column.header}
                        {sortKey === column.key && (
                          <span aria-hidden="true" className="inline-flex items-center">
                            {sortOrder === 'asc' ? <ArrowUp className="h-3 w-3" /> : <ArrowDown className="h-3 w-3" />}
                          </span>
                        )}
                      </button>
                    ) : column.header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loading ? (
                <tr>
                  <td colSpan={columns.length} className="px-4 py-8 text-center text-muted-foreground">
                    <div className="flex justify-center">
                      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                    </div>
                  </td>
                </tr>
              ) : paginatedData.length === 0 ? (
                <tr>
                  <td colSpan={columns.length} className="px-4 py-8 text-center text-muted-foreground">
                    {emptyMessage || messages.common.states.noDataFound}
                  </td>
                </tr>
              ) : (
                paginatedData.map((item) => (
                  <tr
                    key={String(item[keyField])}
                    className="hover:bg-muted/60"
                  >
                    {columns.map((column) => (
                      <td key={column.key} className="px-4 py-3 text-sm">
                        {column.render ? column.render(item) : String(item[column.key] ?? '-')}
                      </td>
                    ))}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {totalPages > 1 && (
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <span>
              {messages.common.states.showingResults} {(page - 1) * pageSize + 1} {messages.common.states.to} {Math.min(page * pageSize, sortedData.length)} {messages.common.states.of} {sortedData.length} {messages.common.states.results}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <Select
              value={String(pageSize)}
              onChange={(e) => {
                setPageSize(Number(e.target.value));
                setPage(1);
              }}
              options={[
                { value: '10', label: messages.common.states.perPage10 },
                { value: '25', label: messages.common.states.perPage25 },
                { value: '50', label: messages.common.states.perPage50 },
                { value: '100', label: messages.common.states.perPage100 },
              ]}
            />
            <div className="flex items-center gap-1">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(page - 1)}
                disabled={page === 1}
                aria-label={messages.common.states.goToPreviousPage}
                title={messages.common.states.goToPreviousPage}
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="px-3 py-1 text-sm">
                {messages.common.states.page} {page} {messages.common.states.of} {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(page + 1)}
                disabled={page === totalPages}
                aria-label={messages.common.states.goToNextPage}
                title={messages.common.states.goToNextPage}
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
