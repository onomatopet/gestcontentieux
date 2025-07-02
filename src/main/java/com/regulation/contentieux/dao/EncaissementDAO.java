package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Banque;
import com.regulation.contentieux.model.Encaissement;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Mandat;
import com.regulation.contentieux.model.enums.ModeReglement;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.service.MandatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter; // AJOUT DE L'IMPORT MANQUANT
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion des encaissements
 * Gère la persistance des encaissements et leurs relations
 */
public class EncaissementDAO extends AbstractSQLiteDAO<Encaissement, Long> {

    private static final Logger logger = LoggerFactory.getLogger(EncaissementDAO.class);

    @Override
    protected String getTableName() {
        return "encaissements";
    }

    @Override
    protected String getIdColumnName() {
        return "id";
    }

    @Override
    protected String getInsertQuery() {
        return """
        INSERT INTO encaissements (numero_encaissement, numero_mandat, date_encaissement, 
                                 montant_encaisse, mode_reglement, banque_id, numero_cheque, 
                                 affaire_id, created_at, updated_at) 
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
        UPDATE encaissements 
        SET numero_encaissement = ?, numero_mandat = ?, date_encaissement = ?, 
            montant_encaisse = ?, mode_reglement = ?, banque_id = ?, numero_cheque = ?, 
            affaire_id = ?, updated_at = ?
        WHERE id = ?
    """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
        SELECT e.*, a.numero_affaire, a.montant_amende_total,
               b.nom_banque as banque_nom
        FROM encaissements e
        LEFT JOIN affaires a ON e.affaire_id = a.id
        LEFT JOIN banques b ON e.banque_id = b.id
        ORDER BY e.date_encaissement DESC
    """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
        SELECT e.*, a.numero_affaire, a.montant_amende_total,
               b.nom_banque as banque_nom
        FROM encaissements e
        LEFT JOIN affaires a ON e.affaire_id = a.id
        LEFT JOIN banques b ON e.banque_id = b.id
        WHERE e.id = ?
    """;
    }

    @Override
    public Encaissement mapResultSetToEntity(ResultSet rs) throws SQLException {
        Encaissement encaissement = new Encaissement();

        encaissement.setId(rs.getLong("id"));

        // CORRECTION : Utiliser numero_encaissement au lieu de reference
        encaissement.setReference(rs.getString("numero_encaissement"));

        Date dateEnc = rs.getDate("date_encaissement");
        if (dateEnc != null) {
            encaissement.setDateEncaissement(dateEnc.toLocalDate());
        }

        encaissement.setMontantEncaisse(rs.getBigDecimal("montant_encaisse"));

        // Mode de règlement
        String modeStr = rs.getString("mode_reglement");
        if (modeStr != null) {
            try {
                // CORRECTION : Normaliser les valeurs des données existantes
                String modeNormalise = normaliserModeReglement(modeStr);
                encaissement.setModeReglement(ModeReglement.valueOf(modeNormalise));
            } catch (IllegalArgumentException e) {
                logger.warn("Mode de règlement invalide: {} - utilisation de ESPECES par défaut", modeStr);
                encaissement.setModeReglement(ModeReglement.ESPECES); // Valeur par défaut
            }
        }

        // CORRECTION : Utiliser numero_cheque au lieu de numero_piece
        encaissement.setNumeroPiece(rs.getString("numero_cheque"));

        // CORRECTION : Utiliser banque_nom de la jointure
        try {
            encaissement.setBanque(rs.getString("banque_nom"));
        } catch (SQLException e) {
            // Colonne optionnelle
        }

        // CORRECTION : Pas de colonne observations dans votre schéma
        // encaissement.setObservations(rs.getString("observations"));

        // CORRECTION : Pas de colonne statut - définir un statut par défaut
        encaissement.setStatut(StatutEncaissement.VALIDE); // Valeur par défaut

        // Affaire liée - DÉJÀ CORRIGÉ
        Long affaireId = rs.getLong("affaire_id");
        if (affaireId != null && affaireId > 0) {
            Affaire affaire = new Affaire();
            affaire.setId(affaireId);
            try {
                affaire.setNumeroAffaire(rs.getString("numero_affaire"));
                affaire.setMontantTotal(rs.getBigDecimal("montant_amende_total"));
            } catch (SQLException e) {
                // Colonnes optionnelles
            }
            encaissement.setAffaire(affaire);
        }

        // Métadonnées - Pas de created_by/updated_by dans votre schéma
        try {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                encaissement.setCreatedAt(createdAt.toLocalDateTime());
            }

            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                encaissement.setUpdatedAt(updatedAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            // Colonnes optionnelles
        }

        return encaissement;
    }

