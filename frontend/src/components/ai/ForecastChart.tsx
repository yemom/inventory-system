'use client';
import React, { useMemo } from 'react';
import {
  Area,
  Bar,
  BarChart,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import { formatCurrency, formatDate } from '@/lib/utils';
import { RevenueForecast } from '@/lib/api/aiApi';

/**
 * The forecast, shown next to the history it came from.
 *
 * <p>A forecast presented on its own cannot be judged. Here the actuals and the
 * prediction share one axis, the boundary between them is marked, and the band
 * is drawn — so a reader can see for themselves whether recent days were
 * predictable before deciding to act on the next fortnight.
 *
 * <p>The model's own error is printed alongside. If it has historically been
 * wrong by 30%, the band is wide and says so; hiding that would make a weak
 * forecast look as certain as a strong one.
 */

interface ForecastChartProps {
  forecast: RevenueForecast | undefined;
  isLoading: boolean;
  horizon: number;
  onHorizonChange?: (horizon: number) => void;
}

const HORIZON_OPTIONS = [7, 14, 30];

interface ChartPoint {
  date: string;
  label: string;
  /** Recorded sales. Null on forecast days. */
  actual: number | null;
  /** Forecast value. Null except from the join point onwards. */
  predicted: number | null;
  /** Width of the prediction interval. */
  band: number | null;
  /** Upper edge of the interval; the band is shaded between the two bounds. */
  upperBound: number | null;
  /** Lower edge of the interval. */
  lowerBound: number | null;
}

export default function ForecastChart({
  forecast,
  isLoading,
  horizon,
  onHorizonChange,
}: ForecastChartProps) {
  const { data, boundaryIndex } = useMemo(() => buildChartData(forecast), [forecast]);

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <h3 className="font-semibold text-gray-800 dark:text-gray-200">
            Sales forecast
          </h3>
        </CardHeader>
        <CardContent>
          <div className="h-[280px] rounded-lg bg-gray-100 dark:bg-gray-700/50 animate-pulse" />
        </CardContent>
      </Card>
    );
  }

  if (!forecast || !forecast.available) {
    return (
      <Card>
        <CardHeader>
          <h3 className="font-semibold text-gray-800 dark:text-gray-200">
            Sales forecast
          </h3>
        </CardHeader>
        <CardContent>
          <div className="py-10 text-center">
            <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
              Not enough trading history to forecast
            </p>
            <p className="mt-2 text-xs text-gray-400 max-w-lg mx-auto">
              {forecast?.explanation ??
                'A forecast needs several weeks of recorded sales. Until then the figures below remain accurate — only the projection is unavailable.'}
            </p>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div>
            <h3 className="font-semibold text-gray-800 dark:text-gray-200">
              Sales forecast
            </h3>
            <p className="text-xs text-gray-400 mt-0.5">
              {forecast.historyDates?.length ?? 0} days of recorded sales ·{' '}
              {formatDate(forecast.from)} – {formatDate(forecast.to)}
            </p>
          </div>
          {onHorizonChange && (
            <div className="flex items-center gap-1">
              {HORIZON_OPTIONS.map((option) => (
                <button
                  key={option}
                  type="button"
                  onClick={() => onHorizonChange(option)}
                  aria-pressed={horizon === option}
                  className={
                    horizon === option
                      ? 'px-2.5 py-1 rounded-md text-xs font-medium bg-blue-600 text-white'
                      : 'px-2.5 py-1 rounded-md text-xs font-medium text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'
                  }
                >
                  {option}d
                </button>
              ))}
            </div>
          )}
        </div>
      </CardHeader>

      <CardContent>
        <ResponsiveContainer width="100%" height={280}>
          <ComposedChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
            <defs>
              <linearGradient id="actualFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#2563EB" stopOpacity={0.18} />
                <stop offset="95%" stopColor="#2563EB" stopOpacity={0} />
              </linearGradient>
              <linearGradient id="bandFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#8B5CF6" stopOpacity={0.22} />
                <stop offset="95%" stopColor="#8B5CF6" stopOpacity={0.05} />
              </linearGradient>
            </defs>

            <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
            <XAxis
              dataKey="label"
              tick={{ fontSize: 10 }}
              interval="preserveStartEnd"
              minTickGap={24}
            />
            <YAxis
              tick={{ fontSize: 11 }}
              width={72}
              tickFormatter={(value: number) => formatCurrency(value)}
            />
            <Tooltip
              formatter={(value: number, name: string) => [
                formatCurrency(value),
                name === 'actual'
                  ? 'Recorded sales'
                  : name === 'predicted'
                    ? 'Forecast'
                    : name === 'upperBound'
                      ? 'Upper bound'
                      : 'Lower bound',
              ]}
              contentStyle={{ fontSize: 12 }}
            />
            <Legend wrapperStyle={{ fontSize: 11 }} />

            {/* The prediction band. Shaded between the lower and upper bounds of
                the interval, so the reader sees the range of the forecast rather
                than a single line implying certainty. */}
            <Area
              type="monotone"
              dataKey="upperBound"
              stroke="#8B5CF6"
              strokeWidth={1}
              strokeOpacity={0.35}
              fill="url(#bandFill)"
              name="upper bound"
            />
            <Area
              type="monotone"
              dataKey="lowerBound"
              stroke="#8B5CF6"
              strokeWidth={1}
              strokeOpacity={0.35}
              fill="#ffffff"
              fillOpacity={0.9}
              name="lower bound"
              legendType="none"
            />

            <Area
              type="monotone"
              dataKey="actual"
              stroke="#2563EB"
              strokeWidth={2}
              fill="url(#actualFill)"
              name="actual"
              connectNulls={false}
            />
            <Line
              type="monotone"
              dataKey="predicted"
              stroke="#8B5CF6"
              strokeWidth={2}
              strokeDasharray="5 4"
              name="forecast"
              dot={false}
              connectNulls={false}
            />

            {/* Marks where recorded history ends and the projection begins. */}
            {boundaryIndex > 0 && boundaryIndex < data.length - 1 && (
              <ReferenceLine
                x={data[boundaryIndex].label}
                stroke="#9CA3AF"
                strokeDasharray="3 3"
                label={{ value: 'today', position: 'top', fontSize: 10 }}
              />
            )}
          </ComposedChart>
        </ResponsiveContainer>

        <div className="mt-3 flex flex-wrap items-center gap-2 text-xs">
          <Badge variant="info">{forecast.algorithm}</Badge>
          {forecast.mae !== null && (
            <span className="text-gray-500 dark:text-gray-400">
              typically wrong by {formatCurrency(forecast.mae)} a day
            </span>
          )}
          {forecast.mapePercent !== null ? (
            <span className="text-gray-500 dark:text-gray-400">
              ({forecast.mapePercent}% on held-out data)
            </span>
          ) : (
            <span className="text-gray-400">
              percentage error unavailable — some days had no sales
            </span>
          )}
          {forecast.modelVersion && (
            <span className="text-gray-400">· {forecast.modelVersion}</span>
          )}
        </div>

        <details className="mt-2 text-xs">
          <summary className="cursor-pointer text-gray-500 hover:text-gray-700 dark:hover:text-gray-300">
            How this forecast was produced
          </summary>
          <div className="mt-2 space-y-1 text-gray-500 dark:text-gray-400">
            <p>{forecast.explanation}</p>
            <p>{forecast.basis}</p>
            {forecast.trendPercent !== null && (
              <p>
                Over this period sales have{' '}
                {forecast.trendPercent >= 0 ? 'risen' : 'fallen'}{' '}
                {Math.abs(forecast.trendPercent)}%.
              </p>
            )}
            <p>
              The shaded range is a prediction interval: a forecast is a range, not
              a promise, and the band widens the further ahead it reaches.
            </p>
          </div>
        </details>
      </CardContent>
    </Card>
  );
}

