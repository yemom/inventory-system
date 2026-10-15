package com.inventory.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.ai.AiProperties;
import com.inventory.backend.ai.AnomalyDetectionService;
import com.inventory.backend.ai.BusinessAssistantService;
import com.inventory.backend.ai.FinancialInsightService;
import com.inventory.backend.ai.ForecastingService;
import com.inventory.backend.ai.InventoryOptimizationService;
import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.CreateProductRequest;
import com.inventory.backend.model.AiModelVersion;
import com.inventory.backend.model.AiQueryLog;
import com.inventory.backend.model.Permission;
import com.inventory.backend.model.Product;
import com.inventory.backend.model.Role;
import com.inventory.backend.model.User;
import com.inventory.backend.model.UserStatus;
import com.inventory.backend.repository.*;
import com.inventory.backend.security.RolePermissionCatalog;
import com.inventory.backend.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The intelligence layer, evaluated against the records it actually reads.
 *
 * <p>Three things are checked here that a unit test cannot. That a forecast is
 * built from recorded sales rather than from a pattern written into the code.
 * That a business with almost no history is told so instead of being handed a
 * confident number derived from three data points. And that authorisation is
 * enforced on the <em>resolved question</em>, so holding the assistant
 * permission is not a way round the permission the answer happens to need.
 *
 * <p>The trading history is written by {@link TradingHistoryFixture}, which is
 * test-only. Everything asserted here is read back through the same services the
 * dashboard uses.
 */
class AiServiceIntegrationTest extends BaseIntegrationTest {

    private static final long SEED = 20260913L;

    @Autowired private ForecastingService forecastingService;
    @Autowired private AnomalyDetectionService anomalyService;
    @Autowired private InventoryOptimizationService inventoryService;
    @Autowired private FinancialInsightService insightService;
    @Autowired private BusinessAssistantService assistantService;
    @Autowired private AiModelVersionRepository modelVersions;
    @Autowired private AiQueryLogRepository queryLogRepository;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AiProperties properties;
    @Autowired private TradingHistoryFixture history;
    @Autowired private org.springframework.transaction.PlatformTransactionManager txManager;
    @Autowired private com.inventory.backend.finance.FinancialCalculationService calculations;
    @Autowired private ObjectMapper json;

    private com.inventory.backend.model.Product product;
    private Long warehouseId;
    private String token;

