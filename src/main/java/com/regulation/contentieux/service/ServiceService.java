package com.regulation.contentieux.service;

/**
 * Classe alias pour ServiceOrganisationService
 * Permet d'utiliser "ServiceService" dans les contrôleurs comme attendu
 * tout en évitant la confusion avec le modèle Service
 *
 * @author Équipe Contentieux
 * @since 1.0.0
 */
public class ServiceService extends ServiceOrganisationService {

    public ServiceService() {
        super();
    }

    // Cette classe hérite de toutes les méthodes de ServiceOrganisationService
    // Elle sert uniquement d'alias pour maintenir la convention de nommage
    // dans les contrôleurs existants
}