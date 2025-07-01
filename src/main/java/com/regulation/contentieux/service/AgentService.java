package com.regulation.contentieux.service;

import com.regulation.contentieux.dao.AgentDAO;
import com.regulation.contentieux.model.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service métier pour la gestion des agents
 * Suit la même logique que ContrevenantService
 */
public class AgentService {

    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);

    private final AgentDAO agentDAO;
    private final ValidationService validationService;

    public AgentService() {
        this.agentDAO = new AgentDAO();
        this.validationService = ValidationService.getInstance(); // Utiliser getInstance() au lieu de new
    }

    /**
     * Recherche d'agents avec pagination
     */
    public List<Agent> searchAgents(String searchText, int limit) {
        if (searchText == null || searchText.trim().length() < 2) {
            return new ArrayList<>();
        }

        String search = searchText.trim();

        // Utilise la méthode du DAO avec les paramètres appropriés
        // Recherche uniquement les agents actifs avec une limite
        return agentDAO.searchAgents(search, null, null, true, 0, limit);
    }

    /**
     * Recherche d'agents avec pagination (méthode existante)
     * Cette méthode est utilisée pour la liste principale avec tous les filtres
     */
    public List<Agent> searchAgents(String nomOuPrenom, String grade, Long serviceId,
                                    Boolean actif, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return agentDAO.searchAgents(nomOuPrenom, grade, serviceId, actif, offset, pageSize);
    }

    /**
     * Compte le nombre total d'agents pour la recherche
     */
    public long countSearchAgents(String nomOuPrenom, String grade, Long serviceId, Boolean actif) {
        return agentDAO.countSearchAgents(nomOuPrenom, grade, serviceId, actif);
    }

    /**
     * Trouve un agent par son ID
     */
    public Optional<Agent> findById(Long id) {
        return agentDAO.findById(id);
    }

    /**
     * Trouve un agent par son code
     */
    public Optional<Agent> findByCodeAgent(String codeAgent) {
        return agentDAO.findByCodeAgent(codeAgent);
    }

    /**
     * Sauvegarde un nouvel agent
     */
    public Agent saveAgent(Agent agent) {
        // Validation des données
        validateAgent(agent);

        // Génération du code si nécessaire
        if (agent.getCodeAgent() == null || agent.getCodeAgent().trim().isEmpty()) {
            String nextCode = agentDAO.generateNextCodeAgent();
            agent.setCodeAgent(nextCode);
        }

        // Vérification d'unicité du code
        if (agentDAO.existsByCodeAgent(agent.getCodeAgent())) {
            throw new IllegalArgumentException("Un agent avec ce code existe déjà: " + agent.getCodeAgent());
        }

        // Normalisation des données
        agent.setNom(validationService.normalizePersonName(agent.getNom()));
        agent.setPrenom(validationService.normalizePersonName(agent.getPrenom()));

        // Sauvegarde
        Agent saved = agentDAO.save(agent);
        logger.info("Nouvel agent créé: {} - {} {}", saved.getCodeAgent(), saved.getPrenom(), saved.getNom());

        return saved;
    }

    /**
     * Met à jour un agent existant
     */
    public Agent updateAgent(Agent agent) {
        if (agent.getId() == null) {
            throw new IllegalArgumentException("L'ID de l'agent est requis pour la mise à jour");
        }

        // Validation des données
        validateAgent(agent);

        // Vérification que l'agent existe
        Optional<Agent> existing = agentDAO.findById(agent.getId());
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Agent non trouvé avec l'ID: " + agent.getId());
        }

        // Vérification d'unicité du code (sauf pour lui-même)
        Optional<Agent> byCode = agentDAO.findByCodeAgent(agent.getCodeAgent());
        if (byCode.isPresent() && !byCode.get().getId().equals(agent.getId())) {
            throw new IllegalArgumentException("Un autre agent utilise déjà ce code: " + agent.getCodeAgent());
        }

        // Normalisation des données
        agent.setNom(validationService.normalizePersonName(agent.getNom()));
        agent.setPrenom(validationService.normalizePersonName(agent.getPrenom()));

        // Mise à jour
        Agent updated = agentDAO.update(agent);
        logger.info("Agent mis à jour: {} - {} {}", updated.getCodeAgent(), updated.getPrenom(), updated.getNom());

        return updated;
    }

    /**
     * Supprime un agent
     */
    public void deleteAgent(Long id) {
        Optional<Agent> agent = agentDAO.findById(id);
        if (agent.isEmpty()) {
            throw new IllegalArgumentException("Agent non trouvé avec l'ID: " + id);
        }

        // TODO: Vérifier s'il y a des affaires liées avant suppression
        agentDAO.deleteById(id);
        logger.info("Agent supprimé: {} - {} {}", agent.get().getCodeAgent(),
                agent.get().getPrenom(), agent.get().getNom());
    }

    /**
     * Active/désactive un agent
     */
    public void toggleAgentActif(Long id) {
        Optional<Agent> optAgent = agentDAO.findById(id);
        if (optAgent.isEmpty()) {
            throw new IllegalArgumentException("Agent non trouvé avec l'ID: " + id);
        }

        Agent agent = optAgent.get();
        agent.setActif(!agent.isActif());

        agentDAO.update(agent);
        logger.info("Agent {} {}: {} {}",
                agent.getCodeAgent(),
                agent.isActif() ? "activé" : "désactivé",
                agent.getPrenom(), agent.getNom());
    }

    /**
     * Désactive un agent
     */
    public boolean deactivateAgent(Long id) {
        Optional<Agent> agent = agentDAO.findById(id);
        if (agent.isEmpty()) {
            throw new IllegalArgumentException("Agent non trouvé avec l'ID: " + id);
        }

        boolean result = agentDAO.deactivateAgent(id);
        if (result) {
            logger.info("Agent désactivé: {} - {} {}",
                    agent.get().getCodeAgent(), agent.get().getPrenom(), agent.get().getNom());
        }
        return result;
    }

    /**
     * Réactive un agent
     */
    public boolean reactivateAgent(Long id) {
        Optional<Agent> agent = agentDAO.findById(id);
        if (agent.isEmpty()) {
            throw new IllegalArgumentException("Agent non trouvé avec l'ID: " + id);
        }

        boolean result = agentDAO.reactivateAgent(id);
        if (result) {
            logger.info("Agent réactivé: {} - {} {}",
                    agent.get().getCodeAgent(), agent.get().getPrenom(), agent.get().getNom());
        }
        return result;
    }

    public List<Agent> getAllAgents(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return agentDAO.findAll(offset, pageSize);
    }

    /**
     * Récupère tous les agents sans pagination
     * Surcharge pour compatibilité avec AffaireEncaissementController
     * @return Liste de tous les agents
     */
    public List<Agent> getAllAgents() {
        return getAllAgents(1, Integer.MAX_VALUE);
    }

    /**
     * Compte le nombre total d'agents
     */
    public long getTotalCount() {
        return agentDAO.count();
    }

    /**
     * Trouve les agents actifs
     */
    public List<Agent> findActiveAgents() {
        return agentDAO.findActiveAgents();
    }

    /**
     * Trouve les agents par service
     */
    public List<Agent> findByServiceId(Long serviceId) {
        return agentDAO.findByServiceId(serviceId);
    }

    /**
     * Valide les données d'un agent
     */
    private void validateAgent(Agent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("L'agent ne peut pas être null");
        }

        // Validation du nom
        if (!validationService.isValidPersonName(agent.getNom())) {
            throw new IllegalArgumentException("Le nom doit contenir entre 2 et 100 caractères et être valide");
        }

        // Validation du prénom
        if (!validationService.isValidPersonName(agent.getPrenom())) {
            throw new IllegalArgumentException("Le prénom doit contenir entre 2 et 100 caractères et être valide");
        }

        // Validation du code (si fourni)
        if (agent.getCodeAgent() != null && !agent.getCodeAgent().trim().isEmpty()) {
            if (!validationService.isValidCode(agent.getCodeAgent())) {
                throw new IllegalArgumentException("Format de code agent invalide");
            }
        }

        // Validation du grade (optionnel)
        if (agent.getGrade() != null && !agent.getGrade().trim().isEmpty()) {
            if (!validationService.isValidGrade(agent.getGrade())) {
                throw new IllegalArgumentException("Grade invalide");
            }
        }

        // Validation du service (optionnel)
        if (agent.getServiceId() != null && agent.getServiceId() <= 0) {
            throw new IllegalArgumentException("ID de service invalide");
        }
    }

    /**
     * Génère le prochain code agent
     */
    public String generateNextCode() {
        return agentDAO.generateNextCodeAgent();
    }

    /**
     * Vérifie si un code existe déjà
     */
    public boolean codeExists(String code) {
        return agentDAO.existsByCodeAgent(code);
    }

    /**
     * Recherche rapide par nom ou code (pour autocomplete)
     */
    public List<Agent> searchQuick(String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        return agentDAO.searchAgents(query.trim(), null, null, true, 0, limit);
    }
}