package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Encaissement;
import com.regulation.contentieux.model.enums.ModeReglement;
import com.regulation.contentieux.model.enums.StatutAffaire;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.sql.DriverManager.getConnection;

/**
 * DAO pour la gestion des encaissements - VERSION COMPLÈTE AVEC MÉTHODES MANQUANTES
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
            INSERT INTO encaissements (affaire_id, montant_encaisse, date_encaissement, 
                                     mode_reglement, reference, banque_id, statut, observations, created_by) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE encaissements 
            SET affaire_id = ?, montant_encaisse = ?, date_encaissement = ?, 
                mode_reglement = ?, reference = ?, banque_id = ?, statut = ?, 
                observations = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP 
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, affaire_id, montant_encaisse, date_encaissement, mode_reglement, 
                   reference, banque_id, statut, observations, created_at, updated_at, 
                   created_by, updated_by 
            FROM encaissements 
            ORDER BY created_at DESC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, affaire_id, montant_encaisse, date_encaissement, mode_reglement, 
                   reference, banque_id, statut, observations, created_at, updated_at, 
                   created_by, updated_by 
            FROM encaissements 
            WHERE id = ?
        """;
    }

    @Override
    protected Encaissement mapResultSetToEntity(ResultSet rs) throws SQLException {
        Encaissement encaissement = new Encaissement();

        encaissement.setId(rs.getLong("id"));
        encaissement.setAffaireId(rs.getLong("affaire_id"));
        encaissement.setMontantEncaisse(rs.getDouble("montant_encaisse"));

        // Gestion de la date d'encaissement
        Date dateEncaissement = rs.getDate("date_encaissement");
        if (dateEncaissement != null) {
            encaissement.setDateEncaissement(dateEncaissement.toLocalDate());
        }

        // Gestion du mode de règlement
        String modeReglementStr = rs.getString("mode_reglement");
        if (modeReglementStr != null) {
            try {
                encaissement.setModeReglement(ModeReglement.valueOf(modeReglementStr));
            } catch (IllegalArgumentException e) {
                logger.warn("Mode de règlement inconnu: {}", modeReglementStr);
            }
        }

        encaissement.setReference(rs.getString("reference"));
        encaissement.setBanqueId(rs.getLong("banque_id"));
        encaissement.setObservations(rs.getString("observations")); // AJOUT MANQUANT

        // Gestion du statut
        String statutStr = rs.getString("statut");
        if (statutStr != null) {
            try {
                encaissement.setStatut(StatutEncaissement.valueOf(statutStr));
            } catch (IllegalArgumentException e) {
                logger.warn("Statut d'encaissement inconnu: {}", statutStr);
                encaissement.setStatut(StatutEncaissement.EN_ATTENTE);
            }
        }

        encaissement.setCreatedBy(rs.getString("created_by"));
        encaissement.setUpdatedBy(rs.getString("updated_by"));

        // Gestion des timestamps
        try {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                encaissement.setCreatedAt(createdAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de created_at pour l'encaissement {}", encaissement.getId());
        }

        try {
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                encaissement.setUpdatedAt(updatedAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de updated_at pour l'encaissement {}", encaissement.getId());
        }

        return encaissement;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Encaissement encaissement) throws SQLException {
        stmt.setLong(1, encaissement.getAffaireId());
        stmt.setDouble(2, encaissement.getMontantEncaisse() != null ? encaissement.getMontantEncaisse() : 0.0);

        if (encaissement.getDateEncaissement() != null) {
            stmt.setDate(3, Date.valueOf(encaissement.getDateEncaissement()));
        } else {
            stmt.setNull(3, Types.DATE);
        }

        stmt.setString(4, encaissement.getModeReglement() != null ? encaissement.getModeReglement().name() : null);
        stmt.setString(5, encaissement.getReference());
        stmt.setObject(6, encaissement.getBanqueId(), Types.BIGINT);
        stmt.setString(7, encaissement.getStatut() != null ? encaissement.getStatut().name() : StatutEncaissement.EN_ATTENTE.name());
        stmt.setString(8, encaissement.getObservations()); // AJOUT MANQUANT
        stmt.setString(9, encaissement.getCreatedBy());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Encaissement encaissement) throws SQLException {
        setInsertParameters(stmt, encaissement);
        stmt.setString(10, encaissement.getUpdatedBy());
        stmt.setLong(11, encaissement.getId());
    }

    @Override
    protected Long getEntityId(Encaissement encaissement) {
        return encaissement.getId();
    }

    @Override
    protected void setEntityId(Encaissement encaissement, Long id) {
        encaissement.setId(id);
    }

    // ========== MÉTHODES MANQUANTES AJOUTÉES ==========

    /**
     * Trouve les encaissements par affaire et période - MÉTHODE MANQUANTE AJOUTÉE
     */
    public List<Encaissement> findByAffaireAndPeriod(Long affaireId, LocalDate dateDebut, LocalDate dateFin) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, affaire_id, montant_encaisse, date_encaissement, mode_reglement, ");
        sql.append("reference, banque_id, statut, observations, created_at, updated_at, created_by, updated_by ");
        sql.append("FROM encaissements WHERE affaire_id = ? ");

        List<Object> parameters = new ArrayList<>();
        parameters.add(affaireId);

        if (dateDebut != null) {
            sql.append("AND date_encaissement >= ? ");
            parameters.add(dateDebut);
        }

        if (dateFin != null) {
            sql.append("AND date_encaissement <= ? ");
            parameters.add(dateFin);
        }

        sql.append("ORDER BY date_encaissement DESC");

        List<Encaissement> encaissements = new ArrayList<>();

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
                encaissements.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche des encaissements par affaire et période", e);
        }

        return encaissements;
    }

    /**
     * Trouve les encaissements par statut - MÉTHODE MANQUANTE AJOUTÉE
     */
    public List<Encaissement> findByStatut(StatutEncaissement statut) {
        String sql = """
            SELECT id, affaire_id, montant_encaisse, date_encaissement, mode_reglement, 
                   reference, banque_id, statut, observations, created_at, updated_at, 
                   created_by, updated_by 
            FROM encaissements 
            WHERE statut = ?
            ORDER BY created_at DESC
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
            logger.error("Erreur lors de la recherche des encaissements par statut: " + statut, e);
        }

        return encaissements;
    }

    /**
     * Calcule le total des encaissements par période - MÉTHODE MANQUANTE AJOUTÉE
     */
    public Double getTotalEncaissementsByPeriod(LocalDate dateDebut, LocalDate dateFin, StatutEncaissement statut) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT SUM(montant_encaisse) as total FROM encaissements WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (dateDebut != null) {
            sql.append("AND date_encaissement >= ? ");
            parameters.add(dateDebut);
        }

        if (dateFin != null) {
            sql.append("AND date_encaissement <= ? ");
            parameters.add(dateFin);
        }

        if (statut != null) {
            sql.append("AND statut = ? ");
            parameters.add(statut.name());
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
                return rs.getDouble("total");
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du calcul du total des encaissements", e);
        }

        return 0.0;
    }

    /**
     * Recherche d'encaissements avec critères multiples - MÉTHODE MANQUANTE AJOUTÉE
     */
    public List<Encaissement> searchEncaissements(String reference, StatutEncaissement statut,
                                                  ModeReglement modeReglement, LocalDate dateDebut,
                                                  LocalDate dateFin, Long affaireId,
                                                  int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, affaire_id, montant_encaisse, date_encaissement, mode_reglement, ");
        sql.append("reference, banque_id, statut, observations, created_at, updated_at, created_by, updated_by ");
        sql.append("FROM encaissements WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (reference != null && !reference.trim().isEmpty()) {
            sql.append("AND reference LIKE ? ");
            parameters.add("%" + reference.trim() + "%");
        }

        if (statut != null) {
            sql.append("AND statut = ? ");
            parameters.add(statut.name());
        }

        if (modeReglement != null) {
            sql.append("AND mode_reglement = ? ");
            parameters.add(modeReglement.name());
        }

        if (dateDebut != null) {
            sql.append("AND date_encaissement >= ? ");
            parameters.add(dateDebut);
        }

        if (dateFin != null) {
            sql.append("AND date_encaissement <= ? ");
            parameters.add(dateFin);
        }

        if (affaireId != null) {
            sql.append("AND affaire_id = ? ");
            parameters.add(affaireId);
        }

        sql.append("ORDER BY created_at DESC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Encaissement> encaissements = new ArrayList<>();

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
                encaissements.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche d'encaissements", e);
        }

        return encaissements;
    }

    /**
     * Compte les encaissements correspondant aux critères - MÉTHODE MANQUANTE AJOUTÉE
     */
    public long countSearchEncaissements(String reference, StatutEncaissement statut,
                                         ModeReglement modeReglement, LocalDate dateDebut,
                                         LocalDate dateFin, Long affaireId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM encaissements WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (reference != null && !reference.trim().isEmpty()) {
            sql.append("AND reference LIKE ? ");
            parameters.add("%" + reference.trim() + "%");
        }

        if (statut != null) {
            sql.append("AND statut = ? ");
            parameters.add(statut.name());
        }

        if (modeReglement != null) {
            sql.append("AND mode_reglement = ? ");
            parameters.add(modeReglement.name());
        }

        if (dateDebut != null) {
            sql.append("AND date_encaissement >= ? ");
            parameters.add(dateDebut);
        }

        if (dateFin != null) {
            sql.append("AND date_encaissement <= ? ");
            parameters.add(dateFin);
        }

        if (affaireId != null) {
            sql.append("AND affaire_id = ? ");
            parameters.add(affaireId);
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
            logger.error("Erreur lors du comptage des encaissements", e);
        }

        return 0;
    }

    /**
     * Recherche des affaires avec pagination
     * CORRIGER la signature pour correspondre aux appels
     */
    public List<Affaire> searchAffaires(String searchText, StatutAffaire statut,
                                        LocalDate dateDebut, LocalDate dateFin,
                                        Integer contrevenantId, int offset, int limit) {
        StringBuilder query = new StringBuilder("SELECT * FROM affaires WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (searchText != null && !searchText.trim().isEmpty()) {
            query.append(" AND (numero_affaire LIKE ? OR observations LIKE ?)");
            params.add("%" + searchText + "%");
            params.add("%" + searchText + "%");
        }

        if (statut != null) {
            query.append(" AND statut = ?");
            params.add(statut.name());
        }

        if (dateDebut != null) {
            query.append(" AND date_creation >= ?");
            params.add(dateDebut);
        }

        if (dateFin != null) {
            query.append(" AND date_creation <= ?");
            params.add(dateFin);
        }

        if (contrevenantId != null) {
            query.append(" AND contrevenant_id = ?");
            params.add(contrevenantId);
        }

        query.append(" ORDER BY date_creation DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return executeQuery(query.toString(), params.toArray());
    }

    /**
     * Compte les affaires correspondant aux critères de recherche
     * CORRIGER la signature pour correspondre aux appels
     */
    public long countSearchAffaires(String searchText, StatutAffaire statut,
                                    LocalDate dateDebut, LocalDate dateFin,
                                    Integer contrevenantId) {
        StringBuilder query = new StringBuilder("SELECT COUNT(*) FROM affaires WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (searchText != null && !searchText.trim().isEmpty()) {
            query.append(" AND (numero_affaire LIKE ? OR observations LIKE ?)");
            params.add("%" + searchText + "%");
            params.add("%" + searchText + "%");
        }

        if (statut != null) {
            query.append(" AND statut = ?");
            params.add(statut.name());
        }

        if (dateDebut != null) {
            query.append(" AND date_creation >= ?");
            params.add(dateDebut);
        }

        if (dateFin != null) {
            query.append(" AND date_creation <= ?");
            params.add(dateFin);
        }

        if (contrevenantId != null) {
            query.append(" AND contrevenant_id = ?");
            params.add(contrevenantId);
        }

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        } catch (SQLException e) {
            logger.error("Erreur lors du comptage des affaires", e);
            return 0;
        }
    }
}