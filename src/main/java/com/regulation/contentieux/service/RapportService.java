package com.regulation.contentieux.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.dao.EncaissementDAO;
import com.regulation.contentieux.dao.AgentDAO;
import com.regulation.contentieux.dao.ContraventionDAO;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Contravention;
import com.regulation.contentieux.model.Encaissement;
import com.regulation.contentieux.model.Agent;
import com.regulation.contentieux.model.enums.StatutAffaire;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service de génération des rapports de rétrocession
 * Gère les calculs de répartition et la génération des documents
 */
public class RapportService {

    private static final Logger logger = LoggerFactory.getLogger(RapportService.class);

    // Pourcentages de répartition selon la réglementation
    private static final BigDecimal POURCENTAGE_ETAT = new BigDecimal("60.00");
    private static final BigDecimal POURCENTAGE_COLLECTIVITE = new BigDecimal("40.00");

    private final AffaireDAO affaireDAO;
    private final EncaissementDAO encaissementDAO;
    private final AgentDAO agentDAO;

    public RapportService() {
        this.affaireDAO = new AffaireDAO();
        this.encaissementDAO = new EncaissementDAO();
        this.agentDAO = new AgentDAO();
    }

    // ==================== RAPPORT PRINCIPAL DE RÉTROCESSION ====================

