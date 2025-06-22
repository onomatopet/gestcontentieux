package com.regulation.contentieux.service;

import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Service pour la gestion des mandats selon le cahier des charges
 * Format: YYMM0001 (ex: 2506M0001)
 * Un seul mandat actif à la fois
 * ENRICHISSEMENT du service existant
 */
public class MandatService {

    private static final Logger logger = LoggerFactory.getLogger(MandatService.class);

    // ENRICHISSEMENT : Instance unique pour gérer le mandat actif
    private static MandatService instance;
    private String mandatActif;

    private MandatService() {
        // ENRICHISSEMENT : Charger le mandat actif au démarrage
        chargerMandatActif();
    }

    public static synchronized MandatService getInstance() {
        if (instance == null) {
            instance = new MandatService();
        }
        return instance;
    }

    /**
     * Génère un nouveau numéro de mandat selon le format YYMM0001
     * ENRICHISSEMENT : Respect strict du cahier des charges
     */
    public String genererNouveauMandat() {
        logger.info("🔍 === GÉNÉRATION NOUVEAU MANDAT ===");
        logger.info("🔍 Format cahier des charges: YYMM0001");

        LocalDate now = LocalDate.now();
        String yearMonth = now.format(DateTimeFormatter.ofPattern("yyMM"));

        // ENRICHISSEMENT : Le cahier mentionne aussi le format avec 'M' (ex: 2506M0001)
        // Vérifier quelle variante est utilisée
        String formatStandard = yearMonth;
        String formatAvecM = yearMonth + "M";

        logger.debug("🔍 Recherche de mandats pour période: {}", yearMonth);

        // Rechercher le dernier mandat pour ce mois
        String sql = """
            SELECT numero_mandat FROM mandats
            WHERE (numero_mandat LIKE ? OR numero_mandat LIKE ?)
            ORDER BY numero_mandat DESC
            LIMIT 1
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, formatStandard + "%");
            stmt.setString(2, formatAvecM + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String dernierMandat = rs.getString("numero_mandat");
                logger.debug("🔍 Dernier mandat trouvé: {}", dernierMandat);

                return genererSuivant(dernierMandat, yearMonth);
            } else {
                // Premier mandat du mois
                String nouveauMandat = determinerFormatMandat(yearMonth) + "0001";
                logger.info("🆕 Premier mandat du mois: {}", nouveauMandat);
                return nouveauMandat;
            }

        } catch (SQLException e) {
            logger.error("❌ Erreur lors de la génération du mandat", e);
            // Fallback
            return yearMonth + "M0001";
        }
    }

    /**
     * ENRICHISSEMENT : Détermine le format de mandat à utiliser
     */
    private String determinerFormatMandat(String yearMonth) {
        // Vérifier s'il existe des mandats avec le format 'M'
        String sql = "SELECT COUNT(*) FROM mandats WHERE numero_mandat LIKE ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%M%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next() && rs.getInt(1) > 0) {
                logger.info("✅ Format avec 'M' détecté (ex: 2506M0001)");
                return yearMonth + "M";
            } else {
                logger.info("✅ Format standard détecté (ex: 25060001)");
                return yearMonth;
            }

        } catch (Exception e) {
            logger.warn("⚠️ Utilisation du format avec 'M' par défaut");
            return yearMonth + "M";
        }
    }

    /**
     * ENRICHISSEMENT : Génère le mandat suivant
     */
    private String genererSuivant(String dernierMandat, String yearMonth) {
        try {
            // Extraire le numéro séquentiel
            String numeroStr = "";
            String prefix = "";

            if (dernierMandat.contains("M")) {
                // Format avec M
                int indexM = dernierMandat.indexOf("M");
                prefix = dernierMandat.substring(0, indexM + 1);
                numeroStr = dernierMandat.substring(indexM + 1);
            } else if (dernierMandat.length() == 8) {
                // Format YYMM0001
                prefix = dernierMandat.substring(0, 4);
                numeroStr = dernierMandat.substring(4);
            }

            int numero = Integer.parseInt(numeroStr);

            // Vérifier si on est toujours dans le même mois
            if (prefix.startsWith(yearMonth)) {
                String nouveauMandat = prefix + String.format("%04d", numero + 1);
                logger.info("✅ Mandat suivant dans la séquence: {}", nouveauMandat);

                // ENRICHISSEMENT : Avertissement si plusieurs mandats dans le mois
                if (numero >= 1) {
                    logger.warn("⚠️ ATTENTION: Plusieurs mandats dans le même mois (rare selon cahier des charges)");
                }

                return nouveauMandat;
            } else {
                // Nouveau mois
                String nouveauMandat = determinerFormatMandat(yearMonth) + "0001";
                logger.info("🔄 Nouveau mois - Premier mandat: {}", nouveauMandat);
                return nouveauMandat;
            }

        } catch (Exception e) {
            logger.error("Erreur dans genererSuivant", e);
            return yearMonth + "M0001";
        }
    }

    /**
     * Active un mandat (un seul mandat actif à la fois)
     * ENRICHISSEMENT : Respect de la contrainte du cahier des charges
     */
    public boolean activerMandat(String numeroMandat) {
        logger.info("🔄 === ACTIVATION MANDAT {} ===", numeroMandat);

        // Vérifier que le mandat existe
        if (!mandatExiste(numeroMandat)) {
            logger.error("❌ Le mandat {} n'existe pas", numeroMandat);
            return false;
        }

        String sql = """
            UPDATE mandats 
            SET actif = CASE 
                WHEN numero_mandat = ? THEN 1 
                ELSE 0 
            END
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, numeroMandat);
            int affected = stmt.executeUpdate();

