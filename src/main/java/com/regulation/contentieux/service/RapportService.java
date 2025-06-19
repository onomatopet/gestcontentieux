package com.regulation.contentieux.service;

import java.math.BigDecimal;
import com.regulation.contentieux.model.Contravention;
import com.regulation.contentieux.dao.ContraventionDAO;
import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.dao.EncaissementDAO;
import com.regulation.contentieux.dao.AgentDAO;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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

                affaireDTO.setMontantEncaisse(montantAffaire);

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
                        AffaireRepartitionCompleteDTO affaireDTO = new AffaireRepartitionCompleteDTO();

                        // Données de base
                        affaireDTO.setNumeroEncaissement(encaissement.getReferenceMandat());
                        affaireDTO.setDateEncaissement(encaissement.getDateEncaissement());
                        affaireDTO.setNumeroAffaire(affaire.getNumeroAffaire());
                        affaireDTO.setDateAffaire(affaire.getDateCreation());

                        // Direction départementale (indicatif pour l'instant)
                        affaireDTO.setDirectionDepartementale(determinerDirectionDepartementale(affaire));

                        // Calculs de répartition selon la hiérarchie
                        BigDecimal produitDisponible = BigDecimal.valueOf(encaissement.getMontant());
                        BigDecimal indicateur = CalculsRepartition.calculerIndicateur(produitDisponible);
                        BigDecimal produitNet = CalculsRepartition.calculerProduitNet(produitDisponible);
                        BigDecimal flcf = CalculsRepartition.calculerFLCF(produitNet);
                        BigDecimal tresor = CalculsRepartition.calculerTresor(produitNet);
                        BigDecimal produitNetAyantsDroits = CalculsRepartition.calculerProduitNetAyantsDroits(produitNet);

                        BigDecimal chefs = CalculsRepartition.calculerPartChefs(produitNetAyantsDroits);
                        BigDecimal saisissants = CalculsRepartition.calculerPartSaisissants(produitNetAyantsDroits);
                        BigDecimal mutuelleNationale = CalculsRepartition.calculerPartMutuelleNationale(produitNetAyantsDroits);
                        BigDecimal masseCommune = CalculsRepartition.calculerPartMasseCommune(produitNetAyantsDroits);
                        BigDecimal interessement = CalculsRepartition.calculerPartInteressement(produitNetAyantsDroits);

                        // Remplissage du DTO
                        affaireDTO.setProduitDisponible(produitDisponible);
                        affaireDTO.setIndicateur(indicateur);
                        affaireDTO.setProduitNet(produitNet);
                        affaireDTO.setFlcf(flcf);
                        affaireDTO.setTresor(tresor);
                        affaireDTO.setProduitNetAyantsDroits(produitNetAyantsDroits);
                        affaireDTO.setChefs(chefs);
                        affaireDTO.setSaisissants(saisissants);
                        affaireDTO.setMutuelleNationale(mutuelleNationale);
                        affaireDTO.setMasseCommune(masseCommune);
                        affaireDTO.setInteressement(interessement);

                        affairesDTO.add(affaireDTO);

                        // Accumulation des totaux
                        totaux.setTotalProduitDisponible(totaux.getTotalProduitDisponible().add(produitDisponible));
                        totaux.setTotalIndicateur(totaux.getTotalIndicateur().add(indicateur));
                        totaux.setTotalProduitNet(totaux.getTotalProduitNet().add(produitNet));
                        totaux.setTotalFlcf(totaux.getTotalFlcf().add(flcf));
                        totaux.setTotalTresor(totaux.getTotalTresor().add(tresor));
                        totaux.setTotalProduitNetAyantsDroits(totaux.getTotalProduitNetAyantsDroits().add(produitNetAyantsDroits));
                        totaux.setTotalChefs(totaux.getTotalChefs().add(chefs));
                        totaux.setTotalSaisissants(totaux.getTotalSaisissants().add(saisissants));
                        totaux.setTotalMutuelleNationale(totaux.getTotalMutuelleNationale().add(mutuelleNationale));
                        totaux.setTotalMasseCommune(totaux.getTotalMasseCommune().add(masseCommune));
                        totaux.setTotalInteressement(totaux.getTotalInteressement().add(interessement));
                    }
                }
            }

            // Tri par date d'encaissement
            affairesDTO.sort((a1, a2) -> a1.getDateEncaissement().compareTo(a2.getDateEncaissement()));

            etat.setAffaires(affairesDTO);
            etat.setTotaux(totaux);
            etat.setTotalAffaires(affaires.size());

            logger.info("État de répartition des affaires généré: {} lignes, {} FCFA produit disponible",
                    affairesDTO.size(), CurrencyFormatter.format(totaux.getTotalProduitDisponible()));

            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état de répartition des affaires", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }

    // ==================== IMPRIMÉ 2: ETAT PAR SERIES DE MANDATEMENT ====================

    /**
     * Génère l'état par séries de mandatement (Imprimé 2)
     */
    public EtatMandatementDTO genererEtatMandatement(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état par séries de mandatement du {} au {}", dateDebut, dateFin);

            EtatMandatementDTO etat = new EtatMandatementDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));
            etat.setTypeEtat("MANDATEMENT");

            // Récupération des affaires avec encaissements
            List<Affaire> affaires = affaireDAO.findAffairesWithEncaissementsByPeriod(dateDebut, dateFin);

            List<MandatementDTO> mandatementsDTO = new ArrayList<>();
            BigDecimal totalProduitNet = BigDecimal.ZERO;
            BigDecimal totalChefs = BigDecimal.ZERO;
            BigDecimal totalSaisissants = BigDecimal.ZERO;
            BigDecimal totalMutuelleNationale = BigDecimal.ZERO;
            BigDecimal totalMasseCommune = BigDecimal.ZERO;
            BigDecimal totalInteressement = BigDecimal.ZERO;

            // Traitement de chaque affaire
            for (Affaire affaire : affaires) {
                List<Encaissement> encaissements = encaissementDAO.findByAffaireAndPeriod(
                        affaire.getId(), dateDebut, dateFin);

                for (Encaissement encaissement : encaissements) {
                    if (encaissement.getStatut() == StatutEncaissement.VALIDE) {
                        MandatementDTO mandatementDTO = new MandatementDTO();

                        // Données de base
                        mandatementDTO.setNumeroEncaissement(encaissement.getReferenceMandat());
                        mandatementDTO.setDateEncaissement(encaissement.getDateEncaissement());
                        mandatementDTO.setNumeroAffaire(affaire.getNumeroAffaire());
                        mandatementDTO.setDateAffaire(affaire.getDateCreation());

                        // Calculs de répartition
                        BigDecimal montantEncaissement = BigDecimal.valueOf(encaissement.getMontant());
                        BigDecimal produitNet = CalculsRepartition.calculerProduitNet(montantEncaissement);
                        BigDecimal produitNetAyantsDroits = CalculsRepartition.calculerProduitNetAyantsDroits(produitNet);

                        mandatementDTO.setProduitNet(produitNet);
                        mandatementDTO.setPartChefs(CalculsRepartition.calculerPartChefs(produitNetAyantsDroits));
                        mandatementDTO.setPartSaisissants(CalculsRepartition.calculerPartSaisissants(produitNetAyantsDroits));
                        mandatementDTO.setPartMutuelleNationale(CalculsRepartition.calculerPartMutuelleNationale(produitNetAyantsDroits));
                        mandatementDTO.setPartMasseCommune(CalculsRepartition.calculerPartMasseCommune(produitNetAyantsDroits));
                        mandatementDTO.setPartInteressement(CalculsRepartition.calculerPartInteressement(produitNetAyantsDroits));

                        // Observations
                        if (produitNet.compareTo(new BigDecimal("100000")) > 0) {
                            mandatementDTO.setObservations("Montant important");
                        }

                        mandatementsDTO.add(mandatementDTO);

                        // Accumulation des totaux
                        totalProduitNet = totalProduitNet.add(produitNet);
                        totalChefs = totalChefs.add(mandatementDTO.getPartChefs());
                        totalSaisissants = totalSaisissants.add(mandatementDTO.getPartSaisissants());
                        totalMutuelleNationale = totalMutuelleNationale.add(mandatementDTO.getPartMutuelleNationale());
                        totalMasseCommune = totalMasseCommune.add(mandatementDTO.getPartMasseCommune());
                        totalInteressement = totalInteressement.add(mandatementDTO.getPartInteressement());
                    }
                }
            }

            // Tri par date d'encaissement
            mandatementsDTO.sort((m1, m2) -> m1.getDateEncaissement().compareTo(m2.getDateEncaissement()));

            etat.setMandatements(mandatementsDTO);
            etat.setTotalProduitNet(totalProduitNet);
            etat.setTotalChefs(totalChefs);
            etat.setTotalSaisissants(totalSaisissants);
            etat.setTotalMutuelleNationale(totalMutuelleNationale);
            etat.setTotalMasseCommune(totalMasseCommune);
            etat.setTotalInteressement(totalInteressement);

            logger.info("État de mandatement généré: {} mandatements, {} FCFA produit net",
                    mandatementsDTO.size(), CurrencyFormatter.format(totalProduitNet));

            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état de mandatement", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }

    // ==================== IMPRIMÉ 3: ETAT CUMULE PAR CENTRE DE REPARTITION ====================

    /**
     * Génère l'état cumulé par centre de répartition (Imprimé 3)
     * Méthode placeholder - à implémenter plus tard
     */
    public EtatCentreRepartitionDTO genererEtatCentreRepartition(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état cumulé par centre de répartition du {} au {}", dateDebut, dateFin);

            EtatCentreRepartitionDTO etat = new EtatCentreRepartitionDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

            // TODO: Implémenter la logique des centres de répartition
            // Cette méthode est gardée pour la fin comme convenu

            List<CentreRepartitionDTO> centres = new ArrayList<>();
            // Logique à implémenter...

            etat.setCentres(centres);

            logger.info("État cumulé par centre de répartition généré (placeholder)");
            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état par centre de répartition", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }

    // ==================== IMPRIMÉ 4: ETAT DE REPARTITION DES INDICATEURS REELS ====================

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

                ServiceIndicateurDTO serviceDTO = new ServiceIndicateurDTO(nomService);
                BigDecimal totalServiceMontant = BigDecimal.ZERO;
                BigDecimal totalServiceIndicateur = BigDecimal.ZERO;
                int totalServiceAffaires = 0;

                // Traitement des sections
                List<SectionIndicateurDTO> sectionsDTO = new ArrayList<>();

                for (Map.Entry<String, List<Affaire>> sectionEntry : sectionsMap.entrySet()) {
                    String nomSection = sectionEntry.getKey();
                    List<Affaire> affairesSection = sectionEntry.getValue();

                    SectionIndicateurDTO sectionDTO = new SectionIndicateurDTO(nomSection);
                    BigDecimal totalSectionMontant = BigDecimal.ZERO;
                    BigDecimal totalSectionIndicateur = BigDecimal.ZERO;

                    // Traitement des affaires de la section
                    List<AffaireIndicateurDTO> affairesDTO = new ArrayList<>();

                    for (Affaire affaire : affairesSection) {
                        List<Encaissement> encaissements = encaissementDAO.findByAffaireAndPeriod(
                                affaire.getId(), dateDebut, dateFin);

                        for (Encaissement encaissement : encaissements) {
                            if (encaissement.getStatut() == StatutEncaissement.VALIDE) {
                                AffaireIndicateurDTO affaireDTO = new AffaireIndicateurDTO();

                                // Remplissage des données
                                affaireDTO.setNumeroEncaissement(encaissement.getReferenceMandat());
                                affaireDTO.setDateEncaissement(encaissement.getDateEncaissement());
                                affaireDTO.setNumeroAffaire(affaire.getNumeroAffaire());
                                affaireDTO.setDateAffaire(affaire.getDateCreation());

                                if (affaire.getContrevenant() != null) {
                                    affaireDTO.setNomContrevenant(affaire.getContrevenant().getNomComplet());
                                }

                                if (affaire.getTypeContravention() != null) {
                                    Contravention contravention = affaire.getTypeContravention();
                                    String libelle = (contravention != null) ? contravention.getLibelle() : "INCONNU";
                                }

                                // Calculs
                                BigDecimal montantEncaissement = BigDecimal.valueOf(encaissement.getMontant());
                                BigDecimal partIndicateur = CalculsRepartition.calculerIndicateur(montantEncaissement);

                                affaireDTO.setMontantEncaissement(montantEncaissement);
                                affaireDTO.setPartIndicateur(partIndicateur);

                                // Observations conditionnelles
                                if (partIndicateur.compareTo(new BigDecimal("50000")) > 0) {
                                    affaireDTO.setObservations("Montant élevé");
                                } else if (partIndicateur.compareTo(new BigDecimal("1000")) < 0) {
                                    affaireDTO.setObservations("Montant faible");
                                }

                                affairesDTO.add(affaireDTO);

                                // Accumulation des totaux
                                totalSectionMontant = totalSectionMontant.add(montantEncaissement);
                                totalSectionIndicateur = totalSectionIndicateur.add(partIndicateur);
                            }
                        }
                    }

                    sectionDTO.setAffaires(affairesDTO);
                    sectionDTO.setTotalMontantSection(totalSectionMontant);
                    sectionDTO.setTotalPartIndicateurSection(totalSectionIndicateur);
                    sectionsDTO.add(sectionDTO);

                    // Accumulation service
                    totalServiceMontant = totalServiceMontant.add(totalSectionMontant);
                    totalServiceIndicateur = totalServiceIndicateur.add(totalSectionIndicateur);
                    totalServiceAffaires += affairesSection.size();
                }

                serviceDTO.setSections(sectionsDTO);
                serviceDTO.setTotalMontantService(totalServiceMontant);
                serviceDTO.setTotalPartIndicateurService(totalServiceIndicateur);
                serviceDTO.setTotalAffairesService(totalServiceAffaires);
                servicesDTO.add(serviceDTO);

                // Accumulation générale
                totalGeneralMontant = totalGeneralMontant.add(totalServiceMontant);
                totalGeneralIndicateur = totalGeneralIndicateur.add(totalServiceIndicateur);
                totalGeneralAffaires += totalServiceAffaires;
            }

            // Tri par montant décroissant
            servicesDTO.sort((s1, s2) -> s2.getTotalMontantService().compareTo(s1.getTotalMontantService()));

            etat.setServices(servicesDTO);
            etat.setTotalMontantEncaissement(totalGeneralMontant);
            etat.setTotalPartIndicateur(totalGeneralIndicateur);
            etat.setTotalAffaires(totalGeneralAffaires);

            logger.info("État des indicateurs réels généré: {} services, {} FCFA indicateur",
                    servicesDTO.size(), CurrencyFormatter.format(totalGeneralIndicateur));

            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état des indicateurs réels", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }

    // ==================== IMPRIMÉ 5: ETAT DE REPARTITION DU PRODUIT DES AFFAIRES CONTENTIEUSES ====================

    /**
     * Génère l'état de répartition du produit des affaires contentieuses (Imprimé 5)
     */
    public EtatRepartitionProduitDTO genererEtatRepartitionProduit(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état de répartition du produit des affaires du {} au {}", dateDebut, dateFin);

            EtatRepartitionProduitDTO etat = new EtatRepartitionProduitDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

            // Récupération des affaires avec encaissements
            List<Affaire> affaires = affaireDAO.findAffairesWithEncaissementsByPeriod(dateDebut, dateFin);

            List<ProduitAffaireDTO> affairesDTO = new ArrayList<>();
            TotauxProduitDTO totaux = new TotauxProduitDTO();

            // Traitement de chaque affaire
            for (Affaire affaire : affaires) {
                List<Encaissement> encaissements = encaissementDAO.findByAffaireAndPeriod(
                        affaire.getId(), dateDebut, dateFin);

                for (Encaissement encaissement : encaissements) {
                    if (encaissement.getStatut() == StatutEncaissement.VALIDE) {
                        ProduitAffaireDTO affaireDTO = new ProduitAffaireDTO();

                        // Données de base
                        affaireDTO.setNumeroEncaissement(encaissement.getReferenceMandat());
                        affaireDTO.setDateEncaissement(encaissement.getDateEncaissement());
                        affaireDTO.setNumeroAffaire(affaire.getNumeroAffaire());
                        affaireDTO.setDateAffaire(affaire.getDateCreation());

                        if (affaire.getContrevenant() != null) {
                            affaireDTO.setNomContrevenant(affaire.getContrevenant().getNomComplet());
                        }

                        if (affaire.getTypeContravention() != null) {
                            Contravention contravention = affaire.getTypeContravention();
                            String libelle = (contravention != null) ? contravention.getLibelle() : "INCONNU";
                        }

                        // Calculs
                        BigDecimal produitDisponible = BigDecimal.valueOf(encaissement.getMontant());
                        BigDecimal partIndicateur = CalculsRepartition.calculerIndicateur(produitDisponible);
                        BigDecimal produitNet = CalculsRepartition.calculerProduitNet(produitDisponible);
                        BigDecimal flcf = CalculsRepartition.calculerFLCF(produitNet);
                        BigDecimal tresor = CalculsRepartition.calculerTresor(produitNet);
                        BigDecimal produitNetAyantsDroits = CalculsRepartition.calculerProduitNetAyantsDroits(produitNet);

                        // Remplissage du DTO
                        affaireDTO.setProduitDisponible(produitDisponible);
                        affaireDTO.setPartIndicateur(partIndicateur);
                        affaireDTO.setPartDirectionContentieux(BigDecimal.ZERO); // À définir selon logique métier
                        affaireDTO.setPartIndicateur2(partIndicateur); // Duplication pour l'affichage
                        affaireDTO.setFlcf(flcf);
                        affaireDTO.setMontantTresor(tresor);
                        affaireDTO.setMontantGlobalAyantsDroits(produitNetAyantsDroits);

                        affairesDTO.add(affaireDTO);

                        // Accumulation des totaux
                        totaux.setTotalProduitDisponible(totaux.getTotalProduitDisponible().add(produitDisponible));
                        totaux.setTotalPartIndicateur(totaux.getTotalPartIndicateur().add(partIndicateur));
                        totaux.setTotalPartDirectionContentieux(totaux.getTotalPartDirectionContentieux().add(affaireDTO.getPartDirectionContentieux()));
                        totaux.setTotalPartIndicateur2(totaux.getTotalPartIndicateur2().add(partIndicateur));
                        totaux.setTotalFlcf(totaux.getTotalFlcf().add(flcf));
                        totaux.setTotalMontantTresor(totaux.getTotalMontantTresor().add(tresor));
                        totaux.setTotalMontantGlobalAyantsDroits(totaux.getTotalMontantGlobalAyantsDroits().add(produitNetAyantsDroits));
                    }
                }
            }

            // Tri par date d'encaissement
            affairesDTO.sort((a1, a2) -> a1.getDateEncaissement().compareTo(a2.getDateEncaissement()));

            etat.setAffaires(affairesDTO);
            etat.setTotaux(totaux);
            etat.setTotalAffaires(affaires.size());

            logger.info("État de répartition du produit généré: {} lignes, {} FCFA produit disponible",
                    affairesDTO.size(), CurrencyFormatter.format(totaux.getTotalProduitDisponible()));

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

            // Récupération des affaires avec encaissements
            List<Affaire> affaires = affaireDAO.findAffairesWithEncaissementsByPeriod(dateDebut, dateFin);

            // Récupération des agents DG et DD
            List<Agent> agentsDG = recupererAgentsDG();
            List<Agent> agentsDD = recupererAgentsDD();

            // Map pour accumuler les parts par agent
            Map<String, AgentCumuleDTO> agentsMap = new HashMap<>();

            // Traitement de chaque affaire
            for (Affaire affaire : affaires) {
                // Récupération des encaissements valides
                List<Encaissement> encaissements = encaissementDAO.findByAffaireAndPeriod(
                        affaire.getId(), dateDebut, dateFin);

                BigDecimal totalMontant = encaissements.stream()
                        .map(encaissement -> BigDecimal.valueOf(encaissement.getMontant()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (montantTotalAffaire.compareTo(BigDecimal.ZERO) > 0) {
                    // Calculs de répartition pour cette affaire
                    BigDecimal produitNet = CalculsRepartition.calculerProduitNet(montantTotalAffaire);
                    BigDecimal produitNetAyantsDroits = CalculsRepartition.calculerProduitNetAyantsDroits(produitNet);
                    BigDecimal partTotaleChefs = CalculsRepartition.calculerPartChefs(produitNetAyantsDroits);
                    BigDecimal partTotaleSaisissants = CalculsRepartition.calculerPartSaisissants(produitNetAyantsDroits);

                    // Récupération des acteurs de l'affaire
                    List<Agent> chefsAffaire = recupererChefsAffaire(affaire);
                    List<Agent> saisissantsAffaire = recupererSaisissantsAffaire(affaire);

                    // Ajout automatique des DG et DD comme chefs
                    List<Agent> tousLesChefs = new ArrayList<>(chefsAffaire);
                    tousLesChefs.addAll(agentsDG);
                    tousLesChefs.addAll(agentsDD);

                    // Calcul de la part individuelle par chef
                    BigDecimal partParChef = BigDecimal.ZERO;
                    if (!tousLesChefs.isEmpty()) {
                        partParChef = partTotaleChefs.divide(
                                BigDecimal.valueOf(tousLesChefs.size()), 2, RoundingMode.HALF_UP);
                    }

                    // Calcul de la part individuelle par saisissant
                    BigDecimal partParSaisissant = BigDecimal.ZERO;
                    if (!saisissantsAffaire.isEmpty()) {
                        partParSaisissant = partTotaleSaisissants.divide(
                                BigDecimal.valueOf(saisissantsAffaire.size()), 2, RoundingMode.HALF_UP);
                    }

                    // Attribution des parts aux chefs (incluant DG/DD)
                    for (Agent chef : tousLesChefs) {
                        String keyAgent = chef.getCodeAgent();
                        AgentCumuleDTO agentDTO = agentsMap.computeIfAbsent(keyAgent,
                                k -> new AgentCumuleDTO(chef.getPrenom() + " " + chef.getNom(), chef.getCodeAgent()));

                        // Déterminer le type de rôle
                        if (agentsDG.contains(chef)) {
                            agentDTO.setPartDG(agentDTO.getPartDG().add(partParChef));
                        } else if (agentsDD.contains(chef)) {
                            agentDTO.setPartDD(agentDTO.getPartDD().add(partParChef));
                        } else {
                            agentDTO.setPartChef(agentDTO.getPartChef().add(partParChef));
                            agentDTO.setNombreAffairesChef(agentDTO.getNombreAffairesChef() + 1);
                        }
                    }

                    // Attribution des parts aux saisissants
                    for (Agent saisissant : saisissantsAffaire) {
                        String keyAgent = saisissant.getCodeAgent();
                        AgentCumuleDTO agentDTO = agentsMap.computeIfAbsent(keyAgent,
                                k -> new AgentCumuleDTO(saisissant.getPrenom() + " " + saisissant.getNom(), saisissant.getCodeAgent()));

                        agentDTO.setPartSaisissant(agentDTO.getPartSaisissant().add(partParSaisissant));
                        agentDTO.setNombreAffairesSaisissant(agentDTO.getNombreAffairesSaisissant() + 1);
                    }
                }
            }

            // Finalisation des calculs et tri
            List<AgentCumuleDTO> agentsDTO = new ArrayList<>();
            BigDecimal totalGeneral = BigDecimal.ZERO;

            for (AgentCumuleDTO agentDTO : agentsMap.values()) {
                agentDTO.calculerTotal();
                agentsDTO.add(agentDTO);
                totalGeneral = totalGeneral.add(agentDTO.getPartTotaleAgent());
            }

            // Tri par montant total décroissant
            agentsDTO.sort((a1, a2) -> a2.getPartTotaleAgent().compareTo(a1.getPartTotaleAgent()));

            etat.setAgents(agentsDTO);
            etat.setTotalGeneral(totalGeneral);
            etat.setTotalAffairesTraitees(affaires.size());

            logger.info("État cumulé par agent généré: {} agents, {} FCFA total",
                    agentsDTO.size(), CurrencyFormatter.format(totalGeneral));

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
                // Récupération du service via l'agent chef de dossier
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

                // Calcul du montant total encaissé pour ce service
                BigDecimal montantService = BigDecimal.ZERO;
                for (Affaire affaire : affairesService) {
                    List<Encaissement> encaissements = encaissementDAO.findByAffaireAndPeriod(
                            affaire.getId(), dateDebut, dateFin);

                    BigDecimal totalMontant = encaissements.stream()
                            .map(encaissement -> BigDecimal.valueOf(encaissement.getMontant()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);


                    montantService = montantService.add(montantAffaire);
                }

                ServiceAmendeDTO serviceDTO = new ServiceAmendeDTO(
                        nomService,
                        affairesService.size(),
                        montantService
                );

                // Observations conditionnelles
                if (affairesService.size() > 10) {
                    serviceDTO.setObservations("Service très actif");
                } else if (montantService.compareTo(BigDecimal.ZERO) == 0) {
                    serviceDTO.setObservations("Aucun encaissement");
                }

                servicesDTO.add(serviceDTO);
                totalGeneral = totalGeneral.add(montantService);
                totalAffairesGeneral += affairesService.size();
            }

            // Tri par montant décroissant
            servicesDTO.sort((s1, s2) -> s2.getMontantTotal().compareTo(s1.getMontantTotal()));

            tableau.setServices(servicesDTO);
            tableau.setTotalMontant(totalGeneral);
            tableau.setTotalAffaires(totalAffairesGeneral);

            logger.info("Tableau des amendes par services généré: {} services, {} FCFA",
                    servicesDTO.size(), CurrencyFormatter.format(totalGeneral));

            return tableau;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération du tableau des amendes par services", e);
            throw new RuntimeException("Impossible de générer le tableau: " + e.getMessage(), e);
        }
    }

    // ==================== IMPRIMÉ 8: ETAT PAR SERIES DE MANDATEMENTS AGENTS ====================

    /**
     * Génère l'état par séries de mandatement avec détail agents (Imprimé 8)
     */
    public EtatMandatementDTO genererEtatMandatementAgents(LocalDate dateDebut, LocalDate dateFin) {
        EtatMandatementDTO etat = genererEtatMandatement(dateDebut, dateFin);
        etat.setTypeEtat("MANDATEMENT_AGENT");

        // Ajout des colonnes DG et DD pour l'imprimé 8
        // Les calculs sont similaires mais avec distinction DG/DD

        return etat;
    }

    // ==================== MÉTHODES UTILITAIRES POUR LES 8 IMPRIMÉS ====================

    /**
     * Génère un imprimé selon son type
     */
    public Object genererImprimeParType(String typeImprime, LocalDate dateDebut, LocalDate dateFin) {
        switch (typeImprime) {
            case "ETAT_REPARTITION_AFFAIRES":
                return genererEtatRepartitionAffaires(dateDebut, dateFin);
            case "ETAT_MANDATEMENT":
                return genererEtatMandatement(dateDebut, dateFin);
            case "ETAT_CENTRE_REPARTITION":
                return genererEtatCentreRepartition(dateDebut, dateFin);
            case "ETAT_INDICATEURS_REELS":
                return genererEtatRepartitionIndicateursReels(dateDebut, dateFin);
            case "ETAT_REPARTITION_PRODUIT":
                return genererEtatRepartitionProduit(dateDebut, dateFin);
            case "ETAT_CUMULE_AGENT":
                return genererEtatCumuleParAgent(dateDebut, dateFin);
            case "TABLEAU_AMENDES_SERVICES":
                return genererTableauAmendesParServices(dateDebut, dateFin);
            case "ETAT_MANDATEMENT_AGENTS":
                return genererEtatMandatementAgents(dateDebut, dateFin);
            default:
                throw new IllegalArgumentException("Type d'imprimé non reconnu: " + typeImprime);
        }
    }

    /**
     * Retourne la liste des types d'imprimés disponibles
     */
    public static List<String> getTypesImprimesDisponibles() {
        return Arrays.asList(
                "ETAT_REPARTITION_AFFAIRES",           // Imprimé 1
                "ETAT_MANDATEMENT",                    // Imprimé 2
                "ETAT_CENTRE_REPARTITION",             // Imprimé 3
                "ETAT_INDICATEURS_REELS",              // Imprimé 4
                "ETAT_REPARTITION_PRODUIT",            // Imprimé 5
                "ETAT_CUMULE_AGENT",                   // Imprimé 6
                "TABLEAU_AMENDES_SERVICES",            // Imprimé 7
                "ETAT_MANDATEMENT_AGENTS"              // Imprimé 8
        );
    }

    /**
     * Retourne le libellé d'un type d'imprimé
     */
    public static String getLibelleTypeImprime(String typeImprime) {
        switch (typeImprime) {
            case "ETAT_REPARTITION_AFFAIRES":
                return "État de Répartition des Affaires Contentieuses";
            case "ETAT_MANDATEMENT":
                return "État par Séries de Mandatement";
            case "ETAT_CENTRE_REPARTITION":
                return "État Cumulé par Centre de Répartition";
            case "ETAT_INDICATEURS_REELS":
                return "État de Répartition des Part des Indicateurs Réels";
            case "ETAT_REPARTITION_PRODUIT":
                return "État de Répartition du Produit des Affaires Contentieuses";
            case "ETAT_CUMULE_AGENT":
                return "État Cumulé par Agent";
            case "TABLEAU_AMENDES_SERVICES":
                return "Tableau des Amendes par Services";
            case "ETAT_MANDATEMENT_AGENTS":
                return "État par Séries de Mandatements (Agents)";
            default:
                return "Type d'imprimé inconnu";
        }
    }

    /**
     * Valide qu'un imprimé peut être généré
     */
    public boolean validerParametresImprime(String typeImprime, LocalDate dateDebut, LocalDate dateFin) {
        // Validation générale
        if (!validerParametresRapport(dateDebut, dateFin)) {
            return false;
        }

        // Validations spécifiques par type
        switch (typeImprime) {
            case "ETAT_CENTRE_REPARTITION":
                // Ce type nécessite des données spéciales
                logger.warn("L'imprimé {} nécessite une configuration avancée", typeImprime);
                return false; // Temporairement désactivé
            default:
                return true;
        }
    }

    /**
     * Calcule les statistiques générales d'un ensemble d'imprimés
     */
    public Map<String, Object> calculerStatistiquesImprimes(LocalDate dateDebut, LocalDate dateFin) {
        Map<String, Object> stats = new HashMap<>();

        try {
            // Génération de quelques imprimés clés pour les statistiques
            TableauAmendesParServicesDTO tableau = genererTableauAmendesParServices(dateDebut, dateFin);
            EtatCumuleParAgentDTO etatAgents = genererEtatCumuleParAgent(dateDebut, dateFin);

            stats.put("nombreServices", tableau.getServices().size());
            stats.put("totalMontantServices", tableau.getTotalMontant());
            stats.put("nombreAgents", etatAgents.getAgents().size());
            stats.put("totalMontantAgents", etatAgents.getTotalGeneral());
            stats.put("affairesTraitees", tableau.getTotalAffaires());

        } catch (Exception e) {
            logger.warn("Erreur lors du calcul des statistiques d'imprimés", e);
            stats.put("erreur", "Impossible de calculer les statistiques");
        }

        return stats;
    }

    /**
     * Calcule les statistiques avancées pour un rapport
     */
    public Map<String, Object> calculerStatistiquesAvancees(RapportRepartitionDTO rapport) {
        Map<String, Object> stats = new HashMap<>();

        // Montant moyen par affaire
        if (rapport.getNombreAffaires() > 0) {
            BigDecimal moyenneParAffaire = rapport.getTotalEncaisse()
                    .divide(BigDecimal.valueOf(rapport.getNombreAffaires()), 2, RoundingMode.HALF_UP);
            stats.put("moyenneParAffaire", moyenneParAffaire);
        }

        // Taux de recouvrement moyen
        BigDecimal totalAmende = rapport.getAffaires().stream()
                .map(AffaireRepartitionDTO::getMontantAmende)
                .map(encaissement -> BigDecimal.valueOf(encaissement.getMontant()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAmende.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal tauxRecouvrement = rapport.getTotalEncaisse()
                    .divide(totalAmende, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            stats.put("tauxRecouvrement", tauxRecouvrement);
        }

        // Bureau le plus performant
        rapport.getRepartitionParBureau().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(entry -> {
                    stats.put("meilleurBureau", entry.getKey());
                    stats.put("montantMeilleurBureau", entry.getValue());
                });

        return stats;
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

        if (dateDebut.isAfter(LocalDate.now())) {
            return false;
        }

        // Limite à 5 ans en arrière
        if (dateDebut.isBefore(LocalDate.now().minusYears(5))) {
            return false;
        }

        return true;
    }

    // ==================== MÉTHODES UTILITAIRES PRIVÉES ====================

    /**
     * Crée un DTO d'affaire pour le rapport
     */
    private AffaireRepartitionDTO createAffaireDTO(Affaire affaire) {
        AffaireRepartitionDTO dto = new AffaireRepartitionDTO();

        dto.setNumeroAffaire(affaire.getNumeroAffaire());
        dto.setDateCreation(affaire.getDateCreation());
        dto.setMontantAmende(affaire.getMontantAmende());
        dto.setStatut(affaire.getStatut().getLibelle());

        if (affaire.getContrevenant() != null) {
            dto.setContrevenantNom(affaire.getContrevenant().getNomComplet());
        }

        if (affaire.getTypeContravention() != null) {
            Contravention contravention = affaire.getTypeContravention();
            String libelle = (contravention != null) ? contravention.getLibelle() : "INCONNU";
        }

        // Récupération du chef de dossier et bureau via les relations
        try {
            // Ici, il faudrait récupérer via AffaireActeurDAO les agents liés à l'affaire
            // Pour l'instant, on utilise des valeurs par défaut
            dto.setChefDossier("À définir");
            dto.setBureau("Bureau Central");
        } catch (Exception e) {
            logger.warn("Impossible de récupérer les détails de l'affaire {}", affaire.getNumeroAffaire());
            dto.setChefDossier("Non défini");
            dto.setBureau("Non défini");
        }

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
     * Détermine la direction départementale (indicatif pour l'instant)
     */
    private String determinerDirectionDepartementale(Affaire affaire) {
        // TODO: Logique à implémenter plus tard
        return "DD Centrale";
    }

    /**
     * Détermine le service d'une affaire via l'agent chef de dossier
     */
    private String determinerServiceAffaire(Affaire affaire) {
        try {
            // TODO: Récupérer via AffaireActeurDAO le chef de dossier de l'affaire
            // puis récupérer le service de cet agent
            // Pour l'instant, retour d'une valeur par défaut
            return "Service Central"; // À remplacer par la logique réelle

        } catch (Exception e) {
            logger.warn("Impossible de déterminer le service pour l'affaire {}", affaire.getNumeroAffaire());
            return "Service Non Défini";
        }
    }

    /**
     * Détermine la section d'une affaire via l'agent chef de dossier
     */
    private String determinerSectionAffaire(Affaire affaire) {
        try {
            // TODO: Récupérer via AffaireActeurDAO le chef de dossier de l'affaire
            // puis récupérer la section de cet agent
            // Pour l'instant, retour d'une valeur par défaut
            return "Section A"; // À remplacer par la logique réelle

        } catch (Exception e) {
            logger.warn("Impossible de déterminer la section pour l'affaire {}", affaire.getNumeroAffaire());
            return "Section Non Définie";
        }
    }

    /**
     * Récupère les agents ayant le rôle DG
     */
    private List<Agent> recupererAgentsDG() {
        try {
            // TODO: Récupérer via RoleSpecialDAO les agents avec rôle DG
            // Pour l'instant, retour d'une liste vide
            return new ArrayList<>();
        } catch (Exception e) {
            logger.warn("Impossible de récupérer les agents DG");
            return new ArrayList<>();
        }
    }

    /**
     * Récupère les agents ayant le rôle DD
     */
    private List<Agent> recupererAgentsDD() {
        try {
            // TODO: Récupérer via RoleSpecialDAO les agents avec rôle DD
            // Pour l'instant, retour d'une liste vide
            return new ArrayList<>();
        } catch (Exception e) {
            logger.warn("Impossible de récupérer les agents DD");
            return new ArrayList<>();
        }
    }

    /**
     * Récupère les chefs d'une affaire
     */
    private List<Agent> recupererChefsAffaire(Affaire affaire) {
        try {
            // TODO: Récupérer via AffaireActeurDAO les agents avec rôle CHEF sur cette affaire
            // Pour l'instant, retour d'une liste vide
            return new ArrayList<>();
        } catch (Exception e) {
            logger.warn("Impossible de récupérer les chefs pour l'affaire {}", affaire.getNumeroAffaire());
            return new ArrayList<>();
        }
    }

    /**
     * Récupère les saisissants d'une affaire
     */
    private List<Agent> recupererSaisissantsAffaire(Affaire affaire) {
        try {
            // TODO: Récupérer via AffaireActeurDAO les agents avec rôle SAISISSANT sur cette affaire
            // Pour l'instant, retour d'une liste vide
            return new ArrayList<>();
        } catch (Exception e) {
            logger.warn("Impossible de récupérer les saisissants pour l'affaire {}", affaire.getNumeroAffaire());
            return new ArrayList<>();
        }
    }

    // ==================== CLASSE UTILITAIRE CALCULS REPARTITION ====================

    /**
     * Calculs de base selon les règles de rétrocession
     */
    public static class CalculsRepartition {

        public static BigDecimal calculerIndicateur(BigDecimal produitDisponible) {
            // Indicateur = 10% du produit disponible
            return produitDisponible.multiply(new BigDecimal("0.10"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        public static BigDecimal calculerProduitNet(BigDecimal produitDisponible) {
            // Produit net = Produit disponible - Indicateur
            BigDecimal indicateur = calculerIndicateur(produitDisponible);
            return produitDisponible.subtract(indicateur)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        public static BigDecimal calculerFLCF(BigDecimal produitNet) {
            // FLCF = 10% du produit net
            return produitNet.multiply(new BigDecimal("0.10"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        public static BigDecimal calculerTresor(BigDecimal produitNet) {
            // Trésor = 15% du produit net
            return produitNet.multiply(new BigDecimal("0.15"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        public static BigDecimal calculerProduitNetAyantsDroits(BigDecimal produitNet) {
            // Produit net ayants droits = Produit net - (FLCF + Trésor)
            BigDecimal flcf = calculerFLCF(produitNet);
            BigDecimal tresor = calculerTresor(produitNet);
            return produitNet.subtract(flcf.add(tresor))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        public static BigDecimal calculerPartChefs(BigDecimal produitNetAyantsDroits) {
            // Chefs = 15% du produit net ayants droits
            return produitNetAyantsDroits.multiply(new BigDecimal("0.15"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        public static BigDecimal calculerPartSaisissants(BigDecimal produitNetAyantsDroits) {
            // Saisissants = 35% du produit net ayants droits
            return produitNetAyantsDroits.multiply(new BigDecimal("0.35"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        public static BigDecimal calculerPartMutuelleNationale(BigDecimal produitNetAyantsDroits) {
            // Mutuelle nationale = 5% du produit net ayants droits
            return produitNetAyantsDroits.multiply(new BigDecimal("0.05"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        public static BigDecimal calculerPartMasseCommune(BigDecimal produitNetAyantsDroits) {
            // Masse commune = 30% du produit net ayants droits
            return produitNetAyantsDroits.multiply(new BigDecimal("0.30"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        public static BigDecimal calculerPartInteressement(BigDecimal produitNetAyantsDroits) {
            // Intéressement = 15% du produit net ayants droits
            return produitNetAyantsDroits.multiply(new BigDecimal("0.15"))
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }

    // ==================== DTOs POUR RAPPORT PRINCIPAL ====================

    /**
     * DTO pour les données du rapport de rétrocession
     */
    public static class RapportRepartitionDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;

        // Totaux généraux
        private BigDecimal totalEncaisse;
        private BigDecimal totalEtat;
        private BigDecimal totalCollectivite;
        private int nombreAffaires;
        private int nombreEncaissements;

        // Détails par affaire
        private List<AffaireRepartitionDTO> affaires;

        // Statistiques
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

    // ==================== DTOs POUR IMPRIMÉ 1: ETAT DE REPARTITION DES AFFAIRES CONTENTIEUSES ====================

    /**
     * DTO pour l'imprimé 1: "ETAT DE REPARTITION DES AFFAIRES CONTENTIEUSE"
     * Le plus complexe avec toutes les colonnes de répartition
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
     * DTO pour une ligne complète de répartition d'affaire
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
     * DTO pour les totaux de répartition
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

    // ==================== DTOs 2 & 8: ETAT PAR SERIES DE MANDATEMENT ====================

    /**
     * DTO pour l'imprimé 2: "ETAT PAR SERIES DE MANDATEMENT"
     * (et imprimé 8 similaire)
     */
    public static class EtatMandatementDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private String typeEtat; // "MANDATEMENT" ou "MANDATEMENT_AGENT"
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
        private String observations;

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

        public String getObservations() { return observations; }
        public void setObservations(String observations) { this.observations = observations; }
    }

    // ==================== DTO 3: ETAT CUMULE PAR CENTRE DE REPARTITION ====================

    /**
     * DTO pour l'imprimé 3: "ETAT CUMULE PAR CENTRE DE REPARTITION"
     * (À garder pour la fin comme convenu)
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

        /**
         * Calcule le total du centre
         */
        public void calculerTotal() {
            this.partTotaleCentre = this.partRepartitionBase.add(this.partRepartitionIndicateurFictif);
        }
    }

    // ==================== DTO 4: ETAT DE REPARTITION DES INDICATEURS REELS ====================

    /**
     * DTO pour l'imprimé 4: "ETAT DE REPARTITION DES PART DES INDICATEURS REELS"
     */
    public static class EtatRepartitionIndicateursReelsDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private List<ServiceIndicateurDTO> services;
        private BigDecimal totalMontantEncaissement;
        private BigDecimal totalPartIndicateur;
        private int totalAffaires;

        public EtatRepartitionIndicateursReelsDTO() {
            this.services = new ArrayList<>();
            this.dateGeneration = LocalDate.now();
            this.totalMontantEncaissement = BigDecimal.ZERO;
            this.totalPartIndicateur = BigDecimal.ZERO;
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

        public List<ServiceIndicateurDTO> getServices() { return services; }
        public void setServices(List<ServiceIndicateurDTO> services) { this.services = services; }

        public BigDecimal getTotalMontantEncaissement() { return totalMontantEncaissement; }
        public void setTotalMontantEncaissement(BigDecimal totalMontantEncaissement) { this.totalMontantEncaissement = totalMontantEncaissement; }

        public BigDecimal getTotalPartIndicateur() { return totalPartIndicateur; }
        public void setTotalPartIndicateur(BigDecimal totalPartIndicateur) { this.totalPartIndicateur = totalPartIndicateur; }

        public int getTotalAffaires() { return totalAffaires; }
        public void setTotalAffaires(int totalAffaires) { this.totalAffaires = totalAffaires; }
    }

    /**
     * DTO pour un service dans l'état des indicateurs réels
     */
    public static class ServiceIndicateurDTO {
        private String nomService;
        private List<SectionIndicateurDTO> sections;
        private BigDecimal totalMontantService;
        private BigDecimal totalPartIndicateurService;
        private int totalAffairesService;

        public ServiceIndicateurDTO() {
            this.sections = new ArrayList<>();
            this.totalMontantService = BigDecimal.ZERO;
            this.totalPartIndicateurService = BigDecimal.ZERO;
            this.totalAffairesService = 0;
        }

        public ServiceIndicateurDTO(String nomService) {
            this();
            this.nomService = nomService;
        }

        // Getters et setters
        public String getNomService() { return nomService; }
        public void setNomService(String nomService) { this.nomService = nomService; }

        public List<SectionIndicateurDTO> getSections() { return sections; }
        public void setSections(List<SectionIndicateurDTO> sections) { this.sections = sections; }

        public BigDecimal getTotalMontantService() { return totalMontantService; }
        public void setTotalMontantService(BigDecimal totalMontantService) { this.totalMontantService = totalMontantService; }

        public BigDecimal getTotalPartIndicateurService() { return totalPartIndicateurService; }
        public void setTotalPartIndicateurService(BigDecimal totalPartIndicateurService) { this.totalPartIndicateurService = totalPartIndicateurService; }

        public int getTotalAffairesService() { return totalAffairesService; }
        public void setTotalAffairesService(int totalAffairesService) { this.totalAffairesService = totalAffairesService; }
    }

    /**
     * DTO pour une section dans l'état des indicateurs réels
     */
    public static class SectionIndicateurDTO {
        private String nomSection;
        private List<AffaireIndicateurDTO> affaires;
        private BigDecimal totalMontantSection;
        private BigDecimal totalPartIndicateurSection;

        public SectionIndicateurDTO() {
            this.affaires = new ArrayList<>();
            this.totalMontantSection = BigDecimal.ZERO;
            this.totalPartIndicateurSection = BigDecimal.ZERO;
        }

        public SectionIndicateurDTO(String nomSection) {
            this();
            this.nomSection = nomSection;
        }

        // Getters et setters
        public String getNomSection() { return nomSection; }
        public void setNomSection(String nomSection) { this.nomSection = nomSection; }

        public List<AffaireIndicateurDTO> getAffaires() { return affaires; }
        public void setAffaires(List<AffaireIndicateurDTO> affaires) { this.affaires = affaires; }

        public BigDecimal getTotalMontantSection() { return totalMontantSection; }
        public void setTotalMontantSection(BigDecimal totalMontantSection) { this.totalMontantSection = totalMontantSection; }

        public BigDecimal getTotalPartIndicateurSection() { return totalPartIndicateurSection; }
        public void setTotalPartIndicateurSection(BigDecimal totalPartIndicateurSection) { this.totalPartIndicateurSection = totalPartIndicateurSection; }
    }

    /**
     * DTO pour une affaire dans l'état des indicateurs réels
     */
    public static class AffaireIndicateurDTO {
        private String numeroEncaissement;
        private LocalDate dateEncaissement;
        private String numeroAffaire;
        private LocalDate dateAffaire;
        private String nomContrevenant;
        private String nomContravention;
        private BigDecimal montantEncaissement;
        private BigDecimal partIndicateur;
        private String observations;

        public AffaireIndicateurDTO() {}

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

        public BigDecimal getMontantEncaissement() { return montantEncaissement; }
        public void setMontantEncaissement(BigDecimal montantEncaissement) { this.montantEncaissement = montantEncaissement; }

        public BigDecimal getPartIndicateur() { return partIndicateur; }
        public void setPartIndicateur(BigDecimal partIndicateur) { this.partIndicateur = partIndicateur; }

        public String getObservations() { return observations; }
        public void setObservations(String observations) { this.observations = observations; }
    }

    // ==================== DTO 5: ETAT DE REPARTITION DU PRODUIT DES AFFAIRES CONTENTIEUSES ====================

    /**
     * DTO pour l'imprimé 5: "ETAT DE REPARTITION DU PRODUIT DES AFFAIRES CONTENTIEUSES"
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
        private BigDecimal partIndicateur2; // Deuxième colonne "Part indicateur"
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

    // ==================== DTO 6: ETAT CUMULE PAR AGENT ====================

    /**
     * DTO pour l'imprimé 6: "ETAT CUMULE PAR AGENT"
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

    // ==================== DTO 7: TABLEAU DES AMENDES PAR SERVICES ====================

    /**
     * DTO pour l'imprimé 7: "TABLEAU DES AMENDES PAR SERVICES"
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