    /**
     * Génère un rapport de rétrocession pour une période donnée
     */
    public RapportRepartitionDTO genererRapportRepartition(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération du rapport de rétrocession du {} au {}", dateDebut, dateFin);

            RapportRepartitionDTO rapport = new RapportRepartitionDTO();
            rapport.setDateDebut(dateDebut);
            rapport.setDateFin(dateFin);
            rapport.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

            // Récupération des affaires avec encaissements pour la période
            List<Affaire> affaires = affaireDAO.findAffairesWithEncaissementsByPeriod(dateDebut, dateFin);

            BigDecimal totalEncaisse = BigDecimal.ZERO;
            BigDecimal totalEtat = BigDecimal.ZERO;
            BigDecimal totalCollectivite = BigDecimal.ZERO;
            int nombreEncaissements = 0;

            List<AffaireRepartitionDTO> affairesDTO = new ArrayList<>();
            Map<String, BigDecimal> repartitionBureau = new HashMap<>();
            Map<String, BigDecimal> repartitionAgent = new HashMap<>();
            Map<String, Integer> statutCount = new HashMap<>();

            for (Affaire affaire : affaires) {
                AffaireRepartitionDTO affaireDTO = createAffaireDTO(affaire);

                // Calcul des encaissements pour cette affaire
                List<Encaissement> encaissements = encaissementDAO.findByAffaireAndPeriod(
                        affaire.getId(), dateDebut, dateFin);

                BigDecimal totalMontant = encaissements.stream()
                        .map(encaissement -> BigDecimal.valueOf(encaissement.getMontant()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal montantAffaire = BigDecimal.valueOf(affaire.getMontantAmende());

                // Calcul de la répartition
                BigDecimal partEtat = montantAffaire.multiply(POURCENTAGE_ETAT)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                BigDecimal partCollectivite = montantAffaire.multiply(POURCENTAGE_COLLECTIVITE)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                affaireDTO.setPartEtat(partEtat);
                affaireDTO.setPartCollectivite(partCollectivite);

                // Accumulation des totaux
                totalEncaisse = totalEncaisse.add(montantAffaire);
                totalEtat = totalEtat.add(partEtat);
                totalCollectivite = totalCollectivite.add(partCollectivite);
                nombreEncaissements += encaissements.size();

                // Statistiques par bureau
                String bureau = affaireDTO.getBureau();
                repartitionBureau.merge(bureau, montantAffaire, BigDecimal::add);

                // Statistiques par agent
                String agent = affaireDTO.getChefDossier();
                if (agent != null) {
                    repartitionAgent.merge(agent, montantAffaire, BigDecimal::add);
                }

                // Statistiques par statut
                String statut = affaire.getStatut().getLibelle();
                statutCount.merge(statut, 1, Integer::sum);

                affairesDTO.add(affaireDTO);
            }

            // Finalisation du rapport
            rapport.setAffaires(affairesDTO);
            rapport.setTotalEncaisse(totalEncaisse);
            rapport.setTotalEtat(totalEtat);
            rapport.setTotalCollectivite(totalCollectivite);
            rapport.setNombreAffaires(affaires.size());
            rapport.setNombreEncaissements(nombreEncaissements);
            rapport.setRepartitionParBureau(repartitionBureau);
            rapport.setRepartitionParAgent(repartitionAgent);
            rapport.setNombreAffairesParStatut(statutCount);

            logger.info("Rapport généré avec succès: {} affaires, {} FCFA encaissés",
                    affaires.size(), CurrencyFormatter.format(totalEncaisse));

            return rapport;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération du rapport de rétrocession", e);
            throw new RuntimeException("Impossible de générer le rapport: " + e.getMessage(), e);
        }
    }

    // ==================== IMPRIMÉ 5: ETAT DE REPARTITION DU PRODUIT ====================

    /**
     * Génère l'état de répartition du produit des affaires contentieuses (Imprimé 5)
     */
    public EtatRepartitionProduitDTO genererEtatRepartitionProduit(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état de répartition du produit du {} au {}", dateDebut, dateFin);

            EtatRepartitionProduitDTO etat = new EtatRepartitionProduitDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

            // Récupération des affaires avec encaissements
            List<Affaire> affaires = affaireDAO.findAffairesWithEncaissementsByPeriod(dateDebut, dateFin);

            List<ProduitAffaireDTO> affairesDTO = new ArrayList<>();
            TotauxProduitDTO totaux = new TotauxProduitDTO();

            for (Affaire affaire : affaires) {
                List<Encaissement> encaissements = encaissementDAO.findByAffaireAndPeriod(
                        affaire.getId(), dateDebut, dateFin);

                for (Encaissement encaissement : encaissements) {
                    ProduitAffaireDTO dto = new ProduitAffaireDTO();
                    dto.setNumeroEncaissement(encaissement.getReference());
                    dto.setDateEncaissement(encaissement.getDateEncaissement());
                    dto.setNumeroAffaire(affaire.getNumeroAffaire());
                    dto.setDateAffaire(affaire.getDateCreation());

                    // Calcul des répartitions
                    BigDecimal produitDisponible = BigDecimal.valueOf(encaissement.getMontant());
                    dto.setProduitDisponible(produitDisponible);

                    // Accumulation des totaux
                    totaux.setTotalProduitDisponible(
                            totaux.getTotalProduitDisponible().add(produitDisponible));

                    affairesDTO.add(dto);
                }
            }

            etat.setAffaires(affairesDTO);
            etat.setTotaux(totaux);
            etat.setTotalAffaires(affaires.size());

            logger.info("État de répartition du produit généré: {} affaires", affaires.size());
            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état de répartition du produit", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }

    // ==================== IMPRIMÉ 6: ETAT CUMULE PAR AGENT ====================

    /**
     * Génère l'état cumulé par agent (Imprimé 6)
     */
    public EtatCumuleParAgentDTO genererEtatCumuleParAgent(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état cumulé par agent du {} au {}", dateDebut, dateFin);

            EtatCumuleParAgentDTO etat = new EtatCumuleParAgentDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

            // Récupération des affaires
            List<Affaire> affaires = affaireDAO.findAffairesWithEncaissementsByPeriod(dateDebut, dateFin);

            Map<String, AgentCumuleDTO> agentsMap = new HashMap<>();
            BigDecimal totalGeneral = BigDecimal.ZERO;
            int totalAffairesTraitees = 0;

            for (Affaire affaire : affaires) {
                String nomAgent = determinerAgentPrincipal(affaire);
                if (nomAgent == null) continue;

                AgentCumuleDTO agentDTO = agentsMap.computeIfAbsent(nomAgent,
                        k -> new AgentCumuleDTO(k, "AGT" + k.hashCode()));

                // Calcul des parts pour cet agent
                BigDecimal montantAffaire = BigDecimal.valueOf(affaire.getMontantAmende());

                // Exemple de répartition - à adapter selon la logique métier
                agentDTO.setPartChef(agentDTO.getPartChef().add(montantAffaire.multiply(new BigDecimal("0.10"))));
                agentDTO.setNombreAffairesChef(agentDTO.getNombreAffairesChef() + 1);

                totalGeneral = totalGeneral.add(montantAffaire);
                totalAffairesTraitees++;
            }

            // Calcul des totaux pour chaque agent
            for (AgentCumuleDTO agent : agentsMap.values()) {
                agent.calculerTotal();
            }

            etat.setAgents(new ArrayList<>(agentsMap.values()));
            etat.setTotalGeneral(totalGeneral);
            etat.setTotalAffairesTraitees(totalAffairesTraitees);

            logger.info("État cumulé par agent généré: {} agents", agentsMap.size());
            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état cumulé par agent", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }

    // ==================== IMPRIMÉ 7: TABLEAU DES AMENDES PAR SERVICES ====================

    /**
     * Génère le tableau des amendes par services (Imprimé 7)
     */
    public TableauAmendesParServicesDTO genererTableauAmendesParServices(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération du tableau des amendes par services du {} au {}", dateDebut, dateFin);

            TableauAmendesParServicesDTO tableau = new TableauAmendesParServicesDTO();
            tableau.setDateDebut(dateDebut);
            tableau.setDateFin(dateFin);
            tableau.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

            // Récupération des affaires avec encaissements pour la période
            List<Affaire> affaires = affaireDAO.findAffairesWithEncaissementsByPeriod(dateDebut, dateFin);

            // Groupement par service
            Map<String, List<Affaire>> affairesParService = new HashMap<>();

            for (Affaire affaire : affaires) {
                String nomService = determinerServiceAffaire(affaire);
                affairesParService.computeIfAbsent(nomService, k -> new ArrayList<>()).add(affaire);
            }

            // Création des DTOs par service
            List<ServiceAmendeDTO> servicesDTO = new ArrayList<>();
            BigDecimal totalGeneral = BigDecimal.ZERO;
            int totalAffairesGeneral = 0;

            for (Map.Entry<String, List<Affaire>> entry : affairesParService.entrySet()) {
                String nomService = entry.getKey();
                List<Affaire> affairesService = entry.getValue();

                // Calcul du montant total pour ce service
                BigDecimal montantService = BigDecimal.ZERO;
                for (Affaire affaire : affairesService) {
                    montantService = montantService.add(BigDecimal.valueOf(affaire.getMontantAmende()));
                }

                ServiceAmendeDTO serviceDTO = new ServiceAmendeDTO(nomService, affairesService.size(), montantService);
                servicesDTO.add(serviceDTO);

                totalGeneral = totalGeneral.add(montantService);
                totalAffairesGeneral += affairesService.size();
            }

            // Tri par montant décroissant
            servicesDTO.sort((s1, s2) -> s2.getMontantTotal().compareTo(s1.getMontantTotal()));

            tableau.setServices(servicesDTO);
            tableau.setTotalMontant(totalGeneral);
            tableau.setTotalAffaires(totalAffairesGeneral);

            logger.info("Tableau des amendes par services généré: {} services", servicesDTO.size());
            return tableau;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération du tableau des amendes par services", e);
            throw new RuntimeException("Impossible de générer le tableau: " + e.getMessage(), e);
        }
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Crée un DTO d'affaire pour les rapports
     */
    private AffaireRepartitionDTO createAffaireDTO(Affaire affaire) {
        AffaireRepartitionDTO dto = new AffaireRepartitionDTO();

        dto.setNumeroAffaire(affaire.getNumeroAffaire());
        dto.setDateCreation(affaire.getDateCreation());
        dto.setMontantAmende(BigDecimal.valueOf(affaire.getMontantAmende()));
        dto.setStatut(affaire.getStatut().getLibelle());

        // Récupération du contrevenant
        try {
            // TODO: Récupérer le contrevenant via DAO
            dto.setContrevenantNom("Contrevenant " + affaire.getId());
        } catch (Exception e) {
            dto.setContrevenantNom("Inconnu");
        }

        // Récupération de la contravention
        try {
            // TODO: Récupérer la contravention via DAO
            dto.setContraventionType("Type " + affaire.getId());
        } catch (Exception e) {
            dto.setContraventionType("Inconnu");
        }

        // Agent et bureau par défaut
        dto.setChefDossier("Agent " + affaire.getId());
        dto.setBureau("Bureau Central");

        return dto;
    }

    /**
     * Formate la période pour l'affichage
     */
    private String formatPeriode(LocalDate debut, LocalDate fin) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        if (debut.getYear() == fin.getYear() && debut.getMonth() == fin.getMonth()) {
            return debut.getMonth().name() + " " + debut.getYear();
        } else if (debut.getYear() == fin.getYear()) {
            return "Du " + debut.format(formatter) + " au " + fin.format(formatter);
        } else {
            return "Du " + debut.format(formatter) + " au " + fin.format(formatter);
        }
    }

    /**
     * Détermine le service d'une affaire
     */
    private String determinerServiceAffaire(Affaire affaire) {
        try {
            // TODO: Logique à implémenter selon les règles métier
            return "Service Central";
        } catch (Exception e) {
            logger.warn("Impossible de déterminer le service pour l'affaire {}", affaire.getNumeroAffaire());
            return "Service Non Défini";
        }
    }

    /**
     * Détermine la section d'une affaire
     */
    private String determinerSectionAffaire(Affaire affaire) {
        try {
            // TODO: Logique à implémenter selon les règles métier
            return "Section A";
        } catch (Exception e) {
            logger.warn("Impossible de déterminer la section pour l'affaire {}", affaire.getNumeroAffaire());
            return "Section Non Définie";
        }
    }

    /**
     * Détermine l'agent principal d'une affaire
     */
    private String determinerAgentPrincipal(Affaire affaire) {
        try {
            // TODO: Logique à implémenter selon les règles métier
            return "Agent Principal " + affaire.getId();
        } catch (Exception e) {
            logger.warn("Impossible de déterminer l'agent principal pour l'affaire {}", affaire.getNumeroAffaire());
            return null;
        }
    }

    // ==================== CLASSES DTO ====================

    /**
     * DTO principal pour le rapport de rétrocession
     */
    public static class RapportRepartitionDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private BigDecimal totalEncaisse;
        private BigDecimal totalEtat;
        private BigDecimal totalCollectivite;
        private int nombreAffaires;
        private int nombreEncaissements;
        private List<AffaireRepartitionDTO> affaires;
        private Map<String, BigDecimal> repartitionParBureau;
        private Map<String, BigDecimal> repartitionParAgent;
        private Map<String, Integer> nombreAffairesParStatut;

        public RapportRepartitionDTO() {
            this.affaires = new ArrayList<>();
            this.repartitionParBureau = new HashMap<>();
            this.repartitionParAgent = new HashMap<>();
            this.nombreAffairesParStatut = new HashMap<>();
            this.dateGeneration = LocalDate.now();
        }

        // Getters et Setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        public BigDecimal getTotalEncaisse() { return totalEncaisse; }
        public void setTotalEncaisse(BigDecimal totalEncaisse) { this.totalEncaisse = totalEncaisse; }

        public BigDecimal getTotalEtat() { return totalEtat; }
        public void setTotalEtat(BigDecimal totalEtat) { this.totalEtat = totalEtat; }

        public BigDecimal getTotalCollectivite() { return totalCollectivite; }
        public void setTotalCollectivite(BigDecimal totalCollectivite) { this.totalCollectivite = totalCollectivite; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }

        public int getNombreEncaissements() { return nombreEncaissements; }
        public void setNombreEncaissements(int nombreEncaissements) { this.nombreEncaissements = nombreEncaissements; }

        public List<AffaireRepartitionDTO> getAffaires() { return affaires; }
        public void setAffaires(List<AffaireRepartitionDTO> affaires) { this.affaires = affaires; }

        public Map<String, BigDecimal> getRepartitionParBureau() { return repartitionParBureau; }
        public void setRepartitionParBureau(Map<String, BigDecimal> repartitionParBureau) { this.repartitionParBureau = repartitionParBureau; }

        public Map<String, BigDecimal> getRepartitionParAgent() { return repartitionParAgent; }
        public void setRepartitionParAgent(Map<String, BigDecimal> repartitionParAgent) { this.repartitionParAgent = repartitionParAgent; }

        public Map<String, Integer> getNombreAffairesParStatut() { return nombreAffairesParStatut; }
        public void setNombreAffairesParStatut(Map<String, Integer> nombreAffairesParStatut) { this.nombreAffairesParStatut = nombreAffairesParStatut; }
    }

    /**
     * DTO pour les détails d'une affaire dans le rapport
     */
    public static class AffaireRepartitionDTO {
        private String numeroAffaire;
        private LocalDate dateCreation;
        private String contrevenantNom;
        private String contraventionType;
        private BigDecimal montantAmende;
        private BigDecimal montantEncaisse;
        private BigDecimal partEtat;
        private BigDecimal partCollectivite;
        private String chefDossier;
        private String bureau;
        private String statut;

        public AffaireRepartitionDTO() {}

        // Getters et setters
        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public LocalDate getDateCreation() { return dateCreation; }
        public void setDateCreation(LocalDate dateCreation) { this.dateCreation = dateCreation; }

        public String getContrevenantNom() { return contrevenantNom; }
        public void setContrevenantNom(String contrevenantNom) { this.contrevenantNom = contrevenantNom; }

        public String getContraventionType() { return contraventionType; }
        public void setContraventionType(String contraventionType) { this.contraventionType = contraventionType; }

        public BigDecimal getMontantAmende() { return montantAmende; }
        public void setMontantAmende(BigDecimal montantAmende) { this.montantAmende = montantAmende; }

        public BigDecimal getMontantEncaisse() { return montantEncaisse; }
        public void setMontantEncaisse(BigDecimal montantEncaisse) { this.montantEncaisse = montantEncaisse; }

        public BigDecimal getPartEtat() { return partEtat; }
        public void setPartEtat(BigDecimal partEtat) { this.partEtat = partEtat; }

        public BigDecimal getPartCollectivite() { return partCollectivite; }
        public void setPartCollectivite(BigDecimal partCollectivite) { this.partCollectivite = partCollectivite; }

        public String getChefDossier() { return chefDossier; }
        public void setChefDossier(String chefDossier) { this.chefDossier = chefDossier; }

        public String getBureau() { return bureau; }
        public void setBureau(String bureau) { this.bureau = bureau; }

        public String getStatut() { return statut; }
        public void setStatut(String statut) { this.statut = statut; }
    }

    /**
     * DTO pour l'état de répartition du produit des affaires contentieuses
     */
    public static class EtatRepartitionProduitDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private List<ProduitAffaireDTO> affaires;
        private TotauxProduitDTO totaux;
        private int totalAffaires;

        public EtatRepartitionProduitDTO() {
            this.affaires = new ArrayList<>();
            this.dateGeneration = LocalDate.now();
            this.totaux = new TotauxProduitDTO();
            this.totalAffaires = 0;
        }

        // Getters et setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        public List<ProduitAffaireDTO> getAffaires() { return affaires; }
        public void setAffaires(List<ProduitAffaireDTO> affaires) { this.affaires = affaires; }

        public TotauxProduitDTO getTotaux() { return totaux; }
        public void setTotaux(TotauxProduitDTO totaux) { this.totaux = totaux; }

        public int getTotalAffaires() { return totalAffaires; }
        public void setTotalAffaires(int totalAffaires) { this.totalAffaires = totalAffaires; }
    }

