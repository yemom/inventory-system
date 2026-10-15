'use client';

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Sparkles } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import { useAuth } from '@/lib/auth/AuthProvider';
import { aiApi } from '@/lib/api/aiApi';
import { formatDateTime } from '@/lib/utils';

/**
 * Every model this system has trained, and how accurate it was.
 *
 * <h2>Why this page exists</h2>
 *
 * <p>A forecast presented without its error rate is a guess wearing a decimal
 * point. This screen exists so that the number can be judged: which model won,
 * what it was measured against, how many observations it was fitted on, and how
 * wrong it has historically been on data it never saw.
 *
 * <p>Versions are superseded, not overwritten. A figure reported last month was
 * produced by a specific model on a specific training period; overwriting that
 * record would leave the reported figure unreproducible, which is the one
 * property that makes a number worth reporting.
 */

/** How each field is read. Kept here rather than inline so the table is legible. */
const FIELD_LABELS: Record<string, string> = {
  modelKey: 'Model',
  algorithm: 'Algorithm',
  version: 'Version',
  sampleSize: 'Samples',
  trainingPeriodStart: 'Training period',
  trainingPeriodEnd: '',
  mae: 'MAE',
  rmse: 'RMSE',
  mapePercent: 'MAPE',
  trainedAt: 'Trained at',
};

export default function ModelEvaluationPage() {
  const { hasPermission } = useAuth();
  const permitted = hasPermission('AI_MODEL_VIEW');

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['ai', 'models', 'all'],
    queryFn: () => aiApi.models(),
    enabled: permitted,
    retry: false,
  });

  if (!permitted) {
    return (
      <Card>
        <CardContent className="py-8 text-center text-sm text-gray-500">
          Model evaluation requires the model permission.
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Model Evaluation"
        subtitle="Every trained model with its training period, sample size and measured error on held-out data"
      />

      {error ? (
        <Card>
          <CardContent className="py-8 text-center text-sm text-gray-500">
            Model history could not be loaded. That is a failure to reach the
            server, not an absence of models.
            <div className="mt-3">
              <Button variant="outline" size="sm" onClick={() => refetch()}>
                Try again
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : isLoading ? (
        <div className="h-48 rounded-xl bg-gray-100 dark:bg-gray-700/50 animate-pulse" />
      ) : (data ?? []).length === 0 ? (
        <Card>
          <CardContent className="py-10 text-center">
            <Sparkles size={20} className="mx-auto text-gray-400 mb-2" />
            <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
              No model has been trained yet
            </p>
            <p className="mt-1 text-xs text-gray-400 max-w-md mx-auto">
              A model is trained the first time a forecast is requested with
              enough recorded history behind it. Until then the system says so
              rather than producing a number from almost nothing.
            </p>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <h3 className="font-semibold text-gray-800 dark:text-gray-200">
              Active models
            </h3>
          </CardHeader>
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 dark:bg-gray-800/60">
                  <tr>
                    {Object.entries(FIELD_LABELS)
                      .filter(([, label]) => label)
                      .map(([key, label]) => (
                        <th
                          key={key}
                          className="px-4 py-3 text-xs font-semibold uppercase text-gray-500 text-left"
                        >
                          {label}
                        </th>
                      ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
                  {(data ?? []).map((model: any) => (
                    <React.Fragment
                      key={`${model.modelKey}-${model.version}`}
                    >
                      <tr>
                        <td className="px-4 py-2.5 font-mono text-xs text-blue-600">
                          {String(model.modelKey)}
                        </td>
                        <td className="px-4 py-2.5 text-gray-700 dark:text-gray-300">
                          {String(model.algorithm)}
                        </td>
                        <td className="px-4 py-2.5 tabular-nums text-gray-500">
                          v{String(model.version)}
                          {model.active ? ' (active)' : ''}
                        </td>
                        <td className="px-4 py-2.5 text-xs text-gray-500">
                          {model.trainingPeriodStart
                            ? `${formatDateTime(String(model.trainingPeriodStart))} – ${formatDateTime(String(model.trainingPeriodEnd))}`
                            : '—'}
                        </td>
                        <td className="px-4 py-2.5 tabular-nums text-gray-500">
                          {Number(model.sampleSize ?? 0).toLocaleString()}
                        </td>
                        <td className="px-4 py-2.5 tabular-nums text-gray-700 dark:text-gray-300">
                          {String(model.mae ?? '—')}
                        </td>
                        <td className="px-4 py-2.5 tabular-nums text-gray-700 dark:text-gray-300">
                          {String(model.rmse ?? '—')}
                        </td>
                        <td className="px-4 py-2.5 tabular-nums text-gray-700 dark:text-gray-300">
                          {model.mapePercent != null
                            ? `${model.mapePercent}%`
                            : '—'}
                        </td>
                        <td className="px-4 py-2.5 text-xs text-gray-400">
                          {formatDateTime(String(model.trainedAt))}
                        </td>
                      </tr>
                      {model.explanation && (
                        <tr>
                          <td
                            colSpan={9}
                            className="px-4 pb-3 text-xs text-gray-500 whitespace-pre-line"
                          >
                            {String(model.explanation)}
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}