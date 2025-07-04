package com.regulation.contentieux.service;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service de numérotation automatique centralisé
 * ENRICHISSEMENT COMPLET selon le cahier des charges
 * Format affaires: YYMMNNNNN (ex: 250600001)
 * Format encaissements: YYMMRNNNNN (ex: 2506R00001)
 * Format mandats: YYMMM0001 (ex: 2506M0001)
 *
 * Garantit l'unicité et la séquence continue sans gaps
 */
public class NumerotationService {

    private static final Logger logger = LoggerFactory.getLogger(NumerotationService.class);

    // Instance unique
    private static NumerotationService instance;

    // Verrous pour éviter les doublons en cas de concurrence
    private final ReentrantLock affaireLock = new ReentrantLock();
    private final ReentrantLock encaissementLock = new ReentrantLock();
    private final ReentrantLock mandatLock = new ReentrantLock();

    // Formats selon le cahier des charges
    private static final String AFFAIRE_FORMAT = "yyMM";
    private static final String ENCAISSEMENT_FORMAT = "yyMM'R'";
    private static final String MANDAT_FORMAT = "yyMM'M'";

    private NumerotationService() {}

    public static synchronized NumerotationService getInstance() {
        if (instance == null) {
            instance = new NumerotationService();
        }
        return instance;
    }

    // ==================== NUMÉROTATION DES AFFAIRES ====================

