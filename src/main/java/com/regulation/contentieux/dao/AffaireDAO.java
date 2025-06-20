package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Contravention;
import com.regulation.contentieux.model.enums.StatutAffaire;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * VERSION NETTOYÉE - IMPORTS DUPLIQUÉS SUPPRIMÉS
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

        // Gestion de la date de création
        Date dateCreation = rs.getDate("date_creation");
        if (dateCreation != null) {
            affaire.setDateCreation(dateCreation.toLocalDate());
        }

        affaire.setMontantAmendeTotal(rs.getDouble("montant_amende_total"));

        // Gestion du statut
        String statutStr = rs.getString("statut");
        if (statutStr != null) {
            try {
                affaire.setStatut(StatutAffaire.valueOf(statutStr));
            } catch (IllegalArgumentException e) {
                logger.warn("Statut d'affaire inconnu: {}", statutStr);
                affaire.setStatut(StatutAffaire.OUVERTE);
            }
        }

        affaire.setContrevenantId(rs.getLong("contrevenant_id"));
        affaire.setContraventionId(rs.getLong("contravention_id"));
        affaire.setBureauId(rs.getLong("bureau_id"));
        affaire.setServiceId(rs.getLong("service_id"));
        affaire.setCreatedBy(rs.getString("created_by"));
        affaire.setUpdatedBy(rs.getString("updated_by"));

        // Gestion des timestamps
        try {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                affaire.setCreatedAt(createdAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de created_at pour l'affaire {}", affaire.getId());
        }

        try {
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                affaire.setUpdatedAt(updatedAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de updated_at pour l'affaire {}", affaire.getId());
        }

        return affaire;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Affaire affaire) throws SQLException {
        stmt.setString(1, affaire.getNumeroAffaire());

        if (affaire.getDateCreation() != null) {
            stmt.setDate(2, Date.valueOf(affaire.getDateCreation()));
        } else {
            stmt.setNull(2, Types.DATE);
        }

        stmt.setDouble(3, affaire.getMontantAmendeTotal() != null ? affaire.getMontantAmendeTotal() : 0.0);
        stmt.setString(4, affaire.getStatut() != null ? affaire.getStatut().name() : StatutAffaire.OUVERTE.name());

        stmt.setObject(5, affaire.getContrevenantId(), Types.BIGINT);
        stmt.setObject(6, affaire.getContraventionId(), Types.BIGINT);
        stmt.setObject(7, affaire.getBureauId(), Types.BIGINT);
        stmt.setObject(8, affaire.getServiceId(), Types.BIGINT);

        stmt.setString(9, affaire.getCreatedBy());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Affaire affaire) throws SQLException {
        setInsertParameters(stmt, affaire);
        stmt.setString(10, affaire.getUpdatedBy());
        stmt.setLong(11, affaire.getId());
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
     * Recherche d'affaires avec critères multiples
     */
    public List<Affaire> searchAffaires(String numeroOuContrevenant, StatutAffaire statut,
                                        LocalDate dateDebut, LocalDate dateFin,
                                        int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, numero_affaire, date_creation, montant_amende_total, ");
        sql.append("statut, contrevenant_id, contravention_id, bureau_id, service_id, ");
        sql.append("created_at, updated_at, created_by, updated_by ");
        sql.append("FROM affaires WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (numeroOuContrevenant != null && !numeroOuContrevenant.trim().isEmpty()) {
            sql.append("AND numero_affaire LIKE ? ");
            parameters.add("%" + numeroOuContrevenant.trim() + "%");
        }

        if (statut != null) {
            sql.append("AND statut = ? ");
            parameters.add(statut.name());
        }

        if (dateDebut != null) {
            sql.append("AND date_creation >= ? ");
            parameters.add(dateDebut);
        }

        if (dateFin != null) {
            sql.append("AND date_creation <= ? ");
            parameters.add(dateFin);
        }

        sql.append("ORDER BY created_at DESC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                Object param = parameters.get(i);
                if (param instanceof LocalDate) {
                    stmt.setDate(i + 1, Date.valueOf((LocalDate) param));
                } else {
                    stmt.setObject(i + 1, param);
                }
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
     * Compte les affaires correspondant aux critères
     */
    public long countSearchAffaires(String numeroOuContrevenant, StatutAffaire statut,
                                    LocalDate dateDebut, LocalDate dateFin) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM affaires WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (numeroOuContrevenant != null && !numeroOuContrevenant.trim().isEmpty()) {
            sql.append("AND numero_affaire LIKE ? ");
            parameters.add("%" + numeroOuContrevenant.trim() + "%");
        }

        if (statut != null) {
            sql.append("AND statut = ? ");
            parameters.add(statut.name());
        }

        if (dateDebut != null) {
            sql.append("AND date_creation >= ? ");
            parameters.add(dateDebut);
        }

        if (dateFin != null) {
            sql.append("AND date_creation <= ? ");
            parameters.add(dateFin);
        }

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                Object param = parameters.get(i);
                if (param instanceof LocalDate) {
                    stmt.setDate(i + 1, Date.valueOf((LocalDate) param));
                } else {
                    stmt.setObject(i + 1, param);
                }
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
     * Trouve une affaire par son numéro
     */
    public Optional<Affaire> findByNumeroAffaire(String numeroAffaire) {
        String sql = getSelectAllQuery().replace("ORDER BY created_at DESC", "WHERE numero_affaire = ?");

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
     * Génère le prochain numéro d'affaire
     */
    public String generateNextNumeroAffaire() {
        LocalDate today = LocalDate.now();
        String prefix = today.format(DateTimeFormatter.ofPattern("yyyyMM"));

        String sql = """
            SELECT numero_affaire FROM affaires 
            WHERE numero_affaire LIKE ? 
            ORDER BY numero_affaire DESC 
            LIMIT 1
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefix + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastNumero = rs.getString("numero_affaire");
                return generateNextNumeroFromLast(lastNumero, prefix);
            } else {
                return prefix + "001";
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du numéro d'affaire", e);
            return prefix + "001";
        }
    }

    private String generateNextNumeroFromLast(String lastNumero, String prefix) {
        try {
            if (lastNumero != null && lastNumero.startsWith(prefix) && lastNumero.length() == 9) {
                String numericPart = lastNumero.substring(6);
                int lastNumber = Integer.parseInt(numericPart);
                return prefix + String.format("%03d", lastNumber + 1);
            }
            return prefix + "001";
        } catch (Exception e) {
            logger.warn("Erreur lors du parsing du dernier numéro d'affaire: {}", lastNumero, e);
            return prefix + "001";
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
            logger.error("Erreur lors de la récupération des affaires par contrevenant: " + contrevenantId, e);
        }

        return affaires;
    }

    /**
     * Statistiques des affaires par statut
     */
    public Map<StatutAffaire, Long> getStatistiquesParStatut() {
        String sql = """
            SELECT statut, COUNT(*) as count 
            FROM affaires 
            GROUP BY statut
        """;

        Map<StatutAffaire, Long> stats = new HashMap<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String statutStr = rs.getString("statut");
                Long count = rs.getLong("count");

                try {
                    StatutAffaire statut = StatutAffaire.valueOf(statutStr);
                    stats.put(statut, count);
                } catch (IllegalArgumentException e) {
                    logger.warn("Statut inconnu trouvé: {}", statutStr);
                }
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des statistiques par statut", e);
        }

        return stats;
    }

    /**
     * Calcule le montant total des amendes pour une période
     */
    public Double getMontantTotalAmendes(LocalDate dateDebut, LocalDate dateFin) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT SUM(montant_amende_total) as total FROM affaires WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (dateDebut != null) {
            sql.append("AND date_creation >= ? ");
            parameters.add(dateDebut);
        }

        if (dateFin != null) {
            sql.append("AND date_creation <= ? ");
            parameters.add(dateFin);
        }

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setDate(i + 1, Date.valueOf((LocalDate) parameters.get(i)));
            }

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("total");
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du calcul du montant total des amendes", e);
        }

        return 0.0;
    }

    /**
     * Obtient les affaires récentes pour le tableau de bord
     */
    public List<Affaire> getRecentAffaires(int limit) {
        String sql = """
            SELECT id, numero_affaire, date_creation, montant_amende_total, 
                   statut, contrevenant_id, contravention_id, bureau_id, 
                   service_id, created_at, updated_at, created_by, updated_by 
            FROM affaires 
            ORDER BY created_at DESC 
            LIMIT ?
        """;

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
     * Trouve les affaires avec encaissements par période - MÉTHODE MANQUANTE AJOUTÉE
     */
    public List<Affaire> findAffairesWithEncaissementsByPeriod(LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
            SELECT DISTINCT a.id, a.numero_affaire, a.date_creation, a.montant_amende_total, 
                   a.statut, a.contrevenant_id, a.contravention_id, a.bureau_id, 
                   a.service_id, a.created_at, a.updated_at, a.created_by, a.updated_by 
            FROM affaires a
            INNER JOIN encaissements e ON a.id = e.affaire_id
            WHERE e.date_encaissement >= ? AND e.date_encaissement <= ?
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

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des affaires avec encaissements par période", e);
        }

        return affaires;
    }
}