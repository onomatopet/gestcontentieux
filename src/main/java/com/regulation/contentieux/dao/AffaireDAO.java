package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.StatutAffaire;
import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.service.NumerotationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion des affaires contentieuses
 * Gère la persistance des affaires et leurs relations
 */
public class AffaireDAO extends AbstractSQLiteDAO<Affaire, Long> {

    private static final Logger logger = LoggerFactory.getLogger(AffaireDAO.class);
    private final NumerotationService numerotationService;

    public AffaireDAO() {
        super();
        this.numerotationService = NumerotationService.getInstance();
    }

    @Override
    protected String getTableName() {
        return "affaires";
    }

    @Override
    protected String getIdColumnName() {
        return "id";
    }

    @Override
    protected String getInsertQuery() {
        return """
            INSERT INTO affaires (numero_affaire, date_creation, date_constatation, 
                                lieu_constatation, description, montant_total, montant_encaisse,
                                montant_amende_total, statut, observations, 
                                contrevenant_id, agent_verbalisateur_id,
                                bureau_id, service_id, created_by, created_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE affaires 
            SET date_constatation = ?, lieu_constatation = ?, description = ?,
                montant_total = ?, montant_encaisse = ?, montant_amende_total = ?,
                statut = ?, observations = ?,
                contrevenant_id = ?, agent_verbalisateur_id = ?, bureau_id = ?, 
                service_id = ?, updated_by = ?, updated_at = ?
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
        SELECT a.id, a.numero_affaire, a.date_creation, a.montant_amende_total, 
               a.statut, a.contrevenant_id, a.contravention_id, a.bureau_id, 
               a.service_id, a.created_at, a.updated_at,
               c.nom_complet as contrevenant_nom_complet,
               cv.libelle as contravention_libelle,
               b.nom_bureau as bureau_nom,
               s.nom_service as service_nom
        FROM affaires a
        LEFT JOIN contrevenants c ON a.contrevenant_id = c.id
        LEFT JOIN contraventions cv ON a.contravention_id = cv.id
        LEFT JOIN bureaux b ON a.bureau_id = b.id
        LEFT JOIN services s ON a.service_id = s.id
        ORDER BY a.date_creation DESC
    """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
        SELECT a.id, a.numero_affaire, a.date_creation, a.montant_amende_total, 
               a.statut, a.contrevenant_id, a.contravention_id, a.bureau_id, 
               a.service_id, a.created_at, a.updated_at,
               c.nom_complet as contrevenant_nom_complet,
               cv.libelle as contravention_libelle,
               b.nom_bureau as bureau_nom,
               s.nom_service as service_nom
        FROM affaires a
        LEFT JOIN contrevenants c ON a.contrevenant_id = c.id
        LEFT JOIN contraventions cv ON a.contravention_id = cv.id
        LEFT JOIN bureaux b ON a.bureau_id = b.id
        LEFT JOIN services s ON a.service_id = s.id
        WHERE a.id = ?
    """;
    }

    @Override
    protected Affaire mapResultSetToEntity(ResultSet rs) throws SQLException {
        Affaire affaire = new Affaire();

        // ✅ COLONNES RÉELLES de la table affaires
        affaire.setId(rs.getLong("id"));
        affaire.setNumeroAffaire(rs.getString("numero_affaire"));

        // Date création (colonne réelle)
        Date dateCreation = rs.getDate("date_creation");
        if (dateCreation != null) {
            affaire.setDateCreation(dateCreation.toLocalDate());
        }

        // ✅ CORRECTION : montant_amende_total au lieu de montant_total
        BigDecimal montantAmendeTotal = rs.getBigDecimal("montant_amende_total");
        affaire.setMontantTotal(montantAmendeTotal != null ? montantAmendeTotal : BigDecimal.ZERO);

        // Note: montant_encaisse n'existe pas dans la vraie table, on le met à zéro
        affaire.setMontantEncaisse(BigDecimal.ZERO);

        // Statut
        String statutStr = rs.getString("statut");
        if (statutStr != null) {
            try {
                affaire.setStatut(StatutAffaire.valueOf(statutStr));
            } catch (IllegalArgumentException e) {
                affaire.setStatut(StatutAffaire.EN_COURS);
            }
        }

        // ✅ CONTREVENANT (via contrevenant_id - colonne réelle)
        try {
            String contrevenantNomComplet = rs.getString("contrevenant_nom_complet");
            if (contrevenantNomComplet != null) {
                Contrevenant contrevenant = new Contrevenant();
                contrevenant.setId(rs.getLong("contrevenant_id"));
                contrevenant.setNomComplet(contrevenantNomComplet);
                affaire.setContrevenant(contrevenant);
            }
        } catch (SQLException e) {
            logger.debug("Pas de contrevenant associé pour l'affaire {}", affaire.getId());
        }

        // ✅ CONTRAVENTION (via contravention_id - colonne réelle)
        try {
            String contraventionLibelle = rs.getString("contravention_libelle");
            if (contraventionLibelle != null) {
                Contravention contravention = new Contravention();
                contravention.setId(rs.getLong("contravention_id"));
                contravention.setLibelle(contraventionLibelle);
                //affaire.setContravention(contravention);
            }
        } catch (SQLException e) {
            logger.debug("Pas de contravention associée pour l'affaire {}", affaire.getId());
        }

        // ✅ BUREAU (via bureau_id - colonne réelle optionnelle)
        try {
            Long bureauId = rs.getLong("bureau_id");
            if (bureauId != null && bureauId > 0) {
                String bureauNom = rs.getString("bureau_nom");
                if (bureauNom != null) {
                    Bureau bureau = new Bureau();
                    bureau.setId(bureauId);
                    bureau.setNomBureau(bureauNom);
                    affaire.setBureau(bureau);
                }
            }
        } catch (SQLException e) {
            logger.debug("Pas de bureau associé pour l'affaire {}", affaire.getId());
        }

        // ✅ SERVICE (via service_id - colonne réelle optionnelle)
        try {
            Long serviceId = rs.getLong("service_id");
            if (serviceId != null && serviceId > 0) {
                String serviceNom = rs.getString("service_nom");
                if (serviceNom != null) {
                    Service service = new Service();
                    service.setId(serviceId);
                    service.setNomService(serviceNom);
                    affaire.setService(service);
                }
            }
        } catch (SQLException e) {
            logger.debug("Pas de service associé pour l'affaire {}", affaire.getId());
        }

        // ✅ TIMESTAMPS (colonnes réelles)
        try {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                affaire.setCreatedAt(createdAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Colonne created_at nulle");
        }

        try {
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                affaire.setUpdatedAt(updatedAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Colonne updated_at nulle");
        }

        return affaire;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Affaire affaire) throws SQLException {
        stmt.setString(1, affaire.getNumeroAffaire());
        stmt.setDate(2, Date.valueOf(affaire.getDateCreation()));
        stmt.setDate(3, Date.valueOf(affaire.getDateConstatation()));
        stmt.setString(4, affaire.getLieuConstatation());
        stmt.setString(5, affaire.getDescription());
        stmt.setBigDecimal(6, affaire.getMontantTotal());
        stmt.setBigDecimal(7, affaire.getMontantEncaisse());
        stmt.setBigDecimal(8, affaire.getMontantAmendeTotal());
        stmt.setString(9, affaire.getStatut().name());
        stmt.setString(10, affaire.getObservations());

        // IDs des relations
        if (affaire.getContrevenantId() != null) {
            stmt.setLong(11, affaire.getContrevenantId());
        } else {
            stmt.setNull(11, Types.BIGINT);
        }

        if (affaire.getAgentVerbalisateur() != null && affaire.getAgentVerbalisateur().getId() != null) {
            stmt.setLong(12, affaire.getAgentVerbalisateur().getId());
        } else {
            stmt.setNull(12, Types.BIGINT);
        }

        if (affaire.getBureauId() != null) {
            stmt.setLong(13, affaire.getBureauId());
        } else {
            stmt.setNull(13, Types.BIGINT);
        }

        if (affaire.getServiceId() != null) {
            stmt.setLong(14, affaire.getServiceId());
        } else {
            stmt.setNull(14, Types.BIGINT);
        }

        stmt.setString(15, affaire.getCreatedBy());
        stmt.setTimestamp(16, Timestamp.valueOf(affaire.getCreatedAt()));
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Affaire affaire) throws SQLException {
        stmt.setDate(1, Date.valueOf(affaire.getDateConstatation()));
        stmt.setString(2, affaire.getLieuConstatation());
        stmt.setString(3, affaire.getDescription());
        stmt.setBigDecimal(4, affaire.getMontantTotal());
        stmt.setBigDecimal(5, affaire.getMontantEncaisse());
        stmt.setBigDecimal(6, affaire.getMontantAmendeTotal());
        stmt.setString(7, affaire.getStatut().name());
        stmt.setString(8, affaire.getObservations());

        if (affaire.getContrevenantId() != null) {
            stmt.setLong(9, affaire.getContrevenantId());
        } else {
            stmt.setNull(9, Types.BIGINT);
        }

        if (affaire.getAgentVerbalisateur() != null && affaire.getAgentVerbalisateur().getId() != null) {
            stmt.setLong(10, affaire.getAgentVerbalisateur().getId());
        } else {
            stmt.setNull(10, Types.BIGINT);
        }

        if (affaire.getBureauId() != null) {
            stmt.setLong(11, affaire.getBureauId());
        } else {
            stmt.setNull(11, Types.BIGINT);
        }

        if (affaire.getServiceId() != null) {
            stmt.setLong(12, affaire.getServiceId());
        } else {
            stmt.setNull(12, Types.BIGINT);
        }

        stmt.setString(13, affaire.getUpdatedBy());
        stmt.setTimestamp(14, Timestamp.valueOf(LocalDateTime.now()));
        stmt.setLong(15, affaire.getId());
    }

    @Override
    protected Long getEntityId(Affaire affaire) {
        return affaire.getId();
    }

    @Override
    protected void setEntityId(Affaire affaire, Long id) {
        affaire.setId(id);
    }

    // ========== MÉTHODES SPÉCIFIQUES AUX AFFAIRES ==========

    /**
     * Trouve les affaires non soldées (EN_COURS avec solde restant > 0)
     */
    public List<Affaire> findAffairesNonSoldees() {
        String sql = """
        SELECT a.id, a.numero_affaire, a.date_creation, a.montant_amende_total, 
               a.statut, a.contrevenant_id, a.contravention_id, a.bureau_id, 
               a.service_id, a.created_at, a.updated_at,
               c.nom_complet as contrevenant_nom_complet,
               cv.libelle as contravention_libelle,
               b.nom_bureau as bureau_nom,
               s.nom_service as service_nom
        FROM affaires a
        LEFT JOIN contrevenants c ON a.contrevenant_id = c.id
        LEFT JOIN contraventions cv ON a.contravention_id = cv.id
        LEFT JOIN bureaux b ON a.bureau_id = b.id
        LEFT JOIN services s ON a.service_id = s.id
        WHERE a.statut = 'EN_COURS'
        ORDER BY a.date_creation ASC
    """;

        List<Affaire> affaires = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                affaires.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche des affaires non soldées", e);
        }

        return affaires;
    }

    public String generateNextCode() {
        try {
            // Utiliser le service de numérotation existant qui gère déjà :
            // - Le format YYMMNNNNN
            // - Les verrous (ReentrantLock)
            // - La remise à zéro mensuelle
            // - La vérification des séquences
            String numeroGenere = numerotationService.genererNumeroAffaire();

            logger.info("✅ Numéro d'affaire généré via NumerotationService : {}", numeroGenere);
            return numeroGenere;

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la génération via NumerotationService", e);

            // FALLBACK : Si le service échoue, utiliser la méthode locale
            // basée sur le code existant mais adapté au format YYMMNNNNN
            return generateNextCodeFallback();
        }
    }

    /**
     * Méthode de fallback en cas d'échec du NumerotationService
     * Utilise la logique existante mais adaptée au format YYMMNNNNN
     */
    private String generateNextCodeFallback() {
        LocalDate now = LocalDate.now();
        String yearMonth = now.format(DateTimeFormatter.ofPattern("yyMM"));

        logger.warn("⚠️ Utilisation du fallback pour génération numéro affaire");

        // Requête adaptée pour le format YYMMNNNNN
        String sql = """
        SELECT numero_affaire FROM affaires 
        WHERE numero_affaire LIKE ? 
        AND LENGTH(numero_affaire) = 9
        ORDER BY numero_affaire DESC 
        LIMIT 1
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, yearMonth + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastCode = rs.getString("numero_affaire");

                // Vérifier si c'est bien le format YYMMNNNNN
                if (lastCode != null && lastCode.length() == 9) {
                    try {
                        String lastYearMonth = lastCode.substring(0, 4);

                        // Si on est dans le même mois, incrémenter
                        if (lastYearMonth.equals(yearMonth)) {
                            String numberPart = lastCode.substring(4);
                            int lastNumber = Integer.parseInt(numberPart);

                            if (lastNumber >= 99999) {
                                logger.error("Limite mensuelle atteinte (99999)");
                                return yearMonth + "99999"; // Limite max
                            }

                            return yearMonth + String.format("%05d", lastNumber + 1);
                        }
                    } catch (Exception parseEx) {
                        logger.error("Erreur parsing du dernier numéro: {}", lastCode, parseEx);
                    }
                }
            }

