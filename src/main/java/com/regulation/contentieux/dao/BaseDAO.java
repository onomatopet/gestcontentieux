package com.regulation.contentieux.dao;

import java.util.List;
import java.util.Optional;

/**
 * Interface de base pour tous les DAOs
 * Définit les opérations CRUD communes
 *
 * @param <T> Type de l'entité
 * @param <ID> Type de l'identifiant
 */
public interface BaseDAO<T, ID> {

    /**
     * Sauvegarde une nouvelle entité
     *
     * @param entity L'entité à sauvegarder
     * @return L'entité sauvegardée avec son ID généré
     */
    T save(T entity);

    /**
     * Met à jour une entité existante
     *
     * @param entity L'entité à mettre à jour
     * @return L'entité mise à jour
     */
    T update(T entity);

    /**
     * Supprime une entité par son ID
     *
     * @param id L'ID de l'entité à supprimer
     */
    void deleteById(ID id);

    /**
     * Supprime une entité
     *
     * @param entity L'entité à supprimer
     */
    void delete(T entity);

    /**
     * Trouve une entité par son ID
     *
     * @param id L'ID de l'entité recherchée
     * @return L'entité trouvée ou Optional.empty()
     */
    Optional<T> findById(ID id);

    /**
     * Trouve toutes les entités
     *
     * @return Liste de toutes les entités
     */
    List<T> findAll();

    /**
     * Trouve toutes les entités avec pagination
     *
     * @param offset Décalage
     * @param limit Limite
     * @return Liste paginée des entités
     */
    List<T> findAll(int offset, int limit);

    /**
     * Compte le nombre total d'entités
     *
     * @return Nombre total d'entités
     */
    long count();

    /**
     * Vérifie si une entité existe par son ID
     *
     * @param id L'ID à vérifier
     * @return true si l'entité existe
     */
    boolean existsById(ID id);

    /**
     * Sauvegarde plusieurs entités en lot
     *
     * @param entities Liste des entités à sauvegarder
     * @return Liste des entités sauvegardées
     */
    List<T> saveAll(List<T> entities);

    /**
     * Supprime toutes les entités
     */
    void deleteAll();

    /**
     * Supprime plusieurs entités par leurs IDs
     *
     * @param ids Liste des IDs à supprimer
     */
    void deleteAllById(List<ID> ids);
}