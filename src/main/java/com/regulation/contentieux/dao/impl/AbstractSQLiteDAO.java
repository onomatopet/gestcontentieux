package com.regulation.contentieux.dao.impl;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.dao.BaseDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Classe abstraite de base pour tous les DAOs SQLite
 * Implémente les opérations CRUD communes
 *
 * @param <T> Type de l'entité
 * @param <ID> Type de l'identifiant (généralement Long)
 */
public abstract class AbstractSQLiteDAO<T, ID> implements BaseDAO<T, ID> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Retourne le nom de la table
     */
    protected abstract String getTableName();

    /**
     * Retourne le nom de la colonne ID
     */
    protected abstract String getIdColumnName();

    /**
     * Retourne la requête INSERT
     */
    protected abstract String getInsertQuery();

    /**
     * Retourne la requête UPDATE
     */
    protected abstract String getUpdateQuery();

    /**
     * Retourne la requête SELECT ALL
     */
    protected abstract String getSelectAllQuery();

    /**
     * Retourne la requête SELECT BY ID
     */
    protected abstract String getSelectByIdQuery();

    /**
     * Mappe un ResultSet vers une entité
     */
    protected abstract T mapResultSetToEntity(ResultSet rs) throws SQLException;

    /**
     * Définit les paramètres pour l'INSERT
     */
    protected abstract void setInsertParameters(PreparedStatement stmt, T entity) throws SQLException;

    /**
     * Définit les paramètres pour l'UPDATE
     */
    protected abstract void setUpdateParameters(PreparedStatement stmt, T entity) throws SQLException;

    /**
     * Obtient l'ID de l'entité
     */
    protected abstract ID getEntityId(T entity);

    /**
     * Définit l'ID de l'entité
     */
    protected abstract void setEntityId(T entity, ID id);

    /**
     * Obtient une connexion à la base de données
     */
    protected Connection getConnection() throws SQLException {
        return DatabaseConfig.getSQLiteConnection();
    }

    @Override
    public T save(T entity) {
        String sql = getInsertQuery();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            setInsertParameters(stmt, entity);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("La création a échoué, aucune ligne affectée.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    @SuppressWarnings("unchecked")
                    ID generatedId = (ID) Long.valueOf(generatedKeys.getLong(1));
                    setEntityId(entity, generatedId);
                } else {
                    throw new SQLException("La création a échoué, aucun ID généré.");
                }
            }

            logger.debug("Entité sauvegardée avec succès: {}", entity);
            return entity;

        } catch (SQLException e) {
            logger.error("Erreur lors de la sauvegarde de l'entité", e);
            throw new RuntimeException("Erreur lors de la sauvegarde", e);
        }
    }

    @Override
    public T update(T entity) {
        String sql = getUpdateQuery();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setUpdateParameters(stmt, entity);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("La mise à jour a échoué, aucune ligne affectée.");
            }

            logger.debug("Entité mise à jour avec succès: {}", entity);
            return entity;

        } catch (SQLException e) {
            logger.error("Erreur lors de la mise à jour de l'entité", e);
            throw new RuntimeException("Erreur lors de la mise à jour", e);
        }
    }

    @Override
    public void deleteById(ID id) {
        String sql = "DELETE FROM " + getTableName() + " WHERE " + getIdColumnName() + " = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                logger.warn("Aucune entité trouvée avec l'ID: {}", id);
            } else {
                logger.debug("Entité supprimée avec l'ID: {}", id);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la suppression de l'entité avec l'ID: " + id, e);
            throw new RuntimeException("Erreur lors de la suppression", e);
        }
    }

    @Override
    public void delete(T entity) {
        ID id = getEntityId(entity);
        if (id != null) {
            deleteById(id);
        } else {
            throw new IllegalArgumentException("L'entité n'a pas d'ID");
        }
    }

    @Override
    public Optional<T> findById(ID id) {
        String sql = getSelectByIdQuery();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    T entity = mapResultSetToEntity(rs);
                    return Optional.of(entity);
                }
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par ID: " + id, e);
            throw new RuntimeException("Erreur lors de la recherche", e);
        }

        return Optional.empty();
    }

    @Override
    public List<T> findAll() {
        return findAll(0, Integer.MAX_VALUE);
    }

    @Override
    public List<T> findAll(int offset, int limit) {
        String sql = getSelectAllQuery() + " LIMIT ? OFFSET ?";
        List<T> entities = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            stmt.setInt(2, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entities.add(mapResultSetToEntity(rs));
                }
            }

            logger.debug("Trouvé {} entités", entities.size());

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche de toutes les entités", e);
            throw new RuntimeException("Erreur lors de la recherche", e);
        }

        return entities;
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM " + getTableName();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage des entités", e);
            throw new RuntimeException("Erreur lors du comptage", e);
        }

        return 0;
    }

    @Override
    public boolean existsById(ID id) {
        String sql = "SELECT COUNT(*) FROM " + getTableName() +
                " WHERE " + getIdColumnName() + " = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification d'existence par ID: " + id, e);
            throw new RuntimeException("Erreur lors de la vérification", e);
        }

        return false;
    }

    @Override
    public List<T> saveAll(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }

        List<T> savedEntities = new ArrayList<>();
        Connection conn = null;

        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            for (T entity : entities) {
                savedEntities.add(save(entity));
            }

            conn.commit();
            logger.debug("Sauvegardé {} entités en lot", savedEntities.size());

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("Erreur lors du rollback", ex);
                }
            }
            logger.error("Erreur lors de la sauvegarde en lot", e);
            throw new RuntimeException("Erreur lors de la sauvegarde en lot", e);

        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Erreur lors de la fermeture de la connexion", e);
                }
            }
        }

        return savedEntities;
    }

    @Override
    public void deleteAll() {
        String sql = "DELETE FROM " + getTableName();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int deletedRows = stmt.executeUpdate();
            logger.info("Supprimé {} entités de la table {}", deletedRows, getTableName());

        } catch (SQLException e) {
            logger.error("Erreur lors de la suppression de toutes les entités", e);
            throw new RuntimeException("Erreur lors de la suppression", e);
        }
    }

    @Override
    public void deleteAllById(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        String sql = "DELETE FROM " + getTableName() +
                " WHERE " + getIdColumnName() + " IN (" +
                String.join(",", ids.stream().map(id -> "?").toArray(String[]::new)) + ")";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int index = 1;
            for (ID id : ids) {
                stmt.setObject(index++, id);
            }

            int deletedRows = stmt.executeUpdate();
            logger.debug("Supprimé {} entités", deletedRows);

        } catch (SQLException e) {
            logger.error("Erreur lors de la suppression multiple", e);
            throw new RuntimeException("Erreur lors de la suppression multiple", e);
        }
    }

    /**
     * Méthode utilitaire pour exécuter une requête personnalisée
     */
    protected List<T> executeQuery(String sql, Object... params) {
        List<T> results = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de l'exécution de la requête: " + sql, e);
            throw new RuntimeException("Erreur lors de l'exécution de la requête", e);
        }

        return results;
    }

    /**
     * Méthode utilitaire pour exécuter une mise à jour personnalisée
     */
    protected int executeUpdate(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            return stmt.executeUpdate();

        } catch (SQLException e) {
            logger.error("Erreur lors de l'exécution de la mise à jour: " + sql, e);
            throw new RuntimeException("Erreur lors de la mise à jour", e);
        }
    }
}