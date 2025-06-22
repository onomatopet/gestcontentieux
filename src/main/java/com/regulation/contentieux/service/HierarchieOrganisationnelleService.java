package com.regulation.contentieux.service;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Service pour valider et gérer la hiérarchie Centre/Service/Bureau
 * Selon le cahier des charges :
 * - Un centre peut contenir plusieurs services ET bureaux
 * - Service et Bureau sont au même niveau hiérarchique
 * - Différence : administrative (Service) vs opérationnelle (Bureau)
 */
public class HierarchieOrganisationnelleService {

    private static final Logger logger = LoggerFactory.getLogger(HierarchieOrganisationnelleService.class);

    /**
     * Valide la cohérence de la hiérarchie complète
     */
    public ValidationResult validerHierarchieComplete() {
        logger.info("🏢 === VALIDATION DE LA HIÉRARCHIE ORGANISATIONNELLE ===");

        ValidationResult result = new ValidationResult();

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            // 1. Vérifier l'intégrité référentielle
            verifierIntegriteReferentielle(conn, result);

            // 2. Vérifier qu'il n'y a pas de hiérarchie incorrecte
            verifierAbsenceHierarchieIncorrecte(conn, result);

            // 3. Vérifier les doublons
            verifierDoublons(conn, result);

            // 4. Vérifier la cohérence des affectations
            verifierCoherenceAffectations(conn, result);

            // 5. Générer le rapport de hiérarchie
            genererRapportHierarchie(conn, result);

        } catch (SQLException e) {
            logger.error("Erreur lors de la validation", e);
            result.addError("Erreur SQL: " + e.getMessage());
        }

        // Résumé
        if (result.isValid()) {
            logger.info("✅ Hiérarchie valide");
        } else {
            logger.error("❌ Hiérarchie invalide: {} erreurs, {} avertissements",
                    result.getErrors().size(), result.getWarnings().size());
        }

