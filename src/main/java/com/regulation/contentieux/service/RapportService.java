package com.regulation.contentieux.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.regulation.contentieux.dao.*;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.*;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import java.util.List;
import java.util.Map;

import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.dao.EncaissementDAO;
import com.regulation.contentieux.dao.AgentDAO;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Encaissement;
import com.regulation.contentieux.model.enums.StatutAffaire;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import com.regulation.contentieux.util.CurrencyFormatter;
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

    // ==================== RAPPORT PRINCIPAL DE RÉTROCESSION ====================

    // ==================== TEMPLATE 1: ÉTAT DE RÉPARTITION DES AFFAIRES CONTENTIEUSES ====================

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
                        <th>N° encaissement<br/>et Date</th>
                        <th>N° Affaire<br/>et Date</th>
                        <th>Produit<br/>disponible</th>
                        <th>Direction<br/>Départementale</th>
                        <th>Indicateur</th>
                        <th>Produit net</th>
                        <th>FLCF</th>
                        <th>Trésor</th>
                        <th>Produit net<br/>ayants droits</th>
                        <th>Chefs</th>
                        <th>Saisissants</th>
                        <th>Mutuelle<br/>nationale</th>
                        <th>Masse<br/>commune</th>
                        <th>Intéressement</th>
                    </tr>
                </thead>
                <tbody>
        """);

        // Récupérer les données
        List<Encaissement> encaissements = encaissementDAO.findByPeriode(dateDebut, dateFin);
        BigDecimal totalProduitDisponible = BigDecimal.ZERO;
        BigDecimal totalDD = BigDecimal.ZERO;
        BigDecimal totalIndicateur = BigDecimal.ZERO;
        BigDecimal totalProduitNet = BigDecimal.ZERO;
        BigDecimal totalFLCF = BigDecimal.ZERO;
        BigDecimal totalTresor = BigDecimal.ZERO;
        BigDecimal totalProduitNetAyantsDroits = BigDecimal.ZERO;
        BigDecimal totalChefs = BigDecimal.ZERO;
        BigDecimal totalSaisissants = BigDecimal.ZERO;
        BigDecimal totalMutuelle = BigDecimal.ZERO;
        BigDecimal totalMasseCommune = BigDecimal.ZERO;
        BigDecimal totalInteressement = BigDecimal.ZERO;

        for (Encaissement enc : encaissements) {
            if (enc.getAffaire() == null) continue;

            // Calculer la répartition
            RepartitionResultat repartition = repartitionService.calculerRepartition(enc);

            html.append("<tr>");
            html.append("<td>").append(enc.getReference()).append("<br/>")
                    .append(DateFormatter.format(enc.getDateEncaissement())).append("</td>");
            html.append("<td>").append(enc.getAffaire().getNumeroAffaire()).append("<br/>")
                    .append(DateFormatter.format(enc.getAffaire().getDateCreation())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getProduitDisponible())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartDD())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartIndicateur())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getProduitNet())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartFLCF())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartTresor())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getProduitNetAyantsDroits())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartChefs())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartSaisissants())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartMutuelle())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartMasseCommune())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartInteressement())).append("</td>");
            html.append("</tr>");

            // Accumuler les totaux
            totalProduitDisponible = totalProduitDisponible.add(repartition.getProduitDisponible());
            totalDD = totalDD.add(repartition.getPartDD());
            totalIndicateur = totalIndicateur.add(repartition.getPartIndicateur());
            totalProduitNet = totalProduitNet.add(repartition.getProduitNet());
            totalFLCF = totalFLCF.add(repartition.getPartFLCF());
            totalTresor = totalTresor.add(repartition.getPartTresor());
            totalProduitNetAyantsDroits = totalProduitNetAyantsDroits.add(repartition.getProduitNetAyantsDroits());
            totalChefs = totalChefs.add(repartition.getPartChefs());
            totalSaisissants = totalSaisissants.add(repartition.getPartSaisissants());
            totalMutuelle = totalMutuelle.add(repartition.getPartMutuelle());
            totalMasseCommune = totalMasseCommune.add(repartition.getPartMasseCommune());
            totalInteressement = totalInteressement.add(repartition.getPartInteressement());
        }

        // Ligne de total
        html.append("""
            <tr class='total-row'>
                <td colspan='2'><strong>TOTAL</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
            </tr>
        """.formatted(
                CurrencyFormatter.format(totalProduitDisponible),
                CurrencyFormatter.format(totalDD),
                CurrencyFormatter.format(totalIndicateur),
                CurrencyFormatter.format(totalProduitNet),
                CurrencyFormatter.format(totalFLCF),
                CurrencyFormatter.format(totalTresor),
                CurrencyFormatter.format(totalProduitNetAyantsDroits),
                CurrencyFormatter.format(totalChefs),
                CurrencyFormatter.format(totalSaisissants),
                CurrencyFormatter.format(totalMutuelle),
                CurrencyFormatter.format(totalMasseCommune),
                CurrencyFormatter.format(totalInteressement)
        ));

        html.append("</tbody></table>");

        // Pied de page
        html.append(genererPiedDePageRapport());

        return html.toString();
    }

    // ==================== TEMPLATE 2: ÉTAT PAR SÉRIES DE MANDATEMENT ====================

    /**
     * Génère le rapport HTML pour l'état par séries de mandatement (Template 2)
     */
    public String genererHtmlEtatMandatement(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📊 Génération du rapport État par Séries de Mandatement - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();

        // En-tête
        html.append(genererEnTeteRapport("ÉTAT PAR SÉRIES DE MANDATEMENT", dateDebut, dateFin));

        // Tableau
        html.append("""
            <table class="rapport-table">
                <thead>
                    <tr>
                        <th rowspan="2">N° encaissement<br/>et Date</th>
                        <th rowspan="2">N° Affaire<br/>et Date</th>
                        <th rowspan="2">Produit net</th>
                        <th colspan="5">Part revenant aux</th>
                        <th rowspan="2">Observations</th>
                    </tr>
                    <tr>
                        <th>Chefs</th>
                        <th>Saisissants</th>
                        <th>Mutuelle<br/>nationale</th>
                        <th>Masse<br/>commune</th>
                        <th>Intéressement</th>
                    </tr>
                </thead>
                <tbody>
        """);

        // Données
        List<Encaissement> encaissements = encaissementDAO.findByPeriode(dateDebut, dateFin);

        for (Encaissement enc : encaissements) {
            if (enc.getAffaire() == null) continue;

            RepartitionResultat repartition = repartitionService.calculerRepartition(enc);

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
            html.append("<td>").append(enc.getObservations() != null ? enc.getObservations() : "").append("</td>");
            html.append("</tr>");
        }

        html.append("</tbody></table>");
        html.append(genererPiedDePageRapport());

        return html.toString();
    }

    // ==================== TEMPLATE 3: ÉTAT CUMULÉ PAR CENTRE DE RÉPARTITION ====================

    /**
     * Génère le rapport HTML pour l'état cumulé par centre (Template 3)
     */
    public String genererHtmlEtatParCentre(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📊 Génération du rapport État Cumulé par Centre - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();

        // En-tête
        html.append(genererEnTeteRapport("ÉTAT CUMULÉ PAR CENTRE DE RÉPARTITION", dateDebut, dateFin));

        // Tableau
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
                        <th>Répartition part indic. fictif</th>
                    </tr>
                </thead>
                <tbody>
        """);

        // Récupérer et calculer par centre
        List<Centre> centres = centreDAO.findAll();
        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalIndicateur = BigDecimal.ZERO;
        BigDecimal totalCentre = BigDecimal.ZERO;

        for (Centre centre : centres) {
            BigDecimal partBase = calculerPartBaseCentre(centre, dateDebut, dateFin);
            BigDecimal partIndicateur = calculerPartIndicateurCentre(centre, dateDebut, dateFin);
            BigDecimal partTotale = partBase.add(partIndicateur);

            if (partTotale.compareTo(BigDecimal.ZERO) > 0) {
                html.append("<tr>");
                html.append("<td>").append(centre.getNomCentre()).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(partBase)).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(partIndicateur)).append("</td>");
                html.append("<td class='montant'><strong>").append(CurrencyFormatter.format(partTotale)).append("</strong></td>");
                html.append("</tr>");

                totalBase = totalBase.add(partBase);
                totalIndicateur = totalIndicateur.add(partIndicateur);
                totalCentre = totalCentre.add(partTotale);
            }
        }

        // Ligne de total
        html.append("""
            <tr class='total-row'>
                <td><strong>TOTAL</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
            </tr>
        """.formatted(
                CurrencyFormatter.format(totalBase),
                CurrencyFormatter.format(totalIndicateur),
                CurrencyFormatter.format(totalCentre)
        ));

        html.append("</tbody></table>");
        html.append(genererPiedDePageRapport());

        return html.toString();
    }

    // ==================== TEMPLATE 4: ÉTAT DE RÉPARTITION DES PARTS DES INDICATEURS RÉELS ====================

    /**
     * Génère le rapport HTML pour les indicateurs réels (Template 4)
     */
    public String genererHtmlIndicateursReels(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📊 Génération du rapport Indicateurs Réels - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();

        // En-tête
        html.append(genererEnTeteRapport("ÉTAT DE RÉPARTITION DES PARTS DES INDICATEURS RÉELS", dateDebut, dateFin));

        // Grouper par service
        Map<Service, List<Encaissement>> encaissementsParService = grouperEncaissementsParService(dateDebut, dateFin);

        for (Map.Entry<Service, List<Encaissement>> entry : encaissementsParService.entrySet()) {
            Service service = entry.getKey();
            List<Encaissement> encaissements = entry.getValue();

            if (encaissements.isEmpty()) continue;

            // En-tête de service
            html.append("<h3>Service : ").append(service.getNomService()).append("</h3>");

            // Tableau pour ce service
            html.append("""
                <table class="rapport-table">
                    <thead>
                        <tr>
                            <th>N° encaissement<br/>et Date</th>
                            <th>N° Affaire<br/>et Date</th>
                            <th>Noms des<br/>contrevenants</th>
                            <th>Contraventions</th>
                            <th>Montant<br/>encaissement</th>
                            <th>Part<br/>indicateur</th>
                            <th>Observations</th>
                        </tr>
                    </thead>
                    <tbody>
            """);

            BigDecimal totalMontant = BigDecimal.ZERO;
            BigDecimal totalIndicateur = BigDecimal.ZERO;

            for (Encaissement enc : encaissements) {
                if (enc.getAffaire() == null) continue;

                RepartitionResultat repartition = repartitionService.calculerRepartition(enc);

                html.append("<tr>");
                html.append("<td>").append(enc.getReference()).append("<br/>")
                        .append(DateFormatter.format(enc.getDateEncaissement())).append("</td>");
                html.append("<td>").append(enc.getAffaire().getNumeroAffaire()).append("<br/>")
                        .append(DateFormatter.format(enc.getAffaire().getDateCreation())).append("</td>");
                html.append("<td>").append(enc.getAffaire().getContrevenant() != null ?
                        enc.getAffaire().getContrevenant().getNomComplet() : "").append("</td>");
                html.append("<td>").append(getContraventionsAffaire(enc.getAffaire())).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(enc.getMontantEncaisse())).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartIndicateur())).append("</td>");
                html.append("<td>").append(enc.getObservations() != null ? enc.getObservations() : "").append("</td>");
                html.append("</tr>");

                totalMontant = totalMontant.add(enc.getMontantEncaisse());
                totalIndicateur = totalIndicateur.add(repartition.getPartIndicateur());
            }

            // Total pour ce service
            html.append("""
                <tr class='total-row'>
                    <td colspan='4'><strong>TOTAL SERVICE</strong></td>
                    <td class='montant'><strong>%s</strong></td>
                    <td class='montant'><strong>%s</strong></td>
                    <td></td>
                </tr>
            """.formatted(
                    CurrencyFormatter.format(totalMontant),
                    CurrencyFormatter.format(totalIndicateur)
            ));

            html.append("</tbody></table><br/>");
        }

        html.append(genererPiedDePageRapport());

        return html.toString();
    }

    // ==================== TEMPLATE 5: ÉTAT DE RÉPARTITION DU PRODUIT DES AFFAIRES CONTENTIEUSES ====================

    /**
     * Génère le rapport HTML pour la répartition du produit (Template 5)
     */
    public String genererHtmlRepartitionProduit(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📊 Génération du rapport Répartition du Produit - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();

        // En-tête
        html.append(genererEnTeteRapport("ÉTAT DE RÉPARTITION DU PRODUIT DES AFFAIRES CONTENTIEUSES", dateDebut, dateFin));

        // Tableau
        html.append("""
            <table class="rapport-table">
                <thead>
                    <tr>
                        <th>N° encaissement<br/>et Date</th>
                        <th>N° Affaire<br/>et Date</th>
                        <th>Noms des<br/>contrevenants</th>
                        <th>Noms des<br/>contraventions</th>
                        <th>Produit<br/>disponible</th>
                        <th>Part<br/>indicateur</th>
                        <th>Part Direction<br/>contentieux</th>
                        <th>Part<br/>indicateur</th>
                        <th>FLCF</th>
                        <th>Montant<br/>Trésor</th>
                        <th>Montant Global<br/>ayants droits</th>
                    </tr>
                </thead>
                <tbody>
        """);

        // Données
        List<Encaissement> encaissements = encaissementDAO.findByPeriode(dateDebut, dateFin);
        BigDecimal totalDisponible = BigDecimal.ZERO;
        BigDecimal totalIndicateur = BigDecimal.ZERO;
        BigDecimal totalDirection = BigDecimal.ZERO;
        BigDecimal totalFLCF = BigDecimal.ZERO;
        BigDecimal totalTresor = BigDecimal.ZERO;
        BigDecimal totalAyantsDroits = BigDecimal.ZERO;

        for (Encaissement enc : encaissements) {
            if (enc.getAffaire() == null) continue;

            RepartitionResultat repartition = repartitionService.calculerRepartition(enc);
            BigDecimal partDirection = repartition.getPartDD().add(repartition.getPartDG());

            html.append("<tr>");
            html.append("<td>").append(enc.getReference()).append("<br/>")
                    .append(DateFormatter.format(enc.getDateEncaissement())).append("</td>");
            html.append("<td>").append(enc.getAffaire().getNumeroAffaire()).append("<br/>")
                    .append(DateFormatter.format(enc.getAffaire().getDateCreation())).append("</td>");
            html.append("<td>").append(enc.getAffaire().getContrevenant() != null ?
                    enc.getAffaire().getContrevenant().getNomComplet() : "").append("</td>");
            html.append("<td>").append(getContraventionsAffaire(enc.getAffaire())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getProduitDisponible())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartIndicateur())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(partDirection)).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartIndicateur())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartFLCF())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getPartTresor())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(repartition.getProduitNetAyantsDroits())).append("</td>");
            html.append("</tr>");

            totalDisponible = totalDisponible.add(repartition.getProduitDisponible());
            totalIndicateur = totalIndicateur.add(repartition.getPartIndicateur());
            totalDirection = totalDirection.add(partDirection);
            totalFLCF = totalFLCF.add(repartition.getPartFLCF());
            totalTresor = totalTresor.add(repartition.getPartTresor());
            totalAyantsDroits = totalAyantsDroits.add(repartition.getProduitNetAyantsDroits());
        }

        // Ligne de total
        html.append("""
            <tr class='total-row'>
                <td colspan='4'><strong>TOTAL</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
            </tr>
        """.formatted(
                CurrencyFormatter.format(totalDisponible),
                CurrencyFormatter.format(totalIndicateur),
                CurrencyFormatter.format(totalDirection),
                CurrencyFormatter.format(totalIndicateur),
                CurrencyFormatter.format(totalFLCF),
                CurrencyFormatter.format(totalTresor),
                CurrencyFormatter.format(totalAyantsDroits)
        ));

        html.append("</tbody></table>");
        html.append(genererPiedDePageRapport());

        return html.toString();
    }

    // ==================== TEMPLATE 6: ÉTAT CUMULÉ PAR AGENT ====================

    /**
     * Génère le rapport HTML pour l'état cumulé par agent (Template 6)
     */
    public String genererHtmlEtatParAgent(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📊 Génération du rapport État Cumulé par Agent - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();

        // En-tête
        html.append(genererEnTeteRapport("ÉTAT CUMULÉ PAR AGENT", dateDebut, dateFin));

        // Tableau
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

        // Calculer les parts par agent
        Map<Agent, Map<String, BigDecimal>> partsParAgent = calculerPartsParAgent(dateDebut, dateFin);
        BigDecimal totalChef = BigDecimal.ZERO;
        BigDecimal totalSaisissant = BigDecimal.ZERO;
        BigDecimal totalDG = BigDecimal.ZERO;
        BigDecimal totalDD = BigDecimal.ZERO;
        BigDecimal totalGlobal = BigDecimal.ZERO;

        for (Map.Entry<Agent, Map<String, BigDecimal>> entry : partsParAgent.entrySet()) {
            Agent agent = entry.getKey();
            Map<String, BigDecimal> parts = entry.getValue();

            BigDecimal partChef = parts.getOrDefault("CHEF", BigDecimal.ZERO);
            BigDecimal partSaisissant = parts.getOrDefault("SAISISSANT", BigDecimal.ZERO);
            BigDecimal partDG = parts.getOrDefault("DG", BigDecimal.ZERO);
            BigDecimal partDD = parts.getOrDefault("DD", BigDecimal.ZERO);
            BigDecimal partTotale = partChef.add(partSaisissant).add(partDG).add(partDD);

            if (partTotale.compareTo(BigDecimal.ZERO) > 0) {
                html.append("<tr>");
                html.append("<td>").append(agent.getNomComplet()).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(partChef)).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(partSaisissant)).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(partDG)).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(partDD)).append("</td>");
                html.append("<td class='montant'><strong>").append(CurrencyFormatter.format(partTotale)).append("</strong></td>");
                html.append("</tr>");

                totalChef = totalChef.add(partChef);
                totalSaisissant = totalSaisissant.add(partSaisissant);
                totalDG = totalDG.add(partDG);
                totalDD = totalDD.add(partDD);
                totalGlobal = totalGlobal.add(partTotale);
            }
        }

        // Ligne de total
        html.append("""
            <tr class='total-row'>
                <td><strong>TOTAL</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td class='montant'><strong>%s</strong></td>
            </tr>
        """.formatted(
                CurrencyFormatter.format(totalChef),
                CurrencyFormatter.format(totalSaisissant),
                CurrencyFormatter.format(totalDG),
                CurrencyFormatter.format(totalDD),
                CurrencyFormatter.format(totalGlobal)
        ));

        html.append("</tbody></table>");
        html.append(genererPiedDePageRapport());

        return html.toString();
    }

    // ==================== TEMPLATE 7: TABLEAU DES AMENDES PAR SERVICES ====================

    /**
     * Génère le rapport HTML pour le tableau des amendes par service (Template 7)
     */
    public String genererHtmlTableauAmendesServices(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📊 Génération du Tableau des Amendes par Services - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();

        // En-tête
        html.append(genererEnTeteRapport("TABLEAU DES AMENDES PAR SERVICES", dateDebut, dateFin));

        // Tableau
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

        // Statistiques par service
        Map<Service, ServiceStats> statsParService = calculerStatsParService(dateDebut, dateFin);
        int totalAffaires = 0;
        BigDecimal totalMontant = BigDecimal.ZERO;

        for (Map.Entry<Service, ServiceStats> entry : statsParService.entrySet()) {
            Service service = entry.getKey();
            ServiceStats stats = entry.getValue();

            html.append("<tr>");
            html.append("<td>").append(service.getNomService()).append("</td>");
            html.append("<td class='text-center'>").append(stats.nombreAffaires).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(stats.montantTotal)).append("</td>");
            html.append("<td>").append(stats.observations).append("</td>");
            html.append("</tr>");

            totalAffaires += stats.nombreAffaires;
            totalMontant = totalMontant.add(stats.montantTotal);
        }

        // Ligne de total
        html.append("""
            <tr class='total-row'>
                <td><strong>TOTAL</strong></td>
                <td class='text-center'><strong>%d</strong></td>
                <td class='montant'><strong>%s</strong></td>
                <td></td>
            </tr>
        """.formatted(totalAffaires, CurrencyFormatter.format(totalMontant)));

        html.append("</tbody></table>");
        html.append(genererPiedDePageRapport());

        return html.toString();
    }

    // ==================== TEMPLATE 8: ÉTAT PAR SÉRIES DE MANDATEMENTS (AGENTS) ====================

    /**
     * Génère le rapport HTML pour l'état de mandatement agents (Template 8)
     */
    public String genererHtmlMandatementAgents(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📊 Génération du rapport État de Mandatement Agents - {} au {}", dateDebut, dateFin);

        StringBuilder html = new StringBuilder();

        // En-tête
        html.append(genererEnTeteRapport("ÉTAT PAR SÉRIES DE MANDATEMENTS", dateDebut, dateFin));

        // Tableau
        html.append("""
            <table class="rapport-table">
                <thead>
                    <tr>
                        <th rowspan="2">N° encaissement<br/>et Date</th>
                        <th rowspan="2">N° Affaire<br/>et Date</th>
                        <th colspan="5">Part revenant à l'agent après répartition en tant que</th>
                        <th rowspan="2">Part agent</th>
                    </tr>
                    <tr>
                        <th>Chefs</th>
                        <th>Saisissants</th>
                        <th>Mutuelle<br/>nationale</th>
                        <th>D.G</th>
                        <th>D.D</th>
                    </tr>
                </thead>
                <tbody>
        """);

        // Pour chaque agent impliqué
        Map<Agent, List<EncaissementAgent>> encaissementsParAgent = grouperEncaissementsParAgent(dateDebut, dateFin);

        for (Map.Entry<Agent, List<EncaissementAgent>> entry : encaissementsParAgent.entrySet()) {
            Agent agent = entry.getKey();
            List<EncaissementAgent> encaissements = entry.getValue();

            // En-tête de l'agent
            html.append("<tr class='agent-header'>");
            html.append("<td colspan='8'><strong>Agent : ").append(agent.getNomComplet())
                    .append(" (").append(agent.getCodeAgent()).append(")</strong></td>");
            html.append("</tr>");

            BigDecimal totalAgent = BigDecimal.ZERO;

            for (EncaissementAgent encAgent : encaissements) {
                Encaissement enc = encAgent.encaissement;
                RepartitionResultat repartition = encAgent.repartition;

                BigDecimal partChef = getPartAgentRole(agent, repartition, "CHEF");
                BigDecimal partSaisissant = getPartAgentRole(agent, repartition, "SAISISSANT");
                BigDecimal partMutuelle = BigDecimal.ZERO; // À calculer selon les règles
                BigDecimal partDG = agent.equals(getDG()) ? repartition.getPartDG() : BigDecimal.ZERO;
                BigDecimal partDD = agent.equals(getDD()) ? repartition.getPartDD() : BigDecimal.ZERO;
                BigDecimal partTotale = partChef.add(partSaisissant).add(partMutuelle).add(partDG).add(partDD);

                html.append("<tr>");
                html.append("<td>").append(enc.getReference()).append("<br/>")
                        .append(DateFormatter.format(enc.getDateEncaissement())).append("</td>");
                html.append("<td>").append(enc.getAffaire().getNumeroAffaire()).append("<br/>")
                        .append(DateFormatter.format(enc.getAffaire().getDateCreation())).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(partChef)).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(partSaisissant)).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(partMutuelle)).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(partDG)).append("</td>");
                html.append("<td class='montant'>").append(CurrencyFormatter.format(partDD)).append("</td>");
                html.append("<td class='montant'><strong>").append(CurrencyFormatter.format(partTotale)).append("</strong></td>");
                html.append("</tr>");

                totalAgent = totalAgent.add(partTotale);
            }

            // Total pour cet agent
            html.append("<tr class='subtotal-row'>");
            html.append("<td colspan='7' class='text-right'><strong>Total pour l'agent ").append(agent.getNomComplet()).append(" :</strong></td>");
            html.append("<td class='montant'><strong>").append(CurrencyFormatter.format(totalAgent)).append("</strong></td>");
            html.append("</tr>");
        }

        html.append("</tbody></table>");
        html.append(genererPiedDePageRapport());

        return html.toString();
    }

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
     * Génère l'en-tête standard des rapports
     */
    private String genererEnTeteRapport(String titre, LocalDate dateDebut, LocalDate dateFin) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>%s</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    h1 { text-align: center; margin-bottom: 5px; }
                    h2 { text-align: center; font-size: 18px; margin-top: 5px; }
                    h3 { margin-top: 20px; margin-bottom: 10px; }
                    .periode { text-align: center; margin-bottom: 20px; }
                    .rapport-table { width: 100%%; border-collapse: collapse; margin: 20px 0; }
                    .rapport-table th, .rapport-table td { border: 1px solid #000; padding: 5px; }
                    .rapport-table th { background-color: #f0f0f0; font-weight: bold; text-align: center; }
                    .montant { text-align: right; }
                    .text-center { text-align: center; }
                    .text-right { text-align: right; }
                    .total-row { background-color: #e0e0e0; font-weight: bold; }
                    .subtotal-row { background-color: #f5f5f5; }
                    .agent-header { background-color: #d0d0d0; }
                    .footer { margin-top: 30px; font-size: 12px; text-align: center; }
                    @media print {
                        body { margin: 10px; }
                        .rapport-table { font-size: 10px; }
                    }
                </style>
            </head>
            <body>
                <h1>RÉPUBLIQUE DU CONGO</h1>
                <h2>MINISTÈRE DES FINANCES ET DU BUDGET</h2>
                <h2>DIRECTION GÉNÉRALE DES DOUANES ET DES DROITS INDIRECTS</h2>
                <h1>%s</h1>
                <div class="periode">Période du %s au %s</div>
        """.formatted(
                titre,
                titre,
                DateFormatter.format(dateDebut),
                DateFormatter.format(dateFin)
        );
    }

    /**
     * Génère le pied de page standard des rapports
     */
    private String genererPiedDePageRapport() {
        return """
                <div class="footer">
                    <p>Document généré le %s à %s</p>
                    <p>Système de Gestion des Affaires Contentieuses - v1.0.0</p>
                </div>
            </body>
            </html>
        """.formatted(
                DateFormatter.format(LocalDate.now()),
                DateFormatter.formatTime(LocalDateTime.now())
        );
    }

    /**
     * Méthodes existantes à conserver
     */

    // Classes internes pour les DTOs
    public static class ServiceStats {
        public int nombreAffaires;
        public BigDecimal montantTotal;
        public String observations;

        public ServiceStats() {
            this.nombreAffaires = 0;
            this.montantTotal = BigDecimal.ZERO;
            this.observations = "";
        }
    }

    public static class EncaissementAgent {
        public Encaissement encaissement;
        public RepartitionResultat repartition;

        public EncaissementAgent(Encaissement e, RepartitionResultat r) {
            this.encaissement = e;
            this.repartition = r;
        }
    }

    // Méthodes utilitaires privées

    private BigDecimal calculerPartBaseCentre(Centre centre, LocalDate dateDebut, LocalDate dateFin) {
        // Logique de calcul pour la part de base du centre
        // À implémenter selon les règles métier
        return BigDecimal.ZERO;
    }

    private BigDecimal calculerPartIndicateurCentre(Centre centre, LocalDate dateDebut, LocalDate dateFin) {
        // Logique de calcul pour la part indicateur du centre
        // À implémenter selon les règles métier
        return BigDecimal.ZERO;
    }

    private Map<Service, List<Encaissement>> grouperEncaissementsParService(LocalDate dateDebut, LocalDate dateFin) {
        List<Encaissement> encaissements = encaissementDAO.findByPeriode(dateDebut, dateFin);
        return encaissements.stream()
                .filter(e -> e.getAffaire() != null && e.getAffaire().getService() != null)
                .collect(Collectors.groupingBy(e -> e.getAffaire().getService()));
    }

    private String getContraventionsAffaire(Affaire affaire) {
        // Récupérer la liste des contraventions de l'affaire
        if (affaire.getContraventions() != null && !affaire.getContraventions().isEmpty()) {
            return affaire.getContraventions().stream()
                    .map(Contravention::getLibelle)
                    .collect(Collectors.joining(", "));
        }
        return "";
    }

    private Map<Agent, Map<String, BigDecimal>> calculerPartsParAgent(LocalDate dateDebut, LocalDate dateFin) {
        Map<Agent, Map<String, BigDecimal>> resultat = new HashMap<>();

        List<Encaissement> encaissements = encaissementDAO.findByPeriode(dateDebut, dateFin);

        for (Encaissement enc : encaissements) {
            if (enc.getAffaire() == null) continue;

            RepartitionResultat repartition = repartitionService.calculerRepartition(enc);

            // Traiter chaque part individuelle
            for (RepartitionResultat.PartIndividuelle part : repartition.getPartsIndividuelles()) {
                Agent agent = part.getAgent();
                resultat.computeIfAbsent(agent, k -> new HashMap<>())
                        .merge(part.getRole(), part.getMontant(), BigDecimal::add);
            }
        }

        return resultat;
    }

    private Map<Service, ServiceStats> calculerStatsParService(LocalDate dateDebut, LocalDate dateFin) {
        Map<Service, ServiceStats> stats = new HashMap<>();

        List<Affaire> affaires = affaireDAO.findByPeriode(dateDebut, dateFin);

        for (Affaire affaire : affaires) {
            if (affaire.getService() != null) {
                ServiceStats serviceStat = stats.computeIfAbsent(affaire.getService(), k -> new ServiceStats());
                serviceStat.nombreAffaires++;
                serviceStat.montantTotal = serviceStat.montantTotal.add(affaire.getMontantAmendeTotal());
            }
        }

        return stats;
    }

    private Map<Agent, List<EncaissementAgent>> grouperEncaissementsParAgent(LocalDate dateDebut, LocalDate dateFin) {
        Map<Agent, List<EncaissementAgent>> resultat = new HashMap<>();

        List<Encaissement> encaissements = encaissementDAO.findByPeriode(dateDebut, dateFin);

        for (Encaissement enc : encaissements) {
            if (enc.getAffaire() == null) continue;

            RepartitionResultat repartition = repartitionService.calculerRepartition(enc);

            // Pour chaque agent impliqué
            Set<Agent> agentsImpliques = new HashSet<>();

            // Récupérer tous les agents de l'affaire
            if (enc.getAffaire().getActeurs() != null) {
                for (AffaireActeur acteur : enc.getAffaire().getActeurs()) {
                    agentsImpliques.add(acteur.getAgent());
                }
            }

            // Ajouter DD et DG
            Agent dd = getDD();
            Agent dg = getDG();
            if (dd != null) agentsImpliques.add(dd);
            if (dg != null) agentsImpliques.add(dg);

            // Grouper
            for (Agent agent : agentsImpliques) {
                resultat.computeIfAbsent(agent, k -> new ArrayList<>())
                        .add(new EncaissementAgent(enc, repartition));
            }
        }

        return resultat;
    }

    private BigDecimal getPartAgentRole(Agent agent, RepartitionResultat repartition, String role) {
        return repartition.getPartsIndividuelles().stream()
                .filter(p -> p.getAgent().equals(agent) && p.getRole().equals(role))
                .map(RepartitionResultat.PartIndividuelle::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Agent getDD() {
        // Récupérer l'agent avec le rôle DD
        return agentDAO.findByRoleSpecial("DD").orElse(null);
    }

    private Agent getDG() {
        // Récupérer l'agent avec le rôle DG
        return agentDAO.findByRoleSpecial("DG").orElse(null);
    }

    // Les méthodes existantes du service original...
    // [Conserver toutes les méthodes existantes sans les modifier]

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
     * COMPLÉTER : genererEtatMandatement (Template 2)
     */
    public EtatMandatementDTO genererEtatMandatement(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état de mandatement du {} au {}", dateDebut, dateFin);

            EtatMandatementDTO etat = new EtatMandatementDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

            List<MandatementDTO> mandatements = new ArrayList<>();

            // Récupérer les encaissements de la période
            List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);

            for (Encaissement enc : encaissements) {
                if (enc.getStatut() != StatutEncaissement.VALIDE) continue;

                Affaire affaire = enc.getAffaire();
                RepartitionResultat repartition = repartitionService.calculerRepartition(enc, affaire);

                MandatementDTO ligne = new MandatementDTO();
                ligne.setNumeroEncaissement(enc.getReference());
                ligne.setDateEncaissement(enc.getDateEncaissement());
                ligne.setNumeroAffaire(affaire.getNumeroAffaire());
                ligne.setDateAffaire(affaire.getDateCreation());
                ligne.setProduitNet(repartition.getProduitNet());
                ligne.setPartChefs(repartition.getPartChefs());
                ligne.setPartSaisissants(repartition.getPartSaisissants());
                ligne.setPartMutuelleNationale(repartition.getPartMutuelle());
                ligne.setPartMasseCommune(repartition.getPartMasseCommune());
                ligne.setPartInteressement(repartition.getPartInteressement());
                ligne.setObservations("");

                mandatements.add(ligne);

                // Accumuler les totaux
                etat.setTotalProduitNet(etat.getTotalProduitNet().add(ligne.getProduitNet()));
                etat.setTotalChefs(etat.getTotalChefs().add(ligne.getPartChefs()));
                etat.setTotalSaisissants(etat.getTotalSaisissants().add(ligne.getPartSaisissants()));
                etat.setTotalMutuelleNationale(etat.getTotalMutuelleNationale().add(ligne.getPartMutuelleNationale()));
                etat.setTotalMasseCommune(etat.getTotalMasseCommune().add(ligne.getPartMasseCommune()));
                etat.setTotalInteressement(etat.getTotalInteressement().add(ligne.getPartInteressement()));
            }

            etat.setMandatements(mandatements);
            logger.info("État de mandatement généré : {} lignes", mandatements.size());
            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état de mandatement", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }

    /**
     * COMPLÉTER : genererEtatCentreRepartition (Template 3)
     */
    public EtatCentreRepartitionDTO genererEtatCentreRepartition(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état par centre de répartition du {} au {}", dateDebut, dateFin);

            EtatCentreRepartitionDTO etat = new EtatCentreRepartitionDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

            List<CentreRepartitionDTO> centres = new ArrayList<>();

            // Récupérer tous les centres
            List<Centre> listeCentres = centreDAO.findAll();

            for (Centre centre : listeCentres) {
                CentreRepartitionDTO centreDTO = new CentreRepartitionDTO();
                centreDTO.setNomCentre(centre.getNomCentre());

                BigDecimal repartitionBase = BigDecimal.ZERO;
                BigDecimal repartitionIndicateur = BigDecimal.ZERO;

                // Récupérer les affaires du centre pour la période
                List<Affaire> affairesCentre = affaireDAO.findByCentreAndPeriod(
                        centre.getId(), dateDebut, dateFin
                );

                for (Affaire affaire : affairesCentre) {
                    List<Encaissement> encaissements = encaissementDAO.findByAffaireAndPeriod(
                            affaire.getId(), dateDebut, dateFin
                    );

                    for (Encaissement enc : encaissements) {
                        if (enc.getStatut() != StatutEncaissement.VALIDE) continue;

                        RepartitionResultat rep = repartitionService.calculerRepartition(enc, affaire);
                        repartitionBase = repartitionBase.add(rep.getProduitNet());
                        repartitionIndicateur = repartitionIndicateur.add(rep.getPartIndicateur());
                    }
                }

                if (repartitionBase.compareTo(BigDecimal.ZERO) > 0) {
                    centreDTO.setRepartitionBase(repartitionBase);
                    centreDTO.setRepartitionIndicateurFictif(repartitionIndicateur);
                    centreDTO.setPartCentre(repartitionBase.add(repartitionIndicateur));
                    centres.add(centreDTO);

                    // Accumuler les totaux
                    etat.setTotalRepartitionBase(etat.getTotalRepartitionBase().add(repartitionBase));
                    etat.setTotalRepartitionIndicateurFictif(
                            etat.getTotalRepartitionIndicateurFictif().add(repartitionIndicateur)
                    );
                    etat.setTotalPartCentre(etat.getTotalPartCentre().add(centreDTO.getPartCentre()));
                }
            }

            etat.setCentres(centres);
            logger.info("État cumulé par centre généré : {} centres", centres.size());
            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état par centre de répartition", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }

    /**
     * COMPLÉTER : genererEtatMandatementAgents (Template 8)
     */
    public EtatMandatementDTO genererEtatMandatementAgents(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état de mandatement agents du {} au {}", dateDebut, dateFin);

            EtatMandatementDTO etat = new EtatMandatementDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));
            etat.setTypeEtat("AGENTS");

            List<MandatementDTO> mandatements = new ArrayList<>();

            // Récupérer les encaissements avec répartition
            List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);

            for (Encaissement enc : encaissements) {
                if (enc.getStatut() != StatutEncaissement.VALIDE) continue;

                RepartitionResultat rep = repartitionService.calculerRepartition(enc, enc.getAffaire());

                // Pour chaque agent ayant une part
                for (RepartitionResultat.PartIndividuelle part : rep.getPartsIndividuelles()) {
                    MandatementDTO ligne = new MandatementDTO();
                    ligne.setNumeroEncaissement(enc.getReference());
                    ligne.setDateEncaissement(enc.getDateEncaissement());
                    ligne.setNumeroAffaire(enc.getAffaire().getNumeroAffaire());
                    ligne.setDateAffaire(enc.getAffaire().getDateCreation());

                    // Initialiser toutes les parts à zéro
                    ligne.setPartChefs(BigDecimal.ZERO);
                    ligne.setPartSaisissants(BigDecimal.ZERO);
                    ligne.setPartMutuelleNationale(BigDecimal.ZERO);
                    ligne.setPartDG(BigDecimal.ZERO);
                    ligne.setPartDD(BigDecimal.ZERO);

                    // Affecter la part selon le rôle
                    switch (part.getRole()) {
                        case "CHEF":
                            ligne.setPartChefs(part.getMontant());
                            break;
                        case "SAISISSANT":
                            ligne.setPartSaisissants(part.getMontant());
                            break;
                        case "DG":
                            ligne.setPartDG(part.getMontant());
                            break;
                        case "DD":
                            ligne.setPartDD(part.getMontant());
                            break;
                    }

                    mandatements.add(ligne);
                }

                // Ajouter la part mutuelle
                if (rep.getPartMutuelle().compareTo(BigDecimal.ZERO) > 0) {
                    MandatementDTO ligneMutuelle = new MandatementDTO();
                    ligneMutuelle.setNumeroEncaissement(enc.getReference());
                    ligneMutuelle.setDateEncaissement(enc.getDateEncaissement());
                    ligneMutuelle.setNumeroAffaire(enc.getAffaire().getNumeroAffaire());
                    ligneMutuelle.setDateAffaire(enc.getAffaire().getDateCreation());
                    ligneMutuelle.setPartMutuelleNationale(rep.getPartMutuelle());
                    ligneMutuelle.setPartChefs(BigDecimal.ZERO);
                    ligneMutuelle.setPartSaisissants(BigDecimal.ZERO);
                    ligneMutuelle.setPartDG(BigDecimal.ZERO);
                    ligneMutuelle.setPartDD(BigDecimal.ZERO);
                    mandatements.add(ligneMutuelle);
                }
            }

            // Calculer les totaux
            for (MandatementDTO m : mandatements) {
                etat.setTotalChefs(etat.getTotalChefs().add(m.getPartChefs()));
                etat.setTotalSaisissants(etat.getTotalSaisissants().add(m.getPartSaisissants()));
                etat.setTotalMutuelleNationale(etat.getTotalMutuelleNationale().add(m.getPartMutuelleNationale()));
                etat.setTotalDG(etat.getTotalDG().add(m.getPartDG()));
                etat.setTotalDD(etat.getTotalDD().add(m.getPartDD()));
            }

            etat.setMandatements(mandatements);
            logger.info("État de mandatement agents généré : {} lignes", mandatements.size());
            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état de mandatement agents", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }

    /**
     * AJOUTER : Méthode utilitaire pour mapper TypeRapport vers les bonnes méthodes
     */
    public Object genererRapportSelonType(com.regulation.contentieux.model.enums.TypeRapport type,
                                          LocalDate dateDebut, LocalDate dateFin) {
        logger.info("Génération du rapport {} pour la période {} - {}",
                type.getLibelle(), dateDebut, dateFin);

        switch (type) {
            case REPARTITION_RETROCESSION:
                // Template 1 : utilise la méthode existante
                return genererEtatRepartitionAffaires(dateDebut, dateFin);

            case ETAT_MANDATEMENT:
                // Template 2
                return genererEtatMandatement(dateDebut, dateFin);

            case CENTRE_REPARTITION:
                // Template 3
                return genererEtatCentreRepartition(dateDebut, dateFin);

            case INDICATEURS_REELS:
                // Template 4
                return genererEtatIndicateursReels(dateDebut, dateFin);

            case REPARTITION_PRODUIT:
                // Template 5
                return genererEtatRepartitionProduit(dateDebut, dateFin);

            case TABLEAU_AMENDES_SERVICE:
                // Template 7
                return genererTableauAmendesParServices(dateDebut, dateFin);

            case MANDATEMENT_AGENTS:
                // Template 8
                return genererEtatMandatementAgents(dateDebut, dateFin);

            case SITUATION_GENERALE:
            case AFFAIRES_NON_SOLDEES:
            case ENCAISSEMENTS_PERIODE:
                // Ces types ne correspondent pas directement aux 8 templates
                // Utiliser les méthodes existantes ou créer un mapping approprié
                return genererRapportRepartition(dateDebut, dateFin);

            default:
                logger.warn("Type de rapport non supporté : {}", type);
                throw new UnsupportedOperationException("Type de rapport non supporté : " + type);
        }
    }

    /**
     * NOTE : Pour le Template 6 (État cumulé par agent),
     * la méthode genererEtatCumuleParAgent() existe déjà et semble complète
     */

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

    public class SituationGeneraleDTO {

        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;

        // Statistiques globales
        private int totalAffaires;
        private int affairesOuvertes;
        private int affairesEnCours;
        private int affairesSoldees;
        private int affairesAnnulees;

        // Montants globaux
        private BigDecimal montantTotalAmendes;
        private BigDecimal montantTotalEncaisse;
        private BigDecimal montantRestantDu;
        private BigDecimal tauxRecouvrement;

        // Répartition par statut
        private Map<String, Integer> repartitionParStatut;
        private Map<String, BigDecimal> montantsParStatut;

        // Répartition par service
        private Map<String, Integer> affairesParService;
        private Map<String, BigDecimal> montantsParService;

        // Répartition par type de contravention
        private Map<String, Integer> affairesParTypeContravention;
        private Map<String, BigDecimal> montantsParTypeContravention;

        // Évolution mensuelle
        private List<com.regulation.contentieux.service.SituationGeneraleDTO.EvolutionMensuelleDTO> evolutionMensuelle;

        // Top contrevenants
        private List<com.regulation.contentieux.service.SituationGeneraleDTO.TopContrevenantDTO> topContrevenants;

        // Constructeur
        public SituationGeneraleDTO() {
            this.dateGeneration = LocalDate.now();
            this.montantTotalAmendes = BigDecimal.ZERO;
            this.montantTotalEncaisse = BigDecimal.ZERO;
            this.montantRestantDu = BigDecimal.ZERO;
            this.tauxRecouvrement = BigDecimal.ZERO;
        }

        // Getters et Setters
        public LocalDate getDateDebut() {
            return dateDebut;
        }

        public void setDateDebut(LocalDate dateDebut) {
            this.dateDebut = dateDebut;
        }

        public LocalDate getDateFin() {
            return dateFin;
        }

        public void setDateFin(LocalDate dateFin) {
            this.dateFin = dateFin;
        }

        public LocalDate getDateGeneration() {
            return dateGeneration;
        }

        public void setDateGeneration(LocalDate dateGeneration) {
            this.dateGeneration = dateGeneration;
        }

        public String getPeriodeLibelle() {
            return periodeLibelle;
        }

        public void setPeriodeLibelle(String periodeLibelle) {
            this.periodeLibelle = periodeLibelle;
        }

        public int getTotalAffaires() {
            return totalAffaires;
        }

        public void setTotalAffaires(int totalAffaires) {
            this.totalAffaires = totalAffaires;
        }

        public int getAffairesOuvertes() {
            return affairesOuvertes;
        }

        public void setAffairesOuvertes(int affairesOuvertes) {
            this.affairesOuvertes = affairesOuvertes;
        }

        public int getAffairesEnCours() {
            return affairesEnCours;
        }

        public void setAffairesEnCours(int affairesEnCours) {
            this.affairesEnCours = affairesEnCours;
        }

        public int getAffairesSoldees() {
            return affairesSoldees;
        }

        public void setAffairesSoldees(int affairesSoldees) {
            this.affairesSoldees = affairesSoldees;
        }

        public int getAffairesAnnulees() {
            return affairesAnnulees;
        }

        public void setAffairesAnnulees(int affairesAnnulees) {
            this.affairesAnnulees = affairesAnnulees;
        }

        public BigDecimal getMontantTotalAmendes() {
            return montantTotalAmendes;
        }

        public void setMontantTotalAmendes(BigDecimal montantTotalAmendes) {
            this.montantTotalAmendes = montantTotalAmendes;
        }

        public BigDecimal getMontantTotalEncaisse() {
            return montantTotalEncaisse;
        }

        public void setMontantTotalEncaisse(BigDecimal montantTotalEncaisse) {
            this.montantTotalEncaisse = montantTotalEncaisse;
        }

        public BigDecimal getMontantRestantDu() {
            return montantRestantDu;
        }

        public void setMontantRestantDu(BigDecimal montantRestantDu) {
            this.montantRestantDu = montantRestantDu;
        }

        public BigDecimal getTauxRecouvrement() {
            return tauxRecouvrement;
        }

        public void setTauxRecouvrement(BigDecimal tauxRecouvrement) {
            this.tauxRecouvrement = tauxRecouvrement;
        }

        public Map<String, Integer> getRepartitionParStatut() {
            return repartitionParStatut;
        }

        public void setRepartitionParStatut(Map<String, Integer> repartitionParStatut) {
            this.repartitionParStatut = repartitionParStatut;
        }

        public Map<String, BigDecimal> getMontantsParStatut() {
            return montantsParStatut;
        }

        public void setMontantsParStatut(Map<String, BigDecimal> montantsParStatut) {
            this.montantsParStatut = montantsParStatut;
        }

        public Map<String, Integer> getAffairesParService() {
            return affairesParService;
        }

        public void setAffairesParService(Map<String, Integer> affairesParService) {
            this.affairesParService = affairesParService;
        }

        public Map<String, BigDecimal> getMontantsParService() {
            return montantsParService;
        }

        public void setMontantsParService(Map<String, BigDecimal> montantsParService) {
            this.montantsParService = montantsParService;
        }

        public Map<String, Integer> getAffairesParTypeContravention() {
            return affairesParTypeContravention;
        }

        public void setAffairesParTypeContravention(Map<String, Integer> affairesParTypeContravention) {
            this.affairesParTypeContravention = affairesParTypeContravention;
        }

        public Map<String, BigDecimal> getMontantsParTypeContravention() {
            return montantsParTypeContravention;
        }

        public void setMontantsParTypeContravention(Map<String, BigDecimal> montantsParTypeContravention) {
            this.montantsParTypeContravention = montantsParTypeContravention;
        }

        public List<com.regulation.contentieux.service.SituationGeneraleDTO.EvolutionMensuelleDTO> getEvolutionMensuelle() {
            return evolutionMensuelle;
        }

        public void setEvolutionMensuelle(List<com.regulation.contentieux.service.SituationGeneraleDTO.EvolutionMensuelleDTO> evolutionMensuelle) {
            this.evolutionMensuelle = evolutionMensuelle;
        }

        public List<com.regulation.contentieux.service.SituationGeneraleDTO.TopContrevenantDTO> getTopContrevenants() {
            return topContrevenants;
        }

        public void setTopContrevenants(List<com.regulation.contentieux.service.SituationGeneraleDTO.TopContrevenantDTO> topContrevenants) {
            this.topContrevenants = topContrevenants;
        }

        /**
         * DTO pour l'évolution mensuelle
         */
        public static class EvolutionMensuelleDTO {
            private String mois;
            private int nombreAffaires;
            private BigDecimal montantTotal;
            private BigDecimal montantEncaisse;

            public EvolutionMensuelleDTO() {
                this.montantTotal = BigDecimal.ZERO;
                this.montantEncaisse = BigDecimal.ZERO;
            }

            // Getters et Setters
            public String getMois() {
                return mois;
            }

            public void setMois(String mois) {
                this.mois = mois;
            }

            public int getNombreAffaires() {
                return nombreAffaires;
            }

            public void setNombreAffaires(int nombreAffaires) {
                this.nombreAffaires = nombreAffaires;
            }

            public BigDecimal getMontantTotal() {
                return montantTotal;
            }

            public void setMontantTotal(BigDecimal montantTotal) {
                this.montantTotal = montantTotal;
            }

            public BigDecimal getMontantEncaisse() {
                return montantEncaisse;
            }

            public void setMontantEncaisse(BigDecimal montantEncaisse) {
                this.montantEncaisse = montantEncaisse;
            }
        }

        /**
         * DTO pour les top contrevenants
         */
        public static class TopContrevenantDTO {
            private String contrevenantNom;
            private String contrevenantCode;
            private int nombreAffaires;
            private BigDecimal montantTotal;
            private BigDecimal montantEncaisse;
            private BigDecimal montantRestant;

            public TopContrevenantDTO() {
                this.montantTotal = BigDecimal.ZERO;
                this.montantEncaisse = BigDecimal.ZERO;
                this.montantRestant = BigDecimal.ZERO;
            }

            // Getters et Setters
            public String getContrevenantNom() {
                return contrevenantNom;
            }

            public void setContrevenantNom(String contrevenantNom) {
                this.contrevenantNom = contrevenantNom;
            }

            public String getContrevenantCode() {
                return contrevenantCode;
            }

            public void setContrevenantCode(String contrevenantCode) {
                this.contrevenantCode = contrevenantCode;
            }

            public int getNombreAffaires() {
                return nombreAffaires;
            }

            public void setNombreAffaires(int nombreAffaires) {
                this.nombreAffaires = nombreAffaires;
            }

            public BigDecimal getMontantTotal() {
                return montantTotal;
            }

            public void setMontantTotal(BigDecimal montantTotal) {
                this.montantTotal = montantTotal;
            }

            public BigDecimal getMontantEncaisse() {
                return montantEncaisse;
            }

            public void setMontantEncaisse(BigDecimal montantEncaisse) {
                this.montantEncaisse = montantEncaisse;
            }

            public BigDecimal getMontantRestant() {
                return montantRestant;
            }

            public void setMontantRestant(BigDecimal montantRestant) {
                this.montantRestant = montantRestant;
            }
        }
    }
}