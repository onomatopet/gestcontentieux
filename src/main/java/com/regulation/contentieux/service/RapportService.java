package com.regulation.contentieux.service;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.dao.*;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.*;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Génère le tableau des amendes par services
     * NOUVELLE MÉTHODE pour corriger le bug
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
        logger.info("📄 Génération de l'état de mandatement - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteRapport("ÉTAT DE MANDATEMENT DES AYANTS-DROITS", dateDebut, dateFin));

        // Tableau principal
        html.append("""
            <table class="rapport-table">
                <thead>
                    <tr>
                        <th rowspan="2">N° Encaissement<br/>et Date</th>
                        <th rowspan="2">N° Affaire<br/>et Date</th>
                        <th rowspan="2">Produit Net<br/>Ayants Droits</th>
                        <th colspan="7">RÉPARTITION</th>
                    </tr>
                    <tr>
                        <th>Part<br/>Chefs</th>
                        <th>Part<br/>Saisissants</th>
                        <th>Mutuelle<br/>Nationale</th>
                        <th>Masse<br/>Commune</th>
                        <th>Intéressement</th>
                        <th>DG</th>
                        <th>DD</th>
                    </tr>
                </thead>
                <tbody>
        """);

        // Récupérer et afficher les données
        List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);

        for (Encaissement enc : encaissements) {
            if (enc.getStatut() != StatutEncaissement.VALIDE || enc.getAffaire() == null) continue;

            RepartitionResultat repartition = repartitionService.calculerRepartition(enc, enc.getAffaire());

            html.append("<tr>");
            html.append("<td>").append(enc.getReference()).append("<br/>")
                    .append(DateFormatter.format(enc.getDateEncaissement())).append("</td>");
            html.append("<td>").append(enc.getAffaire().getNumeroAffaire()).append("<br/>")
                    .append(DateFormatter.format(enc.getAffaire().getDateCreation())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getProduitNet())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartChefs())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartSaisissants())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartMutuelle())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartMasseCommune())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartInteressement())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartDG())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartDD())).append("</td>");
            html.append("</tr>");
        }

        html.append("</tbody></table>");
        html.append(genererSignatures());
        html.append("</body></html>");

        return html.toString();
    }

    // ==================== TEMPLATE 3: ÉTAT PAR CENTRE DE RÉPARTITION ====================

    /**
     * Génère l'état par centre de répartition (Template 3)
     */
    public String genererEtatCentreRepartition(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("🏢 Génération de l'état par centre - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteRapport("ÉTAT PAR CENTRE DE RÉPARTITION", dateDebut, dateFin));

        html.append("""
            <table class="rapport-table">
                <thead>
                    <tr>
                        <th>Centre</th>
                        <th>Part Répartition Base</th>
                        <th>Part Répartition Indicateur Fictif</th>
                        <th>Total Part Centre</th>
                    </tr>
                </thead>
                <tbody>
        """);

        // Récupérer les centres et calculer leurs parts
        List<Centre> centres = centreDAO.findAll();
        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalFictif = BigDecimal.ZERO;
        BigDecimal totalCentre = BigDecimal.ZERO;

        for (Centre centre : centres) {
            BigDecimal partBase = calculerPartCentre(centre, dateDebut, dateFin, false);
            BigDecimal partFictif = calculerPartCentre(centre, dateDebut, dateFin, true);
            BigDecimal partTotal = partBase.add(partFictif);

            html.append("<tr>");
            html.append("<td>").append(centre.getNomCentre()).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(partBase)).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(partFictif)).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(partTotal)).append("</td>");
            html.append("</tr>");

            totalBase = totalBase.add(partBase);
            totalFictif = totalFictif.add(partFictif);
            totalCentre = totalCentre.add(partTotal);
        }

        // Totaux
        html.append("""
            <tr class="total-row">
                <td><strong>TOTAL</strong></td>
                <td class="montant"><strong>""").append(CurrencyFormatter.format(totalBase)).append("""
                </strong></td>
                <td class="montant"><strong>""").append(CurrencyFormatter.format(totalFictif)).append("""
                </strong></td>
                <td class="montant"><strong>""").append(CurrencyFormatter.format(totalCentre)).append("""
                </strong></td>
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

    // ==================== TEMPLATE 5: ÉTAT DE RÉPARTITION PAR SERVICE ====================

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
     * Génère l'état de mandatement par agent (Template 8)
     */
    public String genererEtatMandatementAgents(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("👥 Génération de l'état de mandatement des agents - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteRapport("ÉTAT DE MANDATEMENT DES AGENTS", dateDebut, dateFin));

        html.append("""
            <table class="rapport-table">
                <thead>
                    <tr>
                        <th>Agent</th>
                        <th>Matricule</th>
                        <th>Service</th>
                        <th>Nombre d'affaires</th>
                        <th>Montant total</th>
                        <th>Part agent</th>
                    </tr>
                </thead>
                <tbody>
        """);

        List<Agent> agents = agentDAO.findAllActifs();
        BigDecimal totalGeneral = BigDecimal.ZERO;
        BigDecimal totalPartAgents = BigDecimal.ZERO;
        int totalAffaires = 0;

        for (Agent agent : agents) {
            AgentStatsDTO stats = calculerStatsAgent(agent, dateDebut, dateFin);

            if (stats.getNombreAffaires() > 0) {
                html.append("<tr>");
                html.append("<td>").append(agent.getNomComplet()).append("</td>");
                html.append("<td>").append(agent.getMatricule()).append("</td>");
                html.append("<td>").append(agent.getService() != null ? agent.getService().getNomService() : "").append("</td>");
                html.append("<td class='center'>").append(stats.getNombreAffaires()).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(stats.getMontantTotal())).append("</td>");
                // Part agent calculée comme un pourcentage
                BigDecimal partAgent = stats.getMontantTotal().multiply(new BigDecimal("0.10")); // 10% exemple
                html.append("<td class='montant'>").append(CurrencyFormatter.format(partAgent)).append("</td>");
                html.append("</tr>");

                totalGeneral = totalGeneral.add(stats.getMontantTotal());
                totalPartAgents = totalPartAgents.add(partAgent);
                totalAffaires += stats.getNombreAffaires();
            }
        }

        // Total général
        html.append("""
            <tr class="total-row">
                <td colspan="3"><strong>TOTAL GÉNÉRAL</strong></td>
                <td class="center"><strong>""").append(totalAffaires).append("""
                </strong></td>
                <td class="montant"><strong>""").append(CurrencyFormatter.format(totalGeneral)).append("""
                </strong></td>
                <td class="montant"><strong>""").append(CurrencyFormatter.format(totalPartAgents)).append("""
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
     * Génère l'en-tête HTML standard pour les rapports avec période
     */
    private String genererEnTeteRapport(String titre, LocalDate dateDebut, LocalDate dateFin) {
        String periode = DateFormatter.format(dateDebut) + " au " + DateFormatter.format(dateFin);

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
                    h3 { margin-top: 20px; }
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
                    @media print {
                        body { margin: 10px; }
                        .no-print { display: none; }
                    }
                </style>
            </head>
            <body>
                <div class="header-info">
                    <h1>RÉPUBLIQUE DU CONGO</h1>
                    <h2>MINISTÈRE DES FINANCES ET DU BUDGET</h2>
                    <h2>DIRECTION GÉNÉRALE DES DOUANES ET DES DROITS INDIRECTS</h2>
                    <h2>%s</h2>
                    <p><strong>Période : %s</strong></p>
                    <p>Édité le : %s</p>
                </div>
            """, titre, titre, periode, DateFormatter.format(LocalDate.now()));
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
                    <p>Le Directeur Départemental</p>
                    <div class="signature-line"></div>
                </div>
                <div class="signature-box">
                    <p>Le Directeur Général</p>
                    <div class="signature-line"></div>
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
        stats.setNombreAffaires(0);
        stats.setMontantTotal(BigDecimal.ZERO);
        stats.setObservations("");

        List<Affaire> affaires = affaireDAO.findByPeriod(dateDebut, dateFin);

        for (Affaire affaire : affaires) {
            if (affaire.getService() != null && affaire.getService().getId().equals(service.getId())) {
                stats.setNombreAffaires(stats.getNombreAffaires() + 1);
                stats.setMontantTotal(stats.getMontantTotal().add(affaire.getMontantAmendeTotal()));
            }
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