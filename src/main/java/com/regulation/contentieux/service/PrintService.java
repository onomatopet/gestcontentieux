package com.regulation.contentieux.service;

import com.regulation.contentieux.service.RapportService.*;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.print.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Service d'impression et génération HTML pour les rapports
 */
public class PrintService {

    private static final Logger logger = LoggerFactory.getLogger(PrintService.class);

    private final RapportService rapportService;

    // Template HTML de base pour tous les imprimés
    private static final String BASE_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>{{TITRE}}</title>
            <style>
                {{STYLES}}
            </style>
        </head>
        <body>
            {{HEADER}}
            {{CONTENT}}
            {{FOOTER}}
        </body>
        </html>
        """;

    public PrintService() {
        this.rapportService = new RapportService();
    }

    // ==================== MÉTHODES DE GÉNÉRATION HTML ====================

    /**
     * Génère un aperçu de rapport pour affichage
     */
    public String genererApercuRapport(RapportRepartitionDTO rapport) {
        try {
            logger.info("Génération de l'aperçu du rapport de rétrocession");

            StringBuilder html = new StringBuilder();
            html.append("<div class='rapport-apercu'>");

            // En-tête du rapport
            html.append("<div class='rapport-header'>");
            html.append("<h2>RAPPORT DE RÉTROCESSION</h2>");
            html.append("<p>Période : ").append(rapport.getPeriodeLibelle()).append("</p>");
            html.append("<p>Généré le : ").append(DateFormatter.formatDate(rapport.getDateGeneration())).append("</p>");
            html.append("</div>");

            // Résumé des totaux
            html.append("<div class='rapport-totaux'>");
            html.append("<h3>Résumé</h3>");
            html.append("<table class='totaux-table'>");
            html.append("<tr><td>Nombre d'affaires :</td><td>").append(rapport.getNombreAffaires()).append("</td></tr>");
            html.append("<tr><td>Total encaissé :</td><td>").append(CurrencyFormatter.format(rapport.getTotalEncaisse())).append("</td></tr>");
            html.append("<tr><td>Part État :</td><td>").append(CurrencyFormatter.format(rapport.getTotalEtat())).append("</td></tr>");
            html.append("<tr><td>Part Collectivités :</td><td>").append(CurrencyFormatter.format(rapport.getTotalCollectivite())).append("</td></tr>");
            html.append("</table>");
            html.append("</div>");

            // Tableau des affaires (aperçu des 10 premières)
            html.append("<div class='rapport-affaires'>");
            html.append("<h3>Affaires (aperçu - 10 premières)</h3>");
            html.append("<table class='affaires-table'>");
            html.append("<thead>");
            html.append("<tr><th>N° Affaire</th><th>Date</th><th>Contrevenant</th><th>Montant</th><th>Statut</th></tr>");
            html.append("</thead>");
            html.append("<tbody>");

            int count = 0;
            for (AffaireRepartitionDTO affaire : rapport.getAffaires()) {
                if (count >= 10) break;

                html.append("<tr>");
                html.append("<td>").append(escapeHtml(affaire.getNumeroAffaire())).append("</td>");
                html.append("<td>").append(DateFormatter.formatDate(affaire.getDateCreation())).append("</td>");
                html.append("<td>").append(escapeHtml(affaire.getContrevenantNom())).append("</td>");
                html.append("<td>").append(CurrencyFormatter.format(affaire.getMontantAmende())).append("</td>");
                html.append("<td>").append(escapeHtml(affaire.getStatut())).append("</td>");
                html.append("</tr>");

                count++;
            }

            if (rapport.getAffaires().size() > 10) {
                html.append("<tr><td colspan='5'><em>... et ").append(rapport.getAffaires().size() - 10).append(" autres affaires</em></td></tr>");
            }

            html.append("</tbody>");
            html.append("</table>");
            html.append("</div>");

            html.append("</div>");

            return html.toString();

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'aperçu du rapport", e);
            return "<div class='error'>Erreur lors de la génération de l'aperçu</div>";
        }
    }

    /**
     * Génère le HTML d'un imprimé selon son type
     */
    public String genererHTMLImprimeParType(String typeImprime, Object donneesImprime) {
        switch (typeImprime) {
            case "ETAT_REPARTITION_AFFAIRES":
                return genererHTML_EtatRepartitionAffaires((EtatRepartitionAffairesDTO) donneesImprime);
            case "ETAT_MANDATEMENT":
                return genererHTML_EtatMandatement((EtatMandatementDTO) donneesImprime);
            case "ETAT_CENTRE_REPARTITION":
                return genererHTML_EtatCentreRepartition((EtatCentreRepartitionDTO) donneesImprime);
            case "ETAT_INDICATEURS_REELS":
                return genererHTML_EtatRepartitionIndicateursReels((EtatRepartitionIndicateursReelsDTO) donneesImprime);
            case "ETAT_REPARTITION_PRODUIT":
                return genererHTML_EtatRepartitionProduit((EtatRepartitionProduitDTO) donneesImprime);
            case "ETAT_CUMULE_AGENT":
                return genererHTML_EtatCumuleParAgent((EtatCumuleParAgentDTO) donneesImprime);
            case "TABLEAU_AMENDES_SERVICES":
                return genererHTML_TableauAmendesParServices((TableauAmendesParServicesDTO) donneesImprime);
            case "ETAT_MANDATEMENT_AGENTS":
                return genererHTML_EtatMandatementAgents((EtatMandatementDTO) donneesImprime);
            default:
                throw new IllegalArgumentException("Type d'imprimé non reconnu: " + typeImprime);
        }
    }

    /**
     * Génère le HTML pour l'état de répartition des affaires contentieuses
     */
    private String genererHTML_EtatRepartitionAffaires(EtatRepartitionAffairesDTO etat) {
        StringBuilder content = new StringBuilder();

        content.append("<h1>ÉTAT DE RÉPARTITION DES AFFAIRES CONTENTIEUSES</h1>");
        content.append("<p class='periode'>Période : ").append(etat.getPeriodeLibelle()).append("</p>");

        content.append("<table class='data-table'>");
        content.append("<thead>");
        content.append("<tr>");
        content.append("<th>N° Encaissement</th>");
        content.append("<th>Date Encaissement</th>");
        content.append("<th>N° Affaire</th>");
        content.append("<th>Date Affaire</th>");
        content.append("<th>Produit Disponible</th>");
        content.append("<th>Direction Départementale</th>");
        content.append("<th>Indicateur</th>");
        content.append("<th>Produit Net</th>");
        content.append("</tr>");
        content.append("</thead>");
        content.append("<tbody>");

        for (AffaireRepartitionCompleteDTO affaire : etat.getAffaires()) {
            content.append("<tr>");
            content.append("<td>").append(escapeHtml(affaire.getNumeroEncaissement())).append("</td>");
            content.append("<td>").append(DateFormatter.formatDate(affaire.getDateEncaissement())).append("</td>");
            content.append("<td>").append(escapeHtml(affaire.getNumeroAffaire())).append("</td>");
            content.append("<td>").append(DateFormatter.formatDate(affaire.getDateAffaire())).append("</td>");
            content.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getProduitDisponible())).append("</td>");
            content.append("<td>").append(escapeHtml(affaire.getDirectionDepartementale())).append("</td>");
            content.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getIndicateur())).append("</td>");
            content.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getProduitNet())).append("</td>");
            content.append("</tr>");
        }

        content.append("</tbody>");
        content.append("</table>");

        // Totaux
        content.append("<div class='totaux'>");
        content.append("<h3>TOTAUX</h3>");
        content.append("<p>Total affaires : ").append(etat.getTotalAffaires()).append("</p>");
        content.append("<p>Total produit disponible : ").append(CurrencyFormatter.format(etat.getTotaux().getTotalProduitDisponible())).append("</p>");
        content.append("</div>");

        return assemblerHTML("État de Répartition des Affaires", "ÉTAT DE RÉPARTITION",
                etat.getPeriodeLibelle(), LocalDateTime.now(), content.toString());
    }

    /**
     * Génère le HTML pour l'état de répartition des indicateurs réels
     */
    private String genererHTML_EtatRepartitionIndicateursReels(EtatRepartitionIndicateursReelsDTO etat) {
        StringBuilder content = new StringBuilder();

        content.append("<h1>ÉTAT DE RÉPARTITION DES INDICATEURS RÉELS</h1>");
        content.append("<p class='periode'>Période : ").append(etat.getPeriodeLibelle()).append("</p>");

        for (ServiceIndicateurDTO service : etat.getServices()) {
            content.append("<h2>Service : ").append(escapeHtml(service.getNomService())).append("</h2>");

            for (SectionIndicateurDTO section : service.getSections()) {
                content.append("<h3>Section : ").append(escapeHtml(section.getNomSection())).append("</h3>");

                content.append("<table class='data-table'>");
                content.append("<thead>");
                content.append("<tr>");
                content.append("<th>N° Affaire</th>");
                content.append("<th>Date</th>");
                content.append("<th>Contrevenant</th>");
                content.append("<th>Montant</th>");
                content.append("<th>Part Indicateur</th>");
                content.append("</tr>");
                content.append("</thead>");
                content.append("<tbody>");

                for (AffaireIndicateurDTO affaire : section.getAffaires()) {
                    content.append("<tr>");
                    content.append("<td>").append(escapeHtml(affaire.getNumeroAffaire())).append("</td>");
                    content.append("<td>").append(DateFormatter.formatDate(affaire.getDateAffaire())).append("</td>");
                    content.append("<td>").append(escapeHtml(affaire.getNomContrevenant())).append("</td>");
                    content.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getMontantEncaissement())).append("</td>");
                    content.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getPartIndicateur())).append("</td>");
                    content.append("</tr>");
                }

                content.append("</tbody>");
                content.append("</table>");

                content.append("<p class='sous-total'>Sous-total section : ").append(CurrencyFormatter.format(section.getTotalMontant())).append("</p>");
            }

            content.append("<p class='total-service'>Total service : ").append(CurrencyFormatter.format(service.getTotalMontant())).append("</p>");
        }

        content.append("<div class='totaux'>");
        content.append("<h3>TOTAUX GÉNÉRAUX</h3>");
        content.append("<p>Total affaires : ").append(etat.getTotalGeneralAffaires()).append("</p>");
        content.append("<p>Total montant : ").append(CurrencyFormatter.format(etat.getTotalGeneralMontant())).append("</p>");
        content.append("<p>Total indicateurs : ").append(CurrencyFormatter.format(etat.getTotalGeneralIndicateur())).append("</p>");
        content.append("</div>");

        return assemblerHTML("État des Indicateurs Réels", "ÉTAT DES INDICATEURS RÉELS",
                etat.getPeriodeLibelle(), LocalDateTime.now(), content.toString());
    }

    /**
     * Génère le HTML pour l'état de répartition du produit
     */
    private String genererHTML_EtatRepartitionProduit(EtatRepartitionProduitDTO etat) {
        StringBuilder content = new StringBuilder();

        content.append("<h1>ÉTAT DE RÉPARTITION DU PRODUIT DES AFFAIRES CONTENTIEUSES</h1>");
        content.append("<p class='periode'>Période : ").append(etat.getPeriodeLibelle()).append("</p>");

        content.append("<table class='data-table'>");
        content.append("<thead>");
        content.append("<tr>");
        content.append("<th>N° Encaissement</th>");
        content.append("<th>Date</th>");
        content.append("<th>N° Affaire</th>");
        content.append("<th>Contrevenant</th>");
        content.append("<th>Produit Disponible</th>");
        content.append("<th>Part Indicateur</th>");
        content.append("<th>FLCF</th>");
        content.append("<th>Trésor</th>");
        content.append("</tr>");
        content.append("</thead>");
        content.append("<tbody>");

        for (ProduitAffaireDTO affaire : etat.getAffaires()) {
            content.append("<tr>");
            content.append("<td>").append(escapeHtml(affaire.getNumeroEncaissement())).append("</td>");
            content.append("<td>").append(DateFormatter.formatDate(affaire.getDateEncaissement())).append("</td>");
            content.append("<td>").append(escapeHtml(affaire.getNumeroAffaire())).append("</td>");
            content.append("<td>").append(escapeHtml(affaire.getNomContrevenant())).append("</td>");
            content.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getProduitDisponible())).append("</td>");
            content.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getPartIndicateur())).append("</td>");
            content.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getFlcf())).append("</td>");
            content.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getMontantTresor())).append("</td>");
            content.append("</tr>");
        }

        content.append("</tbody>");
        content.append("</table>");

        content.append("<div class='totaux'>");
        content.append("<h3>TOTAUX</h3>");
        content.append("<p>Total affaires : ").append(etat.getTotalAffaires()).append("</p>");
        content.append("<p>Total produit : ").append(CurrencyFormatter.format(etat.getTotaux().getTotalProduitDisponible())).append("</p>");
        content.append("</div>");

        return assemblerHTML("État de Répartition du Produit", "ÉTAT DE RÉPARTITION DU PRODUIT",
                etat.getPeriodeLibelle(), LocalDateTime.now(), content.toString());
    }

    /**
     * Génère le HTML pour l'état cumulé par agent
     */
    private String genererHTML_EtatCumuleParAgent(EtatCumuleParAgentDTO etat) {
        StringBuilder content = new StringBuilder();

        content.append("<h1>ÉTAT CUMULÉ PAR AGENT</h1>");
        content.append("<p class='periode'>Période : ").append(etat.getPeriodeLibelle()).append("</p>");

        content.append("<table class='data-table'>");
        content.append("<thead>");
        content.append("<tr>");
        content.append("<th>Agent</th>");
        content.append("<th>Code</th>");
        content.append("<th>Part Chef</th>");
        content.append("<th>Part Saisissant</th>");
        content.append("<th>Part DG</th>");
        content.append("<th>Part DD</th>");
        content.append("<th>Total Agent</th>");
        content.append("</tr>");
        content.append("</thead>");
        content.append("<tbody>");

        for (AgentCumuleDTO agent : etat.getAgents()) {
            content.append("<tr>");
            content.append("<td>").append(escapeHtml(agent.getNomAgent())).append("</td>");
            content.append("<td>").append(escapeHtml(agent.getCodeAgent())).append("</td>");
            content.append("<td class='montant'>").append(CurrencyFormatter.format(agent.getPartChef())).append("</td>");
            content.append("<td class='montant'>").append(CurrencyFormatter.format(agent.getPartSaisissant())).append("</td>");
            content.append("<td class='montant'>").append(CurrencyFormatter.format(agent.getPartDG())).append("</td>");
            content.append("<td class='montant'>").append(CurrencyFormatter.format(agent.getPartDD())).append("</td>");
            content.append("<td class='montant total'>").append(CurrencyFormatter.format(agent.getPartTotaleAgent())).append("</td>");
            content.append("</tr>");
        }

        content.append("</tbody>");
        content.append("</table>");

        content.append("<div class='totaux'>");
        content.append("<h3>TOTAUX</h3>");
        content.append("<p>Agents traités : ").append(etat.getAgents().size()).append("</p>");
        content.append("<p>Affaires traitées : ").append(etat.getTotalAffairesTraitees()).append("</p>");
        content.append("<p>Total général : ").append(CurrencyFormatter.format(etat.getTotalGeneral())).append("</p>");
        content.append("</div>");

        return assemblerHTML("État Cumulé par Agent", "ÉTAT CUMULÉ PAR AGENT",
                etat.getPeriodeLibelle(), LocalDateTime.now(), content.toString());
    }

    /**
     * Génère le HTML pour le tableau des amendes par services
     */
    private String genererHTML_TableauAmendesParServices(TableauAmendesParServicesDTO tableau) {
        StringBuilder content = new StringBuilder();

        content.append("<h1>TABLEAU DES AMENDES PAR SERVICES</h1>");
        content.append("<p class='periode'>Période : ").append(tableau.getPeriodeLibelle()).append("</p>");

        content.append("<table class='data-table'>");
        content.append("<thead>");
        content.append("<tr>");
        content.append("<th>Service</th>");
        content.append("<th>Nombre d'Affaires</th>");
        content.append("<th>Montant Total</th>");
        content.append("<th>Observations</th>");
        content.append("</tr>");
        content.append("</thead>");
        content.append("<tbody>");

        for (ServiceAmendeDTO service : tableau.getServices()) {
            content.append("<tr>");
            content.append("<td>").append(escapeHtml(service.getNomService())).append("</td>");
            content.append("<td class='nombre'>").append(service.getNombreAffaires()).append("</td>");
            content.append("<td class='montant'>").append(CurrencyFormatter.format(service.getMontantTotal())).append("</td>");
            content.append("<td>").append(escapeHtml(service.getObservations())).append("</td>");
            content.append("</tr>");
        }

        content.append("</tbody>");
        content.append("</table>");

        content.append("<div class='totaux'>");
        content.append("<h3>TOTAUX</h3>");
        content.append("<p>Total services : ").append(tableau.getServices().size()).append("</p>");
        content.append("<p>Total affaires : ").append(tableau.getTotalAffaires()).append("</p>");
        content.append("<p>Total montant : ").append(CurrencyFormatter.format(tableau.getTotalMontant())).append("</p>");
        content.append("</div>");

        return assemblerHTML("Tableau des Amendes par Services", "TABLEAU DES AMENDES PAR SERVICES",
                tableau.getPeriodeLibelle(), LocalDateTime.now(), content.toString());
    }

    /**
     * Génère le HTML pour l'état de mandatement
     */
    private String genererHTML_EtatMandatement(EtatMandatementDTO etat) {
        StringBuilder content = new StringBuilder();

        content.append("<h1>ÉTAT PAR SÉRIES DE MANDATEMENT</h1>");
        content.append("<p class='periode'>Période : ").append(etat.getPeriodeLibelle()).append("</p>");

        content.append("<p><em>Fonctionnalité en cours de développement</em></p>");

        return assemblerHTML("État de Mandatement", "ÉTAT DE MANDATEMENT",
                etat.getPeriodeLibelle(), LocalDateTime.now(), content.toString());
    }

    /**
     * Génère le HTML pour l'état de centre de répartition
     */
    private String genererHTML_EtatCentreRepartition(EtatCentreRepartitionDTO etat) {
        StringBuilder content = new StringBuilder();

        content.append("<h1>ÉTAT CUMULÉ PAR CENTRE DE RÉPARTITION</h1>");
        content.append("<p class='periode'>Période : ").append(etat.getPeriodeLibelle()).append("</p>");

        content.append("<p><em>Fonctionnalité en cours de développement</em></p>");

        return assemblerHTML("État Centre de Répartition", "ÉTAT CENTRE DE RÉPARTITION",
                etat.getPeriodeLibelle(), LocalDateTime.now(), content.toString());
    }

    /**
     * Génère le HTML pour l'état de mandatement agents
     */
    private String genererHTML_EtatMandatementAgents(EtatMandatementDTO etat) {
        StringBuilder content = new StringBuilder();

        content.append("<h1>ÉTAT PAR SÉRIES DE MANDATEMENTS (AGENTS)</h1>");
        content.append("<p class='periode'>Période : ").append(etat.getPeriodeLibelle()).append("</p>");

        content.append("<p><em>Fonctionnalité en cours de développement</em></p>");

        return assemblerHTML("État Mandatement Agents", "ÉTAT MANDATEMENT AGENTS",
                etat.getPeriodeLibelle(), LocalDateTime.now(), content.toString());
    }

    // ==================== MÉTHODES D'IMPRESSION ====================

    /**
     * Imprime un rapport de rétrocession
     */
    public CompletableFuture<Boolean> imprimerRapport(RapportRepartitionDTO rapport) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Début de l'impression du rapport de rétrocession");

                // Génération du HTML complet pour l'impression
                String htmlContent = genererHTMLRapportComplet(rapport);

                // Création de la page web pour l'impression
                WebEngine webEngine = createPrintWebEngine();

                final boolean[] success = {false};
                final CountDownLatch latch = new CountDownLatch(1);

                Platform.runLater(() -> {
                    try {
                        webEngine.loadContent(htmlContent);

                        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                            if (newState == Worker.State.SUCCEEDED) {
                                try {
                                    // Configuration de l'impression
                                    Printer printer = Printer.getDefaultPrinter();
                                    if (printer != null) {
                                        PageLayout pageLayout = printer.createPageLayout(
                                                Paper.A4,
                                                PageOrientation.PORTRAIT,
                                                Printer.MarginType.DEFAULT
                                        );

                                        PrinterJob job = PrinterJob.createPrinterJob();
                                        if (job != null) {
                                            job.showPrintDialog(null);
                                            success[0] = job.printPage(pageLayout, webEngine.getScene().getRoot());
                                            job.endJob();
                                        }
                                    }
                                } catch (Exception e) {
                                    logger.error("Erreur lors de l'impression", e);
                                } finally {
                                    latch.countDown();
                                }
                            }
                        });
                    } catch (Exception e) {
                        logger.error("Erreur lors de la préparation de l'impression", e);
                        latch.countDown();
                    }
                });

                try {
                    latch.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Timeout lors de l'impression", e);
                }

                return success[0];

            } catch (Exception e) {
                logger.error("Erreur lors de l'impression du rapport", e);
                return false;
            }
        });
    }

    /**
     * Imprime un imprimé selon son type
     */
    public CompletableFuture<Boolean> imprimerImprimeParType(String typeImprime, Object donneesImprime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Début de l'impression de l'imprimé: {}", typeImprime);

                String htmlContent = genererHTMLImprimeParType(typeImprime, donneesImprime);

                // Simulation d'impression réussie
                // TODO: Implémenter l'impression réelle
                Thread.sleep(3000);
                return true;
            } catch (Exception e) {
                logger.error("Erreur lors de l'impression de l'imprimé: " + typeImprime, e);
                return false;
            }
        });
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Vérifie la disponibilité des imprimantes
     */
    public boolean isImprimanteDisponible() {
        return Printer.getDefaultPrinter() != null;
    }

    /**
     * Obtient la liste des imprimantes disponibles
     */
    public List<String> getImprimantesDisponibles() {
        return Printer.getAllPrinters().stream()
                .map(Printer::getName)
                .toList();
    }

    /**
     * Valide qu'un imprimé peut être imprimé
     */
    public boolean validerImprimePourImpression(String typeImprime, Object donneesImprime) {
        if (donneesImprime == null) {
            logger.warn("Données null pour l'imprimé {}", typeImprime);
            return false;
        }

        switch (typeImprime) {
            case "ETAT_REPARTITION_AFFAIRES":
                EtatRepartitionAffairesDTO etatAffaires = (EtatRepartitionAffairesDTO) donneesImprime;
                return etatAffaires.getAffaires() != null && !etatAffaires.getAffaires().isEmpty();

            case "TABLEAU_AMENDES_SERVICES":
                TableauAmendesParServicesDTO tableau = (TableauAmendesParServicesDTO) donneesImprime;
                return tableau.getServices() != null && !tableau.getServices().isEmpty();

            case "ETAT_CUMULE_AGENT":
                EtatCumuleParAgentDTO etatAgents = (EtatCumuleParAgentDTO) donneesImprime;
                return etatAgents.getAgents() != null && !etatAgents.getAgents().isEmpty();

            default:
                return true;
        }
    }

    /**
     * Génère les paramètres d'impression recommandés
     */
    public PageLayout getParametresImpressionRecommandes() {
        Printer defaultPrinter = Printer.getDefaultPrinter();
        if (defaultPrinter == null) {
            return null;
        }

        return defaultPrinter.createPageLayout(
                Paper.A4,
                PageOrientation.PORTRAIT,
                Printer.MarginType.DEFAULT
        );
    }

    /**
     * Génère le HTML complet pour l'impression d'un rapport
     */
    private String genererHTMLRapportComplet(RapportRepartitionDTO rapport) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>");
        html.append("<html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<title>Rapport de Rétrocession</title>");
        html.append("<style>");
        html.append(getStylesImpression());
        html.append("</style>");
        html.append("</head><body>");

        // En-tête officielle
        html.append("<div class='header'>");
        html.append("<h1>RAPPORT DE RÉTROCESSION</h1>");
        html.append("<p>Période : ").append(rapport.getPeriodeLibelle()).append("</p>");
        html.append("<p>Date de génération : ").append(DateFormatter.formatDateTime(LocalDateTime.now())).append("</p>");
        html.append("</div>");

        // Résumé exécutif
        html.append("<div class='summary'>");
        html.append("<h2>RÉSUMÉ EXÉCUTIF</h2>");
        html.append("<table>");
        html.append("<tr><td><strong>Nombre total d'affaires :</strong></td><td>").append(rapport.getNombreAffaires()).append("</td></tr>");
        html.append("<tr><td><strong>Nombre d'encaissements :</strong></td><td>").append(rapport.getNombreEncaissements()).append("</td></tr>");
        html.append("<tr><td><strong>Total encaissé :</strong></td><td><strong>").append(CurrencyFormatter.format(rapport.getTotalEncaisse())).append("</strong></td></tr>");
        html.append("<tr><td><strong>Part État (60%) :</strong></td><td>").append(CurrencyFormatter.format(rapport.getTotalEtat())).append("</td></tr>");
        html.append("<tr><td><strong>Part Collectivités (40%) :</strong></td><td>").append(CurrencyFormatter.format(rapport.getTotalCollectivite())).append("</td></tr>");
        html.append("</table>");
        html.append("</div>");

        // Détail des affaires
        html.append("<div class='details'>");
        html.append("<h2>DÉTAIL DES AFFAIRES</h2>");
        html.append("<table class='affaires-table'>");
        html.append("<thead>");
        html.append("<tr>");
        html.append("<th>N° Affaire</th>");
        html.append("<th>Date</th>");
        html.append("<th>Contrevenant</th>");
        html.append("<th>Type</th>");
        html.append("<th>Montant Amende</th>");
        html.append("<th>Part État</th>");
        html.append("<th>Part Collectivité</th>");
        html.append("<th>Chef Dossier</th>");
        html.append("<th>Bureau</th>");
        html.append("</tr>");
        html.append("</thead>");
        html.append("<tbody>");

        for (AffaireRepartitionDTO affaire : rapport.getAffaires()) {
            html.append("<tr>");
            html.append("<td>").append(escapeHtml(affaire.getNumeroAffaire())).append("</td>");
            html.append("<td>").append(DateFormatter.formatDate(affaire.getDateCreation())).append("</td>");
            html.append("<td>").append(escapeHtml(affaire.getContrevenantNom())).append("</td>");
            html.append("<td>").append(escapeHtml(affaire.getContraventionType())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getMontantAmende())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getPartEtat())).append("</td>");
            html.append("<td class='montant'>").append(CurrencyFormatter.format(affaire.getPartCollectivite())).append("</td>");
            html.append("<td>").append(escapeHtml(affaire.getChefDossier())).append("</td>");
            html.append("<td>").append(escapeHtml(affaire.getBureau())).append("</td>");
            html.append("</tr>");
        }

        html.append("</tbody>");
        html.append("</table>");
        html.append("</div>");

        // Pied de page
        html.append("<div class='footer'>");
        html.append("<p>Rapport généré par le système de gestion contentieuse - ").append(LocalDateTime.now().getYear()).append("</p>");
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Assemble le HTML final avec le template de base
     */
    private String assemblerHTML(String titre, String titreHeader, String periode,
                                 LocalDateTime dateGeneration, String contenu) {
        String header = genererEnteteStandard(titreHeader, periode, dateGeneration);

        return BASE_TEMPLATE
                .replace("{{TITRE}}", titre)
                .replace("{{STYLES}}", getStylesImpression())
                .replace("{{HEADER}}", header)
                .replace("{{CONTENT}}", contenu)
                .replace("{{FOOTER}}", genererPiedPageStandard());
    }

    /**
     * Génère l'en-tête standard pour les imprimés
     */
    private String genererEnteteStandard(String titre, String periode, LocalDateTime dateGeneration) {
        return "<div class='header'>" +
                "<h1>" + titre + "</h1>" +
                "<p>Période : " + periode + "</p>" +
                "<p>Généré le : " + DateFormatter.formatDateTime(dateGeneration) + "</p>" +
                "</div>";
    }

    /**
     * Génère le pied de page standard
     */
    private String genererPiedPageStandard() {
        return "<div class='footer'>" +
                "<p>Direction de la Régulation - Système de Gestion Contentieuse</p>" +
                "<p>Page générée automatiquement - " + LocalDateTime.now().getYear() + "</p>" +
                "</div>";
    }

    /**
     * Retourne les styles CSS pour l'impression
     */
    private String getStylesImpression() {
        return "@page { size: A4; margin: 2cm; }" +
                "body { font-family: Arial, sans-serif; font-size: 12px; line-height: 1.4; }" +
                ".header { text-align: center; border-bottom: 2px solid #000; padding-bottom: 10px; margin-bottom: 20px; }" +
                ".header h1 { margin: 0; font-size: 18px; font-weight: bold; }" +
                ".summary { margin-bottom: 20px; }" +
                ".summary table { width: 100%; border-collapse: collapse; }" +
                ".summary td { padding: 5px; border-bottom: 1px solid #ddd; }" +
                ".data-table, .affaires-table { width: 100%; border-collapse: collapse; font-size: 10px; }" +
                ".data-table th, .data-table td, .affaires-table th, .affaires-table td { border: 1px solid #000; padding: 4px; text-align: left; }" +
                ".data-table th, .affaires-table th { background-color: #f0f0f0; font-weight: bold; }" +
                ".montant, .nombre { text-align: right; }" +
                ".total { font-weight: bold; background-color: #f5f5f5; }" +
                ".totaux { margin-top: 20px; }" +
                ".totaux h3 { margin-bottom: 10px; }" +
                ".footer { margin-top: 30px; text-align: center; font-size: 10px; color: #666; }";
    }

    /**
     * Crée un WebEngine pour l'impression
     */
    private WebEngine createPrintWebEngine() {
        WebView webView = new WebView();
        return webView.getEngine();
    }

    /**
     * Échappe les caractères HTML
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}