    /**
     * DTO pour une ligne de produit d'affaire
     */
    public static class ProduitAffaireDTO {
        private String numeroEncaissement;
        private LocalDate dateEncaissement;
        private String numeroAffaire;
        private LocalDate dateAffaire;
        private String nomContrevenant;
        private String nomContravention;
        private BigDecimal produitDisponible;
        private BigDecimal partIndicateur;
        private BigDecimal partDirectionContentieux;
        private BigDecimal partIndicateur2;
        private BigDecimal flcf;
        private BigDecimal montantTresor;
        private BigDecimal montantGlobalAyantsDroits;

        public ProduitAffaireDTO() {}

        // Getters et setters
        public String getNumeroEncaissement() { return numeroEncaissement; }
        public void setNumeroEncaissement(String numeroEncaissement) { this.numeroEncaissement = numeroEncaissement; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public LocalDate getDateAffaire() { return dateAffaire; }
        public void setDateAffaire(LocalDate dateAffaire) { this.dateAffaire = dateAffaire; }

        public String getNomContrevenant() { return nomContrevenant; }
        public void setNomContrevenant(String nomContrevenant) { this.nomContrevenant = nomContrevenant; }

        public String getNomContravention() { return nomContravention; }
        public void setNomContravention(String nomContravention) { this.nomContravention = nomContravention; }

        public BigDecimal getProduitDisponible() { return produitDisponible; }
        public void setProduitDisponible(BigDecimal produitDisponible) { this.produitDisponible = produitDisponible; }

        public BigDecimal getPartIndicateur() { return partIndicateur; }
        public void setPartIndicateur(BigDecimal partIndicateur) { this.partIndicateur = partIndicateur; }

        public BigDecimal getPartDirectionContentieux() { return partDirectionContentieux; }
        public void setPartDirectionContentieux(BigDecimal partDirectionContentieux) { this.partDirectionContentieux = partDirectionContentieux; }

        public BigDecimal getPartIndicateur2() { return partIndicateur2; }
        public void setPartIndicateur2(BigDecimal partIndicateur2) { this.partIndicateur2 = partIndicateur2; }

        public BigDecimal getFlcf() { return flcf; }
        public void setFlcf(BigDecimal flcf) { this.flcf = flcf; }

        public BigDecimal getMontantTresor() { return montantTresor; }
        public void setMontantTresor(BigDecimal montantTresor) { this.montantTresor = montantTresor; }

        public BigDecimal getMontantGlobalAyantsDroits() { return montantGlobalAyantsDroits; }
        public void setMontantGlobalAyantsDroits(BigDecimal montantGlobalAyantsDroits) { this.montantGlobalAyantsDroits = montantGlobalAyantsDroits; }
    }

