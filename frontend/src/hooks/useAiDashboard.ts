import { useQuery } from '@tanstack/react-query';
import {
  aiApi,
  dashboardApi,
  DateRangeParams,
} from '@/lib/api/aiApi';
import { useAuth } from '@/lib/auth/AuthProvider';

/**
 * Data hooks for the intelligence layer.
 *
 * <p>Each hook is gated on the permission the underlying endpoint requires, and
 * is <b>disabled</b> when that permission is absent rather than fired and
 * refused. A 403 on every dashboard load is noise in the console and a wasted
 * round trip; asking first is both cheaper and clearer about why the panel is
 * empty.
 *
 * <p>The backend is still the authority. These checks decide whether to ask,
 * not whether to show — hiding a panel is a convenience, and the endpoint's own
 * authorisation is the control that actually protects the figures.
 */

/** How many milliseconds the intelligence panel stays fresh. */
const FIVE_MINUTES = 5 * 60 * 1000;

export function useDashboardFinancial(range: DateRangeParams, enabled = true) {
  return useQuery({
    queryKey: ['dashboard', 'financial', range.from, range.to],
    queryFn: () => dashboardApi.financial(range),
    enabled,
    staleTime: 60_000,
    retry: false,
  });
}

/**
 * Sales totals for the period, as the server sees them for this caller.
 *
 * <p>Not gated on a reporting permission: the endpoint answers for everyone, and
 * for a cashier it answers with their own orders alone. That is the point of it —
 * the scoping happens server-side, so nothing here decides whose data to show.
 */
export function useSalesSummary(range: DateRangeParams, enabled = true) {
  return useQuery({
    queryKey: ['dashboard', 'sales-summary', range.from, range.to],
    queryFn: () => dashboardApi.salesSummary(range),
    enabled,
    staleTime: 60_000,
    retry: false,
  });
}

/**
 * The whole AI dashboard in one request.
 *
 * <p>One request rather than five so the page renders from a single consistent
 * snapshot. Fetching the forecast and the alerts separately risks the two
 * describing different periods, and the page quietly contradicting itself.
 */
export function useAiDashboard(
  range: DateRangeParams,
  horizon: number,
  enabled = true,
) {
  return useQuery({
    queryKey: ['ai', 'dashboard', range.from, range.to, horizon],
    queryFn: () => aiApi.dashboard({ ...range, horizon }),
    enabled,
    staleTime: FIVE_MINUTES,
    retry: false,
  });
}

export function useAiInsights(range: DateRangeParams, enabled = true) {
  const { hasPermission } = useAuth();
  return useQuery({
    queryKey: ['ai', 'insights', range.from, range.to],
    queryFn: () => aiApi.insights(range),
    enabled: enabled && hasPermission('AI_INSIGHT_VIEW'),
    staleTime: FIVE_MINUTES,
    retry: false,
  });
}

export function useReorderRecommendations(limit = 25, enabled = true) {
  const { hasPermission } = useAuth();
  return useQuery({
    queryKey: ['ai', 'reorder', limit],
    queryFn: () => aiApi.reorder(limit),
    enabled: enabled && hasPermission('AI_FORECAST_VIEW'),
    staleTime: FIVE_MINUTES,
    retry: false,
  });
}

export function useStockoutRisks(horizonDays = 30, enabled = true) {
  const { hasPermission } = useAuth();
  return useQuery({
    queryKey: ['ai', 'stockouts', horizonDays],
    queryFn: () => aiApi.stockouts(horizonDays),
    enabled: enabled && hasPermission('AI_FORECAST_VIEW'),
    staleTime: FIVE_MINUTES,
    retry: false,
  });
}

export function useAnomalies(range: DateRangeParams, enabled = true) {
  const { hasPermission } = useAuth();
  return useQuery({
    queryKey: ['ai', 'anomalies', range.from, range.to],
    queryFn: () => aiApi.anomalies(range),
    enabled: enabled && hasPermission('AI_INSIGHT_VIEW'),
    staleTime: FIVE_MINUTES,
    retry: false,
  });
}

/** Trained models with their measured accuracy, for the evaluation panel. */
export function useModelHistory(modelKey?: string, enabled = true) {
  const { hasPermission } = useAuth();
  return useQuery({
    queryKey: ['ai', 'models', modelKey ?? 'all'],
    queryFn: () => aiApi.models(modelKey),
    enabled: enabled && hasPermission('AI_MODEL_VIEW'),
    staleTime: FIVE_MINUTES,
    retry: false,
  });
}

export default {
  useDashboardFinancial,
  useSalesSummary,
  useAiDashboard,
  useAiInsights,
  useReorderRecommendations,
  useStockoutRisks,
  useAnomalies,
  useModelHistory,
};