/**
 * Folds history and forecast into one series, marking where each begins.
 *
 * <p>The forecast is drawn from the last actual day rather than from the day
 * after it, so the line joins the history instead of leaving a gap that reads
 * as a missing day of trading.
 */
function buildChartData(forecast: RevenueForecast | undefined): {
  data: ChartPoint[];
  boundaryIndex: number;
} {
  if (!forecast?.available) {
    return { data: [], boundaryIndex: -1 };
  }

  const historyDates = forecast.historyDates ?? [];
  const historyValues = forecast.historyValues ?? [];
  const points = forecast.forecast ?? [];

  const data: ChartPoint[] = historyDates.map((date, index) => ({
    date,
    label: date.slice(5),
    actual: historyValues[index] ?? null,
    predicted: null,
    band: null,
    upperBound: null,
    lowerBound: null,
  }));

  // Join from the last actual so the prediction is continuous with the history.
  if (data.length > 0 && points.length > 0) {
    data[data.length - 1].predicted = historyValues[historyValues.length - 1] ?? 0;
  }

  const boundaryIndex = data.length - 1;

  for (const point of points) {
    data.push({
      date: point.date,
      label: point.date.slice(5),
      actual: null,
      predicted: point.value,
      band: point.upper - point.lower,
      upperBound: point.upper,
      lowerBound: point.lower,
    });
  }

  return { data, boundaryIndex };
}

/** Bars of demand by product, used alongside the revenue forecast. */
export function DemandBarChart({
  products,
}: {
  products: Array<{ name: string; units: number; days: number }>;
}) {
  if (products.length === 0) {
    return null;
  }
  return (
    <ResponsiveContainer width="100%" height={200}>
      <BarChart data={products} layout="vertical" margin={{ left: 0, right: 8 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
        <XAxis type="number" tick={{ fontSize: 11 }} />
        <YAxis type="category" dataKey="name" tick={{ fontSize: 11 }} width={110} />
        <Tooltip contentStyle={{ fontSize: 12 }} />
        <Bar dataKey="units" fill="#2563EB" radius={[0, 4, 4, 0]} name="units/day" />
      </BarChart>
    </ResponsiveContainer>
  );
}