    /**
     * DTO pour les totaux de produit
     */
    public static class TotauxProduitDTO {
        private BigDecimal totalProduitDisponible;
        private BigDecimal totalPartIndicateur;
        private BigDecimal totalPartDirectionContentieux;
        private BigDecimal totalPartIndicateur2;
        private BigDecimal totalFlcf;
        private BigDecimal totalMontantTresor;
        private BigDecimal totalMontantGlobalAyantsDroits;

        public TotauxProduitDTO() {
            this.totalProduitDisponible = BigDecimal.ZERO;
            this.totalPartIndicateur = BigDecimal.ZERO;
            this.totalPartDirectionContentieux = BigDecimal.ZERO;
            this.totalPartIndicateur2 = BigDecimal.ZERO;
            this.totalFlcf = BigDecimal.ZERO;
            this.totalMontantTresor = BigDecimal.ZERO;
            this.totalMontantGlobalAyantsDroits = BigDecimal.ZERO;
        }

        // Getters et setters
        public BigDecimal getTotalProduitDisponible() { return totalProduitDisponible; }
        public void setTotalProduitDisponible(BigDecimal totalProduitDisponible) { this.totalProduitDisponible = totalProduitDisponible; }

        public BigDecimal getTotalPartIndicateur() { return totalPartIndicateur; }
        public void setTotalPartIndicateur(BigDecimal totalPartIndicateur) { this.totalPartIndicateur = totalPartIndicateur; }

