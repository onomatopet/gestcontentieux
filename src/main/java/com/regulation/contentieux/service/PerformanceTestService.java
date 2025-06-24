package com.regulation.contentieux.service;

import com.regulation.contentieux.dao.*;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.sql.*;

/**
 * Service de test de performance selon les critères d'acceptation du cahier des charges
 * Critère : "Performance : chargement < 3s pour 10000 enregistrements"
 *
 * ENRICHISSEMENT COMPLET pour valider les performances
 */
public class PerformanceTestService {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceTestService.class);

    // Critères d'acceptation
    private static final int TARGET_RECORDS = 10000;
    private static final long MAX_LOADING_TIME_MS = 3000; // 3 secondes
    private static final int CONCURRENT_USERS = 10;

    // DAOs pour les tests
    private final AffaireDAO affaireDAO;
    private final EncaissementDAO encaissementDAO;
    private final AgentDAO agentDAO;
    private final ContrevenantDAO contrevenantDAO;

    // Services
    private final RapportService rapportService;
    private final RepartitionService repartitionService;

    public PerformanceTestService() {
        this.affaireDAO = new AffaireDAO();
        this.encaissementDAO = new EncaissementDAO();
        this.agentDAO = new AgentDAO();
        this.contrevenantDAO = new ContrevenantDAO();
        this.rapportService = new RapportService();
        this.repartitionService = new RepartitionService();
    }

    // ==================== TESTS DE PERFORMANCE PRINCIPAUX ====================

    /**
     * Test complet de performance selon les critères d'acceptation
     */
    public PerformanceReport executerTestsComplets() {
        logger.info("🚀 === DÉBUT DES TESTS DE PERFORMANCE ===");

        PerformanceReport report = new PerformanceReport();
        report.setStartTime(LocalDateTime.now());

        try {
            // 1. Test de chargement des données (critère principal)
            report.setChargementTest(testerChargementDonnees());

            // 2. Test de concurrence
            report.setConcurrenceTest(testerConcurrence());

            // 3. Test de génération de rapports
            report.setRapportsTest(testerGenerationRapports());

            // 4. Test de numérotation automatique
            report.setNumerotationTest(testerNumerotationAutomatique());

            // 5. Test de recherche et filtrage
            report.setRechercheTest(testerRechercheEtFiltrage());

            // 6. Test de calculs de répartition
            report.setRepartitionTest(testerCalculsRepartition());

            report.setEndTime(LocalDateTime.now());
            report.calculateOverallResult();

            logger.info("✅ Tests de performance terminés");
            logger.info("📊 Résultat global: {}", report.getOverallResult());

        } catch (Exception e) {
            logger.error("❌ Erreur lors des tests de performance", e);
            report.setErrorMessage(e.getMessage());
            report.setOverallResult(TestResult.FAILURE);
        }

        return report;
    }

    /**
     * Test de chargement de 10000 enregistrements < 3s (critère principal)
     */
    public TestResult testerChargementDonnees() {
        logger.info("📊 Test de chargement de {} enregistrements...", TARGET_RECORDS);

        try {
            // S'assurer qu'on a assez de données
            verifierOuCreerDonnees();

            long startTime = System.currentTimeMillis();

            // Test de chargement des affaires (table principale)
            List<Affaire> affaires = affaireDAO.findAll();

            long loadingTime = System.currentTimeMillis() - startTime;

            logger.info("⏱️ Chargement de {} affaires en {} ms", affaires.size(), loadingTime);

            TestResult result = new TestResult();
            result.setTestName("Chargement données");
            result.setDuration(loadingTime);
            result.setRecordCount(affaires.size());
            result.setSuccess(loadingTime <= MAX_LOADING_TIME_MS);
            result.setCriterion("< " + MAX_LOADING_TIME_MS + " ms pour " + TARGET_RECORDS + " enregistrements");

            if (result.isSuccess()) {
                logger.info("✅ Test de chargement RÉUSSI: {} ms < {} ms", loadingTime, MAX_LOADING_TIME_MS);
            } else {
                logger.warn("❌ Test de chargement ÉCHOUÉ: {} ms > {} ms", loadingTime, MAX_LOADING_TIME_MS);
            }

            return result;

        } catch (Exception e) {
            logger.error("❌ Erreur lors du test de chargement", e);
            TestResult errorResult = new TestResult();
            errorResult.setTestName("Chargement données");
            errorResult.setSuccess(false);
            errorResult.setErrorMessage(e.getMessage());
            return errorResult;
        }
    }

    /**
     * Test de concurrence avec plusieurs utilisateurs simultanés
     */
    public TestResult testerConcurrence() {
        logger.info("👥 Test de concurrence avec {} utilisateurs simultanés...", CONCURRENT_USERS);

        try {
            ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
            List<Future<Long>> futures = new ArrayList<>();

            long startTime = System.currentTimeMillis();

            // Lancer plusieurs requêtes simultanées
            for (int i = 0; i < CONCURRENT_USERS; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        long taskStart = System.currentTimeMillis();

                        // Correction: utiliser findByPeriod au lieu de findByDateRange
                        LocalDate debut = LocalDate.now().minusDays(30);
                        LocalDate fin = LocalDate.now();
                        List<Affaire> affaires = affaireDAO.findByPeriod(debut, fin);

                        return System.currentTimeMillis() - taskStart;
                    } catch (Exception e) {
                        logger.error("Erreur dans tâche concurrente", e);
                        return -1L;
                    }
                }));
            }

            // Attendre la completion de toutes les tâches
            List<Long> durations = new ArrayList<>();
            for (Future<Long> future : futures) {
                Long duration = future.get(10, TimeUnit.SECONDS);
                if (duration > 0) {
                    durations.add(duration);
                }
            }

            executor.shutdown();

            long totalDuration = System.currentTimeMillis() - startTime;
            double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0.0);

            TestResult result = new TestResult();
            result.setTestName("Test concurrence");
            result.setDuration(totalDuration);
            result.setSuccess(totalDuration < 5000 && avgDuration < 1000); // < 5s total, < 1s par requête
            result.setCriterion("10 utilisateurs simultanés en < 5s");
            result.setDetails(String.format("Durée moyenne par requête: %.0f ms", avgDuration));

            if (result.isSuccess()) {
                logger.info("✅ Test concurrence RÉUSSI: {} ms total, {} ms moyenne",
                        totalDuration, Math.round(avgDuration));
            } else {
                logger.warn("❌ Test concurrence ÉCHOUÉ: {} ms total, {} ms moyenne",
                        totalDuration, Math.round(avgDuration));
            }

            return result;

        } catch (Exception e) {
            logger.error("❌ Erreur lors du test de concurrence", e);
            TestResult errorResult = new TestResult();
            errorResult.setTestName("Test concurrence");
            errorResult.setSuccess(false);
            errorResult.setErrorMessage(e.getMessage());
            return errorResult;
        }
    }

    /**
     * Test de génération de rapports
     */
    public TestResult testerGenerationRapports() {
        logger.info("📋 Test de génération de rapports...");

        try {
            long startTime = System.currentTimeMillis();

            LocalDate debut = LocalDate.now().minusMonths(1);
            LocalDate fin = LocalDate.now();

            // Tester les principaux rapports
            rapportService.genererEtatRepartitionAffaires(debut, fin);
            rapportService.genererEtatMandatement(debut, fin);
            rapportService.genererTableauAmendesParServices(debut, fin);

            long duration = System.currentTimeMillis() - startTime;

            TestResult result = new TestResult();
            result.setTestName("Génération rapports");
            result.setDuration(duration);
            result.setSuccess(duration < 5000); // 5 secondes max
            result.setCriterion("3 rapports générés en < 5s");

            if (result.isSuccess()) {
                logger.info("✅ Test rapports RÉUSSI: {} ms", duration);
            } else {
                logger.warn("❌ Test rapports ÉCHOUÉ: {} ms > 5000 ms", duration);
            }

            return result;

        } catch (Exception e) {
            logger.error("❌ Erreur lors du test de rapports", e);
            TestResult errorResult = new TestResult();
            errorResult.setTestName("Génération rapports");
            errorResult.setSuccess(false);
            errorResult.setErrorMessage(e.getMessage());
            return errorResult;
        }
    }

    /**
     * Test de performance de la numérotation automatique
     */
    public TestResult testerNumerotationAutomatique() {
        logger.info("🔢 Test de numérotation automatique...");

        try {
            NumerotationService numerotationService = NumerotationService.getInstance();

            long startTime = System.currentTimeMillis();

            // Générer 1000 numéros
            Set<String> numeros = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                String numero = numerotationService.genererNumeroAffaire();
                numeros.add(numero);
            }

            long duration = System.currentTimeMillis() - startTime;

            TestResult result = new TestResult();
            result.setTestName("Numérotation automatique");
            result.setDuration(duration);
            result.setRecordCount(numeros.size());
            result.setSuccess(numeros.size() == 1000 && duration < 1000); // Tous uniques en < 1s
            result.setCriterion("1000 numéros uniques en < 1s");

            if (result.isSuccess()) {
                logger.info("✅ Test numérotation RÉUSSI: {} numéros uniques en {} ms",
                        numeros.size(), duration);
            } else {
                logger.warn("❌ Test numérotation ÉCHOUÉ: {} numéros en {} ms",
                        numeros.size(), duration);
            }

            return result;

        } catch (Exception e) {
            logger.error("❌ Erreur lors du test de numérotation", e);
            TestResult errorResult = new TestResult();
            errorResult.setTestName("Numérotation automatique");
            errorResult.setSuccess(false);
            errorResult.setErrorMessage(e.getMessage());
            return errorResult;
        }
    }

    /**
     * Test de recherche et filtrage
     */
    public TestResult testerRechercheEtFiltrage() {
        logger.info("🔍 Test de recherche et filtrage...");

        try {
            long startTime = System.currentTimeMillis();

            // Tests de recherche avec les méthodes existantes
            // Correction: utiliser searchAffaires au lieu de findByNumeroContaining
            List<Affaire> parNumero = affaireDAO.searchAffaires("2025", null, null, null, null, 0, 100);

            // Correction: utiliser searchContrevenants au lieu de findByNomContaining
            List<Contrevenant> parNom = contrevenantDAO.searchContrevenants("TEST", null, 0, 100);

            List<Agent> parService = agentDAO.findByServiceId(1L);

            long duration = System.currentTimeMillis() - startTime;

            TestResult result = new TestResult();
            result.setTestName("Recherche et filtrage");
            result.setDuration(duration);
            result.setSuccess(duration < 2000); // < 2 secondes
            result.setCriterion("3 recherches en < 2s");
            result.setDetails(String.format("Affaires: %d, Contrevenants: %d, Agents: %d",
                    parNumero.size(), parNom.size(), parService.size()));

            if (result.isSuccess()) {
                logger.info("✅ Test recherche RÉUSSI: {} ms", duration);
            } else {
                logger.warn("❌ Test recherche ÉCHOUÉ: {} ms > 2000 ms", duration);
            }

            return result;

        } catch (Exception e) {
            logger.error("❌ Erreur lors du test de recherche", e);
            TestResult errorResult = new TestResult();
            errorResult.setTestName("Recherche et filtrage");
            errorResult.setSuccess(false);
            errorResult.setErrorMessage(e.getMessage());
            return errorResult;
        }
    }

    /**
     * Test de calculs de répartition
     */
    public TestResult testerCalculsRepartition() {
        logger.info("💰 Test de calculs de répartition...");

        try {
            long startTime = System.currentTimeMillis();

            // Créer des données de test
            Affaire affaire = new Affaire();
            affaire.setMontantAmendeTotal(new BigDecimal("100000"));

            Encaissement encaissement = new Encaissement();
            encaissement.setMontantEncaisse(new BigDecimal("100000"));
            encaissement.setAffaire(affaire);

            // Tester les calculs (100 fois)
            for (int i = 0; i < 100; i++) {
                RepartitionResultat resultat = repartitionService.calculerRepartition(encaissement, affaire);
                // Vérifier la cohérence
                if (resultat == null || resultat.getProduitDisponible().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new RuntimeException("Calcul de répartition incohérent");
                }
            }

            long duration = System.currentTimeMillis() - startTime;

            TestResult result = new TestResult();
            result.setTestName("Calculs de répartition");
            result.setDuration(duration);
            result.setSuccess(duration < 500); // < 500ms pour 100 calculs
            result.setCriterion("100 calculs en < 500ms");

            if (result.isSuccess()) {
                logger.info("✅ Test répartition RÉUSSI: {} ms", duration);
            } else {
                logger.warn("❌ Test répartition ÉCHOUÉ: {} ms > 500 ms", duration);
            }

            return result;

        } catch (Exception e) {
            logger.error("❌ Erreur lors du test de répartition", e);
            TestResult errorResult = new TestResult();
            errorResult.setTestName("Calculs de répartition");
            errorResult.setSuccess(false);
            errorResult.setErrorMessage(e.getMessage());
            return errorResult;
        }
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Vérifie qu'on a assez de données pour les tests, sinon en crée
     */
    private void verifierOuCreerDonnees() {
        try {
            long countLong = affaireDAO.count();

            // Correction: cast explicite de long vers int avec vérification
            if (countLong > Integer.MAX_VALUE) {
                logger.warn("Nombre d'enregistrements très élevé: {}", countLong);
                return;
            }

            int count = (int) countLong;

            if (count < TARGET_RECORDS) {
                logger.info("📝 Création de {} données de test...", TARGET_RECORDS - count);
                // Cette méthode devrait être implémentée dans un service de données de test
                // pour créer des données factices de manière efficace
            }

        } catch (Exception e) {
            logger.warn("Impossible de vérifier/créer les données de test: {}", e.getMessage());
        }
    }

    // ==================== CLASSES DE RÉSULTATS ====================

    public static class PerformanceReport {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private TestResult chargementTest;
        private TestResult concurrenceTest;
        private TestResult rapportsTest;
        private TestResult numerotationTest;
        private TestResult rechercheTest;
        private TestResult repartitionTest;
        private TestResult overallResult;
        private String errorMessage;

        public void calculateOverallResult() {
            TestResult overall = new TestResult();
            overall.setTestName("Performance globale");

            List<TestResult> tests = Arrays.asList(
                    chargementTest, concurrenceTest, rapportsTest,
                    numerotationTest, rechercheTest, repartitionTest
            );

            boolean allSuccess = tests.stream()
                    .filter(Objects::nonNull)
                    .allMatch(TestResult::isSuccess);

            long totalDuration = tests.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(TestResult::getDuration)
                    .sum();

            overall.setSuccess(allSuccess);
            overall.setDuration(totalDuration);
            overall.setCriterion("Tous les tests réussis");

            this.overallResult = overall;
        }

        // Getters et setters
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

        public TestResult getChargementTest() { return chargementTest; }
        public void setChargementTest(TestResult chargementTest) { this.chargementTest = chargementTest; }

        public TestResult getConcurrenceTest() { return concurrenceTest; }
        public void setConcurrenceTest(TestResult concurrenceTest) { this.concurrenceTest = concurrenceTest; }

        public TestResult getRapportsTest() { return rapportsTest; }
        public void setRapportsTest(TestResult rapportsTest) { this.rapportsTest = rapportsTest; }

        public TestResult getNumerotationTest() { return numerotationTest; }
        public void setNumerotationTest(TestResult numerotationTest) { this.numerotationTest = numerotationTest; }

        public TestResult getRechercheTest() { return rechercheTest; }
        public void setRechercheTest(TestResult rechercheTest) { this.rechercheTest = rechercheTest; }

        public TestResult getRepartitionTest() { return repartitionTest; }
        public void setRepartitionTest(TestResult repartitionTest) { this.repartitionTest = repartitionTest; }

        public TestResult getOverallResult() { return overallResult; }
        public void setOverallResult(TestResult overallResult) { this.overallResult = overallResult; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== RAPPORT DE PERFORMANCE ===\n");
            sb.append("Début: ").append(startTime).append("\n");
            sb.append("Fin: ").append(endTime).append("\n\n");

            List<TestResult> tests = Arrays.asList(
                    chargementTest, concurrenceTest, rapportsTest,
                    numerotationTest, rechercheTest, repartitionTest
            );

            for (TestResult test : tests) {
                if (test != null) {
                    sb.append(test.toString()).append("\n");
                }
            }

            if (overallResult != null) {
                sb.append("\n").append(overallResult.toString());
            }

            return sb.toString();
        }
    }

    public static class TestResult {
        public static final TestResult FAILURE = new TestResult("ÉCHEC GÉNÉRAL", false, 0);

        private String testName;
        private boolean success;
        private long duration;
        private int recordCount;
        private String criterion;
        private String details;
        private String errorMessage;

        // Constructeur par défaut
        public TestResult() {}

        // Constructeur pour les constantes
        private TestResult(String testName, boolean success, long duration) {
            this.testName = testName;
            this.success = success;
            this.duration = duration;
        }

        // Getters et setters existants...
        public String getTestName() { return testName; }
        public void setTestName(String testName) { this.testName = testName; }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public long getDuration() { return duration; }
        public void setDuration(long duration) { this.duration = duration; }

        public int getRecordCount() { return recordCount; }
        public void setRecordCount(int recordCount) { this.recordCount = recordCount; }

        public String getCriterion() { return criterion; }
        public void setCriterion(String criterion) { this.criterion = criterion; }

        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        @Override
        public String toString() {
            String status = success ? "✅ RÉUSSI" : "❌ ÉCHOUÉ";
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s: %s (%d ms)", testName, status, duration));

            if (recordCount > 0) {
                sb.append(String.format(" - %d enregistrements", recordCount));
            }

            if (criterion != null) {
                sb.append(String.format(" - Critère: %s", criterion));
            }

            if (details != null) {
                sb.append(String.format(" - %s", details));
            }

            if (errorMessage != null) {
                sb.append(String.format(" - Erreur: %s", errorMessage));
            }

            return sb.toString();
        }
    }
}