            if (affected > 0) {
                String ancienMandat = this.mandatActif;
                this.mandatActif = numeroMandat;

                logger.info("✅ Mandat {} activé avec succès", numeroMandat);
                if (ancienMandat != null && !ancienMandat.equals(numeroMandat)) {
                    logger.info("📋 Ancien mandat {} désactivé", ancienMandat);
                }

                // ENRICHISSEMENT : Vérifier la cohérence
                verifierCoherenceMandat(numeroMandat);

                return true;
            }

        } catch (SQLException e) {
            logger.error("❌ Erreur lors de l'activation du mandat", e);
        }

        return false;
    }

    /**
     * ENRICHISSEMENT : Vérifie la cohérence d'un mandat
     */
    private void verifierCoherenceMandat(String numeroMandat) {
        logger.debug("🔍 Vérification de cohérence pour mandat {}", numeroMandat);

        try {
            // Extraire le mois du mandat
            String moisMandat = numeroMandat.substring(0, 4); // YYMM

            // Vérifier les affaires de ce mandat
            String sql = """
                SELECT COUNT(*) as total,
                       MIN(date_creation) as premiere_date,
                       MAX(date_creation) as derniere_date
                FROM affaires
                WHERE numero_mandat = ?
            """;

            try (Connection conn = DatabaseConfig.getSQLiteConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, numeroMandat);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    int total = rs.getInt("total");
                    Date premiereDate = rs.getDate("premiere_date");
                    Date derniereDate = rs.getDate("derniere_date");

                    logger.info("📊 Mandat {} : {} affaires", numeroMandat, total);

                    if (premiereDate != null && derniereDate != null) {
                        LocalDate debut = premiereDate.toLocalDate();
                        LocalDate fin = derniereDate.toLocalDate();

                        // Vérifier que toutes les dates sont dans le même mois
                        String moisDebut = debut.format(DateTimeFormatter.ofPattern("yyMM"));
                        String moisFin = fin.format(DateTimeFormatter.ofPattern("yyMM"));

                        if (!moisDebut.equals(moisMandat) || !moisFin.equals(moisMandat)) {
                            logger.warn("⚠️ ATTENTION: Des affaires dépassent le mois du mandat!");
                            logger.warn("⚠️ Mandat: {}, Première affaire: {}, Dernière affaire: {}",
                                    moisMandat, debut, fin);
                        } else {
                            logger.info("✅ Toutes les affaires sont dans le mois du mandat");
                        }
                    }
                }

            } catch (Exception e) {
                logger.error("Erreur lors de la vérification de cohérence", e);
            }

        } catch (Exception e) {
            logger.error("Erreur dans l'extraction du mois du mandat", e);
        }
    }

    /**
     * Récupère le mandat actif
     */
    public String getMandatActif() {
        if (mandatActif == null) {
            chargerMandatActif();
        }
        return mandatActif;
    }

    /**
     * ENRICHISSEMENT : Charge le mandat actif depuis la base
     */
    private void chargerMandatActif() {
        String sql = "SELECT numero_mandat FROM mandats WHERE actif = 1 LIMIT 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                this.mandatActif = rs.getString("numero_mandat");
                logger.info("✅ Mandat actif chargé: {}", this.mandatActif);
            } else {
                logger.warn("⚠️ Aucun mandat actif trouvé");
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du chargement du mandat actif", e);
        }
    }

    /**
     * Vérifie si un mandat existe
     */
    private boolean mandatExiste(String numeroMandat) {
        String sql = "SELECT 1 FROM mandats WHERE numero_mandat = ? LIMIT 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, numeroMandat);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification d'existence du mandat", e);
            return false;
        }
    }

    /**
     * ENRICHISSEMENT : Crée la table mandats si elle n'existe pas
     */
    public static void creerTableMandatsSiNecessaire() {
        String sql = """
            CREATE TABLE IF NOT EXISTS mandats (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                numero_mandat TEXT NOT NULL UNIQUE,
                date_debut DATE NOT NULL,
                date_fin DATE NOT NULL,
                actif INTEGER NOT NULL DEFAULT 0,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                created_by TEXT,
                CONSTRAINT un_seul_actif CHECK (
                    (SELECT COUNT(*) FROM mandats WHERE actif = 1) <= 1
                )
            )
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);
            logger.info("✅ Table mandats vérifiée/créée");

        } catch (SQLException e) {
            logger.error("Erreur lors de la création de la table mandats", e);
        }
    }
}