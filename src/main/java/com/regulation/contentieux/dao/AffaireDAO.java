package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Contravention;
import com.regulation.contentieux.model.enums.StatutAffaire;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.regulation.contentieux.model.Contravention;
import com.regulation.contentieux.dao.ContraventionDAO;
import java.util.Optional;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DAO pour la gestion des affaires contentieuses
 * Version complète avec toutes les méthodes nécessaires
 */
public class AffaireDAO extends AbstractSQLiteDAO<Affaire, Long> {

    private static final Logger logger = LoggerFactory.getLogger(AffaireDAO.class);

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
            INSERT INTO affaires (numero_affaire, date_creation, montant_amende_total, 
                                statut, contrevenant_id, contravention_id, bureau_id, 
                                service_id, created_by) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE affaires 
            SET numero_affaire = ?, date_creation = ?, montant_amende_total = ?, 
                statut = ?, contrevenant_id = ?, contravention_id = ?, 
                bureau_id = ?, service_id = ?, updated_by = ?, 
                updated_at = CURRENT_TIMESTAMP 
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, numero_affaire, date_creation, montant_amende_total, 
                   statut, contrevenant_id, contravention_id, bureau_id, 
                   service_id, created_at, updated_at, created_by, updated_by 
            FROM affaires 
            ORDER BY created_at DESC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, numero_affaire, date_creation, montant_amende_total, 
                   statut, contrevenant_id, contravention_id, bureau_id, 
                   service_id, created_at, updated_at, created_by, updated_by 
            FROM affaires 
            WHERE id = ?
        """;
    }

    @Override
    protected Affaire mapResultSetToEntity(ResultSet rs) throws SQLException {
        Affaire affaire = new Affaire();

        affaire.setId(rs.getLong("id"));
        affaire.setNumeroAffaire(rs.getString("numero_affaire"));

        // CORRIGÉ: Gestion robuste des dates avec différents formats
        try {
            Date sqlDate = rs.getDate("date_creation");
            if (sqlDate != null) {
                affaire.setDateCreation(sqlDate.toLocalDate());
            }
        } catch (SQLException e) {
            // Fallback: essayer de parser la date comme String si le format direct échoue
            logger.debug("Échec du parsing direct de date, tentative avec String pour l'affaire {}", rs.getLong("id"));
            try {
                String dateStr = rs.getString("date_creation");
                if (dateStr != null && !dateStr.trim().isEmpty()) {
                    // Parser différents formats possibles
                    LocalDate parsedDate = parseDateString(dateStr);
                    affaire.setDateCreation(parsedDate);
                    logger.debug("Date parsée avec succès: {} -> {}", dateStr, parsedDate);
                } else {
                    logger.warn("Date de création nulle ou vide pour l'affaire {}", rs.getLong("id"));
                }
            } catch (Exception parseEx) {
                logger.error("Impossible de parser la date de création pour l'affaire {}: {}",
                        rs.getLong("id"), parseEx.getMessage());
                // Utiliser une date par défaut ou null
                affaire.setDateCreation(LocalDate.now());
            }
        }

        affaire.setMontantAmendeTotal(rs.getDouble("montant_amende_total"));

        // Conversion du statut
        String statutStr = rs.getString("statut");
        if (statutStr != null) {
            try {
                affaire.setStatut(StatutAffaire.valueOf(statutStr));
            } catch (IllegalArgumentException e) {
                logger.warn("Statut inconnu pour l'affaire {}: {}", affaire.getId(), statutStr);
                affaire.setStatut(StatutAffaire.OUVERTE); // Valeur par défaut
            }
        }

        affaire.setContrevenantId(rs.getLong("contrevenant_id"));
        affaire.setContraventionId(rs.getLong("contravention_id"));

        // Gestion des valeurs nullable
        long bureauId = rs.getLong("bureau_id");
        if (!rs.wasNull()) {
            affaire.setBureauId(bureauId);
        }

        long serviceId = rs.getLong("service_id");
        if (!rs.wasNull()) {
            affaire.setServiceId(serviceId);
        }

        // CORRIGÉ: Timestamps avec gestion d'erreur similaire
        try {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                affaire.setCreatedAt(createdAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de created_at pour l'affaire {}", affaire.getId());
            affaire.setCreatedAt(LocalDateTime.now());
        }

        try {
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                affaire.setUpdatedAt(updatedAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de updated_at pour l'affaire {}", affaire.getId());
            affaire.setUpdatedAt(LocalDateTime.now());
        }

        affaire.setCreatedBy(rs.getString("created_by"));
        affaire.setUpdatedBy(rs.getString("updated_by"));

        return affaire;
    }

    /**
     * Récupère la contravention associée à une affaire
     * REQUIS POUR RapportService
     */
    public Optional<Contravention> getContraventionByAffaire(Affaire affaire) {
        if (affaire.getContraventionId() == null) {
            return Optional.empty();
        }

        ContraventionDAO contraventionDAO = new ContraventionDAO();
        return contraventionDAO.findById(affaire.getContraventionId());
    }

    /**
     * Parse une date depuis différents formats string possibles
     */
    private LocalDate parseDateString(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        dateStr = dateStr.trim();

        // Format ISO (YYYY-MM-DD)
        if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(dateStr);
        }

        // Format français (DD/MM/YYYY)
        if (dateStr.matches("\\d{2}/\\d{2}/\\d{4}")) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return LocalDate.parse(dateStr, formatter);
        }

        // Format américain (MM/DD/YYYY)
        if (dateStr.matches("\\d{2}/\\d{2}/\\d{4}")) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
            return LocalDate.parse(dateStr, formatter);
        }

        // Si aucun format ne correspond, essayer la conversion directe
        throw new RuntimeException("Format de date non supporté: " + dateStr);
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Affaire affaire) throws SQLException {
        stmt.setString(1, affaire.getNumeroAffaire());
        stmt.setDate(2, Date.valueOf(affaire.getDateCreation()));
        stmt.setDouble(3, affaire.getMontantAmendeTotal());
        stmt.setString(4, affaire.getStatut().name());
        stmt.setLong(5, affaire.getContrevenantId());
        stmt.setLong(6, affaire.getContraventionId());

        if (affaire.getBureauId() != null) {
            stmt.setLong(7, affaire.getBureauId());
        } else {
            stmt.setNull(7, Types.BIGINT);
        }

        if (affaire.getServiceId() != null) {
            stmt.setLong(8, affaire.getServiceId());
        } else {
            stmt.setNull(8, Types.BIGINT);
        }

        stmt.setString(9, affaire.getCreatedBy());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Affaire affaire) throws SQLException {
        stmt.setString(1, affaire.getNumeroAffaire());
        stmt.setDate(2, Date.valueOf(affaire.getDateCreation()));
        stmt.setDouble(3, affaire.getMontantAmendeTotal());
        stmt.setString(4, affaire.getStatut().name());
        stmt.setLong(5, affaire.getContrevenantId());
        stmt.setLong(6, affaire.getContraventionId());

        if (affaire.getBureauId() != null) {
            stmt.setLong(7, affaire.getBureauId());
        } else {
            stmt.setNull(7, Types.BIGINT);
        }

        if (affaire.getServiceId() != null) {
            stmt.setLong(8, affaire.getServiceId());
        } else {
            stmt.setNull(8, Types.BIGINT);
        }

        stmt.setString(9, affaire.getUpdatedBy());
        stmt.setLong(10, affaire.getId());
    }

    @Override
    protected Long getEntityId(Affaire affaire) {
        return affaire.getId();
    }

    @Override
    protected void setEntityId(Affaire affaire, Long id) {
        affaire.setId(id);
    }

    // Méthodes spécifiques aux affaires

    /**
     * Trouve une affaire par son numéro
     */
    public Optional<Affaire> findByNumeroAffaire(String numeroAffaire) {
        String sql = """
            SELECT id, numero_affaire, date_creation, montant_amende_total, 
                   statut, contrevenant_id, contravention_id, bureau_id, 
                   service_id, created_at, updated_at, created_by, updated_by 
            FROM affaires 
            WHERE numero_affaire = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, numeroAffaire);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par numéro d'affaire: " + numeroAffaire, e);
        }

        return Optional.empty();
    }

    /**
     * Trouve les affaires par statut
     */
    public List<Affaire> findByStatut(StatutAffaire statut) {
        String sql = """
            SELECT id, numero_affaire, date_creation, montant_amende_total, 
                   statut, contrevenant_id, contravention_id, bureau_id, 
                   service_id, created_at, updated_at, created_by, updated_by 
            FROM affaires 
            WHERE statut = ? 
            ORDER BY created_at DESC
        """;

        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, statut.name());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                affaires.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par statut: " + statut, e);
        }

        return affaires;
    }

    /**
     * Trouve les affaires par contrevenant
     */
    public List<Affaire> findByContrevenantId(Long contrevenantId) {
        String sql = """
            SELECT id, numero_affaire, date_creation, montant_amende_total, 
                   statut, contrevenant_id, contravention_id, bureau_id, 
                   service_id, created_at, updated_at, created_by, updated_by 
            FROM affaires 
            WHERE contrevenant_id = ? 
            ORDER BY created_at DESC
        """;

        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, contrevenantId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                affaires.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par contrevenant: " + contrevenantId, e);
        }

        return affaires;
    }

    /**
     * Trouve les affaires créées dans une période
     */
    public List<Affaire> findByDateCreationBetween(LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
            SELECT id, numero_affaire, date_creation, montant_amende_total, 
                   statut, contrevenant_id, contravention_id, bureau_id, 
                   service_id, created_at, updated_at, created_by, updated_by 
            FROM affaires 
            WHERE date_creation BETWEEN ? AND ? 
            ORDER BY created_at DESC
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
     * Recherche d'affaires avec critères multiples
     */
    public List<Affaire> searchAffaires(String numeroAffaire, StatutAffaire statut,
                                        LocalDate dateDebut, LocalDate dateFin,
                                        Long contrevenantId, int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, numero_affaire, date_creation, montant_amende_total, ");
        sql.append("statut, contrevenant_id, contravention_id, bureau_id, ");
        sql.append("service_id, created_at, updated_at, created_by, updated_by ");
        sql.append("FROM affaires WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (numeroAffaire != null && !numeroAffaire.trim().isEmpty()) {
            sql.append("AND numero_affaire LIKE ? ");
            parameters.add("%" + numeroAffaire.trim() + "%");
        }

        if (statut != null) {
            sql.append("AND statut = ? ");
            parameters.add(statut.name());
        }

        if (dateDebut != null) {
            sql.append("AND date_creation >= ? ");
            parameters.add(Date.valueOf(dateDebut));
        }

        if (dateFin != null) {
            sql.append("AND date_creation <= ? ");
            parameters.add(Date.valueOf(dateFin));
        }

        if (contrevenantId != null) {
            sql.append("AND contrevenant_id = ? ");
            parameters.add(contrevenantId);
        }

        sql.append("ORDER BY created_at DESC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                affaires.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche d'affaires", e);
        }

        return affaires;
    }

    /**
     * Compte les affaires correspondant aux critères de recherche
     */
    public long countSearchAffaires(String numeroAffaire, StatutAffaire statut,
                                    LocalDate dateDebut, LocalDate dateFin,
                                    Long contrevenantId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM affaires WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (numeroAffaire != null && !numeroAffaire.trim().isEmpty()) {
            sql.append("AND numero_affaire LIKE ? ");
            parameters.add("%" + numeroAffaire.trim() + "%");
        }

        if (statut != null) {
            sql.append("AND statut = ? ");
            parameters.add(statut.name());
        }

        if (dateDebut != null) {
            sql.append("AND date_creation >= ? ");
            parameters.add(Date.valueOf(dateDebut));
        }

        if (dateFin != null) {
            sql.append("AND date_creation <= ? ");
            parameters.add(Date.valueOf(dateFin));
        }

        if (contrevenantId != null) {
            sql.append("AND contrevenant_id = ? ");
            parameters.add(contrevenantId);
        }

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
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
     * Génère le prochain numéro d'affaire selon le format YYMMNNNN
     * YY = année sur 2 chiffres
     * MM = mois sur 2 chiffres
     * NNNN = incrémentation du mois (0001, 0002, etc.)
     */
    public String generateNextNumeroAffaire() {
        LocalDate today = LocalDate.now();
        String yearMonth = String.format("%02d%02d", today.getYear() % 100, today.getMonthValue());

        // Rechercher le dernier numéro du mois en cours
        String sql = """
            SELECT numero_affaire FROM affaires 
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
                return generateNextNumeroFromLast(lastNumero, yearMonth);
            } else {
                // Premier numéro du mois
                return yearMonth + "0001";
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du numéro d'affaire", e);
            return yearMonth + "0001"; // Fallback
        }
    }

    /**
     * Génère le numéro suivant basé sur le dernier numéro du mois
     */
    private String generateNextNumeroFromLast(String lastNumero, String currentYearMonth) {
        try {
            // Vérifier que le dernier numéro correspond bien au mois en cours
            if (lastNumero != null && lastNumero.length() == 8) {
                String lastYearMonth = lastNumero.substring(0, 4);

                if (lastYearMonth.equals(currentYearMonth)) {
                    // Même mois, incrémenter le compteur
                    String compteurStr = lastNumero.substring(4);
                    int compteur = Integer.parseInt(compteurStr);
                    return currentYearMonth + String.format("%04d", compteur + 1);
                }
            }

            // Nouveau mois ou format invalide
            return currentYearMonth + "0001";

        } catch (Exception e) {
            logger.warn("Erreur lors du parsing du dernier numéro: {}", lastNumero, e);
            return currentYearMonth + "0001";
        }
    }

    /**
     * Vérifie si un numéro d'affaire existe déjà
     */
    public boolean existsByNumeroAffaire(String numeroAffaire) {
        String sql = "SELECT 1 FROM affaires WHERE numero_affaire = ? LIMIT 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, numeroAffaire);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification du numéro d'affaire", e);
            return false;
        }
    }

    /**
     * Statistiques des affaires par statut
     */
    public Map<StatutAffaire, Long> getStatutStatistics() {
        String sql = "SELECT statut, COUNT(*) as count FROM affaires GROUP BY statut";
        Map<StatutAffaire, Long> stats = new HashMap<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String statutStr = rs.getString("statut");
                long count = rs.getLong("count");

                try {
                    StatutAffaire statut = StatutAffaire.valueOf(statutStr);
                    stats.put(statut, count);
                } catch (IllegalArgumentException e) {
                    logger.warn("Statut inconnu trouvé en base: {}", statutStr);
                }
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des statistiques", e);
        }

        return stats;
    }

    /**
     * Montant total des amendes par période
     */
    public Double getTotalAmendesByPeriod(LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
            SELECT SUM(montant_amende_total) as total 
            FROM affaires 
            WHERE date_creation BETWEEN ? AND ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(dateDebut));
            stmt.setDate(2, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("total");
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du calcul du total des amendes", e);
        }

        return 0.0;
    }

    /**
     * Affaires récentes pour tableau de bord
     */
    public List<Affaire> getRecentAffaires(int limit) {
        String sql = getSelectAllQuery() + " LIMIT ?";

        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                affaires.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des affaires récentes", e);
        }

        return affaires;
    }

    /**
     * Met à jour le statut d'une affaire
     */
    public boolean updateStatut(Long affaireId, StatutAffaire nouveauStatut, String updatedBy) {
        String sql = """
            UPDATE affaires 
            SET statut = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP 
            WHERE id = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nouveauStatut.name());
            stmt.setString(2, updatedBy);
            stmt.setLong(3, affaireId);

            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Statut de l'affaire {} mis à jour vers {} par {}",
                        affaireId, nouveauStatut, updatedBy);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la mise à jour du statut", e);
        }

        return false;
    }

    // MÉTHODES À AJOUTER À LA FIN DE LA CLASSE AffaireDAO

    /**
     * Trouve les affaires qui ont des encaissements dans une période donnée
     * MÉTHODE MANQUANTE POUR RapportService
     */
    public List<Affaire> findAffairesWithEncaissementsByPeriod(LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
        SELECT DISTINCT a.id, a.numero_affaire, a.date_creation, a.montant_amende_total, 
               a.statut, a.contrevenant_id, a.contravention_id, a.bureau_id, 
               a.service_id, a.created_at, a.updated_at, a.created_by, a.updated_by 
        FROM affaires a 
        INNER JOIN encaissements e ON a.id = e.affaire_id 
        WHERE e.date_encaissement BETWEEN ? AND ? 
        AND e.statut = 'VALIDE'
        ORDER BY a.created_at DESC
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

            logger.debug("Trouvé {} affaires avec encaissements entre {} et {}",
                    affaires.size(), dateDebut, dateFin);

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche d'affaires avec encaissements par période", e);
        }

        return affaires;
    }
}