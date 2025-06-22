package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Agent;
import com.regulation.contentieux.model.Bureau;
import com.regulation.contentieux.model.Service;
import com.regulation.contentieux.model.Contrevenant;
import com.regulation.contentieux.model.enums.StatutAffaire;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion des affaires contentieuses
 * Gère la persistance des affaires et leurs relations
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
            WHERE a.deleted = false 
            ORDER BY a.date_creation DESC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
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
            WHERE a.id = ? AND a.deleted = false
        """;
    }

    @Override
    protected Affaire mapResultSetToEntity(ResultSet rs) throws SQLException {
        Affaire affaire = new Affaire();

        // Champs de base
        affaire.setId(rs.getLong("id"));
        affaire.setNumeroAffaire(rs.getString("numero_affaire"));

        // Dates
        Date dateCreation = rs.getDate("date_creation");
        if (dateCreation != null) {
            affaire.setDateCreation(dateCreation.toLocalDate());
        }

        Date dateConstatation = rs.getDate("date_constatation");
        if (dateConstatation != null) {
            affaire.setDateConstatation(dateConstatation.toLocalDate());
        }

        affaire.setLieuConstatation(rs.getString("lieu_constatation"));
        affaire.setDescription(rs.getString("description"));

        // Montants
        BigDecimal montantTotal = rs.getBigDecimal("montant_total");
        affaire.setMontantTotal(montantTotal != null ? montantTotal : BigDecimal.ZERO);

        BigDecimal montantEncaisse = rs.getBigDecimal("montant_encaisse");
        affaire.setMontantEncaisse(montantEncaisse != null ? montantEncaisse : BigDecimal.ZERO);

        // Montant amende total (pour compatibilité)
        try {
            BigDecimal montantAmendeTotal = rs.getBigDecimal("montant_amende_total");
            if (montantAmendeTotal != null) {
                affaire.setMontantAmendeTotal(montantAmendeTotal);
            } else {
                affaire.setMontantAmendeTotal(affaire.getMontantTotal());
            }
        } catch (SQLException e) {
            // Colonne optionnelle
            affaire.setMontantAmendeTotal(affaire.getMontantTotal());
        }

        // Statut
        String statutStr = rs.getString("statut");
        if (statutStr != null) {
            try {
                affaire.setStatut(StatutAffaire.valueOf(statutStr));
            } catch (IllegalArgumentException e) {
                logger.warn("Statut invalide dans la base: {}", statutStr);
                affaire.setStatut(StatutAffaire.OUVERTE);
            }
        }

        affaire.setObservations(rs.getString("observations"));

        // IDs des relations
        long contrevenantId = rs.getLong("contrevenant_id");
        if (!rs.wasNull()) {
            affaire.setContrevenantId(contrevenantId);

            // Charger les infos de base du contrevenant si disponibles
            try {
                Contrevenant contrevenant = new Contrevenant();
                contrevenant.setId(contrevenantId);
                String nom = rs.getString("contrevenant_nom");
                String prenom = rs.getString("contrevenant_prenom");
                String raisonSociale = rs.getString("contrevenant_raison_sociale");

                if (nom != null) {
                    contrevenant.setNom(nom);
                    contrevenant.setPrenom(prenom);
                } else if (raisonSociale != null) {
                    contrevenant.setRaisonSociale(raisonSociale);
                }
                affaire.setContrevenant(contrevenant);
            } catch (SQLException e) {
                // Colonnes optionnelles
            }
        }

        long agentId = rs.getLong("agent_verbalisateur_id");
        if (!rs.wasNull()) {
            try {
                Agent agent = new Agent();
                agent.setId(agentId);
                agent.setCodeAgent(rs.getString("code_agent"));
                agent.setNom(rs.getString("agent_nom"));
                agent.setPrenom(rs.getString("agent_prenom"));
                affaire.setAgentVerbalisateur(agent);
            } catch (SQLException e) {
                // Colonnes optionnelles
            }
        }

        long bureauId = rs.getLong("bureau_id");
        if (!rs.wasNull()) {
            affaire.setBureauId(bureauId);
            try {
                Bureau bureau = new Bureau();
                bureau.setId(bureauId);
                bureau.setCodeBureau(rs.getString("code_bureau"));
                bureau.setNomBureau(rs.getString("nom_bureau"));
                affaire.setBureau(bureau);
            } catch (SQLException e) {
                // Colonnes optionnelles
            }
        }

        long serviceId = rs.getLong("service_id");
        if (!rs.wasNull()) {
            affaire.setServiceId(serviceId);
            try {
                Service service = new Service();
                service.setId(serviceId);
                service.setCodeService(rs.getString("code_service"));
                service.setNomService(rs.getString("nom_service"));
                affaire.setService(service);
            } catch (SQLException e) {
                // Colonnes optionnelles
            }
        }

        // Métadonnées
        affaire.setCreatedBy(rs.getString("created_by"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            affaire.setCreatedAt(createdAt.toLocalDateTime());
        }

        affaire.setUpdatedBy(rs.getString("updated_by"));

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            affaire.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        affaire.setDeleted(rs.getBoolean("deleted"));
        affaire.setDeletedBy(rs.getString("deleted_by"));

        Date deletedAt = rs.getDate("deleted_at");
        if (deletedAt != null) {
            affaire.setDeletedAt(deletedAt.toLocalDate());
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
     * Recherche d'affaires avec critères multiples
     */
    public List<Affaire> searchAffaires(String searchTerm, StatutAffaire statut,
                                        LocalDate dateDebut, LocalDate dateFin,
                                        Integer bureauId, int offset, int limit) {
        StringBuilder sql = new StringBuilder(getSelectAllQuery());
        List<Object> parameters = new ArrayList<>();

        // Ajouter les conditions WHERE
        sql.append(" AND 1=1"); // Pour simplifier l'ajout de conditions

        // Ajout des critères de recherche
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql.append(" AND a.numero_affaire LIKE ?");
            parameters.add("%" + searchTerm.trim() + "%");
        }

        if (statut != null) {
            sql.append(" AND a.statut = ?");
            parameters.add(statut.name());
        }

        if (dateDebut != null) {
            sql.append(" AND a.date_creation >= ?");
            parameters.add(Date.valueOf(dateDebut));
        }

        if (dateFin != null) {
            sql.append(" AND a.date_creation <= ?");
            parameters.add(Date.valueOf(dateFin));
        }

        // AJOUT du paramètre bureauId manquant
        if (bureauId != null) {
            sql.append(" AND a.bureau_id = ?");
            parameters.add(bureauId.longValue());
        }

        // Ajout de la pagination
        sql.append(" ORDER BY a.date_creation DESC LIMIT ? OFFSET ?");
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

            logger.debug("Recherche retournée {} affaires", affaires.size());

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
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM affaires WHERE deleted = false");
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

        // AJOUT du paramètre bureauId manquant
        if (bureauId != null) {
            sql.append(" AND bureau_id = ?");
            parameters.add(bureauId.longValue());
        }

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage avec critères", e);
        }

        return 0;
    }

    /**
     * Compte le nombre total d'affaires
     */
    public long count() {
        String sql = "SELECT COUNT(*) FROM affaires WHERE deleted = false";

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
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM affaires WHERE deleted = false");
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

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
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
        String sql = "SELECT COUNT(*) FROM affaires WHERE statut = ? AND deleted = false";

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
     * Compte les affaires créées dans une année
     */
    public long countByYear(int year) {
        String sql = "SELECT COUNT(*) FROM affaires WHERE strftime('%Y', date_creation) = ? AND deleted = false";

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
        String sql = """
            UPDATE affaires 
            SET deleted = true, deleted_by = ?, deleted_at = ?
            WHERE id = ?
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "SYSTEM"); // À remplacer par l'utilisateur courant
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(3, id);

            int updated = stmt.executeUpdate();

            if (updated > 0) {
                logger.info("Affaire {} supprimée logiquement", id);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la suppression logique de l'affaire", e);
            throw new RuntimeException("Erreur lors de la suppression", e);
        }
    }
}