            // Pas de numéro trouvé ou nouveau mois : commencer à 00001
            String premierNumero = yearMonth + "00001";
            logger.info("Premier numéro du mois : {}", premierNumero);
            return premierNumero;

        } catch (SQLException e) {
            logger.error("Erreur SQL dans le fallback", e);
            // Dernier recours : retourner un numéro basé sur le timestamp
            return yearMonth + "00001";
        }
    }



    /**
     * OPTIONNEL : Si vous voulez conserver l'ancienne méthode pour compatibilité
     * (mais marquez-la comme @Deprecated)
     */
    @Deprecated
    public String generateOldFormatCode() {
        String prefix = "AFF";

        // Ancienne logique avec format AFF00001
        String sql = """
        SELECT numero_affaire FROM affaires 
        WHERE numero_affaire LIKE ? 
        ORDER BY numero_affaire DESC 
        LIMIT 1
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefix + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastCode = rs.getString("numero_affaire");
                if (lastCode != null && lastCode.startsWith(prefix) && lastCode.length() == 8) {
                    String numericPart = lastCode.substring(3);
                    int lastNumber = Integer.parseInt(numericPart);
                    return prefix + String.format("%05d", lastNumber + 1);
                }
            }

            return prefix + "00001";

        } catch (SQLException e) {
            logger.error("Erreur génération ancien format", e);
            return prefix + "00001";
        }
    }

    /**
     * ENRICHISSEMENT : Vérifie si un code respecte le format YYMMNNNNN du cahier des charges
     */
    private boolean isValidCahierChargesFormat(String code) {
        if (code == null || code.length() != 9) {
            return false;
        }

        try {
            // Vérifier que les 4 premiers caractères forment un YYMM valide
            String yymmPart = code.substring(0, 4);
            int year = Integer.parseInt(yymmPart.substring(0, 2));
            int month = Integer.parseInt(yymmPart.substring(2, 4));

            if (month < 1 || month > 12) {
                return false;
            }

            // Vérifier que les 5 derniers caractères sont numériques
            String numberPart = code.substring(4);
            Integer.parseInt(numberPart);

            logger.debug("✅ Code {} validé comme format YYMMNNNNN", code);
            return true;

        } catch (Exception e) {
            logger.debug("❌ Code {} invalide pour format YYMMNNNNN: {}", code, e.getMessage());
            return false;
        }
    }

    /**
     * ENRICHISSEMENT : Génère le prochain code à partir d'un code au format cahier des charges
     */
    private String generateNextCodeFromCahierChargesFormat(String lastCode) {
        try {
            String yymmPart = lastCode.substring(0, 4);
            String numberPart = lastCode.substring(4);
            int lastNumber = Integer.parseInt(numberPart);

            // Vérifier si on est toujours dans le même mois
            LocalDate now = LocalDate.now();
            String currentYYMM = now.format(DateTimeFormatter.ofPattern("yyMM"));

            if (yymmPart.equals(currentYYMM)) {
                // Même mois : incrémenter
                String nextCode = currentYYMM + String.format("%05d", lastNumber + 1);
                logger.info("✅ Prochain numéro dans la séquence: {}", nextCode);
                return nextCode;
            } else {
                // Nouveau mois : recommencer à 00001
                String nextCode = currentYYMM + "00001";
                logger.info("🔄 Nouveau mois détecté - Réinitialisation: {}", nextCode);
                return nextCode;
            }

        } catch (Exception e) {
            logger.error("Erreur dans generateNextCodeFromCahierChargesFormat", e);
            return generateCahierChargesCompliantCode(LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")));
        }
    }

    /**
     * ENRICHISSEMENT : Génère un code conforme au cahier des charges pour un mois donné
     */
    private String generateCahierChargesCompliantCode(String yearMonth) {
        // Rechercher le dernier numéro pour ce mois spécifique
        String sql = """
            SELECT numero_affaire FROM affaires 
            WHERE numero_affaire LIKE ? 
            AND LENGTH(numero_affaire) = 9
            ORDER BY numero_affaire DESC 
            LIMIT 1
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, yearMonth + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastCode = rs.getString("numero_affaire");
                String numberPart = lastCode.substring(4);
                int lastNumber = Integer.parseInt(numberPart);
                return yearMonth + String.format("%05d", lastNumber + 1);
            } else {
                return yearMonth + "00001";
            }

        } catch (Exception e) {
            logger.error("Erreur dans generateCahierChargesCompliantCode", e);
            return yearMonth + "00001";
        }
    }

    /**
     * Génère le code suivant basé sur le dernier code - MÉTHODE EXISTANTE CONSERVÉE ET ENRICHIE
     */
    private String generateNextCodeFromLast(String lastCode, String prefix) {
        try {
            // ENRICHISSEMENT : Logging détaillé pour traçabilité
            logger.debug("📊 Analyse du code pour génération: {}", lastCode);

            if (lastCode != null && lastCode.startsWith(prefix) && lastCode.length() == 7) {
                String numericPart = lastCode.substring(2);
                int lastNumber = Integer.parseInt(numericPart);
                String nextCode = prefix + String.format("%05d", lastNumber + 1);

                // ENRICHISSEMENT : Avertissement sur le format non conforme
                logger.warn("⚠️ Génération au format ancien ({}), considérer migration vers YYMMNNNNN", nextCode);

                return nextCode;
            }

            // Code invalide, recommencer
            logger.warn("⚠️ Format de code non reconnu: {}, réinitialisation", lastCode);
            return prefix + "00001";

        } catch (Exception e) {
            logger.warn("Erreur lors du parsing du dernier code: {}", lastCode, e);
            return prefix + "00001";
        }
    }

    /**
     * ENRICHISSEMENT : Vérifie si on peut migrer vers le nouveau format
     */
    private boolean canMigrateToNewFormat() {
        try {
            // Vérifier dans les propriétés système ou la configuration
            String migrationEnabled = System.getProperty("affaire.format.migration", "false");

            if ("true".equalsIgnoreCase(migrationEnabled)) {
                logger.info("✅ Migration vers nouveau format autorisée par configuration");
                return true;
            }

            // Vérifier s'il y a déjà des codes au nouveau format
            String sql = "SELECT COUNT(*) FROM affaires WHERE LENGTH(numero_affaire) = 9";
            try (Connection conn = DatabaseConfig.getSQLiteConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                if (rs.next() && rs.getInt(1) > 0) {
                    logger.info("✅ Codes au nouveau format détectés, migration autorisée");
                    return true;
                }
            }

            logger.info("❌ Migration non autorisée pour le moment");
            return false;

        } catch (Exception e) {
            logger.error("Erreur lors de la vérification de migration", e);
            return false;
        }
    }

    /**
     * ENRICHISSEMENT : Détermine si on doit utiliser le nouveau format
     */
    private boolean shouldUseNewFormat() {
        // Vérifier la configuration ou les propriétés système
        String useNewFormat = System.getProperty("affaire.format.new", "false");
        boolean result = "true".equalsIgnoreCase(useNewFormat);

        logger.info("Configuration nouveau format: {}", result);
        return result;
    }

    /**
     * ENRICHISSEMENT : Génère un code de secours en cas d'erreur
     */
    private String generateFallbackCode(String prefix, String yearMonth) {
        // Essayer d'abord le format du cahier des charges
        if (shouldUseNewFormat()) {
            return yearMonth + String.format("%05d", System.currentTimeMillis() % 100000);
        } else {
            // Sinon utiliser l'ancien format avec timestamp pour unicité
            return prefix + System.currentTimeMillis() % 100000;
        }
    }

    /**
     * Recherche d'affaires avec critères multiples
     */
    public List<Affaire> searchAffaires(String searchTerm, StatutAffaire statut,
                                        LocalDate dateDebut, LocalDate dateFin,
                                        Integer bureauId, int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT a.id, a.numero_affaire, a.date_creation, a.montant_amende_total, ");
        sql.append("a.statut, a.contrevenant_id, a.contravention_id, a.bureau_id, ");
        sql.append("a.service_id, a.created_at, a.updated_at, ");
        sql.append("c.nom_complet as contrevenant_nom_complet, ");
        sql.append("cv.libelle as contravention_libelle, ");
        sql.append("b.nom_bureau as bureau_nom, ");
        sql.append("s.nom_service as service_nom ");
        sql.append("FROM affaires a ");
        sql.append("LEFT JOIN contrevenants c ON a.contrevenant_id = c.id ");
        sql.append("LEFT JOIN contraventions cv ON a.contravention_id = cv.id ");
        sql.append("LEFT JOIN bureaux b ON a.bureau_id = b.id ");
        sql.append("LEFT JOIN services s ON a.service_id = s.id ");
        sql.append("WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql.append("AND a.numero_affaire LIKE ? ");
            parameters.add("%" + searchTerm.trim() + "%");
        }

        if (statut != null) {
            sql.append("AND a.statut = ? ");
            parameters.add(statut.name());
        }

        if (dateDebut != null) {
            sql.append("AND a.date_creation >= ? ");
            parameters.add(Date.valueOf(dateDebut));
        }

        if (dateFin != null) {
            sql.append("AND a.date_creation <= ? ");
            parameters.add(Date.valueOf(dateFin));
        }

        // ✅ BUREAU_ID : Maintenant possible car la colonne existe !
        if (bureauId != null) {
            sql.append("AND a.bureau_id = ? ");
            parameters.add(bureauId);
        }

        sql.append("ORDER BY a.date_creation DESC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    affaires.add(mapResultSetToEntity(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche d'affaires", e);
            throw new RuntimeException("Erreur lors de la recherche", e);
        }

        return affaires;
    }

    /**
     * Compte les affaires selon les critères de recherche - SIGNATURE CORRIGÉE
     */
    public long countSearchAffaires(String searchTerm, StatutAffaire statut,
                                    LocalDate dateDebut, LocalDate dateFin,
                                    Integer bureauId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM affaires a WHERE 1=1 ");
        List<Object> parameters = new ArrayList<>();

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql.append("AND a.numero_affaire LIKE ? ");
            parameters.add("%" + searchTerm.trim() + "%");
        }

        if (statut != null) {
            sql.append("AND a.statut = ? ");
            parameters.add(statut.name());
        }

        if (dateDebut != null) {
            sql.append("AND a.date_creation >= ? ");
            parameters.add(Date.valueOf(dateDebut));
        }

        if (dateFin != null) {
            sql.append("AND a.date_creation <= ? ");
            parameters.add(Date.valueOf(dateFin));
        }

        // ✅ BUREAU_ID : Maintenant utilisable !
        if (bureauId != null) {
            sql.append("AND a.bureau_id = ? ");
            parameters.add(bureauId);
        }

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage des affaires", e);
        }

        return 0;
    }

    /**
     * Compte le nombre total d'affaires
     */
    @Override
    public long count() {
        // CORRECTION : Supprimer la condition sur 'deleted' qui n'existe pas
        String sql = "SELECT COUNT(*) FROM affaires";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage des affaires", e);
        }

        return 0;
    }

    /**
     * Compte le nombre d'affaires selon des critères
     */
    public long countWithCriteria(String searchTerm, StatutAffaire statut, LocalDate dateDebut, LocalDate dateFin) {
        // CORRECTION : Supprimer la condition sur 'deleted'
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM affaires WHERE 1=1");
        List<Object> parameters = new ArrayList<>();

        // Ajout des critères de recherche
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql.append(" AND numero_affaire LIKE ?");
            parameters.add("%" + searchTerm.trim() + "%");
        }

        if (statut != null) {
            sql.append(" AND statut = ?");
            parameters.add(statut.name());
        }

        if (dateDebut != null) {
            sql.append(" AND date_creation >= ?");
            parameters.add(Date.valueOf(dateDebut));
        }

        if (dateFin != null) {
            sql.append(" AND date_creation <= ?");
            parameters.add(Date.valueOf(dateFin));
        }

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage avec critères", e);
        }

        return 0;
    }

    /**
     * Trouve une affaire par son numéro
     */
    public Optional<Affaire> findByNumeroAffaire(String numeroAffaire) {
        String sql = getSelectByIdQuery().replace("WHERE a.id = ?", "WHERE a.numero_affaire = ?");

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, numeroAffaire);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par numéro: " + numeroAffaire, e);
        }

        return Optional.empty();
    }

    /**
     * Trouve les affaires d'un contrevenant
     */
    public List<Affaire> findByContrevenantId(Long contrevenantId) {
        String sql = getSelectAllQuery().replace(
                "WHERE a.deleted = false",
                "WHERE a.deleted = false AND a.contrevenant_id = ?");

        return executeQuery(sql, contrevenantId);
    }

    /**
     * Compte les affaires par statut
     */
    public long countByStatut(StatutAffaire statut) {
        // CORRECTION : Supprimer la condition sur 'deleted'
        String sql = "SELECT COUNT(*) FROM affaires WHERE statut = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, statut.name());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getLong(1);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage par statut: " + statut, e);
        }

        return 0;
    }

    /**
     * Trouve les affaires par statut
     */
    public List<Affaire> findByStatut(StatutAffaire statut) {
        String sql = getSelectAllQuery().replace(
                "WHERE a.deleted = false",
                "WHERE a.deleted = false AND a.statut = ?");

        return executeQuery(sql, statut.name());
    }


    /**
     * Compte les affaires créées dans une année
     */
    public long countByYear(int year) {
        // CORRECTION : Supprimer la condition sur 'deleted'
        String sql = "SELECT COUNT(*) FROM affaires WHERE strftime('%Y', date_creation) = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, String.valueOf(year));
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getLong(1);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage par année: " + year, e);
        }

        return 0;
    }

    public String generateNextNumeroAffaire() {
        String prefix = "AFF";
        String year = String.valueOf(LocalDate.now().getYear());

        String sql = """
        SELECT numero_affaire FROM affaires 
        WHERE numero_affaire LIKE ? 
        ORDER BY numero_affaire DESC 
        LIMIT 1
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefix + year + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastNumber = rs.getString("numero_affaire");
                // Extraire le numéro séquentiel et incrémenter
                String sequential = lastNumber.substring(prefix.length() + 4);
                int nextNum = Integer.parseInt(sequential) + 1;
                return prefix + year + String.format("%04d", nextNum);
            } else {
                // Premier numéro de l'année
                return prefix + year + "0001";
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du numéro d'affaire", e);
            return prefix + year + "0001";
        }
    }

    public List<Affaire> findByPeriod(LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
        SELECT * FROM affaires 
        WHERE date_creation BETWEEN ? AND ? 
        ORDER BY date_creation DESC
    """;

        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(dateDebut));
            stmt.setDate(2, Date.valueOf(dateFin));
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                affaires.add(mapResultSetToEntity(rs));
            }
        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par période", e);
        }

        return affaires;
    }

    /**
     * Trouve les affaires d'un service pour une période donnée
     */
    public List<Affaire> findByServiceAndPeriod(Long serviceId, LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
        SELECT a.*, 
               c.nom as contrevenant_nom, c.prenom as contrevenant_prenom, 
               c.raison_sociale as contrevenant_raison_sociale,
               ag.code_agent, ag.nom as agent_nom, ag.prenom as agent_prenom,
               b.code_bureau, b.nom_bureau,
               s.code_service, s.nom_service
        FROM affaires a
        LEFT JOIN contrevenants c ON a.contrevenant_id = c.id
        LEFT JOIN agents ag ON a.agent_verbalisateur_id = ag.id
        LEFT JOIN bureaux b ON a.bureau_id = b.id
        LEFT JOIN services s ON a.service_id = s.id
        WHERE a.service_id = ? 
          AND a.date_creation BETWEEN ? AND ?
          AND a.deleted = false
        ORDER BY a.date_creation DESC
    """;

        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, serviceId);
            stmt.setDate(2, Date.valueOf(dateDebut));
            stmt.setDate(3, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                affaires.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par service et période: serviceId={}, période={} à {}",
                    serviceId, dateDebut, dateFin, e);
        }

        return affaires;
    }

    /**
     * Trouve les affaires avec encaissements validés pour une période
     */
    public List<Affaire> findAffairesWithEncaissementsByPeriod(LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
            SELECT DISTINCT a.*, 
                   c.nom as contrevenant_nom, c.prenom as contrevenant_prenom, 
                   c.raison_sociale as contrevenant_raison_sociale,
                   ag.code_agent, ag.nom as agent_nom, ag.prenom as agent_prenom,
                   b.code_bureau, b.nom_bureau,
                   s.code_service, s.nom_service
            FROM affaires a
            INNER JOIN encaissements e ON a.id = e.affaire_id
            LEFT JOIN contrevenants c ON a.contrevenant_id = c.id
            LEFT JOIN agents ag ON a.agent_verbalisateur_id = ag.id
            LEFT JOIN bureaux b ON a.bureau_id = b.id
            LEFT JOIN services s ON a.service_id = s.id
            WHERE e.date_encaissement BETWEEN ? AND ?
              AND e.statut = 'VALIDE'
              AND a.deleted = false
            ORDER BY a.numero_affaire
        """;

        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(dateDebut));
            stmt.setDate(2, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                affaires.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche des affaires avec encaissements", e);
        }

        return affaires;
    }

    /**
     * Met à jour le montant encaissé d'une affaire
     */
    public void updateMontantEncaisse(Long affaireId, BigDecimal montantEncaisse) {
        String sql = """
            UPDATE affaires 
            SET montant_encaisse = ?, updated_at = ?
            WHERE id = ?
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBigDecimal(1, montantEncaisse);
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(3, affaireId);

            int updated = stmt.executeUpdate();

            if (updated > 0) {
                logger.debug("Montant encaissé mis à jour pour l'affaire {}", affaireId);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la mise à jour du montant encaissé", e);
        }
    }

    /**
     * Suppression logique d'une affaire
     */
    @Override
    public void deleteById(Long id) {
        String sql = "DELETE FROM affaires WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                logger.info("Affaire {} supprimée", id);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la suppression de l'affaire", e);
            throw new RuntimeException("Erreur lors de la suppression", e);
        }
    }
}