package com.inventory.backend.controller;

import com.inventory.backend.ai.*;
import com.inventory.backend.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The intelligence endpoints.
 *
 * <p>Every one of them is read-only. There is no endpoint here that trains,
 * approves, applies or executes anything, and no AI-generated figure can be fed
 * back into a transaction. That is a property of the API surface rather than a
 * promise in a comment: adding a write endpoint under {@code /ai} would be a
 * design change, not an oversight.
 *
 * <p>Permissions are split so that reading operational advice and reading
 * commercially sensitive figures stay separable. {@code AI_FORECAST_VIEW} gets
 * demand and stock-risk advice; {@code PROFIT_LOSS_READ} and friends are still
 * required for the financial numbers, and the assistant re-checks them per
 * question so holding the assistant permission does not become a way round them.
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final ForecastingService forecastingService;
    private final AnomalyDetectionService anomalyService;
    private final InventoryOptimizationService inventoryService;
    private final FinancialInsightService insightService;
    private final BusinessAssistantService assistantService;
    private final AiModelRegistry modelRegistry;
    private final AiProperties properties;
    private final com.inventory.backend.repository.AiQueryLogRepository queryLogRepository;
    private final com.inventory.backend.repository.WarehouseRepository warehouseRepository;

    /**
     * The AI dashboard: forecast, alerts and recommendations in one call.
     *
     * <p>One endpoint rather than five so the dashboard renders from a single
     * consistent snapshot — otherwise the forecast and the alerts can describe
     * different periods and the page quietly contradicts itself.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('AI_INSIGHT_VIEW','AI_FORECAST_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer horizon) {

        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(89);
        int days = Math.max(1, (int) java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);
        int forecastHorizon = horizon != null && horizon > 0 && horizon <= 90
                ? horizon : properties.getDefaultDailyHorizon();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", properties.isEnabled());
        payload.put("from", start.toString());
        payload.put("to", end.toString());
        payload.put("horizonDays", forecastHorizon);

        if (!properties.isEnabled()) {
            payload.put("insights", List.of());
            payload.put("forecast", null);
            payload.put("anomalies", List.of());
            payload.put("recommendations", List.of());
            payload.put("stockoutRisks", List.of());
            payload.put("disabled", "The intelligence service is switched off "
                    + "(app.ai.enabled=false). Financial figures remain available from the reports.");
            return ResponseEntity.ok(ApiResponse.ok("AI dashboard (disabled)", payload));
        }

        payload.put("insights", insightService.insightsFor(start, end));
        payload.put("forecast", forecastingService.forecastRevenue(start, end, forecastHorizon));
        payload.put("anomalies", anomalyService.detectAll(start, end));
        payload.put("recommendations", inventoryService.recommendReorders(15));
        payload.put("stockoutRisks", inventoryService.predictStockouts(30));
        payload.put("models", modelRegistry.allActive().stream()
                .map(m -> Map.of("modelKey", m.getModelKey(),
                        "algorithm", m.getAlgorithm(),
                        "version", m.getVersion(),
                        "sampleSize", m.getSampleSize(),
                        "mae", m.getMae() == null ? "n/a" : m.getMae().toPlainString(),
                        "mapePercent", m.getMape() == null ? "n/a" : m.getMape().toPlainString(),
                        "trainedTo", String.valueOf(m.getTrainingPeriodEnd())))
                .toList());

        return ResponseEntity.ok(ApiResponse.ok("AI dashboard", payload));
    }

    /** Business insights for a period: what changed, why, what needs attention. */
    @GetMapping("/insights")
    @PreAuthorize("hasAuthority('AI_INSIGHT_VIEW')")
    public ResponseEntity<ApiResponse<List<FinancialInsightService.Insight>>> insights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);
        return ResponseEntity.ok(ApiResponse.ok("Business insights",
                insightService.insightsFor(start, end)));
    }

    /** Revenue forecast with its history and measured accuracy. */
    @GetMapping("/forecast")
    @PreAuthorize("hasAuthority('AI_FORECAST_VIEW')")
    public ResponseEntity<ApiResponse<ForecastingService.RevenueForecast>> forecast(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer horizon) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from
                : end.minusDays(Math.max(60, Math.min(properties.getHistoryDays(), 540)));
        int forecastHorizon = horizon != null && horizon > 0 && horizon <= 90
                ? horizon : properties.getDefaultDailyHorizon();
        return ResponseEntity.ok(ApiResponse.ok("Sales forecast",
                forecastingService.forecastRevenue(start, end, forecastHorizon)));
    }

    /** Demand forecast for one product. */
    @GetMapping("/forecast/product/{productId}")
    @PreAuthorize("hasAuthority('AI_FORECAST_VIEW')")
    public ResponseEntity<ApiResponse<ForecastingService.DemandForecast>> productForecast(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer horizon) {
        int forecastHorizon = horizon != null && horizon > 0 && horizon <= 90
                ? horizon : 14;
        return ResponseEntity.ok(ApiResponse.ok("Product demand forecast",
                forecastingService.forecastProductDemand(productId, forecastHorizon)));
    }

    /** Demand forecasts for the products that most deserve one. */
    @GetMapping("/forecast/products")
    @PreAuthorize("hasAuthority('AI_FORECAST_VIEW')")
    public ResponseEntity<ApiResponse<List<ForecastingService.DemandForecast>>> productForecasts(
            @RequestParam(required = false) Integer horizon,
            @RequestParam(required = false) Integer limit) {
        int forecastHorizon = horizon != null && horizon > 0 && horizon <= 90 ? horizon : 14;
        int cap = limit != null && limit > 0 ? Math.min(limit, 50) : properties.getMaxDemandModels();
        return ResponseEntity.ok(ApiResponse.ok("Product demand forecasts",
                forecastingService.forecastTopProducts(forecastHorizon, cap)));
    }

    /** Anomaly alerts across revenue, expenses and stock movements. */
    @GetMapping("/anomalies")
    @PreAuthorize("hasAuthority('AI_INSIGHT_VIEW')")
    public ResponseEntity<ApiResponse<List<AnomalyDetectionService.Anomaly>>> anomalies(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(59);
        return ResponseEntity.ok(ApiResponse.ok("Anomaly alerts",
                anomalyService.detectAll(start, end)));
    }

    /** Reorder recommendations, with the arithmetic behind each one. */
    @GetMapping("/reorder")
    @PreAuthorize("hasAuthority('AI_FORECAST_VIEW')")
    public ResponseEntity<ApiResponse<List<InventoryOptimizationService.ReorderRecommendation>>> reorder(
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok("Reorder recommendations",
                inventoryService.recommendReorders(limit != null && limit > 0 ? limit : 25)));
    }

    @GetMapping("/stockouts")
    @PreAuthorize("hasAuthority('AI_FORECAST_VIEW')")
    public ResponseEntity<ApiResponse<List<InventoryOptimizationService.StockoutRisk>>> stockouts(
            @RequestParam(defaultValue = "30") int horizonDays) {
        int horizon = Math.max(1, Math.min(horizonDays, 365));
        return ResponseEntity.ok(ApiResponse.ok("Predicted stockouts",
                inventoryService.predictStockouts(horizon)));
    }

    @GetMapping("/slow-moving")
    @PreAuthorize("hasAuthority('AI_FORECAST_VIEW')")
    public ResponseEntity<ApiResponse<List<InventoryOptimizationService.SlowMovingProduct>>> slowMoving(
            @RequestParam(defaultValue = "60") int lookbackDays,
            @RequestParam(defaultValue = "25") int limit) {
        return ResponseEntity.ok(ApiResponse.ok("Slow-moving stock",
                inventoryService.identifySlowMoving(
                        Math.max(7, Math.min(lookbackDays, 365)),
                        Math.max(1, Math.min(limit, 200)))));
    }

    @GetMapping("/overstocked")
    @PreAuthorize("hasAuthority('AI_FORECAST_VIEW')")
    public ResponseEntity<ApiResponse<List<InventoryOptimizationService.OverstockedProduct>>> overstocked(
            @RequestParam(defaultValue = "90") int thresholdDays,
            @RequestParam(defaultValue = "25") int limit) {
        return ResponseEntity.ok(ApiResponse.ok("Overstocked products",
                inventoryService.identifyOverstocked(
                        Math.max(30, thresholdDays), Math.max(1, Math.min(limit, 200)))));
    }

    /** Warehouse turnover, with the raw counts behind the ratio. */
    @GetMapping("/warehouses")
    @PreAuthorize("hasAuthority('AI_FORECAST_VIEW')")
    public ResponseEntity<ApiResponse<List<ForecastingService.WarehouseTurnover>>> warehouses(
            @RequestParam(defaultValue = "30") int days) {
        int period = Math.max(1, Math.min(days, 365));
        List<ForecastingService.WarehouseTurnover> rows = warehouseRepository.findAll().stream()
                .map(w -> forecastingService.warehouseTurnover(w.getId(), period))
                .sorted((a, b) -> Double.compare(b.turnoverRatio(), a.turnoverRatio()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Warehouse turnover", rows));
    }

    /**
     * Asks the business assistant a question.
     *
     * <p>The assistant's own permission checks run per question, so this endpoint
     * requires only {@code AI_ASSISTANT_USE} and then enforces whatever the
     * resolved intent needs. Requiring the union of every permission up front
     * would mean only a Super Admin could ever use it.
     */
    @PostMapping("/assistant")
    @PreAuthorize("hasAuthority('AI_ASSISTANT_USE')")
    public ResponseEntity<ApiResponse<BusinessAssistantService.AssistantAnswer>> ask(
            @RequestBody Map<String, String> body) {
        String question = body.get("question");
        Long from = parseEpoch(body.get("from"));
        Long to = parseEpoch(body.get("to"));
        return ResponseEntity.ok(ApiResponse.ok("Assistant answer",
                assistantService.ask(question, from, to)));
    }

    /** What the assistant can answer, for the UI's suggestion chips. */
    @GetMapping("/assistant/capabilities")
    @PreAuthorize("hasAuthority('AI_ASSISTANT_USE')")
    public ResponseEntity<ApiResponse<List<String>>> capabilities() {
        return ResponseEntity.ok(ApiResponse.ok("Assistant capabilities",
                assistantService.capabilities()));
    }

    /** Model history: what produced the current figures, and how accurate it was. */
    @GetMapping("/models")
    @PreAuthorize("hasAuthority('AI_MODEL_VIEW')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> models(
            @RequestParam(required = false) String modelKey) {
        List<Map<String, Object>> rows = (modelKey == null || modelKey.isBlank()
                ? modelRegistry.allActive() : modelRegistry.history(modelKey)).stream()
                .map(m -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("modelKey", m.getModelKey());
                    row.put("algorithm", m.getAlgorithm());
                    row.put("version", m.getVersion());
                    row.put("active", m.getActive());
                    row.put("sampleSize", m.getSampleSize());
                    row.put("trainingPeriodStart", String.valueOf(m.getTrainingPeriodStart()));
                    row.put("trainingPeriodEnd", String.valueOf(m.getTrainingPeriodEnd()));
                    row.put("mae", m.getMae());
                    row.put("rmse", m.getRmse());
                    row.put("mapePercent", m.getMape());
                    row.put("trainedAt", String.valueOf(m.getTrainedAt()));
                    row.put("explanation", m.getCandidateMetrics());
                    return row;
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Model versions and evaluation", rows));
    }

    /** The log of questions asked of the assistant, for review. */
    @GetMapping("/assistant/log")
    @PreAuthorize("hasAuthority('AI_QUERY_LOG_VIEW')")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.inventory.backend.model.AiQueryLog>>> log(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 100));
        return ResponseEntity.ok(ApiResponse.ok("Assistant query log",
                queryLogRepository.findAll(
                        org.springframework.data.domain.PageRequest.of(safePage, safeSize,
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC,
                                        "createdAt")))));
    }

    private static Long parseEpoch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}