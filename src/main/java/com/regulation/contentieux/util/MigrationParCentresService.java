package com.regulation.contentieux.util;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.dao.CentreDAO;
import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.dao.AffaireCentreDAO;
import com.regulation.contentieux.model.Centre;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.AffaireCentre;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*; // <-- IMPORT MANQUANT pour Connection, Statement, PreparedStatement, ResultSet
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Scanner;

/**
 * Service de migration des données depuis l'ancienne table parcentres
 */
public class MigrationParCentresService {

    private static final Logger logger = LoggerFactory.getLogger(MigrationParCentresService.class);

    // AJOUTER LES DÉCLARATIONS DES DAOs
    private final CentreDAO centreDAO;
    private final AffaireDAO affaireDAO;
    private final AffaireCentreDAO affaireCentreDAO;

    // CONSTRUCTEUR
    public MigrationParCentresService() {
        this.centreDAO = new CentreDAO();
        this.affaireDAO = new AffaireDAO();
        this.affaireCentreDAO = new AffaireCentreDAO();
    }

    public void migrerDonneesParCentres() {
        logger.info("🔄 Début migration des données parcentres...");

        int compteurMigres = 0;
        int compteurErreurs = 0;
        int compteurCentresCrees = 0;

        try (Connection mysqlConn = DatabaseConfig.getMySQLConnection();
             Connection sqliteConn = DatabaseConfig.getSQLiteConnection()) {

            // 1. Lire toutes les données de parcentres
            String sqlSelect = """
                SELECT CODCENTR, NOTRANSAC, MONTBASE, MONTINDIC, 
                       DATMANDAT, NOMANDAT
                FROM parcentres
                WHERE CODCENTR IS NOT NULL 
                AND NOTRANSAC IS NOT NULL
            """;

            try (PreparedStatement stmtSelect = mysqlConn.prepareStatement(sqlSelect);
                 ResultSet rs = stmtSelect.executeQuery()) {

                // Préparer les requêtes SQLite
                String sqlFindCentre = "SELECT id FROM centres WHERE code_centre = ?";
                String sqlFindAffaire = "SELECT id FROM affaires WHERE numero_affaire = ?";
                String sqlInsertRelation = """
                    INSERT OR REPLACE INTO affaires_centres 
                    (affaire_id, centre_id, montant_base, montant_indicateur, source)
                    VALUES (?, ?, ?, ?, 'MIGRATION')
                """;

                PreparedStatement stmtFindCentre = sqliteConn.prepareStatement(sqlFindCentre);
                PreparedStatement stmtFindAffaire = sqliteConn.prepareStatement(sqlFindAffaire);
                PreparedStatement stmtInsert = sqliteConn.prepareStatement(sqlInsertRelation);

                while (rs.next()) {
                    try {
                        String codeCentre = rs.getString("CODCENTR");
                        String numeroAffaire = rs.getString("NOTRANSAC");
                        double montantBase = rs.getDouble("MONTBASE");
                        double montantIndic = rs.getDouble("MONTINDIC");

                        // Trouver l'ID du centre
                        stmtFindCentre.setString(1, codeCentre);
                        ResultSet rsCentre = stmtFindCentre.executeQuery();

                        Long centreId = null;
                        if (rsCentre.next()) {
                            centreId = rsCentre.getLong("id");
                        } else {
                            logger.warn("Centre non trouvé : {}, création...", codeCentre);
                            // Créer le centre s'il n'existe pas
                            Centre nouveauCentre = new Centre();
                            nouveauCentre.setCodeCentre(codeCentre);
                            nouveauCentre.setNomCentre("Centre " + codeCentre);
                            nouveauCentre = centreDAO.save(nouveauCentre);
                            centreId = nouveauCentre.getId();
                            compteurCentresCrees++;
                            logger.info("✅ Centre créé : {} (ID: {})", codeCentre, centreId);
                        }

                        // Trouver l'ID de l'affaire
                        stmtFindAffaire.setString(1, numeroAffaire);
                        ResultSet rsAffaire = stmtFindAffaire.executeQuery();

                        if (rsAffaire.next() && centreId != null) {
                            long affaireId = rsAffaire.getLong("id");

                            // Insérer la relation
                            stmtInsert.setLong(1, affaireId);
                            stmtInsert.setLong(2, centreId);
                            stmtInsert.setBigDecimal(3, BigDecimal.valueOf(montantBase));
                            stmtInsert.setBigDecimal(4, BigDecimal.valueOf(montantIndic));
                            stmtInsert.executeUpdate();

                            compteurMigres++;

                            if (compteurMigres % 100 == 0) {
                                logger.info("... {} relations migrées", compteurMigres);
                            }
                        } else {
                            if (centreId == null) {
                                logger.error("Impossible de créer/trouver le centre : {}", codeCentre);
                            } else {
                                logger.debug("Affaire non trouvée : {}", numeroAffaire);
                            }
                            compteurErreurs++;
                        }

                    } catch (Exception e) {
                        logger.error("Erreur migration ligne : {}", e.getMessage());
                        compteurErreurs++;
                    }
                }

                // Fermer les PreparedStatements
                stmtFindCentre.close();
                stmtFindAffaire.close();
                stmtInsert.close();
            }

            logger.info("✅ Migration terminée :");
            logger.info("   - Relations migrées : {}", compteurMigres);
            logger.info("   - Centres créés : {}", compteurCentresCrees);
            logger.info("   - Erreurs : {}", compteurErreurs);

        } catch (Exception e) {
            logger.error("❌ Erreur migration parcentres", e);
        }
    }