        return result;
    }

    /**
     * Vérifie l'intégrité référentielle
     */
    private void verifierIntegriteReferentielle(Connection conn, ValidationResult result) throws SQLException {
        logger.info("🔍 Vérification de l'intégrité référentielle...");

        // Services sans centre
        String sqlServicesOrphelins = """
            SELECT s.* FROM services s
            LEFT JOIN centres c ON s.centre_id = c.id
            WHERE s.centre_id IS NOT NULL AND c.id IS NULL
        """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlServicesOrphelins)) {

            while (rs.next()) {
                String message = String.format("Service orphelin: %s (centre_id=%d inexistant)",
                        rs.getString("nom_service"), rs.getLong("centre_id"));
                result.addError(message);
                logger.error("❌ {}", message);
            }
        }

        // Bureaux sans centre
        String sqlBureauxOrphelins = """
            SELECT b.* FROM bureaux b
            LEFT JOIN centres c ON b.centre_id = c.id
            WHERE b.centre_id IS NOT NULL AND c.id IS NULL
        """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlBureauxOrphelins)) {

            while (rs.next()) {
                String message = String.format("Bureau orphelin: %s (centre_id=%d inexistant)",
                        rs.getString("nom_bureau"), rs.getLong("centre_id"));
                result.addError(message);
                logger.error("❌ {}", message);
            }
        }

        // Agents sans service
        String sqlAgentsOrphelins = """
            SELECT a.* FROM agents a
            LEFT JOIN services s ON a.service_id = s.id
            WHERE a.service_id IS NOT NULL AND s.id IS NULL
        """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlAgentsOrphelins)) {

            int count = 0;
            while (rs.next()) {
                count++;
            }

            if (count > 0) {
                String message = String.format("%d agents avec service_id invalide", count);
                result.addWarning(message);
                logger.warn("⚠️ {}", message);
            }
        }
    }

    /**
     * Vérifie qu'il n'y a pas de hiérarchie incorrecte
     * (ex: un service qui aurait un bureau comme parent, ce qui est impossible)
     */
    private void verifierAbsenceHierarchieIncorrecte(Connection conn, ValidationResult result) throws SQLException {
        logger.info("🔍 Vérification de l'absence de hiérarchie incorrecte...");

        // Dans notre modèle, Services et Bureaux sont au même niveau sous Centre
        // Il ne devrait pas y avoir de relation directe entre Service et Bureau

        // Vérifier que les services n'ont que des centres comme parents
        String sql = """
            SELECT COUNT(*) as count FROM services 
            WHERE centre_id IS NULL AND actif = 1
        """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next() && rs.getInt("count") > 0) {
                String message = rs.getInt("count") + " services actifs sans centre";
                result.addWarning(message);
                logger.warn("⚠️ {}", message);
            }
        }

        logger.info("✅ Pas de hiérarchie incorrecte détectée");
    }

    /**
     * Vérifie les doublons de codes
     */
    private void verifierDoublons(Connection conn, ValidationResult result) throws SQLException {
        logger.info("🔍 Vérification des doublons...");

        // Doublons de codes centres
        verifierDoublonsTable(conn, "centres", "code_centre", result);

        // Doublons de codes services
        verifierDoublonsTable(conn, "services", "code_service", result);

        // Doublons de codes bureaux
        verifierDoublonsTable(conn, "bureaux", "code_bureau", result);
    }

    private void verifierDoublonsTable(Connection conn, String table, String codeColumn,
                                       ValidationResult result) throws SQLException {
        String sql = String.format("""
            SELECT %s, COUNT(*) as count 
            FROM %s 
            GROUP BY %s 
            HAVING COUNT(*) > 1
        """, codeColumn, table, codeColumn);

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String message = String.format("Code en double dans %s: %s (%d occurrences)",
                        table, rs.getString(codeColumn), rs.getInt("count"));
                result.addError(message);
                logger.error("❌ {}", message);
            }
        }
    }

    /**
     * Vérifie la cohérence des affectations
     */
    private void verifierCoherenceAffectations(Connection conn, ValidationResult result) throws SQLException {
        logger.info("🔍 Vérification de la cohérence des affectations...");

        // Affaires avec bureau ET service du même centre ?
        String sql = """
            SELECT a.numero_affaire, 
                   s.nom_service, s.centre_id as service_centre,
                   b.nom_bureau, b.centre_id as bureau_centre
            FROM affaires a
            LEFT JOIN services s ON a.service_id = s.id
            LEFT JOIN bureaux b ON a.bureau_id = b.id
            WHERE a.service_id IS NOT NULL 
              AND a.bureau_id IS NOT NULL
              AND s.centre_id != b.centre_id
        """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String message = String.format(
                        "Affaire %s: Service et Bureau de centres différents (%s != %s)",
                        rs.getString("numero_affaire"),
                        rs.getString("service_centre"),
                        rs.getString("bureau_centre")
                );
                result.addError(message);
                logger.error("❌ {}", message);
            }
        }
    }

    /**
     * Génère un rapport de la hiérarchie
     */
    private void genererRapportHierarchie(Connection conn, ValidationResult result) throws SQLException {
        logger.info("📊 Génération du rapport de hiérarchie...");

        StringBuilder rapport = new StringBuilder();
        rapport.append("\n=== STRUCTURE HIÉRARCHIQUE ===\n");

        // Récupérer tous les centres
        String sqlCentres = "SELECT * FROM centres WHERE actif = 1 ORDER BY code_centre";

        try (Statement stmt = conn.createStatement();
             ResultSet rsCentres = stmt.executeQuery(sqlCentres)) {

            while (rsCentres.next()) {
                Long centreId = rsCentres.getLong("id");
                String codeCentre = rsCentres.getString("code_centre");
                String nomCentre = rsCentres.getString("nom_centre");

                rapport.append(String.format("\n📍 CENTRE: %s - %s\n", codeCentre, nomCentre));

                // Services du centre
                rapport.append("   📋 Services:\n");
                String sqlServices = "SELECT * FROM services WHERE centre_id = ? AND actif = 1";
                try (PreparedStatement pstmt = conn.prepareStatement(sqlServices)) {
                    pstmt.setLong(1, centreId);
                    ResultSet rsServices = pstmt.executeQuery();

                    int serviceCount = 0;
                    while (rsServices.next()) {
                        rapport.append(String.format("      - %s: %s\n",
                                rsServices.getString("code_service"),
                                rsServices.getString("nom_service")));
                        serviceCount++;
                    }

                    if (serviceCount == 0) {
                        rapport.append("      (Aucun service)\n");
                    }
                }

                // Bureaux du centre
                rapport.append("   🏢 Bureaux:\n");
                String sqlBureaux = "SELECT * FROM bureaux WHERE centre_id = ? AND actif = 1";
                try (PreparedStatement pstmt = conn.prepareStatement(sqlBureaux)) {
                    pstmt.setLong(1, centreId);
                    ResultSet rsBureaux = pstmt.executeQuery();

                    int bureauCount = 0;
                    while (rsBureaux.next()) {
                        rapport.append(String.format("      - %s: %s\n",
                                rsBureaux.getString("code_bureau"),
                                rsBureaux.getString("nom_bureau")));
                        bureauCount++;
                    }

                    if (bureauCount == 0) {
                        rapport.append("      (Aucun bureau)\n");
                    }
                }
            }
        }

        result.setRapportHierarchie(rapport.toString());
        logger.info(rapport.toString());
    }

    /**
     * Classe pour encapsuler les résultats de validation
     */
    public static class ValidationResult {
        private boolean valid = true;
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private String rapportHierarchie;

        public void addError(String error) {
            this.errors.add(error);
            this.valid = false;
        }

        public void addWarning(String warning) {
            this.warnings.add(warning);
        }

        // Getters et setters
        public boolean isValid() { return valid && errors.isEmpty(); }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public String getRapportHierarchie() { return rapportHierarchie; }
        public void setRapportHierarchie(String rapport) { this.rapportHierarchie = rapport; }
    }

    /**
     * Crée un nouveau centre
     */
    public Centre creerCentre(String code, String nom, String description) {
        logger.info("Création du centre: {} - {}", code, nom);

        Centre centre = new Centre();
        centre.setCodeCentre(code);
        centre.setNomCentre(nom);
        centre.setDescription(description);
        centre.setActif(true);

        // TODO: Sauvegarder en base via CentreDAO

        return centre;
    }

    /**
     * Crée un nouveau service dans un centre
     */
    public Service creerService(String code, String nom, Long centreId) {
        logger.info("Création du service: {} - {} dans centre {}", code, nom, centreId);

        Service service = new Service();
        service.setCodeService(code);
        service.setNomService(nom);
        service.setCentreId(centreId);
        service.setActif(true);

        // TODO: Sauvegarder en base via ServiceDAO

        return service;
    }

    /**
     * Crée un nouveau bureau dans un centre
     */
    public Bureau creerBureau(String code, String nom, Long centreId) {
        logger.info("Création du bureau: {} - {} dans centre {}", code, nom, centreId);

        Bureau bureau = new Bureau();
        bureau.setCodeBureau(code);
        bureau.setNomBureau(nom);
        bureau.setCentreId(centreId);
        bureau.setActif(true);

        // TODO: Sauvegarder en base via BureauDAO

        return bureau;
    }
}