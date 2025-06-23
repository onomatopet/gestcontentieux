package com.regulation.contentieux.service;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.dao.*;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.*;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.regulation.contentieux.model.Centre;
import com.regulation.contentieux.model.enums.StatutEncaissement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    private AffaireDAO affaireDAO = new AffaireDAO();
    private EncaissementDAO encaissementDAO = new EncaissementDAO();
    private AgentDAO agentDAO = new AgentDAO();
    private final ServiceDAO serviceDAO = new ServiceDAO();
    private final CentreDAO centreDAO = new CentreDAO();
    private final RepartitionService repartitionService = new RepartitionService();

    public RapportService() {
        this.affaireDAO = new AffaireDAO();
        this.encaissementDAO = new EncaissementDAO();
        this.agentDAO = new AgentDAO();
    }

    // ==================== MÉTHODES POUR SITUATION GÉNÉRALE ====================

    /**
     * Génère le rapport de situation générale
     * NOUVELLE MÉTHODE pour corriger le bug
     */
    public SituationGeneraleDTO genererSituationGenerale(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📊 Génération de la situation générale - {} au {}", dateDebut, dateFin);

        SituationGeneraleDTO situation = new SituationGeneraleDTO();
        situation.setDateDebut(dateDebut);
        situation.setDateFin(dateFin);
        situation.setDateGeneration(LocalDate.now());
        situation.setPeriodeLibelle(DateFormatter.format(dateDebut) + " au " + DateFormatter.format(dateFin));

        // Récupérer toutes les affaires de la période
        List<Affaire> affaires = affaireDAO.findByPeriod(dateDebut, dateFin);

        // Statistiques globales
        situation.setTotalAffaires(affaires.size());
        situation.setAffairesOuvertes((int) affaires.stream()
                .filter(a -> a.getStatut() == StatutAffaire.OUVERTE)
                .count());
        situation.setAffairesEnCours((int) affaires.stream()
                .filter(a -> a.getStatut() == StatutAffaire.EN_COURS)
                .count());
        situation.setAffairesSoldees((int) affaires.stream()
                .filter(a -> a.getStatut() == StatutAffaire.CLOSE)
                .count());
        situation.setAffairesAnnulees((int) affaires.stream()
                .filter(a -> a.getStatut() == StatutAffaire.ANNULEE)
                .count());

        // Calcul des montants
        BigDecimal totalAmendes = affaires.stream()
                .map(Affaire::getMontantAmendeTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        situation.setMontantTotalAmendes(totalAmendes);

        // Calcul du total encaissé
        BigDecimal totalEncaisse = BigDecimal.ZERO;
        for (Affaire affaire : affaires) {
            List<Encaissement> encaissements = encaissementDAO.findByAffaireId(affaire.getId());
            BigDecimal montantEncaisseAffaire = encaissements.stream()
                    .filter(e -> e.getStatut() == StatutEncaissement.VALIDE)
                    .map(Encaissement::getMontantEncaisse)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalEncaisse = totalEncaisse.add(montantEncaisseAffaire);
        }
        situation.setMontantTotalEncaisse(totalEncaisse);

        // Montant restant dû
        BigDecimal montantRestant = totalAmendes.subtract(totalEncaisse);
        situation.setMontantRestantDu(montantRestant);

        // Taux de recouvrement
        if (totalAmendes.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal tauxRecouvrement = totalEncaisse
                    .multiply(new BigDecimal("100"))
                    .divide(totalAmendes, 2, RoundingMode.HALF_UP);
            situation.setTauxRecouvrement(tauxRecouvrement);
        } else {
            situation.setTauxRecouvrement(BigDecimal.ZERO);
        }

        // Répartition par statut
        Map<String, Integer> repartitionParStatut = new HashMap<>();
        Map<String, BigDecimal> montantsParStatut = new HashMap<>();

        for (StatutAffaire statut : StatutAffaire.values()) {
            List<Affaire> affairesStatut = affaires.stream()
                    .filter(a -> a.getStatut() == statut)
                    .collect(Collectors.toList());

            if (!affairesStatut.isEmpty()) {
                repartitionParStatut.put(statut.name(), affairesStatut.size());
                BigDecimal montantStatut = affairesStatut.stream()
                        .map(Affaire::getMontantAmendeTotal)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                montantsParStatut.put(statut.name(), montantStatut);
            }
        }

        situation.setRepartitionParStatut(repartitionParStatut);
        situation.setMontantsParStatut(montantsParStatut);

        return situation;
    }

    /**
     * DTO pour le tableau des amendes par services
     * NOUVELLE CLASSE pour corriger le bug
     */
    public static class TableauAmendesParServicesDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private List<ServiceAmendeDTO> services = new ArrayList<>();
        private BigDecimal totalGeneral = BigDecimal.ZERO;
        private int nombreTotalAffaires = 0;

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

        public BigDecimal getTotalGeneral() { return totalGeneral; }
        public void setTotalGeneral(BigDecimal totalGeneral) { this.totalGeneral = totalGeneral; }

        public int getNombreTotalAffaires() { return nombreTotalAffaires; }
        public void setNombreTotalAffaires(int nombreTotalAffaires) { this.nombreTotalAffaires = nombreTotalAffaires; }

        /**
         * Alias pour getNombreTotalAffaires()
         * Utilisé par ExportService pour la compatibilité
         *
         * @return le nombre total d'affaires
         */
        public int getTotalAffaires() {
            return nombreTotalAffaires;
        }

        /**
         * Alias pour getTotalGeneral()
         * Utilisé par ExportService pour la compatibilité
         *
         * @return le montant total général
         */
        public BigDecimal getTotalMontant() {
            return totalGeneral;
        }
    }

    /**
     * DTO pour les amendes par service
     * NOUVELLE CLASSE pour corriger le bug
     */
    public static class ServiceAmendeDTO {
        private String nomService;
        private int nombreAffaires;
        private BigDecimal montantTotal;
        private String observations;

        public ServiceAmendeDTO() {
            this.montantTotal = BigDecimal.ZERO;
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

    // ==================== RAPPORT PRINCIPAL DE RÉTROCESSION ====================

    /**
     * Génère le rapport principal de répartition/rétrocession
     */
    public RapportRepartitionDTO genererRapportRepartition(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📊 Génération du rapport de répartition - {} au {}", dateDebut, dateFin);

        RapportRepartitionDTO rapport = new RapportRepartitionDTO();
        rapport.setDateDebut(dateDebut);
        rapport.setDateFin(dateFin);
        rapport.setDateGeneration(LocalDate.now());
        // Ajout de la période libellé pour corriger le bug
        rapport.setPeriodeLibelle(DateFormatter.format(dateDebut) + " au " + DateFormatter.format(dateFin));

        // Récupérer les encaissements validés de la période
        List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);

        for (Encaissement enc : encaissements) {
            if (enc.getStatut() != StatutEncaissement.VALIDE || enc.getAffaire() == null) {
                continue;
            }

            Affaire affaire = enc.getAffaire();
            RepartitionResultat repartition = repartitionService.calculerRepartition(enc, affaire);

            // Créer le DTO pour cette affaire
            AffaireRepartitionDTO affaireDTO = new AffaireRepartitionDTO();
            affaireDTO.setNumeroAffaire(affaire.getNumeroAffaire());
            affaireDTO.setDateCreation(affaire.getDateCreation());
            affaireDTO.setContrevenantNom(affaire.getContrevenant() != null ?
                    affaire.getContrevenant().getNomComplet() : "");
            affaireDTO.setContrevenant(affaire.getContrevenant() != null ?
                    affaire.getContrevenant().getNomComplet() : "");
            affaireDTO.setContraventionType(getContraventionLibelle(affaire));
            affaireDTO.setMontantAmende(affaire.getMontantAmendeTotal());
            affaireDTO.setMontantEncaisse(enc.getMontantEncaisse());
            affaireDTO.setPartEtat(repartition.getPartTresor());
            affaireDTO.setPartCollectivite(repartition.getPartFLCF());
            affaireDTO.setChefDossier(getChefDossier(affaire));
            affaireDTO.setBureau(affaire.getBureau() != null ? affaire.getBureau().getNomBureau() : "");
            affaireDTO.setStatut(affaire.getStatut().name());

            rapport.getAffaires().add(affaireDTO);
        }

        // Calcul des totaux
        rapport.calculateTotaux();

        return rapport;
    }

    /**
     * Génère le rapport des encaissements par période
     */
    public RapportEncaissementsDTO genererRapportEncaissements(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("💰 Génération du rapport des encaissements - {} au {}", dateDebut, dateFin);

        RapportEncaissementsDTO rapport = new RapportEncaissementsDTO();
        rapport.setDateDebut(dateDebut);
        rapport.setDateFin(dateFin);
        rapport.setDateGeneration(LocalDate.now());

        // Récupérer les encaissements
        List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);

        Map<Service, List<Encaissement>> encaissementsParService = new HashMap<>();

        for (Encaissement enc : encaissements) {
            if (enc.getStatut() == StatutEncaissement.VALIDE && enc.getAffaire() != null) {
                Service service = enc.getAffaire().getService();
                if (service != null) {
                    encaissementsParService.computeIfAbsent(service, k -> new ArrayList<>()).add(enc);
                }
            }
        }

        // Créer les DTOs par service
        for (Map.Entry<Service, List<Encaissement>> entry : encaissementsParService.entrySet()) {
            ServiceEncaissementDTO serviceDTO = new ServiceEncaissementDTO();
            serviceDTO.setNomService(entry.getKey().getNomService());

            BigDecimal totalService = BigDecimal.ZERO;
            for (Encaissement enc : entry.getValue()) {
                if (enc.getAffaire() != null) {
                    RepartitionResultat repartition = repartitionService.calculerRepartition(enc, enc.getAffaire());

                    DetailEncaissementDTO detail = new DetailEncaissementDTO();
                    detail.setNumeroEncaissement(enc.getReference());
                    detail.setDateEncaissement(enc.getDateEncaissement());
                    detail.setNumeroAffaire(enc.getAffaire().getNumeroAffaire());
                    detail.setMontant(enc.getMontantEncaisse());
                    detail.setPartIndicateur(repartition.getPartIndicateur());

                    serviceDTO.getEncaissements().add(detail);
                    totalService = totalService.add(enc.getMontantEncaisse());
                }
            }

            serviceDTO.setTotalEncaisse(totalService);
            serviceDTO.setNombreEncaissements(entry.getValue().size());

            rapport.getServices().add(serviceDTO);
        }

        // Calcul du total général
        rapport.setTotalGeneral(
                rapport.getServices().stream()
                        .map(ServiceEncaissementDTO::getTotalEncaisse)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        return rapport;
    }

    /**
     * Génère le rapport des affaires non soldées
     */
    public RapportAffairesNonSoldeesDTO genererRapportAffairesNonSoldees() {
        logger.info("📋 Génération du rapport des affaires non soldées");

        RapportAffairesNonSoldeesDTO rapport = new RapportAffairesNonSoldeesDTO();
        rapport.setDateGeneration(LocalDate.now());

        // Récupérer toutes les affaires non soldées
        List<Affaire> affairesNonSoldees = affaireDAO.findAll().stream()
                .filter(a -> a.getStatut() == StatutAffaire.EN_COURS)
                .collect(Collectors.toList());

        for (Affaire affaire : affairesNonSoldees) {
            AffaireNonSoldeeDTO dto = new AffaireNonSoldeeDTO();
            dto.setNumeroAffaire(affaire.getNumeroAffaire());
            dto.setDateCreation(affaire.getDateCreation());
            dto.setContrevenant(affaire.getContrevenant() != null ? affaire.getContrevenant().getNomComplet() : "");
            dto.setMontantTotal(affaire.getMontantAmendeTotal());

            // Calculer le montant déjà encaissé
            List<Encaissement> encaissements = encaissementDAO.findByAffaireId(affaire.getId());
            BigDecimal montantEncaisse = encaissements.stream()
                    .filter(e -> e.getStatut() == StatutEncaissement.VALIDE)
                    .map(Encaissement::getMontantEncaisse)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            dto.setMontantEncaisse(montantEncaisse);
            dto.setSoldeRestant(affaire.getMontantAmendeTotal().subtract(montantEncaisse));

            rapport.getAffaires().add(dto);
        }

        // Calcul des totaux
        rapport.calculateTotaux();

        return rapport;
    }

    // ==================== TEMPLATE 2: ÉTAT DE MANDATEMENT DES AYANTS-DROITS ====================

    /**
     * Génère l'état de mandatement pour une période donnée (Template 2)
     */
    public String genererEtatMandatement(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération de l'état par séries de mandatement - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteRapport("ÉTAT PAR SÉRIES DE MANDATEMENT", dateDebut, dateFin));

        // Template exact selon le cahier des charges
        html.append("""
        <table class="rapport-table">
            <thead>
                <tr>
                    <th rowspan="2">N° encaissement et Date</th>
                    <th rowspan="2">N° Affaire et Date</th>
                    <th rowspan="2">Produit net</th>
                    <th colspan="5">Part revenant aux</th>
                    <th rowspan="2">Observations</th>
                </tr>
                <tr>
                    <th>Chefs</th>
                    <th>Saisissants</th>
                    <th>Mutuelle nationale</th>
                    <th>Masse commune</th>
                    <th>Intéressement</th>
                </tr>
            </thead>
            <tbody>
    """);

        List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);
        BigDecimal totalProduitNet = BigDecimal.ZERO;
        BigDecimal totalChefs = BigDecimal.ZERO;
        BigDecimal totalSaisissants = BigDecimal.ZERO;
        BigDecimal totalMutuelle = BigDecimal.ZERO;
        BigDecimal totalMasseCommune = BigDecimal.ZERO;
        BigDecimal totalInteressement = BigDecimal.ZERO;

        for (Encaissement enc : encaissements) {
            if (enc.getStatut() != StatutEncaissement.VALIDE || enc.getAffaire() == null) continue;

            RepartitionResultat repartition = repartitionService.calculerRepartition(enc, enc.getAffaire());

            html.append("<tr>");
            html.append("<td>").append(enc.getReference()).append("<br/>")
                    .append(DateFormatter.format(enc.getDateEncaissement())).append("</td>");
            html.append("<td>").append(enc.getAffaire().getNumeroAffaire()).append("<br/>")
                    .append(DateFormatter.format(enc.getAffaire().getDateCreation())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getProduitNetDroits())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartChefs())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartSaisissants())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartMutuelle())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartMasseCommune())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartInteressement())).append("</td>");
            html.append("<td>").append(enc.getObservations() != null ? enc.getObservations() : "").append("</td>");
            html.append("</tr>");

            // Cumuls
            totalProduitNet = totalProduitNet.add(repartition.getProduitNetDroits());
            totalChefs = totalChefs.add(repartition.getPartChefs());
            totalSaisissants = totalSaisissants.add(repartition.getPartSaisissants());
            totalMutuelle = totalMutuelle.add(repartition.getPartMutuelle());
            totalMasseCommune = totalMasseCommune.add(repartition.getPartMasseCommune());
            totalInteressement = totalInteressement.add(repartition.getPartInteressement());
        }

        // Totaux
        html.append("""
        <tr class="total-row">
            <td colspan="2"><strong>TOTAUX</strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalProduitNet)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalChefs)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalSaisissants)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalMutuelle)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalMasseCommune)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalInteressement)).append("""
            </strong></td>
            <td></td>
        </tr>
    """);

        html.append("</tbody></table>");
        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

    // ==================== TEMPLATE 3: ÉTAT PAR CENTRE DE RÉPARTITION ====================

    /**
     * Génère l'état cumulé par centre de répartition (Template 3)
     * Gabarit exact : Centre | Répartition de base | Répartition part indic. | Part centre
     */
    public String genererEtatCentreRepartition(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("🏢 Génération état cumulé par centre de répartition - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteRapport("ÉTAT CUMULÉ PAR CENTRE DE RÉPARTITION", dateDebut, dateFin));

        // Template exact selon le gabarit du cahier des charges
        html.append("""
        <table class="rapport-table">
            <thead>
                <tr>
                    <th rowspan="2">Centre de répartition</th>
                    <th colspan="2">Part revenant au centre au titre de</th>
                    <th rowspan="2">Part centre</th>
                </tr>
                <tr>
                    <th>Répartition de base</th>
                    <th>Répartition part indic.</th>
                </tr>
            </thead>
            <tbody>
    """);

        // Récupération des centres et calcul des répartitions
        List<Centre> centres = centreDAO.findAllActifs();
        BigDecimal totalRepartitionBase = BigDecimal.ZERO;
        BigDecimal totalRepartitionIndic = BigDecimal.ZERO;
        BigDecimal totalPartCentre = BigDecimal.ZERO;

        for (Centre centre : centres) {
            CentreStatsDTO stats = calculerStatsCentre(centre, dateDebut, dateFin);

            if (stats.hasActivite()) {
                html.append("<tr>");
                html.append("<td>").append(centre.getNomCentre()).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(stats.getRepartitionBase())).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(stats.getRepartitionIndicateur())).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(stats.getPartTotalCentre())).append("</td>");
                html.append("</tr>");

                // Cumuls pour totaux
                totalRepartitionBase = totalRepartitionBase.add(stats.getRepartitionBase());
                totalRepartitionIndic = totalRepartitionIndic.add(stats.getRepartitionIndicateur());
                totalPartCentre = totalPartCentre.add(stats.getPartTotalCentre());
            }
        }

        // Ligne de totaux
        html.append("""
        <tr class="total-row">
            <td><strong>TOTAL</strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalRepartitionBase)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalRepartitionIndic)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalPartCentre)).append("""
            </strong></td>
        </tr>
    """);

        html.append("</tbody></table>");
        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Génère l'état de répartition des parts des indicateurs réels (Template 4)
     * Gabarit : Organisé par Service et Section avec colonnes spécifiques indicateurs
     */
    public String genererEtatIndicateursReels(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("🎯 Génération état des indicateurs réels - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteRapport("ÉTAT DE RÉPARTITION DES PARTS DES INDICATEURS RÉELS", dateDebut, dateFin));

        // En-tête du tableau selon le gabarit
        html.append("""
        <table class="rapport-table">
            <thead>
                <tr>
                    <th>N° encaissement et Date</th>
                    <th>N° Affaire et Date</th>
                    <th>Noms des contrevenants</th>
                    <th>Contraventions</th>
                    <th>Montant encaissement</th>
                    <th>Part indicateur</th>
                    <th>Observations</th>
                </tr>
            </thead>
            <tbody>
    """);

        // Récupérer les services ayant des indicateurs
        List<Service> services = serviceDAO.findAllActifs();
        BigDecimal totalEncaissement = BigDecimal.ZERO;
        BigDecimal totalPartIndicateur = BigDecimal.ZERO;

        for (Service service : services) {
            List<Encaissement> encaissementsService = getEncaissementsAvecIndicateurByService(service, dateDebut, dateFin);

            if (!encaissementsService.isEmpty()) {
                // En-tête de section pour le service
                html.append("""
                <tr class="section-header">
                    <td colspan="7"><strong>Service : """).append(service.getNomService()).append("""
                    </strong></td>
                </tr>
            """);

                // Données du service
                for (Encaissement enc : encaissementsService) {
                    if (enc.getAffaire() == null) continue;

                    Affaire affaire = enc.getAffaire();
                    RepartitionResultat repartition = repartitionService.calculerRepartition(enc, affaire);

                    // Vérifier qu'il y a effectivement un indicateur
                    if (repartition.getPartIndicateur().compareTo(BigDecimal.ZERO) > 0) {
                        html.append("<tr>");
                        html.append("<td>").append(enc.getReference()).append("<br/>")
                                .append(DateFormatter.format(enc.getDateEncaissement())).append("</td>");
                        html.append("<td>").append(affaire.getNumeroAffaire()).append("<br/>")
                                .append(DateFormatter.format(affaire.getDateCreation())).append("</td>");
                        html.append("<td>").append(affaire.getContrevenant() != null ?
                                affaire.getContrevenant().getNomComplet() : "").append("</td>");
                        html.append("<td>").append(getLibellesContraventions(affaire)).append("</td>");
                        html.append("<td class='montant'>").append(CurrencyFormatter.format(enc.getMontantEncaisse())).append("</td>");
                        html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartIndicateur())).append("</td>");
                        html.append("<td>").append(enc.getObservations() != null ? enc.getObservations() : "").append("</td>");
                        html.append("</tr>");

                        totalEncaissement = totalEncaissement.add(enc.getMontantEncaisse());
                        totalPartIndicateur = totalPartIndicateur.add(repartition.getPartIndicateur());
                    }
                }

                // Sous-total par service si nécessaire
                html.append("""
                <tr class="subtotal-row">
                    <td colspan="4"><em>Sous-total Service</em></td>
                    <td class="montant"><em>""").append(CurrencyFormatter.format(
                        encaissementsService.stream()
                                .map(Encaissement::getMontantEncaisse)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))).append("""
                    </em></td>
                    <td class="montant"><em>""").append(CurrencyFormatter.format(
                        encaissementsService.stream()
                                .map(enc -> {
                                    try {
                                        return repartitionService.calculerRepartition(enc, enc.getAffaire()).getPartIndicateur();
                                    } catch (Exception e) {
                                        return BigDecimal.ZERO;
                                    }
                                })
                                .reduce(BigDecimal.ZERO, BigDecimal::add))).append("""
                    </em></td>
                    <td></td>
                </tr>
            """);
            }
        }

        // Totaux généraux
        html.append("""
        <tr class="total-row">
            <td colspan="4"><strong>TOTAL GÉNÉRAL</strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalEncaissement)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalPartIndicateur)).append("""
            </strong></td>
            <td></td>
        </tr>
    """);

        html.append("</tbody></table>");
        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

    // ==================== TEMPLATE 4: ÉTAT DES INDICATEURS ====================

    /**
     * Génère l'état des indicateurs (Template 4)
     */
    public String genererEtatIndicateurs(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📊 Génération de l'état des indicateurs - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteRapport("ÉTAT DES INDICATEURS RÉELS", dateDebut, dateFin));

        html.append("""
            <table class="rapport-table">
                <thead>
                    <tr>
                        <th>N° Encaissement</th>
                        <th>Date</th>
                        <th>N° Affaire</th>
                        <th>Contrevenant</th>
                        <th>Montant Encaissé</th>
                        <th>Part Indicateur</th>
                        <th>Observations</th>
                    </tr>
                </thead>
                <tbody>
        """);

        List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);
        BigDecimal totalEncaissement = BigDecimal.ZERO;
        BigDecimal totalIndicateur = BigDecimal.ZERO;

        for (Encaissement enc : encaissements) {
            if (enc.getStatut() == StatutEncaissement.VALIDE && enc.getAffaire() != null) {
                RepartitionResultat repartition = repartitionService.calculerRepartition(enc, enc.getAffaire());

                html.append("<tr>");
                html.append("<td>").append(enc.getReference()).append("</td>");
                html.append("<td>").append(DateFormatter.format(enc.getDateEncaissement())).append("</td>");
                html.append("<td>").append(enc.getAffaire().getNumeroAffaire()).append("</td>");
                html.append("<td>").append(enc.getAffaire().getContrevenant() != null ?
                        enc.getAffaire().getContrevenant().getNomComplet() : "").append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(enc.getMontantEncaisse())).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartIndicateur())).append("</td>");
                html.append("<td>").append(enc.getObservations() != null ? enc.getObservations() : "").append("</td>");
                html.append("</tr>");

                totalEncaissement = totalEncaissement.add(enc.getMontantEncaisse());
                totalIndicateur = totalIndicateur.add(repartition.getPartIndicateur());
            }
        }

        // Totaux
        html.append("""
            <tr class="total-row">
                <td colspan="4"><strong>TOTAL</strong></td>
                <td class="montant"><strong>""").append(CurrencyFormatter.format(totalEncaissement)).append("""
                </strong></td>
                <td class="montant"><strong>""").append(CurrencyFormatter.format(totalIndicateur)).append("""
                </strong></td>
                <td></td>
            </tr>
        """);

        html.append("</tbody></table>");
        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

    // ==================== TEMPLATE 5: ÉTAT DE RÉPARTITION DU PRODUIT DES AFFAIRES ====================

    /**
     * Génère l'état de répartition du produit des affaires contentieuses (Template 5)
     * Gabarit : 11 colonnes incluant produit disponible, parts indicateur, Direction, FLCF, Trésor, etc.
     */
    public String genererEtatRepartitionProduit(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("💰 Génération état de répartition du produit - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteRapport("ÉTAT DE RÉPARTITION DU PRODUIT DES AFFAIRES CONTENTIEUSES", dateDebut, dateFin));

        // Template exact selon le gabarit (11 colonnes)
        html.append("""
        <table class="rapport-table">
            <thead>
                <tr>
                    <th>N° encaissement et Date</th>
                    <th>N° Affaire et Date</th>
                    <th>Noms des contrevenants</th>
                    <th>Noms des contraventions</th>
                    <th>Produit disponible</th>
                    <th>Part indicateur</th>
                    <th>Part Direction contentieux</th>
                    <th>Part indicateur</th>
                    <th>FLCF</th>
                    <th>Montant Trésor</th>
                    <th>Montant Global ayants droits</th>
                </tr>
            </thead>
            <tbody>
    """);

        List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);

        // Variables de cumul
        BigDecimal totalProduitDisponible = BigDecimal.ZERO;
        BigDecimal totalPartIndicateur = BigDecimal.ZERO;
        BigDecimal totalPartDirection = BigDecimal.ZERO;
        BigDecimal totalPartIndicateur2 = BigDecimal.ZERO; // 2ème colonne indicateur
        BigDecimal totalFLCF = BigDecimal.ZERO;
        BigDecimal totalTresor = BigDecimal.ZERO;
        BigDecimal totalAyantsDroits = BigDecimal.ZERO;

        for (Encaissement enc : encaissements) {
            if (enc.getStatut() != StatutEncaissement.VALIDE || enc.getAffaire() == null) continue;

            Affaire affaire = enc.getAffaire();
            RepartitionResultat repartition = repartitionService.calculerRepartition(enc, affaire);

            html.append("<tr>");
            html.append("<td>").append(enc.getReference()).append("<br/>")
                    .append(DateFormatter.format(enc.getDateEncaissement())).append("</td>");
            html.append("<td>").append(affaire.getNumeroAffaire()).append("<br/>")
                    .append(DateFormatter.format(affaire.getDateCreation())).append("</td>");
            html.append("<td>").append(affaire.getContrevenant() != null ?
                    affaire.getContrevenant().getNomComplet() : "").append("</td>");
            html.append("<td>").append(getLibellesContraventions(affaire)).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getProduitDisponible())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartIndicateur())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartDD())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartIndicateur())).append("</td>"); // Répétition selon gabarit
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartFlcf())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartTresor())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getProduitNetDroits())).append("</td>");
            html.append("</tr>");

            // Cumuls
            totalProduitDisponible = totalProduitDisponible.add(repartition.getProduitDisponible());
            totalPartIndicateur = totalPartIndicateur.add(repartition.getPartIndicateur());
            totalPartDirection = totalPartDirection.add(repartition.getPartDD());
            totalPartIndicateur2 = totalPartIndicateur2.add(repartition.getPartIndicateur());
            totalFLCF = totalFLCF.add(repartition.getPartFlcf());
            totalTresor = totalTresor.add(repartition.getPartTresor());
            totalAyantsDroits = totalAyantsDroits.add(repartition.getProduitNetDroits());
        }

        // Totaux
        html.append("""
        <tr class="total-row">
            <td colspan="4"><strong>TOTAUX</strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalProduitDisponible)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalPartIndicateur)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalPartDirection)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalPartIndicateur2)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalFLCF)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalTresor)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalAyantsDroits)).append("""
            </strong></td>
        </tr>
    """);

        html.append("</tbody></table>");
        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

