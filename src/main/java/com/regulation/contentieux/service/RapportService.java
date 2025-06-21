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
                        .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

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

    // ==================== IMPRIMÉ 1: ETAT DE REPARTITION DES AFFAIRES CONTENTIEUSES ====================

    /**
     * Génère l'état de répartition des affaires contentieuses (Imprimé 1)
     */
    public EtatRepartitionAffairesDTO genererEtatRepartitionAffaires(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état de répartition des affaires contentieuses du {} au {}", dateDebut, dateFin);

            EtatRepartitionAffairesDTO etat = new EtatRepartitionAffairesDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

            // Récupération des affaires avec encaissements
            List<Affaire> affaires = affaireDAO.findAffairesWithEncaissementsByPeriod(dateDebut, dateFin);

            List<AffaireRepartitionCompleteDTO> affairesDTO = new ArrayList<>();
            TotauxRepartitionDTO totaux = new TotauxRepartitionDTO();

            // Traitement de chaque affaire
            for (Affaire affaire : affaires) {
                List<Encaissement> encaissements = encaissementDAO.findByAffaireAndPeriod(
                        affaire.getId(), dateDebut, dateFin);

                for (Encaissement encaissement : encaissements) {
                    if (encaissement.getStatut() == StatutEncaissement.VALIDE) {
                        AffaireRepartitionCompleteDTO affaireDTO = createAffaireRepartitionCompleteDTO(affaire, encaissement);
                        affairesDTO.add(affaireDTO);

                        // Accumulation des totaux
                        BigDecimal produit = BigDecimal.valueOf(encaissement.getMontant());
                        totaux.setTotalProduitDisponible(totaux.getTotalProduitDisponible().add(produit));
                    }
                }
            }

            etat.setAffaires(affairesDTO);
            etat.setTotaux(totaux);
            etat.setTotalAffaires(affaires.size());

            logger.info("État de répartition des affaires contentieuses généré: {} affaires", affaires.size());
            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état de répartition des affaires contentieuses", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }

    // ==================== IMPRIMÉ 4: ETAT DE REPARTITION DES INDICATEURS REELS ====================

    /**
     * Génère l'état de répartition des indicateurs réels (Imprimé 4)
     * Alias pour la compatibilité avec PrintService
     */
    public EtatRepartitionIndicateursReelsDTO genererEtatIndicateursReels(LocalDate dateDebut, LocalDate dateFin) {
        return genererEtatRepartitionIndicateursReels(dateDebut, dateFin);
    }

    /**
     * Génère l'état de répartition des indicateurs réels (Imprimé 4)
     */
    public EtatRepartitionIndicateursReelsDTO genererEtatRepartitionIndicateursReels(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état de répartition des indicateurs réels du {} au {}", dateDebut, dateFin);

            EtatRepartitionIndicateursReelsDTO etat = new EtatRepartitionIndicateursReelsDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

            // Récupération des affaires avec encaissements
            List<Affaire> affaires = affaireDAO.findAffairesWithEncaissementsByPeriod(dateDebut, dateFin);

            // Groupement par Service puis par Section
            Map<String, Map<String, List<Affaire>>> hierarchie = new HashMap<>();

            for (Affaire affaire : affaires) {
                String nomService = determinerServiceAffaire(affaire);
                String nomSection = determinerSectionAffaire(affaire);

                hierarchie.computeIfAbsent(nomService, k -> new HashMap<>())
                        .computeIfAbsent(nomSection, k -> new ArrayList<>())
                        .add(affaire);
            }

            // Construction des DTOs
            List<ServiceIndicateurDTO> servicesDTO = new ArrayList<>();
            BigDecimal totalGeneralMontant = BigDecimal.ZERO;
            BigDecimal totalGeneralIndicateur = BigDecimal.ZERO;
            int totalGeneralAffaires = 0;

            for (Map.Entry<String, Map<String, List<Affaire>>> serviceEntry : hierarchie.entrySet()) {
                String nomService = serviceEntry.getKey();
                Map<String, List<Affaire>> sectionsMap = serviceEntry.getValue();

                ServiceIndicateurDTO serviceDTO = new ServiceIndicateurDTO();
                serviceDTO.setNomService(nomService);

                List<SectionIndicateurDTO> sectionsDTO = new ArrayList<>();
                BigDecimal totalMontantService = BigDecimal.ZERO;
                BigDecimal totalIndicateurService = BigDecimal.ZERO;
                int totalAffairesService = 0;

                for (Map.Entry<String, List<Affaire>> sectionEntry : sectionsMap.entrySet()) {
                    String nomSection = sectionEntry.getKey();
                    List<Affaire> affairesSection = sectionEntry.getValue();

                    SectionIndicateurDTO sectionDTO = new SectionIndicateurDTO();
                    sectionDTO.setNomSection(nomSection);

                    List<AffaireIndicateurDTO> affairesSectionDTO = new ArrayList<>();
                    BigDecimal totalMontantSection = BigDecimal.ZERO;
                    BigDecimal totalIndicateurSection = BigDecimal.ZERO;

                    for (Affaire affaire : affairesSection) {
                        AffaireIndicateurDTO affaireDTO = createAffaireIndicateurDTO(affaire, dateDebut, dateFin);
                        affairesSectionDTO.add(affaireDTO);

                        totalMontantSection = totalMontantSection.add(affaireDTO.getMontantEncaissement());
                        totalIndicateurSection = totalIndicateurSection.add(affaireDTO.getPartIndicateur());
                    }

                    sectionDTO.setAffaires(affairesSectionDTO);
                    sectionDTO.setTotalMontant(totalMontantSection);
                    sectionDTO.setTotalIndicateur(totalIndicateurSection);
                    sectionDTO.setNombreAffaires(affairesSection.size());

                    sectionsDTO.add(sectionDTO);

                    totalMontantService = totalMontantService.add(totalMontantSection);
                    totalIndicateurService = totalIndicateurService.add(totalIndicateurSection);
                    totalAffairesService += affairesSection.size();
                }

                serviceDTO.setSections(sectionsDTO);
                serviceDTO.setTotalMontant(totalMontantService);
                serviceDTO.setTotalIndicateur(totalIndicateurService);
                serviceDTO.setNombreAffaires(totalAffairesService);

                servicesDTO.add(serviceDTO);

                totalGeneralMontant = totalGeneralMontant.add(totalMontantService);
                totalGeneralIndicateur = totalGeneralIndicateur.add(totalIndicateurService);
                totalGeneralAffaires += totalAffairesService;
            }

            etat.setServices(servicesDTO);
            etat.setTotalGeneralMontant(totalGeneralMontant);
            etat.setTotalGeneralIndicateur(totalGeneralIndicateur);
            etat.setTotalGeneralAffaires(totalGeneralAffaires);

            logger.info("État de répartition des indicateurs réels généré: {} services", servicesDTO.size());
            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état de répartition des indicateurs réels", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
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

    // ==================== AUTRES IMPRIMES ====================

    /**
     * Génère l'état de mandatement (Imprimé 2)
     */
    public EtatMandatementDTO genererEtatMandatement(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état de mandatement du {} au {}", dateDebut, dateFin);

            EtatMandatementDTO etat = new EtatMandatementDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

            // TODO: Implémenter la logique de mandatement

            logger.info("État de mandatement généré (placeholder)");
            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état de mandatement", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }

    /**
     * Génère l'état de centre de répartition (Imprimé 3)
     */
    public EtatCentreRepartitionDTO genererEtatCentreRepartition(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état par centre de répartition du {} au {}", dateDebut, dateFin);

            EtatCentreRepartitionDTO etat = new EtatCentreRepartitionDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

            // TODO: Implémenter la logique des centres de répartition

            logger.info("État cumulé par centre de répartition généré (placeholder)");
            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état par centre de répartition", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }

    /**
     * Génère l'état de mandatement agents (Imprimé 8)
     */
    public EtatMandatementDTO genererEtatMandatementAgents(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état de mandatement agents du {} au {}", dateDebut, dateFin);

            EtatMandatementDTO etat = new EtatMandatementDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));
            etat.setTypeEtat("AGENTS");

            // TODO: Implémenter la logique spécifique aux agents

            logger.info("État de mandatement agents généré (placeholder)");
            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état de mandatement agents", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }

    // ==================== RAPPORTS PÉRIODIQUES ====================

    /**
     * Génère un rapport mensuel
     */
    public RapportRepartitionDTO genererRapportMensuel(int annee, int mois) {
        LocalDate debut = LocalDate.of(annee, mois, 1);
        LocalDate fin = debut.withDayOfMonth(debut.lengthOfMonth());
        return genererRapportRepartition(debut, fin);
    }

    /**
     * Génère un rapport trimestriel
     */
    public RapportRepartitionDTO genererRapportTrimestriel(int annee, int trimestre) {
        int moisDebut = (trimestre - 1) * 3 + 1;
        LocalDate debut = LocalDate.of(annee, moisDebut, 1);
        LocalDate fin = debut.plusMonths(2).withDayOfMonth(debut.plusMonths(2).lengthOfMonth());
        return genererRapportRepartition(debut, fin);
    }

    /**
     * Génère un rapport annuel
     */
    public RapportRepartitionDTO genererRapportAnnuel(int annee) {
        LocalDate debut = LocalDate.of(annee, 1, 1);
        LocalDate fin = LocalDate.of(annee, 12, 31);
        return genererRapportRepartition(debut, fin);
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Calcule les statistiques avancées pour un rapport
     */
    public Map<String, Object> calculerStatistiquesAvancees(RapportRepartitionDTO rapport) {
        Map<String, Object> statistiques = new HashMap<>();

        if (rapport == null || rapport.getAffaires().isEmpty()) {
            return statistiques;
        }

        // Calculs de moyennes
        BigDecimal moyenneMontant = rapport.getTotalEncaisse()
                .divide(BigDecimal.valueOf(rapport.getNombreAffaires()), 2, RoundingMode.HALF_UP);

        // Répartition par mois
        Map<String, BigDecimal> repartitionMensuelle = new HashMap<>();
        Map<String, Integer> evolutionStatuts = new HashMap<>();

        for (AffaireRepartitionDTO affaire : rapport.getAffaires()) {
            String mois = affaire.getDateCreation().getMonth().name();
            repartitionMensuelle.merge(mois, affaire.getMontantAmende(), BigDecimal::add);
            evolutionStatuts.merge(affaire.getStatut(), 1, Integer::sum);
        }

        statistiques.put("moyenneMontantParAffaire", moyenneMontant);
        statistiques.put("repartitionMensuelle", repartitionMensuelle);
        statistiques.put("evolutionStatuts", evolutionStatuts);

        // Calcul du taux d'encaissement
        if (rapport.getTotalEncaisse().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal total = rapport.getTotalEncaisse().add(rapport.getTotalEtat());
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal taux = rapport.getTotalEncaisse()
                        .divide(total, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                statistiques.put("tauxEncaissement", taux);
            }
        }

        return statistiques;
    }

    /**
     * Valide les paramètres d'un rapport
     */
    public boolean validerParametresRapport(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null) {
            return false;
        }

        if (dateDebut.isAfter(dateFin)) {
            return false;
        }

        // Vérifier que la période n'est pas dans le futur
        if (dateDebut.isAfter(LocalDate.now())) {
            return false;
        }

        // Vérifier que la période n'est pas trop étendue (ex: plus de 2 ans)
        if (dateDebut.plusYears(2).isBefore(dateFin)) {
            return false;
        }

        return true;
    }

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

    /**
     * Crée un DTO d'affaire complet pour les rapports détaillés
     */
    private AffaireRepartitionCompleteDTO createAffaireRepartitionCompleteDTO(Affaire affaire, Encaissement encaissement) {
        AffaireRepartitionCompleteDTO dto = new AffaireRepartitionCompleteDTO();

        dto.setNumeroEncaissement(encaissement.getReference());
        dto.setDateEncaissement(encaissement.getDateEncaissement());
        dto.setNumeroAffaire(affaire.getNumeroAffaire());
        dto.setDateAffaire(affaire.getDateCreation());

        BigDecimal produitDisponible = BigDecimal.valueOf(encaissement.getMontant());
        dto.setProduitDisponible(produitDisponible);

        // Calculs de répartition - Exemple de logique
        dto.setIndicateur(produitDisponible.multiply(new BigDecimal("0.05")));
        dto.setProduitNet(produitDisponible.multiply(new BigDecimal("0.95")));
        dto.setFlcf(dto.getProduitNet().multiply(new BigDecimal("0.10")));
        dto.setTresor(dto.getProduitNet().multiply(POURCENTAGE_ETAT.divide(BigDecimal.valueOf(100))));

        dto.setDirectionDepartementale("DD " + affaire.getId() % 5);

        return dto;
    }

    /**
     * Crée un DTO d'affaire pour les indicateurs
     */
    private AffaireIndicateurDTO createAffaireIndicateurDTO(Affaire affaire, LocalDate dateDebut, LocalDate dateFin) {
        AffaireIndicateurDTO dto = new AffaireIndicateurDTO();

        dto.setNumeroAffaire(affaire.getNumeroAffaire());
        dto.setDateAffaire(affaire.getDateCreation());
        dto.setNomContrevenant("Contrevenant " + affaire.getId());
        dto.setNomContravention("Contravention " + affaire.getId());

        // Calcul du montant encaissé pour cette affaire sur la période
        List<Encaissement> encaissements = encaissementDAO.findByAffaireAndPeriod(affaire.getId(), dateDebut, dateFin);
        BigDecimal montantEncaisse = encaissements.stream()
                .filter(enc -> enc.getStatut() == StatutEncaissement.VALIDE)
                .map(enc -> BigDecimal.valueOf(enc.getMontant()))
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

        dto.setMontantEncaissement(montantEncaisse);
        dto.setPartIndicateur(montantEncaisse.multiply(new BigDecimal("0.05")));
        dto.setObservations("");

        return dto;
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

        private BigDecimal totalMontant;      // Ajouter ce champ
        private BigDecimal totalPartEtat;      // Ajouter ce champ
        private BigDecimal totalPartCollectivite; // Ajouter ce champ

        public RapportRepartitionDTO() {
            this.affaires = new ArrayList<>();
            this.repartitionParBureau = new HashMap<>();
            this.repartitionParAgent = new HashMap<>();
            this.nombreAffairesParStatut = new HashMap<>();
            this.dateGeneration = LocalDate.now();
        }

        // Getters et Setters

        public BigDecimal getTotalMontant() {
            return totalMontant != null ? totalMontant : totalEncaisse;
        }
        public void setTotalMontant(BigDecimal totalMontant) {
            this.totalMontant = totalMontant;
        }

        public BigDecimal getTotalPartEtat() {
            return totalPartEtat != null ? totalPartEtat : totalEtat;
        }
        public void setTotalPartEtat(BigDecimal totalPartEtat) {
            this.totalPartEtat = totalPartEtat;
        }

        public BigDecimal getTotalPartCollectivite() {
            return totalPartCollectivite != null ? totalPartCollectivite : totalCollectivite;
        }
        public void setTotalPartCollectivite(BigDecimal totalPartCollectivite) {
            this.totalPartCollectivite = totalPartCollectivite;
        }

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
        private String contrevenant;      // Ajouter ce champ si manquant
        private BigDecimal montantTotal;  // Ajouter ce champ si manquant

        public AffaireRepartitionDTO() {}

        // Getters et setters

        // Ajouter ces getters/setters
        public String getContrevenant() {
            return contrevenant != null ? contrevenant : contrevenantNom;
        }
        public void setContrevenant(String contrevenant) {
            this.contrevenant = contrevenant;
        }

        public BigDecimal getMontantTotal() {
            return montantTotal != null ? montantTotal : montantAmende;
        }
        public void setMontantTotal(BigDecimal montantTotal) {
            this.montantTotal = montantTotal;
        }

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
     * DTO pour l'état de répartition des affaires contentieuses (Imprimé 1)
     */
    public static class EtatRepartitionAffairesDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private List<AffaireRepartitionCompleteDTO> affaires;
        private TotauxRepartitionDTO totaux;
        private int totalAffaires;

        public EtatRepartitionAffairesDTO() {
            this.affaires = new ArrayList<>();
            this.dateGeneration = LocalDate.now();
            this.totaux = new TotauxRepartitionDTO();
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

        public List<AffaireRepartitionCompleteDTO> getAffaires() { return affaires; }
        public void setAffaires(List<AffaireRepartitionCompleteDTO> affaires) { this.affaires = affaires; }

        public TotauxRepartitionDTO getTotaux() { return totaux; }
        public void setTotaux(TotauxRepartitionDTO totaux) { this.totaux = totaux; }

        public int getTotalAffaires() { return totalAffaires; }
        public void setTotalAffaires(int totalAffaires) { this.totalAffaires = totalAffaires; }
    }

    /**
     * DTO pour une ligne complète de répartition d'affaire (Imprimé 1)
     */
    public static class AffaireRepartitionCompleteDTO {
        private String numeroEncaissement;
        private LocalDate dateEncaissement;
        private String numeroAffaire;
        private LocalDate dateAffaire;
        private BigDecimal produitDisponible;
        private String directionDepartementale;
        private BigDecimal indicateur;
        private BigDecimal produitNet;
        private BigDecimal flcf;
        private BigDecimal tresor;
        private BigDecimal produitNetAyantsDroits;
        private BigDecimal chefs;
        private BigDecimal saisissants;
        private BigDecimal mutuelleNationale;
        private BigDecimal masseCommune;
        private BigDecimal interessement;

        public AffaireRepartitionCompleteDTO() {}

        // Getters et setters
        public String getNumeroEncaissement() { return numeroEncaissement; }
        public void setNumeroEncaissement(String numeroEncaissement) { this.numeroEncaissement = numeroEncaissement; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public LocalDate getDateAffaire() { return dateAffaire; }
        public void setDateAffaire(LocalDate dateAffaire) { this.dateAffaire = dateAffaire; }

        public BigDecimal getProduitDisponible() { return produitDisponible; }
        public void setProduitDisponible(BigDecimal produitDisponible) { this.produitDisponible = produitDisponible; }

        public String getDirectionDepartementale() { return directionDepartementale; }
        public void setDirectionDepartementale(String directionDepartementale) { this.directionDepartementale = directionDepartementale; }

        public BigDecimal getIndicateur() { return indicateur; }
        public void setIndicateur(BigDecimal indicateur) { this.indicateur = indicateur; }

        public BigDecimal getProduitNet() { return produitNet; }
        public void setProduitNet(BigDecimal produitNet) { this.produitNet = produitNet; }

        public BigDecimal getFlcf() { return flcf; }
        public void setFlcf(BigDecimal flcf) { this.flcf = flcf; }

        public BigDecimal getTresor() { return tresor; }
        public void setTresor(BigDecimal tresor) { this.tresor = tresor; }

        public BigDecimal getProduitNetAyantsDroits() { return produitNetAyantsDroits; }
        public void setProduitNetAyantsDroits(BigDecimal produitNetAyantsDroits) { this.produitNetAyantsDroits = produitNetAyantsDroits; }

        public BigDecimal getChefs() { return chefs; }
        public void setChefs(BigDecimal chefs) { this.chefs = chefs; }

        public BigDecimal getSaisissants() { return saisissants; }
        public void setSaisissants(BigDecimal saisissants) { this.saisissants = saisissants; }

        public BigDecimal getMutuelleNationale() { return mutuelleNationale; }
        public void setMutuelleNationale(BigDecimal mutuelleNationale) { this.mutuelleNationale = mutuelleNationale; }

        public BigDecimal getMasseCommune() { return masseCommune; }
        public void setMasseCommune(BigDecimal masseCommune) { this.masseCommune = masseCommune; }

        public BigDecimal getInteressement() { return interessement; }
        public void setInteressement(BigDecimal interessement) { this.interessement = interessement; }
    }

    /**
     * Génère le HTML pour le rapport de répartition
     */
    public String genererHtmlRapportRepartition(RapportRepartitionDTO rapport) {
        StringBuilder html = new StringBuilder();
        html.append("<h1>État de Répartition et de Rétrocession</h1>");
        html.append("<p>Période: ").append(rapport.getPeriodeLibelle()).append("</p>");

        html.append("<table class='table table-bordered'>");
        html.append("<thead><tr>");
        html.append("<th>N° Affaire</th>");
        html.append("<th>Contrevenant</th>");
        html.append("<th>Montant Total</th>");
        html.append("<th>Montant Encaissé</th>");
        html.append("<th>Part État</th>");
        html.append("<th>Part Collectivité</th>");
        html.append("</tr></thead>");
        html.append("<tbody>");

        for (AffaireRepartitionDTO affaire : rapport.getAffaires()) {
            html.append("<tr>");
            html.append("<td>").append(affaire.getNumeroAffaire()).append("</td>");
            html.append("<td>").append(affaire.getContrevenant()).append("</td>");
            html.append("<td class='text-right'>").append(CurrencyFormatter.format(affaire.getMontantTotal())).append("</td>");
            html.append("<td class='text-right'>").append(CurrencyFormatter.format(affaire.getMontantEncaisse())).append("</td>");
            html.append("<td class='text-right'>").append(CurrencyFormatter.format(affaire.getPartEtat())).append("</td>");
            html.append("<td class='text-right'>").append(CurrencyFormatter.format(affaire.getPartCollectivite())).append("</td>");
            html.append("</tr>");
        }

        html.append("</tbody>");
        html.append("<tfoot><tr class='font-weight-bold'>");
        html.append("<td colspan='2'>TOTAUX</td>");
        html.append("<td class='text-right'>").append(CurrencyFormatter.format(rapport.getTotalMontant())).append("</td>");
        html.append("<td class='text-right'>").append(CurrencyFormatter.format(rapport.getTotalEncaisse())).append("</td>");
        html.append("<td class='text-right'>").append(CurrencyFormatter.format(rapport.getTotalPartEtat())).append("</td>");
        html.append("<td class='text-right'>").append(CurrencyFormatter.format(rapport.getTotalPartCollectivite())).append("</td>");
        html.append("</tr></tfoot>");
        html.append("</table>");

        return html.toString();
    }

    /**
     * Génère la situation générale
     */
    public SituationGeneraleDTO genererSituationGenerale(LocalDate dateDebut, LocalDate dateFin) {
        SituationGeneraleDTO situation = new SituationGeneraleDTO();
        situation.setDateDebut(dateDebut);
        situation.setDateFin(dateFin);
        situation.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

        // Récupération des affaires
        List<Affaire> affaires = affaireDAO.findByPeriod(dateDebut, dateFin);

        // Statistiques globales
        situation.setTotalAffaires(affaires.size());
        situation.setAffairesOuvertes((int) affaires.stream()
                .filter(a -> a.getStatut() == StatutAffaire.OUVERTE).count());
        situation.setAffairesEnCours((int) affaires.stream()
                .filter(a -> a.getStatut() == StatutAffaire.EN_COURS).count());
        situation.setAffairesSoldees((int) affaires.stream()
                .filter(a -> a.getStatut() == StatutAffaire.SOLDEE).count());

        // Montants
        BigDecimal totalAmendes = affaires.stream()
                .map(a -> a.getMontantTotal() != null ? a.getMontantTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEncaisse = affaires.stream()
                .map(a -> a.getMontantEncaisse() != null ? a.getMontantEncaisse() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        situation.setMontantTotalAmendes(totalAmendes);
        situation.setMontantTotalEncaisse(totalEncaisse);
        situation.setMontantRestantDu(totalAmendes.subtract(totalEncaisse));

        if (totalAmendes.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal taux = totalEncaisse.multiply(BigDecimal.valueOf(100))
                    .divide(totalAmendes, 2, RoundingMode.HALF_UP);
            situation.setTauxRecouvrement(taux);
        }

        return situation;
    }

    /**
     * Génère le HTML pour la situation générale
     */
    public String genererHtmlSituationGenerale(SituationGeneraleDTO situation) {
        StringBuilder html = new StringBuilder();
        html.append("<h1>Situation Générale des Affaires Contentieuses</h1>");
        html.append("<p>Période: ").append(situation.getPeriodeLibelle()).append("</p>");

        html.append("<div class='row'>");
        html.append("<div class='col-md-6'>");
        html.append("<h3>Statistiques</h3>");
        html.append("<ul>");
        html.append("<li>Total des affaires: ").append(situation.getTotalAffaires()).append("</li>");
        html.append("<li>Affaires ouvertes: ").append(situation.getAffairesOuvertes()).append("</li>");
        html.append("<li>Affaires en cours: ").append(situation.getAffairesEnCours()).append("</li>");
        html.append("<li>Affaires soldées: ").append(situation.getAffairesSoldees()).append("</li>");
        html.append("</ul>");
        html.append("</div>");

        html.append("<div class='col-md-6'>");
        html.append("<h3>Montants</h3>");
        html.append("<ul>");
        html.append("<li>Montant total des amendes: ").append(CurrencyFormatter.format(situation.getMontantTotalAmendes())).append("</li>");
        html.append("<li>Montant encaissé: ").append(CurrencyFormatter.format(situation.getMontantTotalEncaisse())).append("</li>");
        html.append("<li>Montant restant dû: ").append(CurrencyFormatter.format(situation.getMontantRestantDu())).append("</li>");
        html.append("<li>Taux de recouvrement: ").append(situation.getTauxRecouvrement()).append("%</li>");
        html.append("</ul>");
        html.append("</div>");
        html.append("</div>");

        return html.toString();
    }

    /**
     * Génère le HTML pour le tableau des amendes
     */
    public String genererHtmlTableauAmendes(TableauAmendesParServicesDTO tableau) {
        // TODO: Implémenter
        return "<h1>Tableau des Amendes par Service</h1><p>En cours de développement</p>";
    }

    /**
     * Génère le rapport des encaissements
     */
    public Object genererRapportEncaissements(LocalDate dateDebut, LocalDate dateFin) {
        // TODO: Implémenter
        return new Object();
    }

    /**
     * Génère le HTML des encaissements
     */
    public String genererHtmlEncaissements(Object data) {
        // TODO: Implémenter
        return "<h1>État des Encaissements</h1><p>En cours de développement</p>";
    }

    /**
     * Génère le rapport des affaires non soldées
     */
    public Object genererRapportAffairesNonSoldees() {
        // TODO: Implémenter
        return new Object();
    }

    /**
     * Génère le HTML des affaires non soldées
     */
    public String genererHtmlAffairesNonSoldees(Object data) {
        // TODO: Implémenter
        return "<h1>Affaires Non Soldées</h1><p>En cours de développement</p>";
    }

    /**
     * DTO pour les totaux de répartition (Imprimé 1)
     */
    public static class TotauxRepartitionDTO {
        private BigDecimal totalProduitDisponible;
        private BigDecimal totalIndicateur;
        private BigDecimal totalProduitNet;
        private BigDecimal totalFlcf;
        private BigDecimal totalTresor;
        private BigDecimal totalProduitNetAyantsDroits;
        private BigDecimal totalChefs;
        private BigDecimal totalSaisissants;
        private BigDecimal totalMutuelleNationale;
        private BigDecimal totalMasseCommune;
        private BigDecimal totalInteressement;

        public TotauxRepartitionDTO() {
            this.totalProduitDisponible = BigDecimal.ZERO;
            this.totalIndicateur = BigDecimal.ZERO;
            this.totalProduitNet = BigDecimal.ZERO;
            this.totalFlcf = BigDecimal.ZERO;
            this.totalTresor = BigDecimal.ZERO;
            this.totalProduitNetAyantsDroits = BigDecimal.ZERO;
            this.totalChefs = BigDecimal.ZERO;
            this.totalSaisissants = BigDecimal.ZERO;
            this.totalMutuelleNationale = BigDecimal.ZERO;
            this.totalMasseCommune = BigDecimal.ZERO;
            this.totalInteressement = BigDecimal.ZERO;
        }

        // Getters et setters
        public BigDecimal getTotalProduitDisponible() { return totalProduitDisponible; }
        public void setTotalProduitDisponible(BigDecimal totalProduitDisponible) { this.totalProduitDisponible = totalProduitDisponible; }

        public BigDecimal getTotalIndicateur() { return totalIndicateur; }
        public void setTotalIndicateur(BigDecimal totalIndicateur) { this.totalIndicateur = totalIndicateur; }

        public BigDecimal getTotalProduitNet() { return totalProduitNet; }
        public void setTotalProduitNet(BigDecimal totalProduitNet) { this.totalProduitNet = totalProduitNet; }

        public BigDecimal getTotalFlcf() { return totalFlcf; }
        public void setTotalFlcf(BigDecimal totalFlcf) { this.totalFlcf = totalFlcf; }

        public BigDecimal getTotalTresor() { return totalTresor; }
        public void setTotalTresor(BigDecimal totalTresor) { this.totalTresor = totalTresor; }

        public BigDecimal getTotalProduitNetAyantsDroits() { return totalProduitNetAyantsDroits; }
        public void setTotalProduitNetAyantsDroits(BigDecimal totalProduitNetAyantsDroits) { this.totalProduitNetAyantsDroits = totalProduitNetAyantsDroits; }

        public BigDecimal getTotalChefs() { return totalChefs; }
        public void setTotalChefs(BigDecimal totalChefs) { this.totalChefs = totalChefs; }

        public BigDecimal getTotalSaisissants() { return totalSaisissants; }
        public void setTotalSaisissants(BigDecimal totalSaisissants) { this.totalSaisissants = totalSaisissants; }

        public BigDecimal getTotalMutuelleNationale() { return totalMutuelleNationale; }
        public void setTotalMutuelleNationale(BigDecimal totalMutuelleNationale) { this.totalMutuelleNationale = totalMutuelleNationale; }

        public BigDecimal getTotalMasseCommune() { return totalMasseCommune; }
        public void setTotalMasseCommune(BigDecimal totalMasseCommune) { this.totalMasseCommune = totalMasseCommune; }

        public BigDecimal getTotalInteressement() { return totalInteressement; }
        public void setTotalInteressement(BigDecimal totalInteressement) { this.totalInteressement = totalInteressement; }
    }

    /**
     * DTO pour l'état de répartition des indicateurs réels (Imprimé 4)
     */
    public static class EtatRepartitionIndicateursReelsDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private List<ServiceIndicateurDTO> services;
        private BigDecimal totalGeneralMontant;
        private BigDecimal totalGeneralIndicateur;
        private int totalGeneralAffaires;

        public EtatRepartitionIndicateursReelsDTO() {
            this.services = new ArrayList<>();
            this.dateGeneration = LocalDate.now();
            this.totalGeneralMontant = BigDecimal.ZERO;
            this.totalGeneralIndicateur = BigDecimal.ZERO;
            this.totalGeneralAffaires = 0;
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

        public List<ServiceIndicateurDTO> getServices() { return services; }
        public void setServices(List<ServiceIndicateurDTO> services) { this.services = services; }

        public BigDecimal getTotalGeneralMontant() { return totalGeneralMontant; }
        public void setTotalGeneralMontant(BigDecimal totalGeneralMontant) { this.totalGeneralMontant = totalGeneralMontant; }

        public BigDecimal getTotalGeneralIndicateur() { return totalGeneralIndicateur; }
        public void setTotalGeneralIndicateur(BigDecimal totalGeneralIndicateur) { this.totalGeneralIndicateur = totalGeneralIndicateur; }

        public int getTotalGeneralAffaires() { return totalGeneralAffaires; }
        public void setTotalGeneralAffaires(int totalGeneralAffaires) { this.totalGeneralAffaires = totalGeneralAffaires; }
    }

    /**
     * DTO pour un service dans l'état des indicateurs
     */
    public static class ServiceIndicateurDTO {
        private String nomService;
        private List<SectionIndicateurDTO> sections;
        private BigDecimal totalMontant;
        private BigDecimal totalIndicateur;
        private int nombreAffaires;

        public ServiceIndicateurDTO() {
            this.sections = new ArrayList<>();
            this.totalMontant = BigDecimal.ZERO;
            this.totalIndicateur = BigDecimal.ZERO;
            this.nombreAffaires = 0;
        }

        // Getters et setters
        public String getNomService() { return nomService; }
        public void setNomService(String nomService) { this.nomService = nomService; }

        public List<SectionIndicateurDTO> getSections() { return sections; }
        public void setSections(List<SectionIndicateurDTO> sections) { this.sections = sections; }

        public BigDecimal getTotalMontant() { return totalMontant; }
        public void setTotalMontant(BigDecimal totalMontant) { this.totalMontant = totalMontant; }

        public BigDecimal getTotalIndicateur() { return totalIndicateur; }
        public void setTotalIndicateur(BigDecimal totalIndicateur) { this.totalIndicateur = totalIndicateur; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }
    }

    /**
     * DTO pour une section dans l'état des indicateurs
     */
    public static class SectionIndicateurDTO {
        private String nomSection;
        private List<AffaireIndicateurDTO> affaires;
        private BigDecimal totalMontant;
        private BigDecimal totalIndicateur;
        private int nombreAffaires;

        public SectionIndicateurDTO() {
            this.affaires = new ArrayList<>();
            this.totalMontant = BigDecimal.ZERO;
            this.totalIndicateur = BigDecimal.ZERO;
            this.nombreAffaires = 0;
        }

        // Getters et setters
        public String getNomSection() { return nomSection; }
        public void setNomSection(String nomSection) { this.nomSection = nomSection; }

        public List<AffaireIndicateurDTO> getAffaires() { return affaires; }
        public void setAffaires(List<AffaireIndicateurDTO> affaires) { this.affaires = affaires; }

        public BigDecimal getTotalMontant() { return totalMontant; }
        public void setTotalMontant(BigDecimal totalMontant) { this.totalMontant = totalMontant; }

        public BigDecimal getTotalIndicateur() { return totalIndicateur; }
        public void setTotalIndicateur(BigDecimal totalIndicateur) { this.totalIndicateur = totalIndicateur; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }
    }

    /**
     * DTO pour une affaire dans l'état des indicateurs
     */
    public static class AffaireIndicateurDTO {
        private String numeroAffaire;
        private LocalDate dateAffaire;
        private String nomContrevenant;
        private String nomContravention;
        private BigDecimal montantEncaissement;
        private BigDecimal partIndicateur;
        private String observations;

        public AffaireIndicateurDTO() {}

        // Getters et setters
        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public LocalDate getDateAffaire() { return dateAffaire; }
        public void setDateAffaire(LocalDate dateAffaire) { this.dateAffaire = dateAffaire; }

        public String getNomContrevenant() { return nomContrevenant; }
        public void setNomContrevenant(String nomContrevenant) { this.nomContrevenant = nomContrevenant; }

        public String getNomContravention() { return nomContravention; }
        public void setNomContravention(String nomContravention) { this.nomContravention = nomContravention; }

        public BigDecimal getMontantEncaissement() { return montantEncaissement; }
        public void setMontantEncaissement(BigDecimal montantEncaissement) { this.montantEncaissement = montantEncaissement; }

        public BigDecimal getPartIndicateur() { return partIndicateur; }
        public void setPartIndicateur(BigDecimal partIndicateur) { this.partIndicateur = partIndicateur; }

        public String getObservations() { return observations; }
        public void setObservations(String observations) { this.observations = observations; }
    }

    /**
     * DTO pour l'état de répartition du produit des affaires contentieuses (Imprimé 5)
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
     * DTO pour une ligne de produit d'affaire (Imprimé 5)
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
     * DTO pour les totaux de produit (Imprimé 5)
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
     * DTO pour l'état cumulé par agent (Imprimé 6)
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
     * DTO pour un agent dans l'état cumulé (Imprimé 6)
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
     * DTO pour le tableau des amendes par services (Imprimé 7)
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
     * DTO pour une ligne du tableau des amendes par service (Imprimé 7)
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

    /**
     * DTO pour l'état de mandatement (Imprimés 2 et 8)
     */
    public static class EtatMandatementDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private String typeEtat;
        private List<MandatementDTO> mandatements;
        private BigDecimal totalProduitNet;
        private BigDecimal totalChefs;
        private BigDecimal totalSaisissants;
        private BigDecimal totalMutuelleNationale;
        private BigDecimal totalMasseCommune;
        private BigDecimal totalInteressement;
        private BigDecimal totalDG;
        private BigDecimal totalDD;

        public EtatMandatementDTO() {
            this.mandatements = new ArrayList<>();
            this.dateGeneration = LocalDate.now();
            this.totalProduitNet = BigDecimal.ZERO;
            this.totalChefs = BigDecimal.ZERO;
            this.totalSaisissants = BigDecimal.ZERO;
            this.totalMutuelleNationale = BigDecimal.ZERO;
            this.totalMasseCommune = BigDecimal.ZERO;
            this.totalInteressement = BigDecimal.ZERO;
            this.totalDG = BigDecimal.ZERO;
            this.totalDD = BigDecimal.ZERO;
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

        public String getTypeEtat() { return typeEtat; }
        public void setTypeEtat(String typeEtat) { this.typeEtat = typeEtat; }

        public List<MandatementDTO> getMandatements() { return mandatements; }
        public void setMandatements(List<MandatementDTO> mandatements) { this.mandatements = mandatements; }

        public BigDecimal getTotalProduitNet() { return totalProduitNet; }
        public void setTotalProduitNet(BigDecimal totalProduitNet) { this.totalProduitNet = totalProduitNet; }

        public BigDecimal getTotalChefs() { return totalChefs; }
        public void setTotalChefs(BigDecimal totalChefs) { this.totalChefs = totalChefs; }

        public BigDecimal getTotalSaisissants() { return totalSaisissants; }
        public void setTotalSaisissants(BigDecimal totalSaisissants) { this.totalSaisissants = totalSaisissants; }

        public BigDecimal getTotalMutuelleNationale() { return totalMutuelleNationale; }
        public void setTotalMutuelleNationale(BigDecimal totalMutuelleNationale) { this.totalMutuelleNationale = totalMutuelleNationale; }

        public BigDecimal getTotalMasseCommune() { return totalMasseCommune; }
        public void setTotalMasseCommune(BigDecimal totalMasseCommune) { this.totalMasseCommune = totalMasseCommune; }

        public BigDecimal getTotalInteressement() { return totalInteressement; }
        public void setTotalInteressement(BigDecimal totalInteressement) { this.totalInteressement = totalInteressement; }

        public BigDecimal getTotalDG() { return totalDG; }
        public void setTotalDG(BigDecimal totalDG) { this.totalDG = totalDG; }

        public BigDecimal getTotalDD() { return totalDD; }
        public void setTotalDD(BigDecimal totalDD) { this.totalDD = totalDD; }
    }

    /**
     * DTO pour une ligne de mandatement
     */
    public static class MandatementDTO {
        private String numeroEncaissement;
        private LocalDate dateEncaissement;
        private String numeroAffaire;
        private LocalDate dateAffaire;
        private BigDecimal produitNet;
        private BigDecimal partChefs;
        private BigDecimal partSaisissants;
        private BigDecimal partMutuelleNationale;
        private BigDecimal partMasseCommune;
        private BigDecimal partInteressement;
        private BigDecimal partDG;
        private BigDecimal partDD;

        public MandatementDTO() {}

        // Getters et setters
        public String getNumeroEncaissement() { return numeroEncaissement; }
        public void setNumeroEncaissement(String numeroEncaissement) { this.numeroEncaissement = numeroEncaissement; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public LocalDate getDateAffaire() { return dateAffaire; }
        public void setDateAffaire(LocalDate dateAffaire) { this.dateAffaire = dateAffaire; }

        public BigDecimal getProduitNet() { return produitNet; }
        public void setProduitNet(BigDecimal produitNet) { this.produitNet = produitNet; }

        public BigDecimal getPartChefs() { return partChefs; }
        public void setPartChefs(BigDecimal partChefs) { this.partChefs = partChefs; }

        public BigDecimal getPartSaisissants() { return partSaisissants; }
        public void setPartSaisissants(BigDecimal partSaisissants) { this.partSaisissants = partSaisissants; }

        public BigDecimal getPartMutuelleNationale() { return partMutuelleNationale; }
        public void setPartMutuelleNationale(BigDecimal partMutuelleNationale) { this.partMutuelleNationale = partMutuelleNationale; }

        public BigDecimal getPartMasseCommune() { return partMasseCommune; }
        public void setPartMasseCommune(BigDecimal partMasseCommune) { this.partMasseCommune = partMasseCommune; }

        public BigDecimal getPartInteressement() { return partInteressement; }
        public void setPartInteressement(BigDecimal partInteressement) { this.partInteressement = partInteressement; }

        public BigDecimal getPartDG() { return partDG; }
        public void setPartDG(BigDecimal partDG) { this.partDG = partDG; }

        public BigDecimal getPartDD() { return partDD; }
        public void setPartDD(BigDecimal partDD) { this.partDD = partDD; }
    }

    /**
     * DTO pour l'état par centre de répartition (Imprimé 3)
     */
    public static class EtatCentreRepartitionDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private List<CentreRepartitionDTO> centres;
        private BigDecimal totalRepartitionBase;
        private BigDecimal totalRepartitionIndicateurFictif;
        private BigDecimal totalPartCentre;

        public EtatCentreRepartitionDTO() {
            this.centres = new ArrayList<>();
            this.dateGeneration = LocalDate.now();
            this.totalRepartitionBase = BigDecimal.ZERO;
            this.totalRepartitionIndicateurFictif = BigDecimal.ZERO;
            this.totalPartCentre = BigDecimal.ZERO;
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

        public List<CentreRepartitionDTO> getCentres() { return centres; }
        public void setCentres(List<CentreRepartitionDTO> centres) { this.centres = centres; }

        public BigDecimal getTotalRepartitionBase() { return totalRepartitionBase; }
        public void setTotalRepartitionBase(BigDecimal totalRepartitionBase) { this.totalRepartitionBase = totalRepartitionBase; }

        public BigDecimal getTotalRepartitionIndicateurFictif() { return totalRepartitionIndicateurFictif; }
        public void setTotalRepartitionIndicateurFictif(BigDecimal totalRepartitionIndicateurFictif) { this.totalRepartitionIndicateurFictif = totalRepartitionIndicateurFictif; }

        public BigDecimal getTotalPartCentre() { return totalPartCentre; }
        public void setTotalPartCentre(BigDecimal totalPartCentre) { this.totalPartCentre = totalPartCentre; }
    }

    /**
     * DTO pour un centre de répartition
     */
    public static class CentreRepartitionDTO {
        private String nomCentre;
        private BigDecimal partRepartitionBase;
        private BigDecimal partRepartitionIndicateurFictif;
        private BigDecimal partTotaleCentre;

        public CentreRepartitionDTO() {}

        public CentreRepartitionDTO(String nomCentre) {
            this.nomCentre = nomCentre;
            this.partRepartitionBase = BigDecimal.ZERO;
            this.partRepartitionIndicateurFictif = BigDecimal.ZERO;
            this.partTotaleCentre = BigDecimal.ZERO;
        }

        // Getters et setters
        public String getNomCentre() { return nomCentre; }
        public void setNomCentre(String nomCentre) { this.nomCentre = nomCentre; }

        public BigDecimal getPartRepartitionBase() { return partRepartitionBase; }
        public void setPartRepartitionBase(BigDecimal partRepartitionBase) { this.partRepartitionBase = partRepartitionBase; }

        public BigDecimal getPartRepartitionIndicateurFictif() { return partRepartitionIndicateurFictif; }
        public void setPartRepartitionIndicateurFictif(BigDecimal partRepartitionIndicateurFictif) { this.partRepartitionIndicateurFictif = partRepartitionIndicateurFictif; }

        public BigDecimal getPartTotaleCentre() { return partTotaleCentre; }
        public void setPartTotaleCentre(BigDecimal partTotaleCentre) { this.partTotaleCentre = partTotaleCentre; }
    }

}