        public BigDecimal getTotalPartDirectionContentieux() { return totalPartDirectionContentieux; }
        public void setTotalPartDirectionContentieux(BigDecimal totalPartDirectionContentieux) { this.totalPartDirectionContentieux = totalPartDirectionContentieux; }

        public BigDecimal getTotalPartIndicateur2() { return totalPartIndicateur2; }
        public void setTotalPartIndicateur2(BigDecimal totalPartIndicateur2) { this.totalPartIndicateur2 = totalPartIndicateur2; }

        public BigDecimal getTotalFlcf() { return totalFlcf; }
        public void setTotalFlcf(BigDecimal totalFlcf) { this.totalFlcf = totalFlcf; }

        public BigDecimal getTotalMontantTresor() { return totalMontantTresor; }
        public void setTotalMontantTresor(BigDecimal totalMontantTresor) { this.totalMontantTresor = totalMontantTresor; }

        public BigDecimal getTotalMontantGlobalAyantsDroits() { return totalMontantGlobalAyantsDroits; }
        public void setTotalMontantGlobalAyantsDroits(BigDecimal totalMontantGlobalAyantsDroits) { this.totalMontantGlobalAyantsDroits = totalMontantGlobalAyantsDroits; }
    }

    /**
     * DTO pour l'état cumulé par agent
     */
    public static class EtatCumuleParAgentDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private List<AgentCumuleDTO> agents;
        private BigDecimal totalGeneral;
        private int totalAffairesTraitees;

