package com.regulation.contentieux.service;

import com.regulation.contentieux.dao.ServiceDAO;
import com.regulation.contentieux.model.Service;
import com.regulation.contentieux.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service de gestion des services organisationnels
 * Note: Nommé ServiceOrganisationService pour éviter la confusion avec le modèle Service
 * Peut être utilisé comme ServiceService dans les contrôleurs
 *
 * @author Équipe Contentieux
 * @since 1.0.0
 */
public class ServiceOrganisationService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceOrganisationService.class);

    private final ServiceDAO serviceDAO;
    private final ValidationService validationService;

    public ServiceOrganisationService() {
        this.serviceDAO = new ServiceDAO();
        this.validationService = ValidationService.getInstance();
    }

    /**
     * Récupère tous les services
     * @return Liste de tous les services
     */
    public List<Service> getAllServices() {
        logger.debug("Récupération de tous les services");
        return serviceDAO.findAll();
    }

    /**
     * Récupère tous les services avec pagination
     * @param page Numéro de page (commence à 1)
     * @param pageSize Taille de la page
     * @return Liste paginée des services
     */
    public List<Service> getAllServices(int page, int pageSize) {
        logger.debug("Récupération des services - page: {}, taille: {}", page, pageSize);
        int offset = (page - 1) * pageSize;
        return serviceDAO.findAll(); // TODO: Implémenter la pagination dans le DAO si nécessaire
    }

    /**
     * Récupère tous les services actifs
     * @return Liste des services actifs
     */
    public List<Service> getAllServicesActifs() {
        logger.debug("Récupération des services actifs");
        return serviceDAO.findAllActive();
    }

    /**
     * Récupère les services d'un centre
     * @param centreId ID du centre
     * @return Liste des services du centre
     */
    public List<Service> getServicesByCentre(Long centreId) {
        if (centreId == null) {
            logger.warn("Tentative de recherche avec un centreId null");
            return List.of();
        }
        return serviceDAO.findByCentreId(centreId);
    }

    /**
     * Trouve un service par son ID
     * @param id ID du service
     * @return Service trouvé ou Optional.empty()
     */
    public Optional<Service> findById(Long id) {
        if (id == null) {
            logger.warn("Tentative de recherche avec un ID null");
            return Optional.empty();
        }
        return serviceDAO.findById(id);
    }

    /**
     * Trouve un service par son code
     * @param code Code du service
     * @return Service trouvé ou Optional.empty()
     */
    public Optional<Service> findByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            logger.warn("Tentative de recherche avec un code vide");
            return Optional.empty();
        }
        return serviceDAO.findByCodeService(code.trim());
    }

    /**
     * Crée un nouveau service
     * @param service Service à créer
     * @return Service créé avec son ID
     */
    public Service createService(Service service) {
        logger.info("Création d'un nouveau service: {}", service.getNomService());

        // Validation
        validateService(service);

        // Vérification d'unicité du code
        if (serviceDAO.existsByCodeService(service.getCodeService())) {
            throw new IllegalArgumentException("Un service avec ce code existe déjà: " + service.getCodeService());
        }

        // Normalisation
        service.setNomService(validationService.normalizeText(service.getNomService()));
        if (service.getDescription() != null) {
            service.setDescription(validationService.normalizeText(service.getDescription()));
        }

        Service saved = serviceDAO.save(service);
        logger.info("Service créé avec succès - ID: {}, Code: {}", saved.getId(), saved.getCodeService());

        return saved;
    }

    /**
     * Met à jour un service existant
     * @param service Service à mettre à jour
     * @return Service mis à jour
     */
    public Service updateService(Service service) {
        logger.info("Mise à jour du service ID: {}", service.getId());

        if (service.getId() == null) {
            throw new IllegalArgumentException("L'ID du service est requis pour la mise à jour");
        }

        // Vérifier l'existence
        Optional<Service> existing = serviceDAO.findById(service.getId());
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Service non trouvé avec l'ID: " + service.getId());
        }

        // Validation
        validateService(service);

        // Vérification d'unicité du code (sauf pour lui-même)
        Optional<Service> byCode = serviceDAO.findByCodeService(service.getCodeService());
        if (byCode.isPresent() && !byCode.get().getId().equals(service.getId())) {
            throw new IllegalArgumentException("Un autre service utilise déjà ce code: " + service.getCodeService());
        }

        // Normalisation
        service.setNomService(validationService.normalizeText(service.getNomService()));
        if (service.getDescription() != null) {
            service.setDescription(validationService.normalizeText(service.getDescription()));
        }

        Service updated = serviceDAO.update(service);
        logger.info("Service mis à jour avec succès - Code: {}", updated.getCodeService());

        return updated;
    }

    /**
     * Supprime un service
     * @param id ID du service à supprimer
     */
    public void deleteService(Long id) {
        logger.info("Suppression du service ID: {}", id);

        Optional<Service> service = serviceDAO.findById(id);
        if (service.isEmpty()) {
            throw new IllegalArgumentException("Service non trouvé avec l'ID: " + id);
        }

        // TODO: Vérifier s'il y a des bureaux ou agents liés avant suppression

        serviceDAO.deleteById(id);
        logger.info("Service supprimé: {} - {}", service.get().getCodeService(), service.get().getNomService());
    }

    /**
     * Active/désactive un service
     * @param id ID du service
     * @param actif Nouvel état
     */
    public void toggleServiceActif(Long id, boolean actif) {
        Optional<Service> optService = serviceDAO.findById(id);
        if (optService.isEmpty()) {
            throw new IllegalArgumentException("Service non trouvé avec l'ID: " + id);
        }

        Service service = optService.get();
        service.setActif(actif);

        serviceDAO.update(service);
        logger.info("Service {} {}: {}",
                service.getCodeService(),
                actif ? "activé" : "désactivé",
                service.getNomService());
    }

    /**
     * Recherche des services
     * @param query Texte de recherche
     * @return Liste des services correspondants
     */
    public List<Service> searchServices(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllServices();
        }

        logger.debug("Recherche de services avec: {}", query);
        return serviceDAO.searchServices(query.trim(), null, 0, 100);
    }

    /**
     * Compte le nombre total de services
     * @return Nombre de services
     */
    public long countServices() {
        return serviceDAO.count();
    }

    /**
     * Compte le nombre de services par centre
     * @param centreId ID du centre
     * @return Nombre de services
     */
    public long countServicesByCentre(Long centreId) {
        if (centreId == null) {
            return 0;
        }
        return serviceDAO.countByCentreId(centreId);
    }

    /**
     * Valide les données d'un service
     * @param service Service à valider
     */
    private void validateService(Service service) {
        if (service == null) {
            throw new IllegalArgumentException("Le service ne peut pas être null");
        }

        // Code obligatoire
        if (service.getCodeService() == null || service.getCodeService().trim().isEmpty()) {
            throw new IllegalArgumentException("Le code du service est obligatoire");
        }

        // Nom obligatoire
        if (service.getNomService() == null || service.getNomService().trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom du service est obligatoire");
        }

        // Centre obligatoire
        if (service.getCentreId() == null && service.getCentre() == null) {
            throw new IllegalArgumentException("Le centre du service est obligatoire");
        }

        // Validation du format du code
        if (!service.getCodeService().matches("^[A-Z0-9]{2,10}$")) {
            throw new IllegalArgumentException("Le code du service doit contenir entre 2 et 10 caractères alphanumériques majuscules");
        }

        // Validation de la longueur du nom
        if (service.getNomService().trim().length() < 3 || service.getNomService().trim().length() > 100) {
            throw new IllegalArgumentException("Le nom du service doit contenir entre 3 et 100 caractères");
        }
    }

    /**
     * Génère le prochain code de service
     * @return Nouveau code généré
     */
    public String generateNextCode() {
        // Récupérer le dernier code
        List<Service> services = serviceDAO.findAll();
        if (services.isEmpty()) {
            return "SRV001";
        }

        // Trouver le plus grand numéro
        int maxNum = 0;
        for (Service s : services) {
            String code = s.getCodeService();
            if (code != null && code.startsWith("SRV")) {
                try {
                    int num = Integer.parseInt(code.substring(3));
                    maxNum = Math.max(maxNum, num);
                } catch (NumberFormatException e) {
                    // Ignorer les codes non conformes
                }
            }
        }

        return String.format("SRV%03d", maxNum + 1);
    }
}