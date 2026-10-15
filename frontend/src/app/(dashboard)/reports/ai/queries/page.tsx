'use client';

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import { Card, CardContent } from '@/components/ui/Card';
import { useAuth } from '@/lib/auth/AuthProvider';
import { aiApi } from '@/lib/api/aiApi';
import { formatDateTime } from '@/lib/utils';

/**
 * What was asked of the assistant, and what came back.
 *
 * <p>Recorded on purpose. An assistant that quotes a figure is making a claim
 * about the business, and a claim that cannot be traced is a claim nobody can
 * check. Every entry here holds the question as typed, the intent it resolved
 * to, the date range it settled on, the outcome, and the structured result — so
 * the sentence a reader was shown can be compared against the numbers behind it
 * after the fact.
 *
 * <p>Refusals are kept too. A repeated attempt to ask for something the caller
 * is not entitled to see is exactly the kind of thing this log is for.
 */
const PAGE_SIZE = 25;

export default function AssistantQueryLogPage() {
  const { hasPermission } = useAuth();
  const permitted = hasPermission('AI_QUERY_LOG_VIEW');
  const [page, setPage] = useState(0);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['ai', 'assistant', 'log', page],
    queryFn: () => aiApi.queryLog(page, PAGE_SIZE),
    enabled: permitted,
    retry: false,
  });

  if (!permitted) {
    return (
      <Card>
        <CardContent className="py-8 text-center text-sm text-gray-500">
          The assistant query log requires the query-log permission.
        </CardContent>
      </Card>
    );
  }

  const outcomeVariant = (outcome: string) =>
    outcome === 'ANSWERED'
      ? 'success'
      : outcome === 'DENIED'
        ? 'danger'
        : outcome === 'INSUFFICIENT_DATA'
          ? 'warning'
          : 'info';

  return (
    <div className="space-y-6">
      <PageHeader
        title="Assistant Queries"
        subtitle="Every question asked of the assistant, what it resolved to, and whether it was answered"
      />

      {error ? (
        <Card>
          <CardContent className="py-8 text-center text-sm text-gray-500">
            The query log could not be loaded. That is a failure to reach the
            server, not an absence of queries.
            <div className="mt-3">
              <Button variant="outline" size="sm" onClick={() => refetch()}>
                Try again
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : isLoading ? (
        <div className="h-48 rounded-xl bg-gray-100 dark:bg-gray-700/50 animate-pulse" />
      ) : (data?.entries ?? []).length === 0 ? (
        <Card>
          <CardContent className="py-10 text-center text-sm text-gray-500">
            Nothing has been asked yet. This is an empty answer, not a missing
            one.
          </CardContent>
        </Card>
      ) : (
        <>
          <Card>
            <CardContent className="p-0">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 dark:bg-gray-800/60">
                    <tr>
                      {['Asked', 'Question', 'Intent', 'Period', 'Outcome', 'Took'].map(
                        (h) => (
                          <th
                            key={h}
                            className="px-4 py-3 text-xs font-semibold uppercase text-gray-500 text-left"
                          >
                            {h}
                          </th>
                        ),
                      )}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
                    {(data?.entries ?? []).map((entry: any) => (
                      <tr
                        key={entry.id}
                        className="hover:bg-gray-50 dark:hover:bg-gray-700/30"
                      >
                        <td className="px-4 py-2.5 text-xs text-gray-500 whitespace-nowrap">
                          {formatDateTime(String(entry.createdAt ?? ''))}
                        </td>
                        <td className="px-4 py-2.5 text-gray-800 dark:text-gray-200">
                          {String(entry.question ?? '—')}
                        </td>
                        <td className="px-4 py-2.5 font-mono text-xs text-gray-500">
                          {String(entry.intent ?? '—')}
                        </td>
                        <td className="px-4 py-2.5 text-xs text-gray-500 whitespace-nowrap">
                          {entry.periodFrom && entry.periodTo
                            ? `${entry.periodFrom} – ${entry.periodTo}`
                            : '—'}
                        </td>
                        <td className="px-4 py-2.5">
                          <Badge variant={outcomeVariant(String(entry.outcome)) as any}>
                            {String(entry.outcome).replace(/_/g, ' ')}
                          </Badge>
                        </td>
                        <td className="px-4 py-2.5 tabular-nums text-xs text-gray-500">
                          {entry.durationMs != null
                            ? `${entry.durationMs} ms`
                            : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>

          {(data?.totalPages ?? 0) > 1 && (
            <div className="flex items-center justify-between">
              <p className="text-xs text-gray-400">
                Page {page + 1} of {data?.totalPages} ·{' '}
                {(data?.total ?? 0).toLocaleString()} queries
              </p>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  <ChevronLeft size={14} /> Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page + 1 >= (data?.totalPages ?? 0)}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next <ChevronRight size={14} />
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}