        public EtatCumuleParAgentDTO() {
            this.agents = new ArrayList<>();
            this.dateGeneration = LocalDate.now();
            this.totalGeneral = BigDecimal.ZERO;
            this.totalAffairesTraitees = 0;
        }

        // Getters et setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        public List<AgentCumuleDTO> getAgents() { return agents; }
        public void setAgents(List<AgentCumuleDTO> agents) { this.agents = agents; }

        public BigDecimal getTotalGeneral() { return totalGeneral; }
        public void setTotalGeneral(BigDecimal totalGeneral) { this.totalGeneral = totalGeneral; }

        public int getTotalAffairesTraitees() { return totalAffairesTraitees; }
        public void setTotalAffairesTraitees(int totalAffairesTraitees) { this.totalAffairesTraitees = totalAffairesTraitees; }
    }

    /**
     * DTO pour un agent dans l'état cumulé
     */
    public static class AgentCumuleDTO {
        private String nomAgent;
        private String codeAgent;
        private BigDecimal partChef;
        private BigDecimal partSaisissant;
        private BigDecimal partDG;
        private BigDecimal partDD;
        private BigDecimal partTotaleAgent;
        private int nombreAffairesChef;
        private int nombreAffairesSaisissant;

        public AgentCumuleDTO() {
            this.partChef = BigDecimal.ZERO;
            this.partSaisissant = BigDecimal.ZERO;
            this.partDG = BigDecimal.ZERO;
            this.partDD = BigDecimal.ZERO;
            this.partTotaleAgent = BigDecimal.ZERO;
            this.nombreAffairesChef = 0;
            this.nombreAffairesSaisissant = 0;
        }