// ==================== TEMPLATE 6: ÉTAT CUMULÉ PAR AGENT ====================

    /**
     * Génère l'état cumulé par agent (Template 6)
     * Gabarit : Nom agent | Chef | Saisissant | DG | DD | Part agent
     */
    public String genererEtatCumuleParAgent(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("👮 Génération état cumulé par agent - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteRapport("ÉTAT CUMULÉ PAR AGENT", dateDebut, dateFin));

        // Template exact selon le gabarit
        html.append("""
        <table class="rapport-table">
            <thead>
                <tr>
                    <th rowspan="2">Nom de l'agent</th>
                    <th colspan="4">Part revenant à l'agent après répartition en tant que</th>
                    <th rowspan="2">Part agent</th>
                </tr>
                <tr>
                    <th>Chef</th>
                    <th>Saisissant</th>
                    <th>DG</th>
                    <th>DD</th>
                </tr>
            </thead>
            <tbody>
    """);

        // Récupération des agents actifs et calcul de leurs parts
        List<Agent> agents = agentDAO.findAllActifs();
        BigDecimal totalPartChef = BigDecimal.ZERO;
        BigDecimal totalPartSaisissant = BigDecimal.ZERO;
        BigDecimal totalPartDG = BigDecimal.ZERO;
        BigDecimal totalPartDD = BigDecimal.ZERO;
        BigDecimal totalPartAgent = BigDecimal.ZERO;

        for (Agent agent : agents) {
            AgentStatsDTO stats = calculerStatsAgentComplet(agent, dateDebut, dateFin);

            if (stats.hasActivite()) {
                html.append("<tr>");
                html.append("<td>").append(agent.getNomComplet()).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(stats.getPartEnTantQueChef())).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(stats.getPartEnTantQueSaisissant())).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(stats.getPartEnTantQueDG())).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(stats.getPartEnTantQueDD())).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(stats.getPartTotaleAgent())).append("</td>");
                html.append("</tr>");

                // Cumuls
                totalPartChef = totalPartChef.add(stats.getPartEnTantQueChef());
                totalPartSaisissant = totalPartSaisissant.add(stats.getPartEnTantQueSaisissant());
                totalPartDG = totalPartDG.add(stats.getPartEnTantQueDG());
                totalPartDD = totalPartDD.add(stats.getPartEnTantQueDD());
                totalPartAgent = totalPartAgent.add(stats.getPartTotaleAgent());
            }
        }

        // Totaux
        html.append("""
        <tr class="total-row">
            <td><strong>TOTAL</strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalPartChef)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalPartSaisissant)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalPartDG)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalPartDD)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalPartAgent)).append("""
            </strong></td>
        </tr>
    """);

        html.append("</tbody></table>");
        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Génère l'état de répartition par service (Template 5)
     */
    public String genererEtatRepartitionParService(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("🏢 Génération de l'état par service - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteRapport("ÉTAT DE RÉPARTITION PAR SERVICE", dateDebut, dateFin));

        List<Service> services = serviceDAO.findAllActive();

        for (Service service : services) {
            List<Encaissement> encaissements = getEncaissementsByService(service, dateDebut, dateFin);

            if (!encaissements.isEmpty()) {
                html.append("<h3>Service : ").append(service.getNomService()).append("</h3>");

                html.append("""
                    <table class="rapport-table">
                        <thead>
                            <tr>
                                <th>N° Encaissement</th>
                                <th>N° Affaire</th>
                                <th>Contrevenant</th>
                                <th>Montant Encaissé</th>
                                <th>Part Service</th>
                            </tr>
                        </thead>
                        <tbody>
                """);

                BigDecimal totalService = BigDecimal.ZERO;
                BigDecimal totalPartService = BigDecimal.ZERO;

                for (Encaissement enc : encaissements) {
                    if (enc.getAffaire() != null) {
                        RepartitionResultat repartition = repartitionService.calculerRepartition(enc, enc.getAffaire());

                        html.append("<tr>");
                        html.append("<td>").append(enc.getReference()).append("</td>");
                        html.append("<td>").append(enc.getAffaire().getNumeroAffaire()).append("</td>");
                        html.append("<td>").append(enc.getAffaire().getContrevenant() != null ?
                                enc.getAffaire().getContrevenant().getNomComplet() : "").append("</td>");
                        html.append("<td class='montant'>").append(CurrencyFormatter.format(enc.getMontantEncaisse())).append("</td>");
                        html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartIndicateur())).append("</td>");
                        html.append("</tr>");

                        totalService = totalService.add(enc.getMontantEncaisse());
                        totalPartService = totalPartService.add(repartition.getPartIndicateur());
                    }
                }

                html.append("""
                    <tr class="total-row">
                        <td colspan="3"><strong>Total Service</strong></td>
                        <td class="montant"><strong>""").append(CurrencyFormatter.format(totalService)).append("""
                        </strong></td>
                        <td class="montant"><strong>""").append(CurrencyFormatter.format(totalPartService)).append("""
                        </strong></td>
                    </tr>
                """);

                html.append("</tbody></table><br/>");
            }
        }

        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

    // ==================== TEMPLATE 6: SITUATION DES MANDATS ====================

    /**
     * Génère la situation des mandats (Template 6)
     */
    public String genererSituationMandats() {
        logger.info("📑 Génération de la situation des mandats");

        StringBuilder html = new StringBuilder();

        // En-tête sans période
        html.append(genererEnTeteSimple("SITUATION DES MANDATS"));

        // Tableau
        html.append("""
            <table class="rapport-table">
                <thead>
                    <tr>
                        <th>N° Mandat</th>
                        <th>Date Début</th>
                        <th>Date Fin</th>
                        <th>Statut</th>
                        <th>Nombre d'Affaires</th>
                        <th>Montant Total</th>
                        <th>Observations</th>
                    </tr>
                </thead>
                <tbody>
        """);

        // TODO: Implémenter la récupération des mandats
        // Pour l'instant, données de test
        html.append("""
            <tr>
                <td>2024-0001</td>
                <td>01/01/2024</td>
                <td>31/12/2024</td>
                <td>En cours</td>
                <td>150</td>
                <td class="montant">15,000,000 FCFA</td>
                <td></td>
            </tr>
        """);

        html.append("</tbody></table>");
        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

    public String genererTableauAmendesParServices(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("🏢 Génération tableau des amendes par services - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteRapport("TABLEAU DES AMENDES PAR SERVICES", dateDebut, dateFin));

        // Template exact selon le cahier des charges
        html.append("""
        <table class="rapport-table">
            <thead>
                <tr>
                    <th>Services</th>
                    <th>Nombre d'affaires</th>
                    <th>Montant</th>
                    <th>Observations</th>
                </tr>
            </thead>
            <tbody>
    """);

        // Récupération des services et calcul des statistiques
        List<Service> services = serviceDAO.findAllActifs();
        int totalAffaires = 0;
        BigDecimal totalMontant = BigDecimal.ZERO;

        for (Service service : services) {
            ServiceStatsDTO stats = calculerStatsService(service, dateDebut, dateFin);

            if (stats.getNombreAffaires() > 0) {
                html.append("<tr>");
                html.append("<td>").append(service.getNomService()).append("</td>");
                html.append("<td class='nombre'>").append(stats.getNombreAffaires()).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(stats.getMontantTotal())).append("</td>");
                html.append("<td>").append(stats.getObservations() != null ? stats.getObservations() : "").append("</td>");
                html.append("</tr>");

                totalAffaires += stats.getNombreAffaires();
                totalMontant = totalMontant.add(stats.getMontantTotal());
            }
        }

        // Totaux
        html.append("""
        <tr class="total-row">
            <td><strong>TOTAL</strong></td>
            <td class="nombre"><strong>""").append(totalAffaires).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalMontant)).append("""
            </strong></td>
            <td></td>
        </tr>
    """);

        html.append("</tbody></table>");
        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

    // ==================== TEMPLATE 7: ÉTAT DES AMENDES PAR SERVICE ====================

    /**
     * Génère l'état des amendes par service (Template 7)
     */
    public String genererEtatAmendesParService(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("💰 Génération de l'état des amendes par service - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();

        // En-tête
        html.append(genererEnTeteRapport("ÉTAT DES AMENDES PAR SERVICE", dateDebut, dateFin));

        // Tableau
        html.append("""
            <table class="rapport-table">
                <thead>
                    <tr>
                        <th>Service</th>
                        <th>Nombre d'Affaires</th>
                        <th>Montant Total</th>
                        <th>Observations</th>
                    </tr>
                </thead>
                <tbody>
        """);

        // Récupérer les statistiques par service
        List<Service> services = serviceDAO.findAllActive();
        BigDecimal totalGeneral = BigDecimal.ZERO;
        int totalAffaires = 0;

        for (Service service : services) {
            // Compter les affaires et calculer le montant
            ServiceStatsDTO stats = calculerStatsService(service, dateDebut, dateFin);

            if (stats.getNombreAffaires() > 0) {
                html.append("<tr>");
                html.append("<td>").append(service.getNomService()).append("</td>");
                html.append("<td class='center'>").append(stats.getNombreAffaires()).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(stats.getMontantTotal())).append("</td>");
                html.append("<td>").append(stats.getObservations()).append("</td>");
                html.append("</tr>");

                totalGeneral = totalGeneral.add(stats.getMontantTotal());
                totalAffaires += stats.getNombreAffaires();
            }
        }

        // Total général
        html.append("""
            <tr class="total-row">
                <td><strong>TOTAL GÉNÉRAL</strong></td>
                <td class="center"><strong>""").append(totalAffaires).append("""
                </strong></td>
                <td class="montant"><strong>""").append(CurrencyFormatter.format(totalGeneral)).append("""
                </strong></td>
                <td></td>
            </tr>
        """);

        html.append("</tbody></table>");
        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

    // ==================== TEMPLATE 8: ÉTAT DE MANDATEMENT PAR AGENT ====================

    /**
     * Génère l'état détaillé par mandatement pour les agents (Template 8)
     * Gabarit : N° encaissement | N° Affaire | Parts par rôle | Part agent totale
     */
    public String genererEtatMandatementAgents(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("👥 Génération état détaillé par mandatement agents - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteRapport("ÉTAT PAR SÉRIES DE MANDATEMENTS", dateDebut, dateFin));

        // Template exact selon le gabarit
        html.append("""
        <table class="rapport-table">
            <thead>
                <tr>
                    <th rowspan="2">N° encaissement et Date</th>
                    <th rowspan="2">N° Affaire et Date</th>
                    <th colspan="5">Part revenant à l'agent après répartition en tant que</th>
                    <th rowspan="2">Part agent</th>
                </tr>
                <tr>
                    <th>Chefs</th>
                    <th>Saisissants</th>
                    <th>Mutuelle nationale</th>
                    <th>D.G</th>
                    <th>D.D</th>
                </tr>
            </thead>
            <tbody>
    """);

        List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);

        BigDecimal totalChefs = BigDecimal.ZERO;
        BigDecimal totalSaisissants = BigDecimal.ZERO;
        BigDecimal totalMutuelle = BigDecimal.ZERO;
        BigDecimal totalDG = BigDecimal.ZERO;
        BigDecimal totalDD = BigDecimal.ZERO;
        BigDecimal totalPartAgent = BigDecimal.ZERO;

        for (Encaissement enc : encaissements) {
            if (enc.getStatut() != StatutEncaissement.VALIDE || enc.getAffaire() == null) continue;

            Affaire affaire = enc.getAffaire();
            RepartitionResultat repartition = repartitionService.calculerRepartition(enc, affaire);

            // Calculer la part totale agents (somme des rôles)
            BigDecimal partTotaleAgents = repartition.getPartChefs()
                    .add(repartition.getPartSaisissants())
                    .add(repartition.getPartMutuelle())
                    .add(repartition.getPartDG())
                    .add(repartition.getPartDD());

            html.append("<tr>");
            html.append("<td>").append(enc.getReference()).append("<br/>")
                    .append(DateFormatter.format(enc.getDateEncaissement())).append("</td>");
            html.append("<td>").append(affaire.getNumeroAffaire()).append("<br/>")
                    .append(DateFormatter.format(affaire.getDateCreation())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartChefs())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartSaisissants())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartMutuelle())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartDG())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartDD())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(partTotaleAgents)).append("</td>");
            html.append("</tr>");

            // Cumuls
            totalChefs = totalChefs.add(repartition.getPartChefs());
            totalSaisissants = totalSaisissants.add(repartition.getPartSaisissants());
            totalMutuelle = totalMutuelle.add(repartition.getPartMutuelle());
            totalDG = totalDG.add(repartition.getPartDG());
            totalDD = totalDD.add(repartition.getPartDD());
            totalPartAgent = totalPartAgent.add(partTotaleAgents);
        }

        // Totaux
        html.append("""
        <tr class="total-row">
            <td colspan="2"><strong>TOTAUX</strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalChefs)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalSaisissants)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalMutuelle)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalDG)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalDD)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalPartAgent)).append("""
            </strong></td>
        </tr>
    """);

        html.append("</tbody></table>");
        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }


    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * ENRICHISSEMENT : Calcul des statistiques par centre
     */
    private CentreStatsDTO calculerStatsCentre(Centre centre, LocalDate dateDebut, LocalDate dateFin) {
        CentreStatsDTO stats = new CentreStatsDTO();
        stats.setCentre(centre);

        String sql = """
        SELECT 
            COALESCE(SUM(e.montant_encaisse), 0) as montant_total,
            COUNT(DISTINCT a.id) as nombre_affaires
        FROM affaires a
        JOIN encaissements e ON a.id = e.affaire_id
        WHERE a.centre_id = ? 
        AND e.date_encaissement BETWEEN ? AND ?
        AND e.statut = 'VALIDE'
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, centre.getId());
            stmt.setDate(2, Date.valueOf(dateDebut));
            stmt.setDate(3, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                BigDecimal montantTotal = rs.getBigDecimal("montant_total");

                // Calcul des répartitions selon les règles métier
                // 60% pour la répartition de base, part indicateur variable
                stats.setRepartitionBase(montantTotal.multiply(new BigDecimal("0.60")));
                stats.setRepartitionIndicateur(montantTotal.multiply(new BigDecimal("0.10"))); // 10% indicateurs
                stats.setPartTotalCentre(stats.getRepartitionBase().add(stats.getRepartitionIndicateur()));
                stats.setNombreAffaires(rs.getInt("nombre_affaires"));
            }

        } catch (SQLException e) {
            logger.error("Erreur calcul stats centre: {}", centre.getId(), e);
        }

        return stats;
    }

    /**
     * ENRICHISSEMENT : Récupération des encaissements avec indicateur par service
     */
    private List<Encaissement> getEncaissementsAvecIndicateurByService(Service service, LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
        SELECT e.* FROM encaissements e
        JOIN affaires a ON e.affaire_id = a.id
        WHERE a.service_id = ?
        AND e.date_encaissement BETWEEN ? AND ?
        AND e.statut = 'VALIDE'
        AND a.indicateur_existe = 1
        ORDER BY e.date_encaissement, e.reference
    """;

        List<Encaissement> encaissements = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, service.getId());
            stmt.setDate(2, Date.valueOf(dateDebut));
            stmt.setDate(3, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Encaissement enc = encaissementDAO.mapResultSetToEntity(rs);
                encaissements.add(enc);
            }

        } catch (SQLException e) {
            logger.error("Erreur récupération encaissements indicateur service: {}", service.getId(), e);
        }

        return encaissements;
    }

    /**
     * ENRICHISSEMENT : Calcul des statistiques complètes par agent
     */
    private AgentStatsDTO calculerStatsAgentComplet(Agent agent, LocalDate dateDebut, LocalDate dateFin) {
        AgentStatsDTO stats = new AgentStatsDTO();
        stats.setAgent(agent);

        // Calculer les parts selon les différents rôles de l'agent
        stats.setPartEnTantQueChef(calculerPartAgentParRole(agent, "CHEF", dateDebut, dateFin));
        stats.setPartEnTantQueSaisissant(calculerPartAgentParRole(agent, "SAISISSANT", dateDebut, dateFin));

        // Parts spéciales pour DG/DD
        if (agent.getRoleSpecial() == RoleSpecial.DG) {
            stats.setPartEnTantQueDG(calculerPartDG(agent, dateDebut, dateFin));
        }
        if (agent.getRoleSpecial() == RoleSpecial.DD) {
            stats.setPartEnTantQueDD(calculerPartDD(agent, dateDebut, dateFin));
        }

        // Calculer la part totale
        stats.setPartTotaleAgent(
                stats.getPartEnTantQueChef()
                        .add(stats.getPartEnTantQueSaisissant())
                        .add(stats.getPartEnTantQueDG())
                        .add(stats.getPartEnTantQueDD())
        );

        return stats;
    }

    /**
     * ENRICHISSEMENT : Calcul de la part d'un agent selon son rôle
     */
    private BigDecimal calculerPartAgentParRole(Agent agent, String role, LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
        SELECT COALESCE(SUM(e.montant_encaisse), 0) as montant_total
        FROM encaissements e
        JOIN affaires a ON e.affaire_id = a.id
        JOIN affaire_acteurs aa ON a.id = aa.affaire_id
        WHERE aa.agent_id = ? 
        AND aa.role_sur_affaire = ?
        AND e.date_encaissement BETWEEN ? AND ?
        AND e.statut = 'VALIDE'
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, agent.getId());
            stmt.setString(2, role);
            stmt.setDate(3, Date.valueOf(dateDebut));
            stmt.setDate(4, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                BigDecimal montantTotal = rs.getBigDecimal("montant_total");

                // Calcul selon les pourcentages de répartition par rôle
                BigDecimal pourcentage = switch (role) {
                    case "CHEF" -> new BigDecimal("0.15"); // 15% pour les chefs
                    case "SAISISSANT" -> new BigDecimal("0.20"); // 20% pour les saisissants
                    default -> BigDecimal.ZERO;
                };

                return montantTotal.multiply(pourcentage);
            }

        } catch (SQLException e) {
            logger.error("Erreur calcul part agent {} rôle {}", agent.getId(), role, e);
        }

        return BigDecimal.ZERO;
    }

    // ==================== CLASSES DTO POUR LES STATISTIQUES ====================

    public static class CentreStatsDTO {
        private Centre centre;
        private BigDecimal repartitionBase = BigDecimal.ZERO;
        private BigDecimal repartitionIndicateur = BigDecimal.ZERO;
        private BigDecimal partTotalCentre = BigDecimal.ZERO;
        private int nombreAffaires;

        public boolean hasActivite() {
            return nombreAffaires > 0 && partTotalCentre.compareTo(BigDecimal.ZERO) > 0;
        }

        // Getters et setters complets
        public Centre getCentre() { return centre; }
        public void setCentre(Centre centre) { this.centre = centre; }

        public BigDecimal getRepartitionBase() { return repartitionBase; }
        public void setRepartitionBase(BigDecimal repartitionBase) { this.repartitionBase = repartitionBase; }

        public BigDecimal getRepartitionIndicateur() { return repartitionIndicateur; }
        public void setRepartitionIndicateur(BigDecimal repartitionIndicateur) { this.repartitionIndicateur = repartitionIndicateur; }

        public BigDecimal getPartTotalCentre() { return partTotalCentre; }
        public void setPartTotalCentre(BigDecimal partTotalCentre) { this.partTotalCentre = partTotalCentre; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }
    }

    public static class AgentStatsDTO {
        private Agent agent;
        private BigDecimal partEnTantQueChef = BigDecimal.ZERO;
        private BigDecimal partEnTantQueSaisissant = BigDecimal.ZERO;
        private BigDecimal partEnTantQueDG = BigDecimal.ZERO;
        private BigDecimal partEnTantQueDD = BigDecimal.ZERO;
        private BigDecimal partTotaleAgent = BigDecimal.ZERO;

        public boolean hasActivite() {
            return partTotaleAgent.compareTo(BigDecimal.ZERO) > 0;
        }

        // Getters et setters complets
        public Agent getAgent() { return agent; }
        public void setAgent(Agent agent) { this.agent = agent; }

        public BigDecimal getPartEnTantQueChef() { return partEnTantQueChef; }
        public void setPartEnTantQueChef(BigDecimal partEnTantQueChef) { this.partEnTantQueChef = partEnTantQueChef; }

        public BigDecimal getPartEnTantQueSaisissant() { return partEnTantQueSaisissant; }
        public void setPartEnTantQueSaisissant(BigDecimal partEnTantQueSaisissant) { this.partEnTantQueSaisissant = partEnTantQueSaisissant; }

        public BigDecimal getPartEnTantQueDG() { return partEnTantQueDG; }
        public void setPartEnTantQueDG(BigDecimal partEnTantQueDG) { this.partEnTantQueDG = partEnTantQueDG; }

        public BigDecimal getPartEnTantQueDD() { return partEnTantQueDD; }
        public void setPartEnTantQueDD(BigDecimal partEnTantQueDD) { this.partEnTantQueDD = partEnTantQueDD; }

        public BigDecimal getPartTotaleAgent() { return partTotaleAgent; }
        public void setPartTotaleAgent(BigDecimal partTotaleAgent) { this.partTotaleAgent = partTotaleAgent; }
    }

    /**
     * ENRICHISSEMENT : Obtient les libellés des contraventions d'une affaire
     */
    private String getLibellesContraventions(Affaire affaire) {
        try {
            List<Contravention> contraventions = contraventionDAO.findByAffaireId(affaire.getId());
            return contraventions.stream()
                    .map(Contravention::getLibelle)
                    .collect(Collectors.joining(", "));
        } catch (Exception e) {
            logger.debug("Impossible de récupérer les contraventions pour l'affaire {}", affaire.getId());
            return "Non défini";
        }
    }

    /**
     * Génère l'en-tête HTML standard pour les rapports avec période
     */
    private String genererEnTeteRapport(String titre, LocalDate dateDebut, LocalDate dateFin) {
        StringBuilder html = new StringBuilder();
        html.append("""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>""").append(titre).append("""
            </title>
            <style>
                body { font-family: Arial, sans-serif; font-size: 12px; margin: 20px; }
                .header { text-align: center; margin-bottom: 30px; }
                .header h1 { font-size: 16px; font-weight: bold; margin: 5px 0; }
                .header .periode { font-size: 14px; margin: 10px 0; }
                .rapport-table { width: 100%; border-collapse: collapse; margin: 20px 0; }
                .rapport-table th, .rapport-table td { 
                    border: 1px solid #000; 
                    padding: 5px; 
                    text-align: left; 
                    vertical-align: middle;
                }
                .rapport-table th { 
                    background-color: #f0f0f0; 
                    font-weight: bold; 
                    text-align: center;
                }
                .montant { text-align: right; }
                .nombre { text-align: center; }
                .total-row { background-color: #f8f8f8; font-weight: bold; }
                .signatures { margin-top: 50px; }
                .signature-box { 
                    display: inline-block; 
                    width: 200px; 
                    text-align: center; 
                    margin: 0 20px;
                    vertical-align: top;
                }
                .signature-line { 
                    border-bottom: 1px solid #000; 
                    height: 60px; 
                    margin-bottom: 5px;
                }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>DIRECTION DE LA RÉGULATION</h1>
                <h1>""").append(titre).append("""
                </h1>
                <div class="periode">Période du """)
                .append(DateFormatter.format(dateDebut))
                .append(" au ")
                .append(DateFormatter.format(dateFin))
                .append("""
                </div>
            </div>
    """);

        return html.toString();
    }

    /**
     * Génère l'en-tête HTML simple sans période
     */
    private String genererEnTeteSimple(String titre) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>%s</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    h1 { text-align: center; margin-bottom: 5px; }
                    h2 { text-align: center; margin-top: 5px; font-size: 18px; }
                    .header-info { text-align: center; margin-bottom: 20px; }
                    table { width: 100%%; border-collapse: collapse; margin-top: 20px; }
                    th, td { border: 1px solid black; padding: 8px; text-align: left; }
                    th { background-color: #f0f0f0; font-weight: bold; }
                    .montant { text-align: right; }
                    .center { text-align: center; }
                    .total-row { background-color: #e0e0e0; font-weight: bold; }
                    .signatures { margin-top: 50px; display: flex; justify-content: space-between; }
                    .signature-box { text-align: center; width: 200px; }
                    .signature-line { border-top: 1px solid black; margin-top: 50px; }
                </style>
            </head>
            <body>
                <div class="header-info">
                    <h1>RÉPUBLIQUE DU CONGO</h1>
                    <h2>MINISTÈRE DES FINANCES ET DU BUDGET</h2>
                    <h2>DIRECTION GÉNÉRALE DES DOUANES ET DES DROITS INDIRECTS</h2>
                    <h2>%s</h2>
                    <p>Édité le : %s</p>
                </div>
            """, titre, titre, DateFormatter.format(LocalDate.now()));
    }

    /**
     * Génère la section des signatures
     */
    private String genererSignatures() {
        return """
        <div class="signatures">
            <div class="signature-box">
                <div class="signature-line"></div>
                <strong>Préparé par</strong><br/>
                <em>Agent comptable</em>
            </div>
            
            <div class="signature-box">
                <div class="signature-line"></div>
                <strong>Vérifié par</strong><br/>
                <em>Chef de service</em>
            </div>
            
            <div class="signature-box">
                <div class="signature-line"></div>
                <strong>Approuvé par</strong><br/>
                <em>Directeur</em>
            </div>
        </div>
    """;
    }

    /**
     * Récupère le chef de dossier d'une affaire
     */
    private String getChefDossier(Affaire affaire) {
        try {
            // Récupérer les acteurs de type CHEF via la table de liaison
            String sql = """
                SELECT a.* FROM agents a
                JOIN affaire_acteurs aa ON a.id = aa.agent_id
                WHERE aa.affaire_id = ? AND aa.role_sur_affaire = 'CHEF'
                LIMIT 1
            """;

            // Utiliser directement une requête SQL plutôt que getActeurs()
            List<Agent> chefs = getAgentsByAffaireAndRole(affaire.getId(), "CHEF");

            if (!chefs.isEmpty()) {
                return chefs.get(0).getNomComplet();
            }

            return "Non défini";
        } catch (Exception e) {
            logger.warn("Impossible de déterminer le chef de dossier pour l'affaire {}", affaire.getNumeroAffaire());
            return "Non défini";
        }
    }

    /**
     * Génère le rapport HTML pour l'état de répartition (Template 1)
     */
    public String genererHtmlEtatRepartition(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📊 Génération du rapport État de Répartition - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();

        // En-tête du rapport
        html.append(genererEnTeteRapport("ÉTAT DE RÉPARTITION DES AFFAIRES CONTENTIEUSES", dateDebut, dateFin));

        // Tableau principal
        html.append("""
            <table class="rapport-table">
                <thead>
                    <tr>
                        <th>N° Affaire</th>
                        <th>Date</th>
                        <th>Contrevenant</th>
                        <th>Contravention</th>
                        <th>Montant Total</th>
                        <th>Part État (60%)</th>
                        <th>Part Collectivité (40%)</th>
                        <th>Chef Dossier</th>
                        <th>Bureau</th>
                    </tr>
                </thead>
                <tbody>
        """);

        // Récupérer les encaissements de la période
        List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);

        BigDecimal totalMontant = BigDecimal.ZERO;
        BigDecimal totalEtat = BigDecimal.ZERO;
        BigDecimal totalCollectivite = BigDecimal.ZERO;

        for (Encaissement enc : encaissements) {
            Affaire affaire = enc.getAffaire();
            if (affaire == null) continue;

            // Calcul des répartitions avec l'affaire
            RepartitionResultat repartition = repartitionService.calculerRepartition(enc, affaire);

            BigDecimal montant = enc.getMontantEncaisse();
            BigDecimal partEtat = montant.multiply(POURCENTAGE_ETAT).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal partCollectivite = montant.multiply(POURCENTAGE_COLLECTIVITE).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            html.append("<tr>");
            html.append("<td>").append(affaire.getNumeroAffaire()).append("</td>");
            html.append("<td>").append(DateFormatter.format(affaire.getDateCreation())).append("</td>");
            html.append("<td>").append(affaire.getContrevenant() != null ? affaire.getContrevenant().getNomComplet() : "").append("</td>");
            html.append("<td>").append(getContraventionLibelle(affaire)).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(montant)).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(partEtat)).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(partCollectivite)).append("</td>");
            html.append("<td>").append(getChefDossier(affaire)).append("</td>");
            html.append("<td>").append(affaire.getBureau() != null ? affaire.getBureau().getNomBureau() : "").append("</td>");
            html.append("</tr>");

            totalMontant = totalMontant.add(montant);
            totalEtat = totalEtat.add(partEtat);
            totalCollectivite = totalCollectivite.add(partCollectivite);
        }

        // Totaux
        html.append("""
            <tr class="total-row">
                <td colspan="4"><strong>TOTAL</strong></td>
                <td class="montant"><strong>""").append(CurrencyFormatter.format(totalMontant)).append("""
                </strong></td>
                <td class="montant"><strong>""").append(CurrencyFormatter.format(totalEtat)).append("""
                </strong></td>
                <td class="montant"><strong>""").append(CurrencyFormatter.format(totalCollectivite)).append("""
                </strong></td>
                <td colspan="2"></td>
            </tr>
        """);

        html.append("</tbody></table>");
        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Récupère le libellé de la contravention d'une affaire
     */
    private String getContraventionLibelle(Affaire affaire) {
        // Gérer le cas où il y a plusieurs contraventions
        if (affaire.getContraventions() != null && !affaire.getContraventions().isEmpty()) {
            return affaire.getContraventions().stream()
                    .map(Contravention::getLibelle)
                    .collect(Collectors.joining(", "));
        }

        // Si pas de liste mais un ID de contravention
        if (affaire.getContraventionId() != null) {
            // TODO: Charger la contravention depuis le DAO si nécessaire
            return "Contravention #" + affaire.getContraventionId();
        }

        return "";
    }

    /**
     * Récupère les contraventions d'une affaire
     */
    private String getContraventionsAffaire(Affaire affaire) {
        return getContraventionLibelle(affaire);
    }

    /**
     * Récupère les encaissements d'un service
     */
    private List<Encaissement> getEncaissementsByService(Service service, LocalDate dateDebut, LocalDate dateFin) {
        List<Encaissement> result = new ArrayList<>();
        List<Encaissement> allEncaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);

        for (Encaissement enc : allEncaissements) {
            if (enc.getAffaire() != null && enc.getAffaire().getService() != null &&
                    enc.getAffaire().getService().getId().equals(service.getId())) {
                result.add(enc);
            }
        }

        return result;
    }

    /**
     * Calcule le montant pour un centre
     */
    private BigDecimal calculerMontantCentre(Centre centre, LocalDate dateDebut, LocalDate dateFin, boolean indicateurFictif) {
        BigDecimal total = BigDecimal.ZERO;

        // Récupérer les affaires du centre pour la période
        // Utiliser la méthode existante findByPeriod avec filtrage
        List<Affaire> affaires = affaireDAO.findByPeriod(dateDebut, dateFin);

        for (Affaire affaire : affaires) {
            // Vérifier si l'affaire appartient au centre via le service/bureau
            if (affaire.getService() != null &&
                    affaire.getService().getCentre() != null &&
                    affaire.getService().getCentre().getId().equals(centre.getId())) {

                List<Encaissement> encaissements = encaissementDAO.findByAffaireId(affaire.getId());
                for (Encaissement enc : encaissements) {
                    if (enc.getStatut() == StatutEncaissement.VALIDE &&
                            !enc.getDateEncaissement().isBefore(dateDebut) &&
                            !enc.getDateEncaissement().isAfter(dateFin)) {

                        RepartitionResultat repartition = repartitionService.calculerRepartition(enc, affaire);

                        if (indicateurFictif) {
                            total = total.add(repartition.getPartIndicateur());
                        } else {
                            total = total.add(repartition.getProduitNet());
                        }
                    }
                }
            }
        }

        return total;
    }

    /**
     * Calcule les statistiques d'un service
     */
    private ServiceStatsDTO calculerStatsService(Service service, LocalDate dateDebut, LocalDate dateFin) {
        ServiceStatsDTO stats = new ServiceStatsDTO();
        stats.setService(service);

        String sql = """
        SELECT 
            COUNT(DISTINCT a.id) as nombre_affaires,
            COALESCE(SUM(a.montant_amende_total), 0) as montant_total,
            COUNT(DISTINCT e.id) as nombre_encaissements,
            COALESCE(SUM(e.montant_encaisse), 0) as montant_encaisse
        FROM affaires a
        LEFT JOIN encaissements e ON a.id = e.affaire_id 
            AND e.date_encaissement BETWEEN ? AND ?
        WHERE a.service_id = ?
        AND a.date_creation BETWEEN ? AND ?
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(dateDebut));
            stmt.setDate(2, Date.valueOf(dateFin));
            stmt.setLong(3, service.getId());
            stmt.setDate(4, Date.valueOf(dateDebut));
            stmt.setDate(5, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                stats.setNombreAffaires(rs.getInt("nombre_affaires"));
                stats.setMontantTotal(rs.getBigDecimal("montant_total"));
                stats.setNombreEncaissements(rs.getInt("nombre_encaissements"));
                stats.setMontantEncaisse(rs.getBigDecimal("montant_encaisse"));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du calcul des stats service: {}", service.getId(), e);
        }

        return stats;
    }

    /**
     * Calcule les statistiques d'un agent
     */
    private AgentStatsDTO calculerStatsAgent(Agent agent, LocalDate dateDebut, LocalDate dateFin) {
        AgentStatsDTO stats = new AgentStatsDTO();
        stats.setNombreAffaires(0);
        stats.setMontantTotal(BigDecimal.ZERO);
        stats.setObservations("");

        // Récupérer les affaires où l'agent est impliqué via la table de liaison
        List<Affaire> affaires = getAffairesByAgentAndPeriod(agent.getId(), dateDebut, dateFin);

        for (Affaire affaire : affaires) {
            stats.setNombreAffaires(stats.getNombreAffaires() + 1);

            // Calculer le montant en fonction du rôle
            List<Encaissement> encaissements = encaissementDAO.findByAffaireId(affaire.getId());
            for (Encaissement enc : encaissements) {
                if (enc.getStatut() == StatutEncaissement.VALIDE &&
                        !enc.getDateEncaissement().isBefore(dateDebut) &&
                        !enc.getDateEncaissement().isAfter(dateFin)) {

                    RepartitionResultat repartition = repartitionService.calculerRepartition(enc, affaire);

                    // Ajouter la part de l'agent selon son rôle
                    BigDecimal partAgent = getPartAgentFromRepartition(repartition, agent.getId());
                    if (partAgent != null) {
                        stats.setMontantTotal(stats.getMontantTotal().add(partAgent));
                    }
                }
            }
        }

        return stats;
    }

    public String genererEtatRepartitionAffaires(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération de l'état de répartition des affaires - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteRapport("ÉTAT DE RÉPARTITION DES AFFAIRES CONTENTIEUSES", dateDebut, dateFin));

        // Template exact selon le cahier des charges
        html.append("""
        <table class="rapport-table">
            <thead>
                <tr>
                    <th>N° encaissement et Date</th>
                    <th>N° Affaire et Date</th>
                    <th>Produit disponible</th>
                    <th>Direction Départementale</th>
                    <th>Indicateur</th>
                    <th>Produit net</th>
                    <th>FLCF</th>
                    <th>Trésor</th>
                    <th>Produit net ayants droits</th>
                    <th>Chefs</th>
                    <th>Saisissants</th>
                    <th>Mutuelle nationale</th>
                    <th>Masse commune</th>
                    <th>Intéressement</th>
                </tr>
            </thead>
            <tbody>
    """);

        // Récupération et traitement des données
        List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);
        BigDecimal totalProduitDisponible = BigDecimal.ZERO;
        BigDecimal totalDD = BigDecimal.ZERO;
        BigDecimal totalIndicateur = BigDecimal.ZERO;
        BigDecimal totalProduitNet = BigDecimal.ZERO;
        BigDecimal totalFLCF = BigDecimal.ZERO;
        BigDecimal totalTresor = BigDecimal.ZERO;
        BigDecimal totalProduitNetAD = BigDecimal.ZERO;
        BigDecimal totalChefs = BigDecimal.ZERO;
        BigDecimal totalSaisissants = BigDecimal.ZERO;
        BigDecimal totalMutuelle = BigDecimal.ZERO;
        BigDecimal totalMasseCommune = BigDecimal.ZERO;
        BigDecimal totalInteressement = BigDecimal.ZERO;

        for (Encaissement enc : encaissements) {
            if (enc.getStatut() != StatutEncaissement.VALIDE || enc.getAffaire() == null) continue;

            RepartitionResultat repartition = repartitionService.calculerRepartition(enc, enc.getAffaire());

            html.append("<tr>");
            html.append("<td>").append(enc.getReference()).append("<br/>")
                    .append(DateFormatter.format(enc.getDateEncaissement())).append("</td>");
            html.append("<td>").append(enc.getAffaire().getNumeroAffaire()).append("<br/>")
                    .append(DateFormatter.format(enc.getAffaire().getDateCreation())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getProduitDisponible())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartDD())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartIndicateur())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getProduitNet())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartFlcf())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartTresor())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getProduitNetDroits())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartChefs())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartSaisissants())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartMutuelle())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartMasseCommune())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartInteressement())).append("</td>");
            html.append("</tr>");

            // Cumuls pour totaux
            totalProduitDisponible = totalProduitDisponible.add(repartition.getProduitDisponible());
            totalDD = totalDD.add(repartition.getPartDD());
            totalIndicateur = totalIndicateur.add(repartition.getPartIndicateur());
            totalProduitNet = totalProduitNet.add(repartition.getProduitNet());
            totalFLCF = totalFLCF.add(repartition.getPartFlcf());
            totalTresor = totalTresor.add(repartition.getPartTresor());
            totalProduitNetAD = totalProduitNetAD.add(repartition.getProduitNetDroits());
            totalChefs = totalChefs.add(repartition.getPartChefs());
            totalSaisissants = totalSaisissants.add(repartition.getPartSaisissants());
            totalMutuelle = totalMutuelle.add(repartition.getPartMutuelle());
            totalMasseCommune = totalMasseCommune.add(repartition.getPartMasseCommune());
            totalInteressement = totalInteressement.add(repartition.getPartInteressement());
        }

        // Ligne de totaux
        html.append("""
        <tr class="total-row">
            <td colspan="2"><strong>TOTAUX</strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalProduitDisponible)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalDD)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalIndicateur)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalProduitNet)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalFLCF)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalTresor)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalProduitNetAD)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalChefs)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalSaisissants)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalMutuelle)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalMasseCommune)).append("""
            </strong></td>
            <td class="montant"><strong>""").append(CurrencyFormatter.format(totalInteressement)).append("""
            </strong></td>
        </tr>
    """);

        html.append("</tbody></table>");
        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Calcule la part d'un centre pour une période donnée
     *
     * @param centre le centre concerné
     * @param dateDebut date de début de la période
     * @param dateFin date de fin de la période
     * @param indicateurFictif true pour la part indicateur fictif, false pour la part base
     * @return le montant calculé pour ce centre
     */
    private BigDecimal calculerPartCentre(Centre centre, LocalDate dateDebut, LocalDate dateFin, boolean indicateurFictif) {
        if (centre == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;

        try {
            // Récupérer tous les services de ce centre
            List<Service> services = serviceDAO.findByCentreId(centre.getId());

            if (services.isEmpty()) {
                return BigDecimal.ZERO;
            }

            // Récupérer les affaires de la période pour ce centre
            List<Affaire> affaires = affaireDAO.findByPeriod(dateDebut, dateFin);

            for (Affaire affaire : affaires) {
                if (affaire.getService() != null &&
                        services.stream().anyMatch(s -> s.getId().equals(affaire.getService().getId()))) {

                    // Récupérer les encaissements validés de cette affaire
                    List<Encaissement> encaissements = encaissementDAO.findByAffaireId(affaire.getId());

                    for (Encaissement enc : encaissements) {
                        if (enc.getStatut() == StatutEncaissement.VALIDE &&
                                !enc.getDateEncaissement().isBefore(dateDebut) &&
                                !enc.getDateEncaissement().isAfter(dateFin)) {

                            // Calculer la répartition pour cet encaissement
                            RepartitionResultat repartition = repartitionService.calculerRepartition(enc, affaire);

                            if (indicateurFictif) {
                                // Part indicateur fictif
                                total = total.add(repartition.getPartIndicateur());
                            } else {
                                // Part base (produit net)
                                total = total.add(repartition.getProduitNet());
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Erreur lors du calcul de la part du centre {} : {}",
                    centre.getNomCentre(), e.getMessage());
            return BigDecimal.ZERO;
        }

        return total;
    }

    /**
     * Détermine le rôle principal d'un agent
     */
    private String determinerRoleAgent(Agent agent) {
        if (agent.getRoleSpecial() != null && !agent.getRoleSpecial().isEmpty()) {
            return agent.getRoleSpecial();
        }
        return "Agent";
    }

    /**
     * Récupère les agents d'une affaire par rôle
     */
    private List<Agent> getAgentsByAffaireAndRole(Long affaireId, String role) {
        List<Agent> agents = new ArrayList<>();

        String sql = """
            SELECT a.* FROM agents a
            JOIN affaire_acteurs aa ON a.id = aa.agent_id
            WHERE aa.affaire_id = ? AND aa.role_sur_affaire = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            stmt.setString(2, role);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Agent agent = new Agent();
                agent.setId(rs.getLong("id"));
                agent.setCodeAgent(rs.getString("code_agent"));
                agent.setNom(rs.getString("nom"));
                agent.setPrenom(rs.getString("prenom"));
                agent.setGrade(rs.getString("grade"));
                agent.setActif(rs.getBoolean("actif"));
                agents.add(agent);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des agents pour l'affaire {} avec rôle {}", affaireId, role, e);
        }

        return agents;
    }

    /**
     * Récupère les affaires d'un agent pour une période
     */
    private List<Affaire> getAffairesByAgentAndPeriod(Long agentId, LocalDate dateDebut, LocalDate dateFin) {
        List<Affaire> affaires = new ArrayList<>();

        String sql = """
            SELECT DISTINCT a.* FROM affaires a
            JOIN affaire_acteurs aa ON a.id = aa.affaire_id
            WHERE aa.agent_id = ? 
            AND a.date_creation BETWEEN ? AND ?
            AND a.deleted = false
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, agentId);
            stmt.setDate(2, Date.valueOf(dateDebut));
            stmt.setDate(3, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                // Utiliser la méthode findById du DAO pour charger l'affaire complète
                Long affaireId = rs.getLong("id");
                affaireDAO.findById(affaireId).ifPresent(affaires::add);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des affaires pour l'agent {} sur la période", agentId, e);
        }

        return affaires;
    }

    /**
     * Récupère la part d'un agent depuis une répartition
     */
    private BigDecimal getPartAgentFromRepartition(RepartitionResultat repartition, Long agentId) {
        // Parcourir les parts individuelles
        for (RepartitionResultat.PartIndividuelle part : repartition.getPartsIndividuelles()) {
            if (part.getAgent() != null && part.getAgent().getId().equals(agentId)) {
                return part.getMontant();
            }
        }

        // Vérifier aussi les bénéficiaires génériques (DD/DG)
        // TODO: Implémenter si nécessaire

        return null;
    }

    /**
     * Génère le HTML pour les encaissements
     */
    public String genererHtmlEncaissements(RapportEncaissementsDTO rapport) {
        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteRapport("RAPPORT DES ENCAISSEMENTS", rapport.getDateDebut(), rapport.getDateFin()));

        for (ServiceEncaissementDTO service : rapport.getServices()) {
            html.append("<h3>Service : ").append(service.getNomService()).append("</h3>");
            html.append("""
                <table class="rapport-table">
                    <thead>
                        <tr>
                            <th>N° Encaissement</th>
                            <th>Date</th>
                            <th>N° Affaire</th>
                            <th>Montant</th>
                            <th>Part Indicateur</th>
                        </tr>
                    </thead>
                    <tbody>
            """);

            for (DetailEncaissementDTO detail : service.getEncaissements()) {
                html.append("<tr>");
                html.append("<td>").append(detail.getNumeroEncaissement()).append("</td>");
                html.append("<td>").append(DateFormatter.format(detail.getDateEncaissement())).append("</td>");
                html.append("<td>").append(detail.getNumeroAffaire()).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(detail.getMontant())).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(detail.getPartIndicateur())).append("</td>");
                html.append("</tr>");
            }

            html.append("""
                <tr class="total-row">
                    <td colspan="3"><strong>Total Service</strong></td>
                    <td class="montant"><strong>""").append(CurrencyFormatter.format(service.getTotalEncaisse())).append("""
                    </strong></td>
                    <td></td>
                </tr>
            """);

            html.append("</tbody></table><br/>");
        }

        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Génère le HTML pour les affaires non soldées
     */
    public String genererHtmlAffairesNonSoldees(RapportAffairesNonSoldeesDTO rapport) {
        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteSimple("RAPPORT DES AFFAIRES NON SOLDÉES"));

        html.append("""
            <table class="rapport-table">
                <thead>
                    <tr>
                        <th>N° Affaire</th>
                        <th>Date Création</th>
                        <th>Contrevenant</th>
                        <th>Montant Total</th>
                        <th>Montant Encaissé</th>
                        <th>Solde Restant</th>
                    </tr>
                </thead>
                <tbody>
        """);

        for (AffaireNonSoldeeDTO affaire : rapport.getAffaires()) {
            html.append("<tr>");
            html.append("<td>").append(affaire.getNumeroAffaire()).append("</td>");
            html.append("<td>").append(DateFormatter.format(affaire.getDateCreation())).append("</td>");
            html.append("<td>").append(affaire.getContrevenant()).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getMontantTotal())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getMontantEncaisse())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getSoldeRestant())).append("</td>");
            html.append("</tr>");
        }

        // Totaux
        html.append("""
            <tr class="total-row">
                <td colspan="3"><strong>TOTAL</strong></td>
                <td class="montant"><strong>""").append(CurrencyFormatter.format(rapport.getTotalMontant())).append("""
                </strong></td>
                <td class="montant"><strong>""").append(CurrencyFormatter.format(rapport.getTotalEncaisse())).append("""
                </strong></td>
                <td class="montant"><strong>""").append(CurrencyFormatter.format(rapport.getTotalSolde())).append("""
                </strong></td>
            </tr>
        """);

        html.append("</tbody></table>");
        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Récupère la période en cours
     */
    public String getPeriodeEnCours() {
        // Format: "Janvier 2024"
        LocalDate now = LocalDate.now();
        String mois = now.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.FRENCH);
        String annee = String.valueOf(now.getYear());
        return mois.substring(0, 1).toUpperCase() + mois.substring(1) + " " + annee;
    }

    /**
     * Récupère la date/heure actuelle formatée
     */
    public String getCurrentDateTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return now.format(formatter);
    }

    /**
     * Formate un LocalDateTime en LocalTime (correction du bug ligne 1362)
     */
    private LocalTime convertToLocalTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toLocalTime() : LocalTime.now();
    }

    // ==================== CLASSES DTO INTERNES ====================

    /**
     * DTO pour le rapport de répartition principal
     * ENRICHI avec les méthodes manquantes
     */
    public static class RapportRepartitionDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle; // AJOUT pour corriger le bug
        private List<AffaireRepartitionDTO> affaires = new ArrayList<>();
        private BigDecimal totalEncaisse = BigDecimal.ZERO;
        private BigDecimal totalEtat = BigDecimal.ZERO;
        private BigDecimal totalCollectivite = BigDecimal.ZERO;
        private int nombreAffaires = 0;

        public void calculateTotaux() {
            totalEncaisse = affaires.stream()
                    .map(AffaireRepartitionDTO::getMontantEncaisse)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalEtat = affaires.stream()
                    .map(AffaireRepartitionDTO::getPartEtat)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalCollectivite = affaires.stream()
                    .map(AffaireRepartitionDTO::getPartCollectivite)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            nombreAffaires = affaires.size();
        }

        // Getters et setters existants
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public List<AffaireRepartitionDTO> getAffaires() { return affaires; }
        public void setAffaires(List<AffaireRepartitionDTO> affaires) { this.affaires = affaires; }

        public BigDecimal getTotalEncaisse() { return totalEncaisse; }
        public void setTotalEncaisse(BigDecimal totalEncaisse) { this.totalEncaisse = totalEncaisse; }

        public BigDecimal getTotalEtat() { return totalEtat; }
        public void setTotalEtat(BigDecimal totalEtat) { this.totalEtat = totalEtat; }

        public BigDecimal getTotalCollectivite() { return totalCollectivite; }
        public void setTotalCollectivite(BigDecimal totalCollectivite) { this.totalCollectivite = totalCollectivite; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }

        // NOUVELLES MÉTHODES pour corriger les bugs
        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        // Méthodes aliases pour la compatibilité
        public BigDecimal getTotalPartEtat() { return totalEtat; }
        public BigDecimal getTotalPartCollectivite() { return totalCollectivite; }
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
        private String contrevenant;
        private BigDecimal montantTotal;

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
    }

    /**
     * DTO pour le rapport des encaissements
     */
    public static class RapportEncaissementsDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private List<ServiceEncaissementDTO> services = new ArrayList<>();
        private BigDecimal totalGeneral = BigDecimal.ZERO;

        // Getters et setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public List<ServiceEncaissementDTO> getServices() { return services; }
        public void setServices(List<ServiceEncaissementDTO> services) { this.services = services; }

        public BigDecimal getTotalGeneral() { return totalGeneral; }
        public void setTotalGeneral(BigDecimal totalGeneral) { this.totalGeneral = totalGeneral; }
    }

    /**
     * DTO pour les encaissements par service
     */
    public static class ServiceEncaissementDTO {
        private String nomService;
        private List<DetailEncaissementDTO> encaissements = new ArrayList<>();
        private BigDecimal totalEncaisse = BigDecimal.ZERO;
        private int nombreEncaissements = 0;

        // Getters et setters
        public String getNomService() { return nomService; }
        public void setNomService(String nomService) { this.nomService = nomService; }

        public List<DetailEncaissementDTO> getEncaissements() { return encaissements; }
        public void setEncaissements(List<DetailEncaissementDTO> encaissements) { this.encaissements = encaissements; }

        public BigDecimal getTotalEncaisse() { return totalEncaisse; }
        public void setTotalEncaisse(BigDecimal totalEncaisse) { this.totalEncaisse = totalEncaisse; }

        public int getNombreEncaissements() { return nombreEncaissements; }
        public void setNombreEncaissements(int nombreEncaissements) { this.nombreEncaissements = nombreEncaissements; }
    }

    /**
     * DTO pour le détail d'un encaissement
     */
    public static class DetailEncaissementDTO {
        private String numeroEncaissement;
        private LocalDate dateEncaissement;
        private String numeroAffaire;
        private BigDecimal montant;
        private BigDecimal partIndicateur;

        // Getters et setters
        public String getNumeroEncaissement() { return numeroEncaissement; }
        public void setNumeroEncaissement(String numeroEncaissement) { this.numeroEncaissement = numeroEncaissement; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public BigDecimal getMontant() { return montant; }
        public void setMontant(BigDecimal montant) { this.montant = montant; }

        public BigDecimal getPartIndicateur() { return partIndicateur; }
        public void setPartIndicateur(BigDecimal partIndicateur) { this.partIndicateur = partIndicateur; }
    }

    /**
     * DTO pour les affaires non soldées
     */
    public static class RapportAffairesNonSoldeesDTO {
        private LocalDate dateGeneration;
        private List<AffaireNonSoldeeDTO> affaires = new ArrayList<>();
        private BigDecimal totalMontant = BigDecimal.ZERO;
        private BigDecimal totalEncaisse = BigDecimal.ZERO;
        private BigDecimal totalSolde = BigDecimal.ZERO;

        public void calculateTotaux() {
            totalMontant = affaires.stream()
                    .map(AffaireNonSoldeeDTO::getMontantTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalEncaisse = affaires.stream()
                    .map(AffaireNonSoldeeDTO::getMontantEncaisse)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalSolde = affaires.stream()
                    .map(AffaireNonSoldeeDTO::getSoldeRestant)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Getters et setters
        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public List<AffaireNonSoldeeDTO> getAffaires() { return affaires; }
        public void setAffaires(List<AffaireNonSoldeeDTO> affaires) { this.affaires = affaires; }

        public BigDecimal getTotalMontant() { return totalMontant; }
        public void setTotalMontant(BigDecimal totalMontant) { this.totalMontant = totalMontant; }

        public BigDecimal getTotalEncaisse() { return totalEncaisse; }
        public void setTotalEncaisse(BigDecimal totalEncaisse) { this.totalEncaisse = totalEncaisse; }

        public BigDecimal getTotalSolde() { return totalSolde; }
        public void setTotalSolde(BigDecimal totalSolde) { this.totalSolde = totalSolde; }
    }

    /**
     * DTO pour une affaire non soldée
     */
    public static class AffaireNonSoldeeDTO {
        private String numeroAffaire;
        private LocalDate dateCreation;
        private String contrevenant;
        private BigDecimal montantTotal;
        private BigDecimal montantEncaisse;
        private BigDecimal soldeRestant;

        // Getters et setters
        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public LocalDate getDateCreation() { return dateCreation; }
        public void setDateCreation(LocalDate dateCreation) { this.dateCreation = dateCreation; }

        public String getContrevenant() { return contrevenant; }
        public void setContrevenant(String contrevenant) { this.contrevenant = contrevenant; }

        public BigDecimal getMontantTotal() { return montantTotal; }
        public void setMontantTotal(BigDecimal montantTotal) { this.montantTotal = montantTotal; }

        public BigDecimal getMontantEncaisse() { return montantEncaisse; }
        public void setMontantEncaisse(BigDecimal montantEncaisse) { this.montantEncaisse = montantEncaisse; }

        public BigDecimal getSoldeRestant() { return soldeRestant; }
        public void setSoldeRestant(BigDecimal soldeRestant) { this.soldeRestant = soldeRestant; }
    }

    /**
     * DTO pour les statistiques d'un service
     */
    public static class ServiceStatsDTO {
        private Service service;
        private int nombreAffaires;
        private BigDecimal montantTotal = BigDecimal.ZERO;
        private int nombreEncaissements;
        private BigDecimal montantEncaisse = BigDecimal.ZERO;
        private String observations;

        // Getters et Setters complets
        public Service getService() { return service; }
        public void setService(Service service) { this.service = service; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }

        public BigDecimal getMontantTotal() { return montantTotal; }
        public void setMontantTotal(BigDecimal montantTotal) { this.montantTotal = montantTotal; }

        public int getNombreEncaissements() { return nombreEncaissements; }
        public void setNombreEncaissements(int nombreEncaissements) { this.nombreEncaissements = nombreEncaissements; }

        public BigDecimal getMontantEncaisse() { return montantEncaisse; }
        public void setMontantEncaisse(BigDecimal montantEncaisse) { this.montantEncaisse = montantEncaisse; }

        public String getObservations() { return observations; }
        public void setObservations(String observations) { this.observations = observations; }
    }

    /**
     * DTO pour les statistiques d'un agent
     */
    public static class AgentStatsDTO {
        private int nombreAffaires;
        private BigDecimal montantTotal;
        private String observations;

        // Getters et setters
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
     * DTO pour une ligne de mandatement - CORRIGÉ : sans setObservations
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
     * DTO pour un centre de répartition - CORRIGÉ avec les bonnes propriétés
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
        public void setPartRepartitionBase(BigDecimal partRepartitionBase) {
            this.partRepartitionBase = partRepartitionBase;
        }

        public BigDecimal getPartRepartitionIndicateurFictif() { return partRepartitionIndicateurFictif; }
        public void setPartRepartitionIndicateurFictif(BigDecimal partRepartitionIndicateurFictif) {
            this.partRepartitionIndicateurFictif = partRepartitionIndicateurFictif;
        }

        public BigDecimal getPartTotaleCentre() { return partTotaleCentre; }
        public void setPartTotaleCentre(BigDecimal partTotaleCentre) {
            this.partTotaleCentre = partTotaleCentre;
        }

        // Méthodes alternatives pour la compatibilité
        public void setRepartitionBase(BigDecimal value) {
            this.partRepartitionBase = value;
        }

        public void setRepartitionIndicateurFictif(BigDecimal value) {
            this.partRepartitionIndicateurFictif = value;
        }

        public void setPartCentre(BigDecimal value) {
            this.partTotaleCentre = value;
        }

        public BigDecimal getPartCentre() {
            return this.partTotaleCentre;
        }
    }
}