    /**
     * Méthode pour analyser et déduire les relations service-centre et bureau-centre
     */
    public void deduireRelationsOrganisationnelles() {
        logger.info("🔍 Analyse des relations organisationnelles...");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {

            // Identifier quels services travaillent avec quels centres
            String sqlAnalyseServices = """
                SELECT DISTINCT s.id as service_id, 
                       s.nom_service,
                       ac.centre_id,
                       c.nom_centre,
                       COUNT(DISTINCT a.id) as nb_affaires
                FROM affaires a
                JOIN affaires_centres ac ON a.id = ac.affaire_id
                JOIN services s ON a.service_id = s.id
                JOIN centres c ON ac.centre_id = c.id
                GROUP BY s.id, ac.centre_id
                ORDER BY s.id, nb_affaires DESC
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sqlAnalyseServices)) {

                logger.info("\n=== SERVICES PAR CENTRE ===");
                while (rs.next()) {
                    logger.info("Service: {} -> Centre: {} ({} affaires)",
                            rs.getString("nom_service"),
                            rs.getString("nom_centre"),
                            rs.getInt("nb_affaires"));

                    // Optionnel : mettre à jour le service avec son centre principal
                    if (rs.getInt("nb_affaires") > 10) { // Seuil arbitraire
                        String updateSql = "UPDATE services SET centre_id = ? WHERE id = ? AND centre_id IS NULL";
                        try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                            pstmt.setLong(1, rs.getLong("centre_id"));
                            pstmt.setLong(2, rs.getLong("service_id"));
                            pstmt.executeUpdate();
                        }
                    }
                }
            }

            // Même analyse pour les bureaux
            String sqlAnalyseBureaux = """
                SELECT DISTINCT b.id as bureau_id,
                       b.nom_bureau,
                       ac.centre_id,
                       c.nom_centre,
                       COUNT(DISTINCT a.id) as nb_affaires
                FROM affaires a
                JOIN affaires_centres ac ON a.id = ac.affaire_id
                JOIN bureaux b ON a.bureau_id = b.id
                JOIN centres c ON ac.centre_id = c.id
                GROUP BY b.id, ac.centre_id
                ORDER BY b.id, nb_affaires DESC
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sqlAnalyseBureaux)) {

                logger.info("\n=== BUREAUX PAR CENTRE ===");
                while (rs.next()) {
                    logger.info("Bureau: {} -> Centre: {} ({} affaires)",
                            rs.getString("nom_bureau"),
                            rs.getString("nom_centre"),
                            rs.getInt("nb_affaires"));

                    // Mettre à jour le bureau avec son centre principal
                    if (rs.getInt("nb_affaires") > 5) {
                        String updateSql = "UPDATE bureaux SET centre_id = ? WHERE id = ? AND centre_id IS NULL";
                        try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                            pstmt.setLong(1, rs.getLong("centre_id"));
                            pstmt.setLong(2, rs.getLong("bureau_id"));
                            pstmt.executeUpdate();
                        }
                    }
                }
            }

            logger.info("✅ Analyse terminée");

        } catch (Exception e) {
            logger.error("Erreur analyse relations", e);
        }
    }

    public void creerTableAffairesCentres() {  // <-- Assurez-vous que c'est public
        logger.info("📋 Vérification/Création de la table affaires_centres...");

        String createTableSQL = """
        CREATE TABLE IF NOT EXISTS affaires_centres (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            affaire_id INTEGER NOT NULL,
            centre_id INTEGER NOT NULL,
            montant_base REAL DEFAULT 0,
            montant_indicateur REAL DEFAULT 0,
            date_import DATETIME DEFAULT CURRENT_TIMESTAMP,
            source VARCHAR(50) DEFAULT 'MIGRATION',
            FOREIGN KEY (affaire_id) REFERENCES affaires(id),
            FOREIGN KEY (centre_id) REFERENCES centres(id),
            UNIQUE(affaire_id, centre_id)
        )
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createTableSQL);
            logger.info("✅ Table affaires_centres créée/vérifiée");

            // Créer aussi les index
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_affaires_centres_centre ON affaires_centres(centre_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_affaires_centres_affaire ON affaires_centres(affaire_id)");

        } catch (SQLException e) {
            logger.error("❌ Erreur création table affaires_centres", e);
            throw new RuntimeException("Impossible de créer la table affaires_centres", e);
        }
    }
    
    /**
     * Méthode principale pour exécuter toute la migration
     */
    public void executerMigrationComplete() {
        logger.info("🚀 Démarrage migration complète...");

        // 1. Migrer les données de parcentres
        migrerDonneesParCentres();

        // 2. Analyser et déduire les relations
        deduireRelationsOrganisationnelles();

        logger.info("✅ Migration complète terminée");
    }

    public static void main(String[] args) {
        logger.info("=== DÉMARRAGE MIGRATION PARCENTRES ===");

        try {
            MigrationParCentresService migrationService = new MigrationParCentresService();

            // AJOUTER : Créer la table immédiatement
            migrationService.creerTableAffairesCentres();

            String option;

            // Si pas d'arguments, demander interactivement
            if (args.length == 0) {
                Scanner scanner = new Scanner(System.in);

                System.out.println("\nOptions de migration disponibles :");
                System.out.println("1. Migration complète (données + analyse)");
                System.out.println("2. Migration des données uniquement");
                System.out.println("3. Analyse des relations uniquement");
                System.out.print("\nVotre choix (1-3) : ");

                option = scanner.nextLine().trim();
                scanner.close();
            } else {
                option = args[0];
            }

            switch (option) {
                case "1":
                    logger.info("Exécution de la migration complète...");
                    migrationService.executerMigrationComplete();
                    break;

                case "2":
                    logger.info("Migration des données uniquement...");
                    migrationService.migrerDonneesParCentres();
                    break;

                case "3":
                    logger.info("Analyse des relations uniquement...");
                    migrationService.deduireRelationsOrganisationnelles();
                    break;

                default:
                    logger.error("Option invalide : {}", option);
                    System.exit(1);
            }

            logger.info("=== MIGRATION TERMINÉE AVEC SUCCÈS ===");

        } catch (Exception e) {
            logger.error("❌ Erreur fatale durant la migration", e);
            e.printStackTrace();
            System.exit(1);
        }
    }
}