        public AgentCumuleDTO(String nomAgent, String codeAgent) {
            this();
            this.nomAgent = nomAgent;
            this.codeAgent = codeAgent;
        }

        // Getters et setters
        public String getNomAgent() { return nomAgent; }
        public void setNomAgent(String nomAgent) { this.nomAgent = nomAgent; }

        public String getCodeAgent() { return codeAgent; }
        public void setCodeAgent(String codeAgent) { this.codeAgent = codeAgent; }

        public BigDecimal getPartChef() { return partChef; }
        public void setPartChef(BigDecimal partChef) { this.partChef = partChef; }

        public BigDecimal getPartSaisissant() { return partSaisissant; }
        public void setPartSaisissant(BigDecimal partSaisissant) { this.partSaisissant = partSaisissant; }

        public BigDecimal getPartDG() { return partDG; }
        public void setPartDG(BigDecimal partDG) { this.partDG = partDG; }

        public BigDecimal getPartDD() { return partDD; }
        public void setPartDD(BigDecimal partDD) { this.partDD = partDD; }

        public BigDecimal getPartTotaleAgent() { return partTotaleAgent; }
        public void setPartTotaleAgent(BigDecimal partTotaleAgent) { this.partTotaleAgent = partTotaleAgent; }

        public int getNombreAffairesChef() { return nombreAffairesChef; }
        public void setNombreAffairesChef(int nombreAffairesChef) { this.nombreAffairesChef = nombreAffairesChef; }