    /**
     * Génère le prochain numéro d'affaire selon le format YYMMNNNNN
     * Remise à zéro mensuelle garantie
     * Thread-safe avec verrou
     */
    public String genererNumeroAffaire() {
        affaireLock.lock();
        try {
            LocalDate now = LocalDate.now();
            String yearMonth = now.format(DateTimeFormatter.ofPattern("yyMM"));

            logger.debug("🔢 Génération numéro affaire pour période: {}", yearMonth);

            // Rechercher le dernier numéro du mois en cours
            String sql = """
                SELECT numero_affaire 
                FROM affaires 
                WHERE numero_affaire LIKE ? 
                ORDER BY numero_affaire DESC 
                LIMIT 1
            """;

            try (Connection conn = DatabaseConfig.getSQLiteConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, yearMonth + "%");
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String lastNumero = rs.getString("numero_affaire");
                    return genererProchainNumeroAffaire(lastNumero, yearMonth);
                } else {
                    // Premier numéro du mois
                    String nouveauNumero = yearMonth + "00001";
                    logger.info("🆕 Premier numéro d'affaire du mois: {}", nouveauNumero);
                    return nouveauNumero;
                }

            } catch (SQLException e) {
                logger.error("Erreur lors de la génération du numéro d'affaire", e);
                throw new BusinessException("Impossible de générer le numéro d'affaire", e);
            }

        } finally {
            affaireLock.unlock();
        }
    }

    /**
     * Génère le prochain numéro d'affaire
     */
    private String genererProchainNumeroAffaire(String dernierNumero, String yearMonth) {
        try {
            if (dernierNumero.startsWith(yearMonth)) {
                // Même mois : incrémenter
                String numeroPart = dernierNumero.substring(4);
                int numero = Integer.parseInt(numeroPart);

                if (numero >= 99999) {
                    throw new BusinessException("Limite mensuelle d'affaires atteinte (99999)");
                }

                String nouveauNumero = yearMonth + String.format("%05d", numero + 1);
                logger.info("📈 Numéro d'affaire incrémenté: {} -> {}", dernierNumero, nouveauNumero);
                return nouveauNumero;

            } else {
                // Nouveau mois - réinitialiser
                String nouveauNumero = yearMonth + "00001";
                logger.info("🔄 Nouveau mois - Numéro d'affaire réinitialisé: {}", nouveauNumero);
                return nouveauNumero;
            }

        } catch (NumberFormatException e) {
            logger.warn("⚠️ Format invalide pour: {}, génération nouveau: {}", dernierNumero, yearMonth + "00001");
            return yearMonth + "00001";
        }
    }

    // ==================== NUMÉROTATION DES ENCAISSEMENTS ====================

    /**
     * Génère le prochain numéro d'encaissement selon le format YYMMRNNNNN
     * Remise à zéro mensuelle garantie
     * Thread-safe avec verrou
     */
    public String genererNumeroEncaissement() {
        encaissementLock.lock();
        try {
            LocalDate now = LocalDate.now();
            String yearMonth = now.format(DateTimeFormatter.ofPattern("yyMM"));
            String prefixe = yearMonth + "R";

            logger.debug("🔢 Génération numéro encaissement pour période: {}", prefixe);

            // CORRECTION : Utiliser numero_encaissement au lieu de reference
            String sql = """
                SELECT numero_encaissement 
                FROM encaissements 
                WHERE numero_encaissement LIKE ? 
                ORDER BY numero_encaissement DESC 
                LIMIT 1
            """;

            try (Connection conn = DatabaseConfig.getSQLiteConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, prefixe + "%");
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String lastReference = rs.getString("numero_encaissement");
                    return genererProchainNumeroEncaissement(lastReference, prefixe);
                } else {
                    // Premier encaissement du mois
                    String nouveauNumero = prefixe + "00001";
                    logger.info("🆕 Premier numéro d'encaissement du mois: {}", nouveauNumero);
                    return nouveauNumero;
                }

            } catch (SQLException e) {
                logger.error("Erreur lors de la génération du numéro d'encaissement", e);
                throw new BusinessException("Impossible de générer le numéro d'encaissement", e);
            }

        } finally {
            encaissementLock.unlock();
        }
    }

    /**
     * Génère le prochain numéro d'encaissement
     */
    private String genererProchainNumeroEncaissement(String dernierNumero, String prefixe) {
        try {
            if (dernierNumero.startsWith(prefixe)) {
                // Même mois : incrémenter
                String numeroPart = dernierNumero.substring(5); // YYMMR + 5 chiffres
                int numero = Integer.parseInt(numeroPart);

                if (numero >= 99999) {
                    throw new BusinessException("Limite mensuelle d'encaissements atteinte (99999)");
                }

                String nouveauNumero = prefixe + String.format("%05d", numero + 1);
                logger.info("📈 Numéro d'encaissement incrémenté: {} -> {}", dernierNumero, nouveauNumero);
                return nouveauNumero;

            } else {
                // Nouveau mois
                String nouveauNumero = prefixe + "00001";
                logger.info("🔄 Nouveau mois - Numéro d'encaissement réinitialisé: {}", nouveauNumero);
                return nouveauNumero;
            }

        } catch (NumberFormatException e) {
            logger.warn("⚠️ Format invalide pour: {}, génération nouveau: {}", dernierNumero, prefixe + "00001");
            return prefixe + "00001";
        }
    }

    // ==================== NUMÉROTATION DES MANDATS ====================

    /**
     * Génère le prochain numéro de mandat selon le format YYMMM0001
     * Thread-safe avec verrou
     */
    public String genererNumeroMandat() {
        mandatLock.lock();
        try {
            LocalDate now = LocalDate.now();
            String yearMonth = now.format(DateTimeFormatter.ofPattern("yyMM"));
            String prefixe = yearMonth + "M";

            logger.debug("🔢 Génération numéro mandat pour période: {}", prefixe);

            String sql = """
                SELECT numero_mandat 
                FROM mandats 
                WHERE numero_mandat LIKE ? 
                ORDER BY numero_mandat DESC 
                LIMIT 1
            """;

            try (Connection conn = DatabaseConfig.getSQLiteConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, prefixe + "%");
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String lastNumero = rs.getString("numero_mandat");
                    return genererProchainNumeroMandat(lastNumero, prefixe);
                } else {
                    // Premier mandat du mois
                    String nouveauNumero = prefixe + "0001";
                    logger.info("🆕 Premier numéro de mandat du mois: {}", nouveauNumero);
                    return nouveauNumero;
                }

            } catch (SQLException e) {
                logger.error("Erreur lors de la génération du numéro de mandat", e);
                throw new BusinessException("Impossible de générer le numéro de mandat", e);
            }

        } finally {
            mandatLock.unlock();
        }
    }

    /**
     * Génère le prochain numéro de mandat
     */
    private String genererProchainNumeroMandat(String dernierNumero, String prefixe) {
        try {
            if (dernierNumero.startsWith(prefixe)) {
                // Même mois : incrémenter
                String numeroPart = dernierNumero.substring(5); // YYMMM + 4 chiffres
                int numero = Integer.parseInt(numeroPart);

                if (numero >= 9999) {
                    throw new BusinessException("Limite mensuelle de mandats atteinte (9999)");
                }

                String nouveauNumero = prefixe + String.format("%04d", numero + 1);
                logger.info("📈 Numéro de mandat incrémenté: {} -> {}", dernierNumero, nouveauNumero);
                return nouveauNumero;

            } else {
                // Nouveau mois
                String nouveauNumero = prefixe + "0001";
                logger.info("🔄 Nouveau mois - Numéro de mandat réinitialisé: {}", nouveauNumero);
                return nouveauNumero;
            }

        } catch (NumberFormatException e) {
            logger.warn("⚠️ Format invalide pour: {}, génération nouveau: {}", dernierNumero, prefixe + "0001");
            return prefixe + "0001";
        }
    }

    // ==================== MÉTHODES DE DIAGNOSTIC ====================

    /**
     * Vérifie la cohérence des séquences de numérotation
     * Utile pour diagnostiquer les problèmes
     */
    public void verifierCoherenceGlobale() {
        logger.info("🔍 === VÉRIFICATION COHÉRENCE NUMÉROTATION ===");
        verifierSequenceAffaires();
        verifierSequenceEncaissements();
        verifierSequenceMandats();
        logger.info("🔍 === FIN VÉRIFICATION ===");
    }

    /**
     * Vérifie la cohérence des numéros d'affaires
     */
    private void verifierSequenceAffaires() {
        logger.debug("Vérification séquence affaires...");

        String sql = """
            SELECT numero_affaire, date_creation 
            FROM affaires 
            WHERE numero_affaire REGEXP '^[0-9]{9}$' 
            ORDER BY numero_affaire
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            String lastYearMonth = "";
            int lastSequence = 0;
            int anomalies = 0;

            while (rs.next()) {
                String numero = rs.getString("numero_affaire");
                String yearMonth = numero.substring(0, 4);
                int sequence = Integer.parseInt(numero.substring(4));

                if (!yearMonth.equals(lastYearMonth)) {
                    // Nouveau mois
                    lastYearMonth = yearMonth;
                    lastSequence = 0;
                }

                // Vérifier la séquence
                if (sequence != lastSequence + 1) {
                    logger.warn("⚠️ Gap détecté dans les affaires: {} (attendu: {}{})",
                            numero, yearMonth, String.format("%05d", lastSequence + 1));
                    anomalies++;
                }

                lastSequence = sequence;
            }

            if (anomalies > 0) {
                logger.warn("⚠️ {} anomalies détectées dans la séquence des affaires", anomalies);
            } else {
                logger.info("✅ Séquence des affaires cohérente");
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification des affaires", e);
        }
    }

    /**
     * Vérifie la cohérence des numéros d'encaissements
     */
    private void verifierSequenceEncaissements() {
        logger.debug("Vérification séquence encaissements...");

        // CORRECTION : Utiliser numero_encaissement au lieu de reference
        String sql = """
            SELECT numero_encaissement, date_encaissement 
            FROM encaissements 
            WHERE numero_encaissement REGEXP '^[0-9]{4}R[0-9]{5}$' 
            ORDER BY numero_encaissement
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            String lastPrefix = "";
            int lastSequence = 0;
            int anomalies = 0;

            while (rs.next()) {
                String reference = rs.getString("numero_encaissement");
                String prefix = reference.substring(0, 5); // YYMMR
                int sequence = Integer.parseInt(reference.substring(5));

                if (!prefix.equals(lastPrefix)) {
                    // Nouveau mois
                    lastPrefix = prefix;
                    lastSequence = 0;
                }

                // Vérifier la séquence
                if (sequence != lastSequence + 1) {
                    logger.warn("⚠️ Gap détecté dans les encaissements: {} (attendu: {}{})",
                            reference, prefix, String.format("%05d", lastSequence + 1));
                    anomalies++;
                }

                lastSequence = sequence;
            }

            if (anomalies > 0) {
                logger.warn("⚠️ {} anomalies détectées dans la séquence des encaissements", anomalies);
            } else {
                logger.info("✅ Séquence des encaissements cohérente");
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification des encaissements", e);
        }
    }

    /**
     * Vérifie la cohérence des numéros de mandats
     */
    private void verifierSequenceMandats() {
        logger.debug("Vérification séquence mandats...");

        String sql = """
            SELECT numero_mandat, date_creation 
            FROM mandats 
            WHERE numero_mandat REGEXP '^[0-9]{4}M[0-9]{4}$' 
            ORDER BY numero_mandat
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            String lastPrefix = "";
            int lastSequence = 0;
            int anomalies = 0;

            while (rs.next()) {
                String numero = rs.getString("numero_mandat");
                String prefix = numero.substring(0, 5); // YYMMM
                int sequence = Integer.parseInt(numero.substring(5));

                if (!prefix.equals(lastPrefix)) {
                    // Nouveau mois
                    lastPrefix = prefix;
                    lastSequence = 0;
                }

                // Vérifier la séquence
                if (sequence != lastSequence + 1) {
                    logger.warn("⚠️ Gap détecté dans les mandats: {} (attendu: {}{})",
                            numero, prefix, String.format("%04d", lastSequence + 1));
                    anomalies++;
                }

                lastSequence = sequence;
            }

            if (anomalies > 0) {
                logger.warn("⚠️ {} anomalies détectées dans la séquence des mandats", anomalies);
            } else {
                logger.info("✅ Séquence des mandats cohérente");
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification des mandats", e);
        }
    }

    // ==================== RAPPORTS ET STATISTIQUES ====================

    /**
     * Génère un rapport sur l'état des séquences
     */
    public SequenceReport genererRapportSequences() {
        SequenceReport report = new SequenceReport();
        report.setGeneratedAt(LocalDate.now());

        report.setAffairesStats(calculerStatsAffaires());
        report.setEncaissementsStats(calculerStatsEncaissements());
        report.setMandatsStats(calculerStatsMandats());

        return report;
    }

    private SequenceStats calculerStatsAffaires() {
        SequenceStats stats = new SequenceStats("Affaires");

        String sql = """
            SELECT 
                COUNT(*) as total,
                MIN(numero_affaire) as premier_numero,
                MAX(numero_affaire) as dernier_numero,
                COUNT(DISTINCT SUBSTR(numero_affaire, 1, 4)) as mois_differents
            FROM affaires 
            WHERE numero_affaire REGEXP '^[0-9]{9}$'
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                stats.setTotal(rs.getInt("total"));
                stats.setPremierNumero(rs.getString("premier_numero"));
                stats.setDernierNumero(rs.getString("dernier_numero"));
                stats.setMoisDifferents(rs.getInt("mois_differents"));
            }

        } catch (SQLException e) {
            logger.error("Erreur calcul stats affaires", e);
        }

        return stats;
    }

    private SequenceStats calculerStatsEncaissements() {
        SequenceStats stats = new SequenceStats("Encaissements");

        // CORRECTION : Utiliser numero_encaissement au lieu de reference
        String sql = """
            SELECT 
                COUNT(*) as total,
                MIN(numero_encaissement) as premier_numero,
                MAX(numero_encaissement) as dernier_numero,
                COUNT(DISTINCT SUBSTR(numero_encaissement, 1, 5)) as mois_differents
            FROM encaissements 
            WHERE numero_encaissement REGEXP '^[0-9]{4}R[0-9]{5}$'
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                stats.setTotal(rs.getInt("total"));
                stats.setPremierNumero(rs.getString("premier_numero"));
                stats.setDernierNumero(rs.getString("dernier_numero"));
                stats.setMoisDifferents(rs.getInt("mois_differents"));
            }

        } catch (SQLException e) {
            logger.error("Erreur calcul stats encaissements", e);
        }

        return stats;
    }

    private SequenceStats calculerStatsMandats() {
        SequenceStats stats = new SequenceStats("Mandats");

        String sql = """
            SELECT 
                COUNT(*) as total,
                MIN(numero_mandat) as premier_numero,
                MAX(numero_mandat) as dernier_numero,
                COUNT(DISTINCT SUBSTR(numero_mandat, 1, 5)) as mois_differents
            FROM mandats 
            WHERE numero_mandat REGEXP '^[0-9]{4}M[0-9]{4}$'
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                stats.setTotal(rs.getInt("total"));
                stats.setPremierNumero(rs.getString("premier_numero"));
                stats.setDernierNumero(rs.getString("dernier_numero"));
                stats.setMoisDifferents(rs.getInt("mois_differents"));
            }

        } catch (SQLException e) {
            logger.error("Erreur calcul stats mandats", e);
        }

        return stats;
    }

    // ==================== CLASSES INTERNES ====================

    public static class SequenceReport {
        private LocalDate generatedAt;
        private SequenceStats affairesStats;
        private SequenceStats encaissementsStats;
        private SequenceStats mandatsStats;

        // Getters et setters
        public LocalDate getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(LocalDate generatedAt) { this.generatedAt = generatedAt; }

        public SequenceStats getAffairesStats() { return affairesStats; }
        public void setAffairesStats(SequenceStats affairesStats) { this.affairesStats = affairesStats; }

        public SequenceStats getEncaissementsStats() { return encaissementsStats; }
        public void setEncaissementsStats(SequenceStats encaissementsStats) { this.encaissementsStats = encaissementsStats; }

        public SequenceStats getMandatsStats() { return mandatsStats; }
        public void setMandatsStats(SequenceStats mandatsStats) { this.mandatsStats = mandatsStats; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== RAPPORT DES SÉQUENCES DE NUMÉROTATION ===\n");
            sb.append("Généré le: ").append(generatedAt).append("\n\n");

            if (affairesStats != null) {
                sb.append(affairesStats.toString()).append("\n");
            }
            if (encaissementsStats != null) {
                sb.append(encaissementsStats.toString()).append("\n");
            }
            if (mandatsStats != null) {
                sb.append(mandatsStats.toString()).append("\n");
            }

            return sb.toString();
        }
    }

    public static class SequenceStats {
        private String type;
        private int total;
        private String premierNumero;
        private String dernierNumero;
        private int moisDifferents;

        public SequenceStats(String type) {
            this.type = type;
        }

        // Getters et setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }

        public String getPremierNumero() { return premierNumero; }
        public void setPremierNumero(String premierNumero) { this.premierNumero = premierNumero; }

        public String getDernierNumero() { return dernierNumero; }
        public void setDernierNumero(String dernierNumero) { this.dernierNumero = dernierNumero; }

        public int getMoisDifferents() { return moisDifferents; }
        public void setMoisDifferents(int moisDifferents) { this.moisDifferents = moisDifferents; }

        @Override
        public String toString() {
            return String.format("""
                %s:
                  Total: %d enregistrements
                  Premier: %s
                  Dernier: %s
                  Mois différents: %d
                """, type, total, premierNumero, dernierNumero, moisDifferents);
        }
    }
}