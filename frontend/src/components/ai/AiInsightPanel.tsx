'use client';
import React, { useState } from 'react';
import {
  AlertTriangle,
  ChevronDown,
  ChevronRight,
  Info,
  Sparkles,
  TrendingDown,
  TrendingUp,
} from 'lucide-react';
import Link from 'next/link';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import { formatCurrency } from '@/lib/utils';
import {
  Anomaly,
  Insight,
  InsightSeverity,
  ReorderRecommendation,
} from '@/lib/api/aiApi';

/**
 * The insight panel: what changed, what looks unusual, and what is suggested.
 *
 * <p>Every figure shown here is read from the backend's calculation service; this
 * component formats and links, and computes nothing. The explanations quote the
 * same numbers the reports show, so a reader can open the linked report and
 * find the same figure rather than a second version of it.
 *
 * <p>Loading, empty and error states are all distinct and all say something
 * useful. "No insights" and "the analysis failed" are different situations, and
 * a panel that looks the same for both teaches a reader to distrust both.
 */

interface AiInsightPanelProps {
  insights: Insight[] | undefined;
  anomalies: Anomaly[] | undefined;
  recommendations: ReorderRecommendation[] | undefined;
  isLoading: boolean;
  error: unknown;
  onRetry?: () => void;
}

const severityVariant: Record<InsightSeverity, 'info' | 'warning' | 'danger'> = {
  INFO: 'info',
  WARNING: 'warning',
  CRITICAL: 'danger',
};