        public int getNombreAffairesSaisissant() { return nombreAffairesSaisissant; }
        public void setNombreAffairesSaisissant(int nombreAffairesSaisissant) { this.nombreAffairesSaisissant = nombreAffairesSaisissant; }

        /**
         * Calcule le total de l'agent (somme de toutes ses parts)
         */
        public void calculerTotal() {
            this.partTotaleAgent = this.partChef.add(this.partSaisissant)
                    .add(this.partDG)
                    .add(this.partDD);
        }
    }

    /**
     * DTO pour le tableau des amendes par services
     */
    public static class TableauAmendesParServicesDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private List<ServiceAmendeDTO> services;
        private BigDecimal totalMontant;
        private int totalAffaires;

        public TableauAmendesParServicesDTO() {
            this.services = new ArrayList<>();
            this.dateGeneration = LocalDate.now();
            this.totalMontant = BigDecimal.ZERO;
            this.totalAffaires = 0;
        }

        // Getters et setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        public List<ServiceAmendeDTO> getServices() { return services; }
        public void setServices(List<ServiceAmendeDTO> services) { this.services = services; }

        public BigDecimal getTotalMontant() { return totalMontant; }
        public void setTotalMontant(BigDecimal totalMontant) { this.totalMontant = totalMontant; }

        public int getTotalAffaires() { return totalAffaires; }
        public void setTotalAffaires(int totalAffaires) { this.totalAffaires = totalAffaires; }
    }

    /**
     * DTO pour une ligne du tableau des amendes par service
     */
    public static class ServiceAmendeDTO {
        private String nomService;
        private int nombreAffaires;
        private BigDecimal montantTotal;
        private String observations;

        public ServiceAmendeDTO() {}

        public ServiceAmendeDTO(String nomService, int nombreAffaires, BigDecimal montantTotal) {
            this.nomService = nomService;
            this.nombreAffaires = nombreAffaires;
            this.montantTotal = montantTotal;
            this.observations = "";
        }

        // Getters et setters
        public String getNomService() { return nomService; }
        public void setNomService(String nomService) { this.nomService = nomService; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }

        public BigDecimal getMontantTotal() { return montantTotal; }
        public void setMontantTotal(BigDecimal montantTotal) { this.montantTotal = montantTotal; }

        public String getObservations() { return observations; }
        public void setObservations(String observations) { this.observations = observations; }
    }
}