    /**
     * Normalise les modes de règlement pour corriger les données existantes
     */
    private String normaliserModeReglement(String modeOriginal) {
        if (modeOriginal == null) {
            return "ESPECES";
        }

        String mode = modeOriginal.trim().toUpperCase();

        // Corrections des fautes de frappe communes
        switch (mode) {
            case "ESPÉCES":
            case "ESPECES":
            case "ESPÈCES":
                return "ESPECES";
            case "CHÈQUE":
            case "CHEQUE":
                return "CHEQUE";
            case "VIREMENT":
                return "VIREMENT";
            default:
                logger.warn("Mode de règlement non reconnu: {} - conversion en ESPECES", modeOriginal);
                return "ESPECES";
        }
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Encaissement encaissement) throws SQLException {
        // 1. numero_encaissement
        stmt.setString(1, encaissement.getReference());

        // 2. numero_mandat - Utiliser le mandat actif
        String numeroMandat = null;
        try {
            MandatService mandatService = MandatService.getInstance();
            Mandat mandatActif = mandatService.getMandatActif();
            if (mandatActif != null) {
                numeroMandat = mandatActif.getNumeroMandat();
            }
        } catch (Exception e) {
            logger.warn("Impossible de récupérer le mandat actif: {}", e.getMessage());
        }

        if (numeroMandat == null) {
            // Générer un numéro par défaut basé sur la date
            LocalDate now = LocalDate.now();
            numeroMandat = now.format(DateTimeFormatter.ofPattern("yyMM")) + "M0001";
            logger.warn("Aucun mandat actif trouvé, utilisation du numéro par défaut: {}", numeroMandat);
        }
        stmt.setString(2, numeroMandat);

        // 3. date_encaissement
        stmt.setDate(3, Date.valueOf(encaissement.getDateEncaissement()));

        // 4. montant_encaisse
        stmt.setBigDecimal(4, encaissement.getMontantEncaisse());

        // 5. mode_reglement
        stmt.setString(5, encaissement.getModeReglement().name());

        // 6. banque_id
        if (encaissement.getBanqueId() != null) {
            stmt.setLong(6, encaissement.getBanqueId());
        } else {
            stmt.setNull(6, Types.INTEGER);
        }

        // 7. numero_cheque
        stmt.setString(7, encaissement.getNumeroPiece());

        // 8. affaire_id
        if (encaissement.getAffaire() != null) {
            stmt.setLong(8, encaissement.getAffaire().getId());
        } else if (encaissement.getAffaireId() != null) {
            stmt.setLong(8, encaissement.getAffaireId());
        } else {
            stmt.setNull(8, Types.BIGINT);
        }

        // 9. created_at
        stmt.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));

        // 10. updated_at
        stmt.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Encaissement encaissement) throws SQLException {
        // 1. numero_encaissement
        stmt.setString(1, encaissement.getReference());

        // 2. numero_mandat - Utiliser le mandat actif
        String numeroMandat = null;
        try {
            MandatService mandatService = MandatService.getInstance();
            Mandat mandatActif = mandatService.getMandatActif();
            if (mandatActif != null) {
                numeroMandat = mandatActif.getNumeroMandat();
            }
        } catch (Exception e) {
            logger.warn("Impossible de récupérer le mandat actif: {}", e.getMessage());
        }

        if (numeroMandat == null) {
            LocalDate now = LocalDate.now();
            numeroMandat = now.format(DateTimeFormatter.ofPattern("yyMM")) + "M0001";
        }
        stmt.setString(2, numeroMandat);

        // 3. date_encaissement
        stmt.setDate(3, Date.valueOf(encaissement.getDateEncaissement()));

        // 4. montant_encaisse
        stmt.setBigDecimal(4, encaissement.getMontantEncaisse());

        // 5. mode_reglement
        stmt.setString(5, encaissement.getModeReglement().name());

        // 6. banque_id
        if (encaissement.getBanqueId() != null) {
            stmt.setLong(6, encaissement.getBanqueId());
        } else {
            stmt.setNull(6, Types.INTEGER);
        }

        // 7. numero_cheque
        stmt.setString(7, encaissement.getNumeroPiece());

        // 8. affaire_id
        if (encaissement.getAffaire() != null) {
            stmt.setLong(8, encaissement.getAffaire().getId());
        } else if (encaissement.getAffaireId() != null) {
            stmt.setLong(8, encaissement.getAffaireId());
        } else {
            stmt.setNull(8, Types.BIGINT);
        }

        // 9. updated_at
        stmt.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));

        // 10. WHERE id = ?
        stmt.setLong(10, encaissement.getId());
    }

    @Override
    protected Long getEntityId(Encaissement encaissement) {
        return encaissement.getId();
    }

    @Override
    protected void setEntityId(Encaissement encaissement, Long id) {
        encaissement.setId(id);
    }

    // ========== MÉTHODES SPÉCIFIQUES AUX ENCAISSEMENTS ==========

    /**
     * Trouve un encaissement par sa référence
     */
    public Optional<Encaissement> findByReference(String reference) {
        String sql = getSelectAllQuery().replace("ORDER BY e.date_encaissement DESC",
                "WHERE e.numero_encaissement = ?");

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reference);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par référence: " + reference, e);
        }

        return Optional.empty();
    }

    /**
     * Vérifie si une référence existe déjà
     */
    public boolean existsByReference(String reference) {
        String sql = "SELECT COUNT(*) FROM encaissements WHERE numero_encaissement = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reference);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification d'existence de la référence", e);
        }

        return false;
    }

    /**
     * ENRICHISSEMENT : Méthode pour mapper directement un ResultSet vers Encaissement
     * Utilisée par RapportService pour les requêtes complexes
     */
    public static Encaissement mapResultSetToEncaissement(ResultSet rs) throws SQLException {
        Encaissement encaissement = new Encaissement();

        try {
            encaissement.setId(rs.getLong("id"));
            encaissement.setReference(rs.getString("numero_encaissement"));
            encaissement.setMontantEncaisse(rs.getBigDecimal("montant_encaisse"));

            Date dateEnc = rs.getDate("date_encaissement");
            if (dateEnc != null) {
                encaissement.setDateEncaissement(dateEnc.toLocalDate());
            }

            // Pas de colonne statut dans la table encaissements
            encaissement.setStatut(StatutEncaissement.VALIDE);

            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                encaissement.setCreatedAt(createdAt.toLocalDateTime());
            }

            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                encaissement.setUpdatedAt(updatedAt.toLocalDateTime());
            }

            // Gestion des clés étrangères
            try {
                Long affaireId = rs.getLong("affaire_id");
                if (affaireId != null && affaireId > 0) {
                    Affaire affaire = new Affaire();
                    affaire.setId(affaireId);
                    encaissement.setAffaire(affaire);
                }
            } catch (SQLException e) {
                // La colonne affaire_id n'est pas présente
            }

            try {
                String numeroAffaire = rs.getString("numero_affaire");
                if (numeroAffaire != null && encaissement.getAffaire() != null) {
                    encaissement.getAffaire().setNumeroAffaire(numeroAffaire);
                }
            } catch (SQLException e) {
                // La colonne numero_affaire n'est pas présente
            }

        } catch (SQLException e) {
            LoggerFactory.getLogger(EncaissementDAO.class)
                    .warn("Erreur lors du mapping de l'encaissement ID {}: {}",
                            rs.getLong("id"), e.getMessage());
        }

        return encaissement;
    }

    /**
     * Génère le prochain numéro d'encaissement selon le format YYMMRNNNNN
     * MÉTHODE UNIFIÉE ET ENRICHIE - REMPLACE L'ANCIENNE VERSION
     */
    public String generateNextNumeroEncaissement() {
        logger.debug("🔍 === GÉNÉRATION NUMÉRO ENCAISSEMENT ===");
        logger.debug("🔍 Format cahier des charges: YYMMRNNNNN (R = littéral 'R')");

        LocalDate now = LocalDate.now();
        String yearMonth = now.format(DateTimeFormatter.ofPattern("yyMM"));
        String expectedPrefix = yearMonth + "R";
        logger.debug("🔍 Préfixe attendu pour ce mois: {}", expectedPrefix);

        String sql = """
        SELECT numero_encaissement FROM encaissements
        WHERE numero_encaissement LIKE ?
        ORDER BY numero_encaissement DESC
        LIMIT 1
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, expectedPrefix + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastNumero = rs.getString("numero_encaissement");
                logger.debug("🔍 Dernier numéro trouvé: {}", lastNumero);

                // Extraire la partie numérique et incrémenter
                String numberPart = lastNumero.substring(5); // Après YYMMR
                int lastNumber = Integer.parseInt(numberPart);
                String newNumero = expectedPrefix + String.format("%05d", lastNumber + 1);
                logger.info("✅ Nouveau numéro généré: {}", newNumero);
                return newNumero;
            } else {
                String newNumero = expectedPrefix + "00001";
                logger.info("✅ Premier numéro du mois généré: {}", newNumero);
                return newNumero;
            }

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la génération du numéro d'encaissement", e);
            // Numéro de secours avec timestamp
            String fallback = yearMonth + "R" + String.format("%05d", System.currentTimeMillis() % 100000);
            logger.error("🔄 Numéro de secours généré: {}", fallback);
            return fallback;
        }
    }


    /**
     * ALTERNATIVE : Méthode publique instance pour accès depuis RapportService
     * Si la méthode statique pose problème
     */
    public Encaissement mapResultSetToEntityPublic(ResultSet rs) throws SQLException {
        return this.mapResultSetToEntity(rs);
    }

    /**
     * ENRICHISSEMENT : Détermine si on doit utiliser le nouveau format
     */
    private boolean shouldUseNewFormat() {
        // Vérifier la configuration ou les propriétés système
        String useNewFormat = System.getProperty("encaissement.format.new", "true");
        boolean result = "true".equalsIgnoreCase(useNewFormat);

        logger.debug("Configuration nouveau format encaissement: {}", result);
        return result;
    }

    /**
     * ENRICHISSEMENT : Valide le format YYMMRNNNNN
     */
    private boolean isValidEncaissementFormat(String numero) {
        if (numero == null || numero.length() != 11) {
            return false;
        }

        try {
            // Vérifier YYMM
            String yyPart = numero.substring(0, 2);
            String mmPart = numero.substring(2, 4);
            int month = Integer.parseInt(mmPart);

            if (month < 1 || month > 12) {
                return false;
            }

            // Vérifier le R
            if (numero.charAt(4) != 'R') {
                return false;
            }

            // Vérifier les 5 chiffres
            String numberPart = numero.substring(5);
            Integer.parseInt(numberPart);

            logger.debug("✅ Format validé: {}", numero);
            return true;

        } catch (Exception e) {
            logger.debug("❌ Format invalide: {} - {}", numero, e.getMessage());
            return false;
        }
    }

    /**
     * ENRICHISSEMENT : Génère le prochain numéro à partir d'un format valide
     */
    private String generateNextFromValidFormat(String lastNumero, String expectedPrefix) {
        try {
            String currentPrefix = lastNumero.substring(0, 5); // YYMMR

            if (currentPrefix.equals(expectedPrefix)) {
                // Même mois, on incrémente
                String numberPart = lastNumero.substring(5);
                int lastNumber = Integer.parseInt(numberPart);
                String nextNumero = expectedPrefix + String.format("%05d", lastNumber + 1);
                logger.info("✅ Prochain numéro: {}", nextNumero);
                return nextNumero;
            } else {
                // Nouveau mois, on recommence
                String nextNumero = expectedPrefix + "00001";
                logger.info("🔄 Nouveau mois - Réinitialisation: {}", nextNumero);
                return nextNumero;
            }

        } catch (Exception e) {
            logger.error("Erreur dans generateNextFromValidFormat", e);
            return expectedPrefix + "00001";
        }
    }

    /**
     * ENRICHISSEMENT : Gère la migration depuis l'ancien format
     */
    private String handleFormatMigration(String oldFormat, String expectedPrefix) {
        logger.warn("🔄 === MIGRATION DE FORMAT DÉTECTÉE ===");
        logger.warn("🔄 Ancien format: {}", oldFormat);

        // Essayer de comprendre l'ancien format et migrer
        if (oldFormat != null && oldFormat.startsWith("ENC-")) {
            logger.warn("🔄 Format détecté: ENC-YYYY-NNNNN");

            // Vérifier s'il existe déjà des numéros au nouveau format pour ce mois
            String checkSql = """
                SELECT COUNT(*) as count FROM encaissements
                WHERE reference LIKE ?
            """;

            try (Connection conn = DatabaseConfig.getSQLiteConnection();
                 PreparedStatement stmt = conn.prepareStatement(checkSql)) {

                stmt.setString(1, expectedPrefix + "%");
                ResultSet rs = stmt.executeQuery();

                if (rs.next() && rs.getInt("count") > 0) {
                    // Il y a déjà des numéros au nouveau format
                    return generateNextNumeroFromDatabase(expectedPrefix);
                } else {
                    // Premier numéro au nouveau format
                    String newNumero = expectedPrefix + "00001";
                    logger.info("✅ Premier numéro au nouveau format: {}", newNumero);
                    return newNumero;
                }

            } catch (Exception e) {
                logger.error("Erreur lors de la vérification de migration", e);
            }
        }

        // Par défaut, commencer une nouvelle séquence
        return expectedPrefix + "00001";
    }

    /**
     * ENRICHISSEMENT : Recherche le dernier numéro en base pour un préfixe donné
     */
    private String generateNextNumeroFromDatabase(String prefix) {
        String sql = """
            SELECT reference FROM encaissements
            WHERE reference LIKE ?
            AND LENGTH(reference) = 11
            ORDER BY reference DESC
            LIMIT 1
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefix + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastNumero = rs.getString("reference");
                String numberPart = lastNumero.substring(5);
                int lastNumber = Integer.parseInt(numberPart);
                return prefix + String.format("%05d", lastNumber + 1);
            } else {
                return prefix + "00001";
            }

        } catch (Exception e) {
            logger.error("Erreur dans generateNextNumeroFromDatabase", e);
            return prefix + "00001";
        }
    }

    /**
     * ENRICHISSEMENT : Méthode pour vérifier la cohérence des numéros d'encaissement
     * Peut être appelée périodiquement pour diagnostiquer les problèmes
     */
    public void verifierCoherenceNumerotation() {
        logger.info("🔍 === VÉRIFICATION COHÉRENCE NUMÉROTATION ENCAISSEMENTS ===");

        String sql = """
            SELECT 
                reference,
                date_encaissement,
                LENGTH(reference) as longueur
            FROM encaissements
            ORDER BY date_encaissement DESC
            LIMIT 100
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            int conformes = 0;
            int nonConformes = 0;

            while (rs.next()) {
                String numero = rs.getString("reference");
                LocalDate date = rs.getDate("date_encaissement").toLocalDate();
                int longueur = rs.getInt("longueur");

                if (isValidEncaissementFormat(numero)) {
                    conformes++;
                    logger.debug("✅ {} - Conforme au cahier des charges", numero);
                } else {
                    nonConformes++;
                    logger.warn("❌ {} - NON conforme (longueur: {}, date: {})", numero, longueur, date);
                }
            }

            logger.info("📊 RÉSULTAT: {} conformes, {} non conformes", conformes, nonConformes);

            if (nonConformes > 0) {
                logger.warn("⚠️ Action recommandée: Migration progressive vers le format YYMMRNNNNN");
            }

        } catch (Exception e) {
            logger.error("Erreur lors de la vérification de cohérence", e);
        }
    }

    /**
     * Trouve tous les encaissements d'une affaire
     */
    public List<Encaissement> findByAffaireId(Long affaireId) {
        String sql = getSelectAllQuery().replace("ORDER BY e.date_encaissement DESC",
                "WHERE e.affaire_id = ? ORDER BY e.date_encaissement DESC");
        List<Encaissement> encaissements = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                encaissements.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par affaire", e);
        }

        return encaissements;
    }

    /**
     * Calcule le total encaissé pour une affaire
     */
    public BigDecimal getTotalEncaisseByAffaire(Long affaireId) {
        String sql = """
            SELECT SUM(montant_encaisse) as total 
            FROM encaissements 
            WHERE affaire_id = ? AND statut = 'VALIDE'
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                BigDecimal total = rs.getBigDecimal("total");
                return total != null ? total : BigDecimal.ZERO;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du calcul du total encaissé", e);
        }

        return BigDecimal.ZERO;
    }

    /**
     * Met à jour le statut d'un encaissement
     */
    public boolean updateStatut(Long encaissementId, StatutEncaissement nouveauStatut, String updatedBy) {
        String sql = """
            UPDATE encaissements 
            SET statut = ?, updated_by = ?, updated_at = ?
            WHERE id = ?
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nouveauStatut.name());
            stmt.setString(2, updatedBy);
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(4, encaissementId);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            logger.error("Erreur lors de la mise à jour du statut", e);
            return false;
        }
    }

    /**
     * CORRECTION : Recherche d'encaissements avec critères multiples
     */
    public List<Encaissement> searchEncaissements(String reference, StatutEncaissement statut,
                                                  ModeReglement modeReglement, LocalDate dateDebut,
                                                  LocalDate dateFin, Long affaireId,
                                                  int offset, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT e.*, a.numero_affaire, a.montant_amende_total, " +
                        "b.nom_banque as banque_nom " +
                        "FROM encaissements e " +
                        "LEFT JOIN affaires a ON e.affaire_id = a.id " +
                        "LEFT JOIN banques b ON e.banque_id = b.id " +
                        "WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (reference != null && !reference.trim().isEmpty()) {
            sql.append("AND e.numero_encaissement LIKE ? ");
            parameters.add("%" + reference.trim() + "%");
        }

        if (modeReglement != null) {
            sql.append("AND e.mode_reglement = ? ");
            parameters.add(modeReglement.name());
        }

        if (dateDebut != null) {
            sql.append("AND e.date_encaissement >= ? ");
            parameters.add(Date.valueOf(dateDebut));
        }

        if (dateFin != null) {
            sql.append("AND e.date_encaissement <= ? ");
            parameters.add(Date.valueOf(dateFin));
        }

        if (affaireId != null) {
            sql.append("AND e.affaire_id = ? ");
            parameters.add(affaireId);
        }

        sql.append("ORDER BY e.date_encaissement DESC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Encaissement> encaissements = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                encaissements.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche d'encaissements", e);
        }

        return encaissements;
    }

    /**
     * Compte les encaissements selon les critères
     */
    public long countSearchEncaissements(String reference, StatutEncaissement statut,
                                         ModeReglement modeReglement, LocalDate dateDebut,
                                         LocalDate dateFin, Long affaireId) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM encaissements WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (reference != null && !reference.trim().isEmpty()) {
            sql.append("AND numero_encaissement LIKE ? ");
            parameters.add("%" + reference.trim() + "%");
        }

        if (modeReglement != null) {
            sql.append("AND mode_reglement = ? ");
            parameters.add(modeReglement.name());
        }

        if (dateDebut != null) {
            sql.append("AND date_encaissement >= ? ");
            parameters.add(Date.valueOf(dateDebut));
        }

        if (dateFin != null) {
            sql.append("AND date_encaissement <= ? ");
            parameters.add(Date.valueOf(dateFin));
        }

        if (affaireId != null) {
            sql.append("AND affaire_id = ? ");
            parameters.add(affaireId);
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
            logger.error("Erreur lors du comptage des encaissements", e);
        }

        return 0;
    }

    /**
     * CORRECTION : Trouve les encaissements par statut
     */
    public List<Encaissement> findByStatut(StatutEncaissement statut) {
        String sql = """
            SELECT e.*, a.numero_affaire, a.montant_amende_total
            FROM encaissements e
            LEFT JOIN affaires a ON e.affaire_id = a.id 
            WHERE e.statut = ? 
            ORDER BY e.date_encaissement DESC
        """;
        List<Encaissement> encaissements = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, statut.name());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                encaissements.add(mapResultSetToEntity(rs));
            }
        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par statut", e);
        }

        return encaissements;
    }

    /**
     * Calcule le total des encaissements par période et statut
     */
    public BigDecimal getTotalEncaissementsByPeriod(LocalDate debut, LocalDate fin, StatutEncaissement statut) {
        String sql;

        if (debut == null && fin == null) {
            // Cas spécial pour les statistiques générales
            sql = "SELECT COALESCE(SUM(montant_encaisse), 0) as total FROM encaissements WHERE statut = ?";
        } else {
            sql = "SELECT COALESCE(SUM(montant_encaisse), 0) as total FROM encaissements WHERE date_encaissement BETWEEN ? AND ? AND statut = ?";
        }

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (debut == null && fin == null) {
                stmt.setString(1, statut.name());
            } else {
                stmt.setDate(1, Date.valueOf(debut));
                stmt.setDate(2, Date.valueOf(fin));
                stmt.setString(3, statut.name());
            }

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                BigDecimal total = rs.getBigDecimal("total");
                return total != null ? total : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            logger.error("Erreur lors du calcul du total", e);
        }

        return BigDecimal.ZERO;
    }

    /**
     * CORRECTION : Trouve les encaissements pour une période donnée (pour les rapports)
     */
    public List<Encaissement> findByPeriod(LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
        SELECT e.*, a.numero_affaire, a.montant_amende_total,
               b.nom_banque as banque_nom
        FROM encaissements e
        LEFT JOIN affaires a ON e.affaire_id = a.id
        LEFT JOIN banques b ON e.banque_id = b.id
        WHERE e.date_encaissement BETWEEN ? AND ?
        ORDER BY e.date_encaissement
    """;

        List<Encaissement> encaissements = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(dateDebut));
            stmt.setDate(2, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                encaissements.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par période", e);
        }

        return encaissements;
    }

    /**
     * CORRECTION : Trouve les encaissements par période et statut
     */
    public List<Encaissement> findByPeriodAndStatut(LocalDate dateDebut, LocalDate dateFin, StatutEncaissement statut) {
        // CORRECTION : Ignorer le statut car la colonne n'existe pas
        return findByPeriod(dateDebut, dateFin);
    }

    /**
     * CORRECTION : Trouve les encaissements d'une affaire pour une période donnée
     */
    public List<Encaissement> findByAffaireAndPeriod(Long affaireId, LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
            SELECT e.*, a.numero_affaire, a.montant_amende_total
            FROM encaissements e
            LEFT JOIN affaires a ON e.affaire_id = a.id
            WHERE e.affaire_id = ?
              AND e.date_encaissement BETWEEN ? AND ?
              AND e.statut = 'VALIDE'
            ORDER BY e.date_encaissement
        """;

        List<Encaissement> encaissements = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            stmt.setDate(2, Date.valueOf(dateDebut));
            stmt.setDate(3, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                encaissements.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par affaire et période", e);
        }

        return encaissements;
    }
}