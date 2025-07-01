package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Centre;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion des centres - ENRICHI
 * CORRECTION BUG SÉRIE 2 : Ajout de la méthode findAllActifs()
 */
public class CentreDAO extends AbstractSQLiteDAO<Centre, Long> {

    private static final Logger logger = LoggerFactory.getLogger(CentreDAO.class);

    @Override
    protected String getTableName() {
        return "centres";
    }

    @Override
    protected String getIdColumnName() {
        return "id";
    }

    @Override
    protected String getInsertQuery() {
        return """
            INSERT INTO centres (code_centre, nom_centre) 
            VALUES (?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE centres 
            SET code_centre = ?, nom_centre = ?
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, code_centre, nom_centre, created_at 
            FROM centres 
            ORDER BY nom_centre ASC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, code_centre, nom_centre, created_at 
            FROM centres 
            WHERE id = ?
        """;
    }

    @Override
    protected Centre mapResultSetToEntity(ResultSet rs) throws SQLException {
        Centre centre = new Centre();

        centre.setId(rs.getLong("id"));
        centre.setCodeCentre(rs.getString("code_centre"));
        centre.setNomCentre(rs.getString("nom_centre"));

        // Les colonnes description et actif n'existent pas dans la table
        centre.setDescription("");  // Valeur par défaut
        centre.setActif(true);     // Valeur par défaut

        // Gestion des timestamps
        try {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                centre.setCreatedAt(createdAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de created_at pour le centre {}", centre.getId());
            centre.setCreatedAt(LocalDateTime.now());
        }

        return centre;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Centre centre) throws SQLException {
        stmt.setString(1, centre.getCodeCentre());
        stmt.setString(2, centre.getNomCentre());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Centre centre) throws SQLException {
        stmt.setString(1, centre.getCodeCentre());
        stmt.setString(2, centre.getNomCentre());
        stmt.setLong(3, centre.getId());
    }

    @Override
    protected Long getEntityId(Centre centre) {
        return centre.getId();
    }

    @Override
    protected void setEntityId(Centre centre, Long id) {
        centre.setId(id);
    }

    // ========== MÉTHODES SPÉCIFIQUES AUX CENTRES ==========

    /**
     * Vérifie si un code centre existe déjà
     * @param codeCentre Le code à vérifier
     * @return true si le code existe déjà
     */
    public boolean existsByCodeCentre(String codeCentre) {
        String sql = "SELECT COUNT(*) FROM centres WHERE code_centre = ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codeCentre);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification de l'existence du code centre", e);
        }

        return false;
    }

    /**
     * Trouve un centre par son code
     * @param codeCentre Le code du centre
     * @return Le centre trouvé ou empty
     */
    public Optional<Centre> findByCodeCentre(String codeCentre) {
        String sql = """
            SELECT id, code_centre, nom_centre, created_at 
            FROM centres 
            WHERE code_centre = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codeCentre);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par code centre: " + codeCentre, e);
        }

        return Optional.empty();
    }

    /**
     * Recherche des centres avec critères multiples - COMME AffaireDAO
     * @param nomOuCode Nom ou code à rechercher
     * @param actifOnly Si on ne veut que les actifs (ignoré car pas de colonne actif)
     * @param offset Décalage pour la pagination
     * @param limit Nombre max de résultats
     * @return Liste des centres trouvés
     */
    public List<Centre> searchCentres(String nomOuCode, Boolean actifOnly, int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, code_centre, nom_centre, created_at ");
        sql.append("FROM centres WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (nom_centre LIKE ? OR code_centre LIKE ?) ");
            String searchPattern = "%" + nomOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        // Ignorer actifOnly car la colonne n'existe pas

        sql.append("ORDER BY nom_centre ASC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Centre> centres = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                centres.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche de centres", e);
        }

        return centres;
    }

    /**
     * Compte les centres selon les critères de recherche
     * @param nomOuCode Nom ou code à rechercher
     * @param actifOnly Si on ne veut que les actifs (ignoré)
     * @return Nombre de centres trouvés
     */
    public long countSearchCentres(String nomOuCode, Boolean actifOnly) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM centres WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (nom_centre LIKE ? OR code_centre LIKE ?) ");
            String searchPattern = "%" + nomOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
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
            logger.error("Erreur lors du comptage des centres", e);
        }

        return 0;
    }

    /**
     * Trouve tous les centres actifs - POUR LES COMBOBOX
     * Comme il n'y a pas de colonne actif, on retourne tous les centres
     */
    public List<Centre> findAllActifs() {
        String sql = """
            SELECT id, code_centre, nom_centre, created_at 
            FROM centres 
            ORDER BY nom_centre ASC
        """;

        List<Centre> centres = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                centres.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des centres actifs", e);
        }

        return centres;
    }

    /**
     * Trouve tous les centres actifs - ALIAS pour compatibilité
     * Comme il n'y a pas de colonne actif, on retourne tous les centres
     */
    public List<Centre> findAllActive() {
        return findAllActifs();
    }
}