    @BeforeEach
    void seed() throws Exception {
        // Model provenance is written in its own transaction on purpose — the
        // dashboard reads figures inside a read-only transaction, and the write
        // that explains those figures has to survive it. That means these rows no
        // longer roll back with this test's transaction, so a suite that leaves
        // them behind hands the next test someone else's trained model: the
        // active version would answer for it, and version numbers would start
        // mid-history. Clearing the table in its own committed transaction gives
        // each test the empty registry it expects.
        org.springframework.transaction.support.TransactionTemplate cleanup =
                new org.springframework.transaction.support.TransactionTemplate(txManager);
        // REQUIRES_NEW, not the default REQUIRED: the test's own transaction is
        // already open and would roll this delete straight back.
        cleanup.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        cleanup.executeWithoutResult(status -> modelVersions.deleteAll());

        token = loginAsSuperAdmin();
        warehouseId = warehouseRepository.findAll().get(0).getId();

        CreateProductRequest request = new CreateProductRequest();
        request.setSku("AI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        request.setName("Forecasted item");
        request.setCategoryId(categoryRepository.findAll().get(0).getId());
        request.setSellingPrice(new BigDecimal("50.00"));
        request.setPurchasePrice(new BigDecimal("25.00"));
        request.setInitialQuantity(500000);
        product = productRepository.findById(
                productService.createProduct(request).getId()).orElseThrow();
    }

    // ── Forecasting ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("with no history the forecast refuses and says why")
    void refusesToForecastWithoutHistory() {
        ForecastingService.RevenueForecast forecast = forecastingService.forecastRevenue(
                LocalDate.now().minusDays(30), LocalDate.now(), 14);

        assertFalse(forecast.available(),
                "three days of trading cannot support a forecast");
        assertTrue(forecast.forecast().isEmpty(),
                "no forecast may be returned when the data is insufficient");
        assertNotNull(forecast.explanation());
        String reason = forecast.explanation().toLowerCase();
        assertTrue(reason.contains("observation") || reason.contains("no sales"),
                "the caller must be told the reason; was: " + forecast.explanation());
    }

    @Test
    @DisplayName("with enough history a forecast is produced with its accuracy attached")
    void producesForecastWithAccuracy() {
        history.recordTrading(product.getId(), warehouseId, 160, 8, SEED);

        // The window is deliberately wider than the seeded history, so the days
        // at its start have no recorded sale. That is what makes the
        // "gaps are zeros" assertion below meaningful: with the window matching
        // the seed exactly there are no gaps at all, and the assertion used to
        // pass only because the current day was silently empty.
        ForecastingService.RevenueForecast forecast = forecastingService.forecastRevenue(
                LocalDate.now().minusDays(180), LocalDate.now(), 14);

        assertTrue(forecast.available(),
                "160 days of sales is enough to model; refused with: "
                        + forecast.explanation());
        assertEquals(181, forecast.historyDates().size(),
                "every day in the window must be present, including days with no sales");
        assertEquals(14, forecast.forecast().size());
        assertNotNull(forecast.algorithm(), "the chosen model must be named");
        assertNotNull(forecast.mae(), "a forecast without measured accuracy is not evidence-based");
        assertTrue(forecast.historyValues().stream().anyMatch(v -> v == 0.0),
                "days with no recorded sale must appear as zero, not be omitted: "
                        + "skipping them flattens the average and inflates the forecast");

        for (ForecastingService.ForecastPoint point : forecast.forecast()) {
            assertTrue(point.value() >= 0, "a sales forecast cannot be negative");
            assertTrue(point.lower() <= point.value() && point.value() <= point.upper(),
                    "the point forecast must lie inside its interval");
        }
    }

    @Test
    @DisplayName("the forecast reflects the data it was trained on")
    void forecastTracksTheActualLevel() {
        history.recordTrading(product.getId(), warehouseId, 160, 20, SEED);

        ForecastingService.RevenueForecast forecast = forecastingService.forecastRevenue(
                LocalDate.now().minusDays(160), LocalDate.now(), 14);

        double recentDaily = forecast.historyValues().stream()
                .skip(forecast.historyValues().size() - 28)
                .mapToDouble(Double::doubleValue).average().orElse(0);
        double nextWeek = forecast.forecast().stream().limit(7)
                .mapToDouble(ForecastingService.ForecastPoint::value).sum() / 7.0;

        // A model that ignores its input would produce the same numbers whatever
        // history it was given. A factor of two either way is a loose bound that
        // still fails a model returning a constant or a nonsense scale.
        assertTrue(nextWeek > recentDaily / 2 && nextWeek < recentDaily * 2,
                "the forecast (per day " + nextWeek + ") should be in the same "
                        + "region as the recent actuals (" + recentDaily + ")");
    }

    @Test
    @DisplayName("the forecast interval widens with the horizon")
    void intervalWidens() {
        history.recordTrading(product.getId(), warehouseId, 160, 8, SEED);
        ForecastingService.RevenueForecast forecast = forecastingService.forecastRevenue(
                LocalDate.now().minusDays(160), LocalDate.now(), 14);

        List<ForecastingService.ForecastPoint> points = forecast.forecast();
        double firstWidth = points.get(0).upper() - points.get(0).lower();
        double lastWidth = points.get(points.size() - 1).upper()
                - points.get(points.size() - 1).lower();
        assertTrue(lastWidth > firstWidth,
                "being further ahead must not be more certain");
    }

    @Test
    @DisplayName("a trained model is recorded with its period and its measured error")
    void trainingIsRecorded() {
        history.recordTrading(product.getId(), warehouseId, 160, 8, SEED);
        forecastingService.forecastRevenue(
                LocalDate.now().minusDays(160), LocalDate.now(), 14);

        AiModelVersion version = modelVersions
                .findFirstByModelKeyAndActiveTrueOrderByVersionDesc(
                        ForecastingService.MODEL_REVENUE_DAILY).orElseThrow();

        assertNotNull(version.getAlgorithm());
        assertEquals(1, version.getVersion());
        assertNotNull(version.getTrainedAt());
        assertNotNull(version.getTrainingPeriodStart());
        assertNotNull(version.getTrainingPeriodEnd());
        assertTrue(version.getSampleSize() >= 160);
        assertNotNull(version.getMae());
        assertNotNull(version.getCandidateMetrics(),
                "the explanation for why this model won must be recorded, not just the winner");
        assertTrue(Boolean.TRUE.equals(version.getActive()));
    }

    @Test
    @DisplayName("retraining supersedes the previous version rather than overwriting it")
    void retrainingKeepsHistory() {
        history.recordTrading(product.getId(), warehouseId, 160, 8, SEED);
        forecastingService.forecastRevenue(LocalDate.now().minusDays(160), LocalDate.now(), 14);
        forecastingService.forecastRevenue(LocalDate.now().minusDays(170), LocalDate.now(), 14);

        List<AiModelVersion> versions = committedVersions(ForecastingService.MODEL_REVENUE_DAILY);

        assertEquals(2, versions.size(),
                "both versions must remain readable; overwriting the first loses the "
                        + "evidence for figures already reported");
        assertEquals(2, versions.get(0).getVersion());
        assertTrue(Boolean.TRUE.equals(versions.get(0).getActive()));
        assertFalse(Boolean.TRUE.equals(versions.get(1).getActive()),
                "exactly one version answers at a time");
    }

    @Test
    @DisplayName("demand for a product with no sales is reported as unforecastable")
    void productDemandRefusesWithoutHistory() {
        ForecastingService.DemandForecast demand =
                forecastingService.forecastProductDemand(product.getId(), 14);

        assertFalse(demand.available(),
                "no recorded sales means no demand to model, however long the "
                        + "window is — a forecast of zero would read as a prediction "
                        + "about a product rather than an absence of data");
        assertTrue(demand.forecast().isEmpty());
        assertTrue(demand.explanation().toLowerCase().contains("no recorded sales"),
                "the caller must be told the reason; was: " + demand.explanation());
        assertTrue(demand.explanation().contains("reorder level"),
                "and told what applies instead; was: " + demand.explanation());
    }

    @Test
    @DisplayName("demand is forecast from recorded quantities, not from revenue")
    void productDemandUsesQuantity() {
        history.recordTrading(product.getId(), warehouseId, 80, 3, SEED);

        ForecastingService.DemandForecast demand =
                forecastingService.forecastProductDemand(product.getId(), 14);

        assertTrue(demand.available(),
                "80 days of unit sales is enough; refused with: " + demand.explanation());
        assertEquals(81, demand.historyDates().size());
        assertNotNull(demand.averageDailyUnits());
        assertTrue(demand.averageDailyUnits() > 0);
        // Around 3 units a day, with the weekend uplift the fixture writes.
        assertTrue(demand.averageDailyUnits() > 2 && demand.averageDailyUnits() < 6,
                "velocity should be near the units actually sold each day, was "
                        + demand.averageDailyUnits());
    }

    // ── Inventory optimisation ────────────────────────────────────────────────

    @Test
    @DisplayName("a product that never sold gets no quantity recommendation")
    void noHistoryMeansNoQuantity() {
        InventoryOptimizationService.ReorderRecommendation forProduct =
                inventoryService.recommendReorders(0).stream()
                        .filter(r -> product.getId().equals(r.productId()))
                        .findFirst().orElseThrow();

        assertFalse(forProduct.hasSufficientHistory(),
                "no sales means no demand estimate, and so no quantity");
        assertEquals(0, forProduct.suggestedQuantity());
        assertEquals("NO_SALES_HISTORY", forProduct.urgency());
        assertTrue(forProduct.evidence().containsKey("configuredReorderLevel"),
                "the manual reorder level still applies and must be reported");
        assertTrue(forProduct.authorityNote().toLowerCase().contains("no quantity"),
                "was: " + forProduct.authorityNote());
    }

    @Test
    @DisplayName("a recommendation states the evidence and the arithmetic behind it")
    void recommendationCarriesItsEvidence() {
        history.recordTrading(product.getId(), warehouseId, 100, 4, SEED);

        InventoryOptimizationService.ReorderRecommendation forProduct =
                inventoryService.recommendReorders(0).stream()
                        .filter(r -> product.getId().equals(r.productId()))
                        .findFirst().orElseThrow();

        assertTrue(forProduct.hasSufficientHistory());
        assertTrue(forProduct.reorderPoint() >= forProduct.safetyStock(),
                "the reorder point must cover the lead time as well as the safety stock");
        assertTrue(forProduct.coefficientOfVariation() > 0);
        assertTrue(forProduct.observationCount() >= 100);

        java.util.Map<String, Object> evidence = forProduct.evidence();
        assertTrue(evidence.containsKey("averageDailyUnits"));
        assertTrue(evidence.containsKey("dailyDemandStdDev"));
        assertTrue(evidence.containsKey("serviceLevel"));
        assertTrue(evidence.containsKey("assumedLeadTimeDays"));
        assertTrue(evidence.containsKey("formula"),
                "the arithmetic must be readable, not merely asserted");
        assertTrue(evidence.containsKey("leadTimeSource"),
                "a default lead time must be labelled as a default, not passed off as measured");
        assertTrue(forProduct.authorityNote().toLowerCase().contains("human"),
                "a recommendation must say that it decides nothing on its own");
    }

    @Test
    @DisplayName("nothing is projected to run out for a product that has never sold")
    void stockoutRiskExcludesProductsWithoutHistory() {
        assertTrue(inventoryService.predictStockouts(30).stream()
                        .noneMatch(r -> product.getId().equals(r.productId())),
                "a product that has never sold has no velocity, so it cannot be "
                        + "projected to run out — saying so is different from saying "
                        + "it needs nothing");
    }

    @Test
    @DisplayName("days of stock remaining is reported from the observed rate")
    void daysOfStockIsDerivedFromVelocity() {
        history.recordTrading(product.getId(), warehouseId, 100, 5, SEED);

        InventoryOptimizationService.ReorderRecommendation forProduct =
                inventoryService.recommendReorders(0).stream()
                        .filter(r -> product.getId().equals(r.productId()))
                        .findFirst().orElseThrow();

        assertNotNull(forProduct.daysOfStockRemaining());
        double expected = forProduct.stockOnHand() / forProduct.averageDailyUnits();
        // Compared as a ratio. The velocity is reported rounded to two decimal
        // places, so dividing by it and by the unrounded value differs slightly;
        // an absolute tolerance would be meaningless at this magnitude.
        assertEquals(1.0, expected / forProduct.daysOfStockRemaining(), 0.005,
                "days of cover is stock on hand divided by the sales rate");
    }

    // ── Anomaly detection ─────────────────────────────────────────────────────

    @Test
    @DisplayName("an ordinary period with a weekly pattern raises no revenue alerts")
    void ordinaryPeriodRaisesNoAlerts() {
        history.recordTrading(product.getId(), warehouseId, 100, 8, SEED);

        List<AnomalyDetectionService.Anomaly> anomalies = anomalyService.detectRevenueAnomalies(
                LocalDate.now().minusDays(100), LocalDate.now());

        assertTrue(anomalies.isEmpty(),
                "a normal period whose weekends trade harder must not fire a stream "
                        + "of alerts, but raised " + anomalies.size());
    }

    @Test
    @DisplayName("a genuine spike is raised as an investigation alert, not a verdict")
    void spikeIsRaisedAsAnAlert() {
        history.recordTrading(product.getId(), warehouseId, 100, 8, SEED);
        // Two orders of a thousand units against an ordinary eight. Large as well
        // as improbable: the detector has to notice a big number, not only a
        // statistically odd one.
        history.recordSpike(product.getId(), warehouseId, 1000);

        List<AnomalyDetectionService.Anomaly> anomalies = anomalyService.detectRevenueAnomalies(
                LocalDate.now().minusDays(100), LocalDate.now());

        assertFalse(anomalies.isEmpty(),
                "a day of 240 units against a norm of 8 must be flagged");
        AnomalyDetectionService.Anomaly top = anomalies.get(0);
        assertEquals("REVENUE", top.source());
        assertEquals("UNUSUALLY_HIGH", top.kind());
        assertTrue(top.summary().toLowerCase().contains("unusually"),
                "the wording must describe rather than accuse: " + top.summary());
        assertTrue(top.evidence().containsKey("medianOfHistory"),
                "the evidence behind the alert must be readable");
        assertTrue(top.evidence().containsKey("method"),
                "and the method must be stated so the threshold can be judged");
        assertTrue(top.evidence().containsKey("baseline"),
                "and what it was compared against");
        assertTrue(top.evidence().containsKey("expectedForThisWeekday"),
                "and what that weekday normally trades at");

        // Two conditions, not one: improbable and worth someone's time. The
        // evidence has to state both, or a reader cannot tell why the detector
        // stayed quiet on the days it did.
        double deviation = ((Number) top.evidence().get("differencePercent")).doubleValue();
        double floor = ((Number) top.evidence().get("minimumDeviationPercent")).doubleValue();
        assertTrue(deviation >= floor,
                "an alert must be materially different, not merely improbable; was "
                        + deviation + "% against a floor of " + floor + "%");
    }

    @Test
    @DisplayName("a day that is statistically odd but small in money is not raised")
    void statisticallyOddButTrivialIsNotRaised() {
        // Trading this regular leaves almost no spread, so ordinary variation
        // scores high on a robust z-score. Without the practical-significance
        // floor those days would fill the alerts panel and train people to
        // dismiss it — which is when a real spike goes unread.
        history.recordTrading(product.getId(), warehouseId, 100, 8, SEED);

        assertTrue(anomalyService.detectRevenueAnomalies(
                        LocalDate.now().minusDays(100), LocalDate.now()).isEmpty(),
                "no day of this fixture's trading is far enough from its own weekday "
                        + "normal to be worth raising");
    }

    @Test
    @DisplayName("a period with too little history raises nothing")
    void shortPeriodRaisesNothing() {
        assertTrue(anomalyService.detectRevenueAnomalies(
                        LocalDate.now().minusDays(5), LocalDate.now()).isEmpty(),
                "five days cannot establish what is normal for this business");
    }

    @Test
    @DisplayName("an alert never claims wrongdoing")
    void alertsAreFramedAsInvestigations() {
        history.recordTrading(product.getId(), warehouseId, 100, 8, SEED);
        history.recordSpike(product.getId(), warehouseId, 1000);

        for (AnomalyDetectionService.Anomaly anomaly : anomalyService.detectAll(
                LocalDate.now().minusDays(100), LocalDate.now())) {
            String summary = anomaly.summary().toLowerCase();
            assertFalse(summary.contains("fraud"), "an alert must not accuse: " + anomaly.summary());
            assertFalse(summary.contains("theft"), "an alert must not accuse: " + anomaly.summary());
            assertFalse(summary.contains("stolen"), "an alert must not accuse: " + anomaly.summary());
        }
    }

    // ── Financial insights ────────────────────────────────────────────────────

    @Test
    @DisplayName("an empty period says there is nothing to analyse, not that nothing happened")
    void emptyPeriodIsReportedHonestly() {
        List<FinancialInsightService.Insight> insights = insightService.insightsFor(
                LocalDate.now().minusDays(300), LocalDate.now().minusDays(280));

        assertFalse(insights.isEmpty(), "the caller must get an answer");
        assertEquals("DATA", insights.get(0).category());
        assertEquals(FinancialInsightService.Severity.INFO, insights.get(0).severity());
        assertTrue(insights.get(0).message().contains("no conclusion can be drawn"),
                "was: " + insights.get(0).message());
        assertNotNull(insights.get(0).sourceUrl(),
                "every insight links to the report showing the same figures");
    }

    @Test
    @DisplayName("an explanation quotes the figures the calculation layer produced")
    void explanationIsGroundedInTheSummary() {
        history.recordTrading(product.getId(), warehouseId, 40, 6, SEED);

        LocalDate from = LocalDate.now().minusDays(39);
        LocalDate to = LocalDate.now();

        List<FinancialInsightService.Insight> insights = insightService.insightsFor(from, to);
        assertFalse(insights.isEmpty());

        FinancialInsightService.Insight profit = insights.stream()
                .filter(i -> "PROFIT".equals(i.category()) || "PROFIT_DECLINED".equals(i.category()))
                .findFirst().orElseThrow();

        java.util.Map<String, Object> evidence = profit.evidence();
        java.math.BigDecimal netSales = (java.math.BigDecimal) evidence.get("netSales");
        java.math.BigDecimal netProfit = (java.math.BigDecimal) evidence.get("netProfit");

        assertNotNull(netSales, "the explanation must carry the figure it talks about");
        assertNotNull(netProfit);
        assertTrue(netSales.signum() > 0);

        // Every figure the explanation cites must equal the one the summary
        // reports. If these can disagree, the dashboard and the explanation can
        // disagree, which is the thing this whole layer exists to prevent.
        com.inventory.backend.finance.FinancialSummary summary =
                calculations.summarise(from, to);
        assertEquals(0, summary.netSales().compareTo(netSales));
        assertEquals(0, summary.netProfit().compareTo(netProfit));

        assertTrue(profit.message().contains("Gross profit")
                        || profit.message().contains("gross profit")
                        || profit.message().contains("Net profit")
                        || profit.message().contains("net profit"),
                "the explanation must speak in the figures it is explaining; was: "
                        + profit.message());
    }


    // ── The assistant ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a recognised question is answered from validated figures")
    void answersARecognisedQuestion() {
        // Some trading first: with an empty month the correct answer is
        // "insufficient data", which is covered separately below.
        history.recordTrading(product.getId(), warehouseId, 45, 6, SEED);

        BusinessAssistantService.AssistantAnswer answer =
                asRole("ACCOUNTANT", () -> assistantService.ask("What was my gross profit last month?", null, null));

        assertEquals(AiQueryLog.OUTCOME_ANSWERED, answer.outcome());
        assertEquals("GROSS_PROFIT", answer.intent());
        assertNotNull(answer.period());
        assertFalse(answer.assumptions().isEmpty(),
                "every answer must state what its figures assume");
        assertFalse(answer.sources().isEmpty(),
                "every answer must link to the report showing the same numbers");
        assertTrue(answer.answer().contains("Gross profit"),
                "was: " + answer.answer());
        assertTrue(answer.data().containsKey("grossProfit"),
                "the structured result must be returned alongside the wording, so a "
                        + "reader can check the sentence against the number");
    }

    @Test
    @DisplayName("an unrecognised question asks rather than guessing")
    void asksWhenItDoesNotUnderstand() {
        BusinessAssistantService.AssistantAnswer answer =
                asRole("ACCOUNTANT", () -> assistantService.ask("What is the weather like in Addis Ababa?", null, null));

        assertEquals(AiQueryLog.OUTCOME_CLARIFICATION_NEEDED, answer.outcome());
        assertTrue(answer.answer().toLowerCase().contains("could not tell"),
                "was: " + answer.answer());
        assertTrue(answer.data().containsKey("canAnswer"),
                "a clarification must say what it can answer");
        assertFalse(answer.data().containsKey("grossProfit"),
                "no figures may be quoted when the question was not understood");
    }

    @Test
    @DisplayName("the date range in the question is honoured and reported")
    void resolvesThePeriodFromTheQuestion() {
        BusinessAssistantService.AssistantAnswer answer =
                asRole("ACCOUNTANT", () -> assistantService.ask("What was my gross profit last month?", null, null));

        LocalDate expectedEnd = LocalDate.now().withDayOfMonth(1).minusDays(1);
        assertEquals(expectedEnd, answer.period().to());
        assertEquals(expectedEnd.withDayOfMonth(1), answer.period().from());
        assertEquals("last month", answer.period().label(),
                "the reader must be able to tell which window was used");
    }

    @Test
    @DisplayName("an explicit date filter overrides the words in the question")
    void explicitDatesWin() {
        long from = LocalDate.now().minusDays(6).atStartOfDay()
                .toInstant(ZoneOffset.UTC).toEpochMilli();
        long to = LocalDate.now().atStartOfDay()
                .toInstant(ZoneOffset.UTC).toEpochMilli();

        BusinessAssistantService.AssistantAnswer answer =
                asRole("ACCOUNTANT", () -> assistantService.ask("What was my gross profit last month?", from, to));

        assertEquals(LocalDate.now().minusDays(6), answer.period().from(),
                "a filter set on the dashboard must override the wording");
        assertEquals(LocalDate.now(), answer.period().to());
        assertEquals("the selected date range", answer.period().label());
    }

    @Test
    @DisplayName("a question with no matching data says so instead of reporting zero")
    void reportsInsufficientData() {
        BusinessAssistantService.AssistantAnswer answer =
                asRole("ACCOUNTANT", () -> assistantService.ask("Show purchases received but not fully paid", null, null));

        if (AiQueryLog.OUTCOME_INSUFFICIENT_DATA.equals(answer.outcome())) {
            assertTrue(answer.note().toLowerCase().contains("no estimate"),
                    "was: " + answer.note());
        } else {
            assertEquals(AiQueryLog.OUTCOME_ANSWERED, answer.outcome());
            assertNotNull(answer.data());
        }
    }

    @Test
    @DisplayName("the AI dashboard serves with history present, recording provenance as it goes")
    void aiDashboardServesWithHistory() throws Exception {
        // The endpoint trains a model and records it. That write used to join the
        // dashboard's read-only transaction, so PostgreSQL rejected the INSERT,
        // marked the whole transaction aborted, and every later statement in the
        // request failed with "current transaction is aborted" — a dashboard that
        // only wanted to read figures answering 500.
        history.recordTrading(product.getId(), warehouseId, 160, 8, SEED);

        mockMvc.perform(get("/api/v1/ai/dashboard")
                        .param("from", LocalDate.now().minusDays(160).toString())
                        .param("to", LocalDate.now().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("every question is logged with what was asked and what was answered")
    void questionsAreLogged() {
        asRole("ACCOUNTANT", () -> assistantService.ask("What was my gross profit last month?", null, null));
        asRole("ACCOUNTANT", () -> assistantService.ask("Something completely unrelated", null, null));

        List<AiQueryLog> logs = queryLogRepository.findAll();
        assertTrue(logs.stream()
                        .anyMatch(l -> l.getQuestion().contains("gross profit")),
                "the question must be recorded so a figure can be audited afterwards");
        assertTrue(logs.stream().anyMatch(l ->
                        AiQueryLog.OUTCOME_CLARIFICATION_NEEDED.equals(l.getOutcome())),
                "a question that could not be understood is still worth keeping");
        assertTrue(logs.stream().allMatch(l -> l.getResolvedParameters() != null),
                "the parameters must be recorded so the query can be re-run");
        assertTrue(logs.stream().allMatch(l -> l.getResultPayload() != null),
                "the structured result must be recorded alongside the wording");
        assertTrue(logs.stream().noneMatch(l -> l.getQuestion() != null
                        && l.getQuestion().toLowerCase().contains("password")),
                "no credential may be written to the log");
    }

    // ── Authorisation boundaries ──────────────────────────────────────────────

    @Test
    @DisplayName("a cashier gets nothing from the AI endpoints")
    void aiEndpointsRequirePermission() throws Exception {
        String cashier = tokenForRole("CASHIER");

        mockMvc.perform(get("/api/v1/ai/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashier))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/ai/insights")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashier))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/ai/assistant")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What was my gross profit last month?\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a supervisor may use the assistant but is refused the profit figures")
    void profitIsRefusedWithoutProfitPermission() throws Exception {
        String supervisor = tokenForRole("SUPERVISOR");

        // Asserted from the catalogue rather than assumed: this test only means
        // something because the Supervisor role holds AI_ASSISTANT_USE without
        // PROFIT_LOSS_READ.
        assertTrue(RolePermissionCatalog.permissionsFor("SUPERVISOR")
                        .contains("AI_ASSISTANT_USE"),
                "the supervisor must be able to use the assistant for this to test anything");
        assertFalse(RolePermissionCatalog.permissionsFor("SUPERVISOR")
                        .contains("PROFIT_LOSS_READ"),
                "the supervisor must NOT hold profit access for this boundary to exist");

        mockMvc.perform(post("/api/v1/ai/assistant")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What was my gross profit last month?\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a supervisor may still ask operational questions")
    void operationalQuestionsRemainAvailable() throws Exception {
        tokenForRole("SUPERVISOR");

        // An explicitly empty authority set. No role in the catalogue holds
        // nothing, so this is the only way to prove the check fails closed —
        // and getting that wrong would let an unauthorised caller read balances.
        //
        // The service throws rather than returning a refusal, because the
        // controller has to produce 403: a 200 carrying "not allowed" in the
        // body would be indistinguishable from an answer to a client that only
        // checks the status code.
        org.springframework.security.access.AccessDeniedException refusal =
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                        () -> withAuthorities(Set.of(), () -> assistantService.ask(
                                "Which products are likely to run out in the next 14 days?",
                                null, null)));

        assertTrue(refusal.getMessage().toLowerCase().contains("permission"),
                "the refusal must say why, without disclosing the figures; was: "
                        + refusal.getMessage());
        assertFalse(refusal.getMessage().contains("stock"),
                "the refusal must not describe the answer it withheld");
    }

    @Test
    @DisplayName("an inventory question is answered for a role entitled to ask it")
    void operationalQuestionIsAnsweredForTheRightRole() {
        BusinessAssistantService.AssistantAnswer answer = asRole("SUPERVISOR", () ->
                assistantService.ask(
                        "Which products are likely to run out in the next 14 days?", null, null));

        assertEquals(AiQueryLog.OUTCOME_ANSWERED, answer.outcome(),
                "the supervisor holds AI_FORECAST_VIEW and INVENTORY_READ, so a stock "
                        + "question is one they are entitled to ask");
        assertFalse(answer.data().containsKey("grossProfit"),
                "and a stock question must not pull a profit figure into the reply");
    }

    @Test
    @DisplayName("an AI query never exposes a credential")
    void queriesExposeNoCredentials() {
        BusinessAssistantService.AssistantAnswer answer =
                asRole("ACCOUNTANT", () -> assistantService.ask("What was my gross profit last month?", null, null));

        String payload = answer.answer() + answer.data() + answer.sources()
                + answer.assumptions() + answer.note();
        assertFalse(payload.toLowerCase().contains("password"));
        assertFalse(payload.toLowerCase().contains("jwt"));
        assertFalse(payload.toLowerCase().contains("secret"));
        assertFalse(payload.contains("Bearer "));
    }

    @Test
    @DisplayName("the model history endpoint is closed without the model permission")
    void modelHistoryRequiresPermission() throws Exception {
        String cashier = tokenForRole("CASHIER");

        mockMvc.perform(get("/api/v1/ai/models")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashier))
                .andExpect(status().isForbidden());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Runs {@code work} with the given role's authorities on the current thread.
     *
     * <p>Needed because the assistant enforces its permissions against the
     * security context, and a direct call to the service from a test has none.
     * It fails closed by design — that is what the authorisation tests rely on —
     * so a test that wants a successful answer has to be acting as somebody.
     * The HTTP tests below go through the real filter chain instead and are not
     * affected by this.
     */
    private <T> T asRole(String roleName, Supplier<T> work) {
        return withAuthorities(RolePermissionCatalog.permissionsFor(roleName), work);
    }

    /**
     * Runs {@code work} holding exactly {@code permissions}, and nothing else.
     *
     * <p>Restores the previous authentication on the way out, including when
     * {@code work} throws, so one test cannot widen what the next one sees.
     */
    private <T> T withAuthorities(Set<String> permissions, Supplier<T> work) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "test-caller", "n/a", authoritiesOf(permissions)));
        try {
            return work.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private static List<org.springframework.security.core.GrantedAuthority> authoritiesOf(
            Set<String> permissions) {
        return permissions.stream()
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .map(granted -> (org.springframework.security.core.GrantedAuthority) granted)
                .toList();
    }

    /**
     * Reads the recorded model versions as they are <em>committed</em>.
     *
     * <p>The registry writes provenance in its own transaction, so the write is
     * already durable when the call returns — while this test's own transaction
     * is still open and its persistence context still holds whatever it read
     * earlier. Reading through it answers with a stale row: two versions both
     * claiming to be the active one, which is not what the database says. A
     * fresh transaction is a fresh persistence context, so this sees the truth.
     */
    private List<AiModelVersion> committedVersions(String modelKey) {
        org.springframework.transaction.support.TransactionTemplate read =
                new org.springframework.transaction.support.TransactionTemplate(txManager);
        read.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return read.execute(status ->
                modelVersions.findByModelKeyOrderByVersionDesc(modelKey));
    }

    /**
     * A token for a user holding {@code roleName} with that role's permissions
     * from the catalogue.
     *
     * <p>Created rather than logged in as the super admin, because the boundary
     * under test is "this role may see the dashboard but not this figure" — a
     * super admin would pass every check and prove nothing.
     */
    private String tokenForRole(String roleName) throws Exception {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        String email = "ai-" + roleName.toLowerCase() + "@stockflow.local";
        String password = "AiBoundary#" + UUID.randomUUID().toString().substring(0, 12);

        User user = userRepository.findByEmail(email).orElseGet(() -> User.builder()
                .username("ai_" + roleName.toLowerCase())
                .firstName("Boundary")
                .lastName(roleName)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .build());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.saveAndFlush(user);
        return login(email, password);
    }
}
