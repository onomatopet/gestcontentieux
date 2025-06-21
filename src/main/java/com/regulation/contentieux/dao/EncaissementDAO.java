package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Encaissement;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.enums.ModeReglement;
import com.regulation.contentieux.model.enums.StatutEncaissement;
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
            INSERT INTO encaissements (reference, date_encaissement, montant_encaisse, 
                                     mode_reglement, numero_piece, banque, observations,
                                     statut, affaire_id, created_by, created_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE encaissements 
            SET reference = ?, date_encaissement = ?, montant_encaisse = ?,
                mode_reglement = ?, numero_piece = ?, banque = ?, observations = ?,
                statut = ?, affaire_id = ?, updated_by = ?, updated_at = ?
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT e.*, a.numero_affaire, a.montant_total
            FROM encaissements e
            LEFT JOIN affaires a ON e.affaire_id = a.id
            ORDER BY e.date_encaissement DESC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT e.*, a.numero_affaire, a.montant_total
            FROM encaissements e
            LEFT JOIN affaires a ON e.affaire_id = a.id
            WHERE e.id = ?
        """;
    }

    @Override
    protected Encaissement mapResultSetToEntity(ResultSet rs) throws SQLException {
        Encaissement encaissement = new Encaissement();

        encaissement.setId(rs.getLong("id"));
        encaissement.setReference(rs.getString("reference"));

        Date dateEnc = rs.getDate("date_encaissement");
        if (dateEnc != null) {
            encaissement.setDateEncaissement(dateEnc.toLocalDate());
        }

        encaissement.setMontantEncaisse(rs.getBigDecimal("montant_encaisse"));

        // Mode de règlement
        String modeStr = rs.getString("mode_reglement");
        if (modeStr != null) {
            try {
                encaissement.setModeReglement(ModeReglement.valueOf(modeStr));
            } catch (IllegalArgumentException e) {
                logger.warn("Mode de règlement invalide: {}", modeStr);
            }
        }

        encaissement.setNumeroPiece(rs.getString("numero_piece"));
        encaissement.setBanque(rs.getString("banque"));
        encaissement.setObservations(rs.getString("observations"));

        // Statut
        String statutStr = rs.getString("statut");
        if (statutStr != null) {
            try {
                encaissement.setStatut(StatutEncaissement.valueOf(statutStr));
            } catch (IllegalArgumentException e) {
                logger.warn("Statut invalide: {}", statutStr);
                encaissement.setStatut(StatutEncaissement.EN_ATTENTE);
            }
        }

        // Affaire liée (relation simplifiée)
        Long affaireId = rs.getLong("affaire_id");
        if (affaireId != null && affaireId > 0) {
            Affaire affaire = new Affaire();
            affaire.setId(affaireId);
            try {
                affaire.setNumeroAffaire(rs.getString("numero_affaire"));
                affaire.setMontantTotal(rs.getBigDecimal("montant_total"));
            } catch (SQLException e) {
                // Colonnes optionnelles
            }
            encaissement.setAffaire(affaire);
        }

        // Métadonnées
        encaissement.setCreatedBy(rs.getString("created_by"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            encaissement.setCreatedAt(createdAt.toLocalDateTime());
        }

        encaissement.setUpdatedBy(rs.getString("updated_by"));
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            encaissement.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        return encaissement;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Encaissement encaissement) throws SQLException {
        stmt.setString(1, encaissement.getReference());
        stmt.setDate(2, Date.valueOf(encaissement.getDateEncaissement()));
        stmt.setBigDecimal(3, encaissement.getMontantEncaisse());
        stmt.setString(4, encaissement.getModeReglement().name());
        stmt.setString(5, encaissement.getNumeroPiece());
        stmt.setString(6, encaissement.getBanque());
        stmt.setString(7, encaissement.getObservations());
        stmt.setString(8, encaissement.getStatut().name());

        if (encaissement.getAffaire() != null) {
            stmt.setLong(9, encaissement.getAffaire().getId());
        } else {
            stmt.setNull(9, Types.BIGINT);
        }

        stmt.setString(10, encaissement.getCreatedBy());
        stmt.setTimestamp(11, Timestamp.valueOf(encaissement.getCreatedAt()));
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Encaissement encaissement) throws SQLException {
        setInsertParameters(stmt, encaissement); // Les 11 premiers paramètres
        stmt.setString(10, encaissement.getUpdatedBy());
        stmt.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
        stmt.setLong(12, encaissement.getId());
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
                "WHERE e.reference = ?");

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
        String sql = "SELECT COUNT(*) FROM encaissements WHERE reference = ?";

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
     * Génère le prochain numéro d'encaissement
     */
    public String generateNextNumeroEncaissement() {
        String sql = """
            SELECT COUNT(*) + 1 as next_num 
            FROM encaissements 
            WHERE strftime('%Y', date_encaissement) = strftime('%Y', 'now')
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int nextNum = rs.getInt("next_num");
                int year = LocalDate.now().getYear();
                return String.format("ENC-%d-%05d", year, nextNum);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du numéro d'encaissement", e);
        }

        // Valeur par défaut
        return String.format("ENC-%d-%05d", LocalDate.now().getYear(), 1);
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
     * Recherche d'encaissements avec critères multiples
     */
    public List<Encaissement> searchEncaissements(String reference, StatutEncaissement statut,
                                                  ModeReglement modeReglement, LocalDate dateDebut,
                                                  LocalDate dateFin, Long affaireId,
                                                  int offset, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT e.*, a.numero_affaire, a.montant_total " +
                        "FROM encaissements e " +
                        "LEFT JOIN affaires a ON e.affaire_id = a.id " +
                        "WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (reference != null && !reference.trim().isEmpty()) {
            sql.append("AND e.reference LIKE ? ");
            parameters.add("%" + reference.trim() + "%");
        }

        if (statut != null) {
            sql.append("AND e.statut = ? ");
            parameters.add(statut.name());
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
     * Trouve les encaissements pour une période donnée (pour les rapports)
     */
    public List<Encaissement> findByPeriod(LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
            SELECT e.*, a.numero_affaire, a.montant_total
            FROM encaissements e
            LEFT JOIN affaires a ON e.affaire_id = a.id
            WHERE e.date_encaissement BETWEEN ? AND ?
              AND e.statut = 'VALIDE'
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
}