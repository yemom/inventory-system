'use client';

import React, { useState } from 'react';
import { AlertTriangle, Loader2, RefreshCw, Sparkles } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import DateRangeFilter, {
  DateRange,
  lastNDays,
} from '@/components/ui/DateRangeFilter';
import ForecastChart from '@/components/ai/ForecastChart';
import AiInsightPanel from '@/components/ai/AiInsightPanel';
import AssistantPanel from '@/components/ai/AssistantPanel';
import { useAiDashboard, useModelHistory } from '@/hooks/useAiDashboard';
import { useAuth } from '@/lib/auth/AuthProvider';
import { formatDate } from '@/lib/utils';

/**
 * Forecast, alerts and recommended actions in one place.
 *
 * <p>Reads from the same snapshot the dashboard does, so a figure quoted in an
 * explanation here is the same figure quoted there, for the same period.
 *
 * <p>Everything on this page is read-only and every claim is checkable: an
 * alert carries the numbers that produced it, a recommendation carries the
 * arithmetic, and a forecast carries the history it was trained on together with
 * the error it has historically made. A model whose numbers cannot be inspected
 * is not worth acting on, and one that has never been wrong is worth nothing.
 */
export default function AiReportPage() {
  const { hasPermission } = useAuth();
  const [range, setRange] = useState<DateRange>(() => lastNDays(90));
  const [horizon, setHorizon] = useState(14);

  const maySeeModels = hasPermission('AI_MODEL_VIEW');
  // Gate the fetch on the same permission the endpoint enforces. Hardcoded
  // `true` meant every visit by a user without AI_INSIGHT_VIEW /
  // AI_FORECAST_VIEW fired a request the backend could only refuse with 403 —
  // a guaranteed console error and a console full of failed calls for a page
  // they are not permitted to read.
  const maySeeAi = hasPermission('AI_INSIGHT_VIEW') || hasPermission('AI_FORECAST_VIEW');
  const ai = useAiDashboard(range, horizon, maySeeAi);
  const models = useModelHistory(undefined, maySeeModels);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Forecast & AI Alerts"
        subtitle="Computed from this application's own recorded transactions. Read-only: nothing here changes stock, payments or the accounts."
      />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <DateRangeFilter value={range} onChange={setRange} label="History window" />
        {ai.isFetching && (
          <span className="flex items-center gap-1.5 text-xs text-gray-400">
            <Loader2 size={13} className="animate-spin" /> Analysing
          </span>
        )}
      </div>

      {ai.error && (
        <div
          role="alert"
          className="rounded-lg border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/20 px-4 py-3 text-sm text-amber-800 dark:text-amber-200"
        >
          <p className="font-medium">The analysis could not be produced</p>
          <p className="mt-1 text-xs">
            This is a failure to compute, not an absence of activity. The
            dashboards and reports are unaffected — they do not depend on these
            models.
          </p>
          <button
            type="button"
            onClick={() => ai.refetch()}
            className="mt-2 inline-flex items-center gap-1.5 text-xs font-medium hover:underline"
          >
            <RefreshCw size={12} /> Try again
          </button>
        </div>
      )}

      {ai.data?.enabled === false && (
        <div className="rounded-lg border border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800 px-4 py-3 text-sm text-gray-600 dark:text-gray-400">
          <p className="font-medium">The intelligence service is switched off</p>
          <p className="mt-1 text-xs">
            {ai.data.disabled ??
              'AI_ENABLED is false for this deployment.'}{' '}
            Every financial figure on every other screen is unaffected — those
            are computed by the reporting layer, not by these models.
          </p>
        </div>
      )}

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        <div className="xl:col-span-2">
          <ForecastChart
            forecast={ai.data?.forecast ?? undefined}
            isLoading={ai.isLoading}
            horizon={horizon}
            onHorizonChange={setHorizon}
          />
        </div>
        <AssistantPanel range={range} />
      </div>

      <AiInsightPanel
        insights={ai.data?.insights}
        anomalies={ai.data?.anomalies}
        recommendations={ai.data?.recommendations}
        isLoading={ai.isLoading}
        error={ai.error}
        onRetry={() => ai.refetch()}
      />

      {/* ── Trained models ───────────────────────────────────────────────── */}
      {maySeeModels && (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
                <Sparkles size={16} className="text-purple-500" /> Trained models
              </h3>
              <span className="text-xs text-gray-400">
                What the forecast was built from, and how wrong it has been
              </span>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            {models.isLoading ? (
              <div className="h-24 animate-pulse bg-gray-100 dark:bg-gray-700/50 m-4 rounded-lg" />
            ) : (models.data ?? []).length === 0 ? (
              <p className="px-6 py-6 text-center text-xs text-gray-400">
                No model has been trained yet. One is trained the first time a
                forecast is requested with enough history behind it.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 dark:bg-gray-800/60">
                    <tr>
                      {['Model', 'Algorithm', 'Version', 'Trained on', 'Samples', 'Mean error', 'MAPE'].map(
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
                    {(models.data ?? []).map((model: any) => (
                      <tr key={`${model.modelKey}-${model.version}`}>
                        <td className="px-4 py-2.5 font-mono text-xs text-blue-600">
                          {String(model.modelKey)}
                        </td>
                        <td className="px-4 py-2.5 text-gray-700 dark:text-gray-300">
                          {String(model.algorithm)}
                        </td>
                        <td className="px-4 py-2.5 tabular-nums text-gray-500">
                          v{String(model.version)}
                        </td>
                        <td className="px-4 py-2.5 text-xs text-gray-500">
                          {model.trainingPeriodStart
                            ? `${formatDate(String(model.trainingPeriodStart))} – ${formatDate(String(model.trainingPeriodEnd))}`
                            : '—'}
                        </td>
                        <td className="px-4 py-2.5 tabular-nums text-gray-500">
                          {Number(model.sampleSize ?? 0).toLocaleString()}
                        </td>
                        <td className="px-4 py-2.5 tabular-nums text-gray-700 dark:text-gray-300">
                          {String(model.mae ?? '—')}
                        </td>
                        <td className="px-4 py-2.5 tabular-nums text-gray-700 dark:text-gray-300">
                          {model.mapePercent != null
                            ? `${model.mapePercent}%`
                            : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <p className="px-6 py-3 text-xs text-gray-400 border-t border-gray-100 dark:divide-gray-700/50">
              Accuracy is measured by backtesting on held-out history in
              chronological order — the model is fitted on the earlier part of the
              series and scored on the later part it never saw. Training period,
              sample size and measured error are recorded with every version so a
              figure already reported can always be traced to the model that
              produced it.
            </p>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
            <AlertTriangle size={16} className="text-amber-500" /> What this
            service will not do
          </h3>
        </CardHeader>
        <CardContent>
          <ul className="space-y-1.5 text-xs text-gray-500 dark:text-gray-400">
            {[
              'It will not approve a payment, issue a refund or post an accounting entry.',
              'It will not change stock, receive goods or adjust a quantity.',
              'It will not answer a question the caller lacks the permission for — it refuses before reading any data.',
              'An alert is a prompt to look at something, never a finding of wrongdoing.',
              'A reorder recommendation is advice. Nothing is ordered until a person places the order.',
            ].map((line) => (
              <li key={line} className="flex gap-2">
                <span aria-hidden className="text-gray-300">
                  •
                </span>
                {line}
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}