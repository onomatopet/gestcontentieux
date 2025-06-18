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
 * Implémentation abstraite de base pour les DAOs SQLite
 *
 * @param <T> Type de l'entité
 * @param <ID> Type de l'identifiant
 */
public abstract class AbstractSQLiteDAO<T, ID> implements BaseDAO<T, ID> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * @return Le nom de la table associée à cette entité
     */
    protected abstract String getTableName();

    /**
     * @return Le nom de la colonne ID
     */
    protected abstract String getIdColumnName();

    /**
     * @return La requête SQL pour l'insertion
     */
    protected abstract String getInsertQuery();

    /**
     * @return La requête SQL pour la mise à jour
     */
    protected abstract String getUpdateQuery();

    /**
     * @return La requête SQL pour la sélection de tous les enregistrements
     */
    protected abstract String getSelectAllQuery();

    /**
     * @return La requête SQL pour la sélection par ID
     */
    protected abstract String getSelectByIdQuery();

    /**
     * Mappe un ResultSet vers une entité
     *
     * @param rs Le ResultSet
     * @return L'entité mappée
     * @throws SQLException En cas d'erreur SQL
     */
    protected abstract T mapResultSetToEntity(ResultSet rs) throws SQLException;

    /**
     * Configure les paramètres pour l'insertion
     *
     * @param stmt L'PreparedStatement
     * @param entity L'entité
     * @throws SQLException En cas d'erreur SQL
     */
    protected abstract void setInsertParameters(PreparedStatement stmt, T entity) throws SQLException;

    /**
     * Configure les paramètres pour la mise à jour
     *
     * @param stmt L'PreparedStatement
     * @param entity L'entité
     * @throws SQLException En cas d'erreur SQL
     */
    protected abstract void setUpdateParameters(PreparedStatement stmt, T entity) throws SQLException;

    /**
     * Récupère l'ID d'une entité
     *
     * @param entity L'entité
     * @return L'ID
     */
    protected abstract ID getEntityId(T entity);

    /**
     * Définit l'ID d'une entité
     *
     * @param entity L'entité
     * @param id L'ID à définir
     */
    protected abstract void setEntityId(T entity, ID id);

    @Override
    public T save(T entity) {
        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(getInsertQuery(), Statement.RETURN_GENERATED_KEYS)) {

            setInsertParameters(stmt, entity);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        @SuppressWarnings("unchecked")
                        ID generatedId = (ID) generatedKeys.getObject(1);
                        setEntityId(entity, generatedId);
                    }
                }
            }

            logger.debug("Entité sauvegardée dans {}: {}", getTableName(), getEntityId(entity));
            return entity;

        } catch (SQLException e) {
            logger.error("Erreur lors de la sauvegarde dans " + getTableName(), e);
            throw new RuntimeException("Impossible de sauvegarder l'entité", e);
        }
    }

    @Override
    public T update(T entity) {
        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(getUpdateQuery())) {

            setUpdateParameters(stmt, entity);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new RuntimeException("Aucune entité trouvée avec l'ID: " + getEntityId(entity));
            }

            logger.debug("Entité mise à jour dans {}: {}", getTableName(), getEntityId(entity));
            return entity;

        } catch (SQLException e) {
            logger.error("Erreur lors de la mise à jour dans " + getTableName(), e);
            throw new RuntimeException("Impossible de mettre à jour l'entité", e);
        }
    }

    @Override
    public void deleteById(ID id) {
        String sql = "DELETE FROM " + getTableName() + " WHERE " + getIdColumnName() + " = ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new RuntimeException("Aucune entité trouvée avec l'ID: " + id);
            }

            logger.debug("Entité supprimée de {}: {}", getTableName(), id);

        } catch (SQLException e) {
            logger.error("Erreur lors de la suppression dans " + getTableName(), e);
            throw new RuntimeException("Impossible de supprimer l'entité", e);
        }
    }

    @Override
    public void delete(T entity) {
        deleteById(getEntityId(entity));
    }

    @Override
    public Optional<T> findById(ID id) {
        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(getSelectByIdQuery())) {

            stmt.setObject(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }

            return Optional.empty();

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par ID dans " + getTableName(), e);
            throw new RuntimeException("Impossible de rechercher l'entité", e);
        }
    }

    @Override
    public List<T> findAll() {
        List<T> entities = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(getSelectAllQuery());
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                entities.add(mapResultSetToEntity(rs));
            }

            logger.debug("Récupération de {} entités de {}", entities.size(), getTableName());
            return entities;

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération de toutes les entités de " + getTableName(), e);
            throw new RuntimeException("Impossible de récupérer les entités", e);
        }
    }

    @Override
    public List<T> findAll(int offset, int limit) {
        List<T> entities = new ArrayList<>();
        String sql = getSelectAllQuery() + " LIMIT ? OFFSET ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                entities.add(mapResultSetToEntity(rs));
            }

            logger.debug("Récupération de {} entités de {} (offset: {}, limit: {})",
                    entities.size(), getTableName(), offset, limit);
            return entities;

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération paginée de " + getTableName(), e);
            throw new RuntimeException("Impossible de récupérer les entités", e);
        }
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM " + getTableName();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }

            return 0;

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage dans " + getTableName(), e);
            throw new RuntimeException("Impossible de compter les entités", e);
        }
    }

    @Override
    public boolean existsById(ID id) {
        String sql = "SELECT 1 FROM " + getTableName() + " WHERE " + getIdColumnName() + " = ? LIMIT 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            ResultSet rs = stmt.executeQuery();

            return rs.next();

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification d'existence dans " + getTableName(), e);
            return false;
        }
    }

    @Override
    public List<T> saveAll(List<T> entities) {
        List<T> savedEntities = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(getInsertQuery(), Statement.RETURN_GENERATED_KEYS)) {

                for (T entity : entities) {
                    setInsertParameters(stmt, entity);
                    stmt.addBatch();
                }

                int[] affectedRows = stmt.executeBatch();

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    int index = 0;
                    while (generatedKeys.next() && index < entities.size()) {
                        @SuppressWarnings("unchecked")
                        ID generatedId = (ID) generatedKeys.getObject(1);
                        T entity = entities.get(index);
                        setEntityId(entity, generatedId);
                        savedEntities.add(entity);
                        index++;
                    }
                }

                conn.commit();
                logger.debug("Sauvegarde en lot de {} entités dans {}", savedEntities.size(), getTableName());

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la sauvegarde en lot dans " + getTableName(), e);
            throw new RuntimeException("Impossible de sauvegarder les entités en lot", e);
        }

        return savedEntities;
    }

    @Override
    public void deleteAll() {
        String sql = "DELETE FROM " + getTableName();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int affectedRows = stmt.executeUpdate();
            logger.debug("Suppression de {} entités de {}", affectedRows, getTableName());

        } catch (SQLException e) {
            logger.error("Erreur lors de la suppression de toutes les entités de " + getTableName(), e);
            throw new RuntimeException("Impossible de supprimer toutes les entités", e);
        }
    }

    @Override
    public void deleteAllById(List<ID> ids) {
        if (ids.isEmpty()) {
            return;
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String sql = "DELETE FROM " + getTableName() + " WHERE " + getIdColumnName() + " IN (" + placeholders + ")";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < ids.size(); i++) {
                stmt.setObject(i + 1, ids.get(i));
            }

            int affectedRows = stmt.executeUpdate();
            logger.debug("Suppression de {} entités de {} par IDs", affectedRows, getTableName());

        } catch (SQLException e) {
            logger.error("Erreur lors de la suppression par IDs dans " + getTableName(), e);
            throw new RuntimeException("Impossible de supprimer les entités par IDs", e);
        }
    }

    /**
     * Exécute une requête personnalisée
     *
     * @param sql La requête SQL
     * @param parameters Les paramètres
     * @return Liste des entités résultantes
     */
    protected List<T> executeQuery(String sql, Object... parameters) {
        List<T> entities = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < parameters.length; i++) {
                stmt.setObject(i + 1, parameters[i]);
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                entities.add(mapResultSetToEntity(rs));
            }

            return entities;

        } catch (SQLException e) {
            logger.error("Erreur lors de l'exécution de la requête personnalisée", e);
            throw new RuntimeException("Impossible d'exécuter la requête", e);
        }
    }

    /**
     * Exécute une mise à jour personnalisée
     *
     * @param sql La requête SQL
     * @param parameters Les paramètres
     * @return Nombre de lignes affectées
     */
    protected int executeUpdate(String sql, Object... parameters) {
        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < parameters.length; i++) {
                stmt.setObject(i + 1, parameters[i]);
            }

            return stmt.executeUpdate();

        } catch (SQLException e) {
            logger.error("Erreur lors de l'exécution de la mise à jour personnalisée", e);
            throw new RuntimeException("Impossible d'exécuter la mise à jour", e);
        }
    }
}