export default function AiInsightPanel({
  insights,
  anomalies,
  recommendations,
  isLoading,
  error,
  onRetry,
}: AiInsightPanelProps) {
  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
            <Sparkles size={16} className="text-purple-500" /> Business intelligence
          </h3>
        </CardHeader>
        <CardContent className="space-y-3">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="h-16 rounded-lg bg-gray-100 dark:bg-gray-700/50 animate-pulse"
              aria-hidden
            />
          ))}
          <p className="text-xs text-gray-400">Analysing recorded transactions…</p>
        </CardContent>
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <CardHeader>
          <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
            <AlertTriangle size={16} className="text-amber-500" /> Business intelligence
          </h3>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-gray-600 dark:text-gray-400">
            The analysis could not be produced. The figures on this page come from
            the same records, so they are unaffected — only the commentary and the
            forecasts are missing.
          </p>
          {onRetry && (
            <button
              type="button"
              onClick={onRetry}
              className="mt-3 text-sm font-medium text-blue-600 hover:underline"
            >
              Try again
            </button>
          )}
        </CardContent>
      </Card>
    );
  }

  const insightList = insights ?? [];
  const anomalyList = anomalies ?? [];
  const recommendationList = (recommendations ?? [])
    .filter((r) => r.hasSufficientHistory && r.urgency !== 'OK')
    .slice(0, 5);

  const hasNothing =
    insightList.length === 0 && anomalyList.length === 0 && recommendationList.length === 0;

  return (
    <div className="space-y-4">
      {/* ── Recommendations ───────────────────────────────────────────────── */}
      {recommendationList.length > 0 && (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-gray-800 dark:text-gray-200">
                Suggested reordering
              </h3>
              <span className="text-xs text-gray-400">
                Advice only — nothing has been ordered
              </span>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            <ul className="divide-y divide-gray-100 dark:divide-gray-700/50">
              {recommendationList.map((r) => (
                <RecommendationRow key={r.productId} recommendation={r} />
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {/* ── Insights ─────────────────────────────────────────────────────── */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-gray-800 dark:text-gray-200">
              What the numbers say
            </h3>
            {insightList.length > 0 && (
              <span className="text-xs text-gray-400">
                {insightList.length} observation
                {insightList.length === 1 ? '' : 's'}
              </span>
            )}
          </div>
        </CardHeader>
        <CardContent className={hasNothing ? 'py-8' : 'p-0'}>
          {hasNothing ? (
            <EmptyState
              title="Nothing stands out"
              body="Nothing in this period differs enough from your own history to be worth raising. That is a real answer, not a missing one."
            />
          ) : (
            <ul className="divide-y divide-gray-100 dark:divide-gray-700/50">
              {insightList.map((insight, index) => (
                <InsightRow key={`${insight.category}-${index}`} insight={insight} />
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {/* ── Alerts ────────────────────────────────────────────────────────── */}
      {anomalyList.length > 0 && (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-gray-800 dark:text-gray-200">
                Worth a look
              </h3>
              <span className="text-xs text-gray-400">
                Investigation prompts, not findings
              </span>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            <ul className="divide-y divide-gray-100 dark:divide-gray-700/50">
              {anomalyList.slice(0, 8).map((anomaly, index) => (
                <AnomalyRow key={`${anomaly.source}-${anomaly.period}-${index}`} anomaly={anomaly} />
              ))}
            </ul>
            {anomalyList.length > 8 && (
              <p className="px-6 py-3 text-xs text-gray-400 border-t border-gray-100 dark:border-gray-700/50">
                {anomalyList.length - 8} more in the AI anomalies report.
              </p>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}

// ── Rows ─────────────────────────────────────────────────────────────────────

function InsightRow({ insight }: { insight: Insight }) {
  const [open, setOpen] = useState(false);
  const Icon =
    insight.severity === 'INFO' ? Info : TrendingDown;

  return (
    <li className="px-6 py-4">
      <div className="flex items-start gap-3">
        <Icon
          size={16}
          className={
            insight.severity === 'INFO'
              ? 'mt-0.5 text-blue-500'
              : 'mt-0.5 text-amber-500'
          }
        />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-medium text-gray-800 dark:text-gray-200">
              {insight.headline}
            </span>
            <Badge variant={severityVariant[insight.severity]}>
              {insight.category}
            </Badge>
          </div>
          <p className="mt-1 text-sm text-gray-600 dark:text-gray-400">
            {insight.message}
          </p>

          <div className="mt-2 flex items-center gap-3 text-xs">
            <button
              type="button"
              onClick={() => setOpen((v) => !v)}
              className="flex items-center gap-1 text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
              aria-expanded={open}
            >
              {open ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
              {open ? 'Hide' : 'Show'} the figures behind this
            </button>
            {insight.sourceUrl && (
              <Link
                href={insight.sourceUrl}
                className="text-blue-600 hover:underline"
              >
                Open the report
              </Link>
            )}
          </div>

          {open && (
            <dl className="mt-3 grid grid-cols-2 sm:grid-cols-3 gap-x-4 gap-y-2 rounded-lg bg-gray-50 dark:bg-gray-700/30 p-3">
              {Object.entries(insight.evidence)
                .filter(([, value]) => typeof value !== 'object')
                .slice(0, 12)
                .map(([key, value]) => (
                  <div key={key} className="min-w-0">
                    <dt className="text-[11px] uppercase tracking-wide text-gray-400 truncate">
                      {key.replace(/([A-Z])/g, ' $1')}
                    </dt>
                    <dd className="text-xs font-medium text-gray-700 dark:text-gray-300 truncate">
                      {formatEvidenceValue(key, value)}
                    </dd>
                  </div>
                ))}
            </dl>
          )}
        </div>
      </div>
    </li>
  );
}

function AnomalyRow({ anomaly }: { anomaly: Anomaly }) {
  const [open, setOpen] = useState(false);

  return (
    <li className="px-6 py-4">
      <div className="flex items-start gap-3">
        <AlertTriangle size={15} className="mt-0.5 text-amber-500 shrink-0" />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <Badge variant="warning">{anomaly.source}</Badge>
            <span className="text-xs text-gray-400">{anomaly.period}</span>
          </div>
          <p className="mt-1 text-sm text-gray-700 dark:text-gray-300">
            {anomaly.summary}
          </p>
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            className="mt-2 flex items-center gap-1 text-xs text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
            aria-expanded={open}
          >
            {open ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
            {open ? 'Hide' : 'Show'} how this was judged
          </button>
          {open && (
            <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 rounded-lg bg-gray-50 dark:bg-gray-700/30 p-3">
              {Object.entries(anomaly.evidence)
                .filter(([, value]) => typeof value !== 'object')
                .slice(0, 10)
                .map(([key, value]) => (
                  <div key={key} className="min-w-0">
                    <dt className="text-[11px] uppercase tracking-wide text-gray-400 truncate">
                      {key.replace(/([A-Z])/g, ' $1')}
                    </dt>
                    <dd className="text-xs font-medium text-gray-700 dark:text-gray-300 truncate">
                      {formatEvidenceValue(key, value)}
                    </dd>
                  </div>
                ))}
            </dl>
          )}
        </div>
      </div>
    </li>
  );
}

function RecommendationRow({
  recommendation,
}: {
  recommendation: ReorderRecommendation;
}) {
  const [open, setOpen] = useState(false);
  const urgent = recommendation.urgency === 'OUT_OF_STOCK';
  const days = recommendation.daysOfStockRemaining;

  return (
    <li className="px-6 py-4">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-medium text-gray-800 dark:text-gray-200 truncate">
              {recommendation.name}
            </span>
            <Badge variant={urgent ? 'danger' : 'warning'}>
              {recommendation.urgency.replace(/_/g, ' ')}
            </Badge>
          </div>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            {recommendation.stockOnHand} in stock
            {recommendation.stockOnOrder > 0 &&
              `, ${recommendation.stockOnOrder} already on order`}
            {days !== null &&
              ` · about ${days} days of cover at ${recommendation.averageDailyUnits} units/day`}
          </p>
        </div>
        <div className="text-right shrink-0">
          <p className="text-sm font-semibold text-gray-800 dark:text-gray-200">
            {recommendation.suggestedQuantity > 0
              ? `Order ${recommendation.suggestedQuantity}`
              : 'No order needed'}
          </p>
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            className="mt-1 text-xs text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
          >
            {open ? 'Why' : 'Why?'}
          </button>
        </div>
      </div>

      {open && (
        <div className="mt-3 rounded-lg bg-gray-50 dark:bg-gray-700/30 p-3">
          <dl className="grid grid-cols-2 sm:grid-cols-4 gap-x-4 gap-y-2">
            <EvidenceTerm label="Reorder point" value={`${recommendation.reorderPoint} units`} />
            <EvidenceTerm label="Safety stock" value={`${recommendation.safetyStock} units`} />
            <EvidenceTerm
              label="Lead time"
              value={`${recommendation.leadTimeDays} days`}
            />
            <EvidenceTerm
              label="Variability"
              value={recommendation.coefficientOfVariation.toFixed(2)}
            />
          </dl>
          <p className="mt-3 text-xs text-gray-500 dark:text-gray-400">
            {String(recommendation.evidence.formula ?? '')}
          </p>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            {recommendation.authorityNote}
          </p>
        </div>
      )}
    </li>
  );
}

function EvidenceTerm({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-[11px] uppercase tracking-wide text-gray-400">{label}</dt>
      <dd className="text-xs font-medium text-gray-700 dark:text-gray-300">{value}</dd>
    </div>
  );
}

function EmptyState({ title, body }: { title: string; body: string }) {
  return (
    <div className="text-center py-4">
      <p className="text-sm font-medium text-gray-600 dark:text-gray-400">{title}</p>
      <p className="mt-1 text-xs text-gray-400 max-w-md mx-auto">{body}</p>
    </div>
  );
}

/**
 * Renders one evidence value.
 *
 * <p>Money-looking keys are formatted as currency so a figure in the evidence
 * matches its formatting on the report it links to. Anything non-numeric is
 * printed as-is, because an unparseable value is more useful visible than
 * silently rendered as zero.
 */
function formatEvidenceValue(key: string, value: unknown): string {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'number') {
    if (/amount|profit|sales|cost|value|paid|balance|revenue|margin/i.test(key)) {
      return formatCurrency(value);
    }
    if (Number.isInteger(value)) return String(value);
    return value.toFixed(2);
  }
  if (typeof value === 'boolean') return value ? 'yes' : 'no';
  return String(value);
}

export { TrendingUp };