package com.regulation.contentieux.service;

import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.concurrent.Worker;
import javafx.application.Platform;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.dao.EncaissementDAO;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Encaissement;
import com.regulation.contentieux.service.RapportService.*;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import com.regulation.contentieux.service.RapportService.*;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import javafx.concurrent.Task;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service d'impression pour les rapports et documents - VERSION CORRIGÉE
 * Gestion de l'impression PDF et directe
 */
public class PrintService {

    private static final Logger logger = LoggerFactory.getLogger(PrintService.class);

    private final RapportService rapportService;
    private final AffaireDAO affaireDAO;
    private final EncaissementDAO encaissementDAO;

    public PrintService() {
        this.rapportService = new RapportService();
        this.affaireDAO = new AffaireDAO();
        this.encaissementDAO = new EncaissementDAO();
    }

    // ==================== IMPRESSION DIRECTE ====================

    /**
     * Imprime un rapport de rétrocession
     */
    public CompletableFuture<Boolean> printRapportRetrocession(LocalDate dateDebut, LocalDate dateFin) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Génération du rapport de rétrocession: {} à {}", dateDebut, dateFin);

                // Génération des données
                EtatRepartitionAffairesDTO rapport = rapportService.genererEtatRepartitionAffaires(dateDebut, dateFin);

                // Création du contenu HTML
                String htmlContent = generateHtmlRapportRetrocession(rapport);

                // Impression
                return printHtmlContent(htmlContent, "Rapport de Rétrocession");

            } catch (Exception e) {
                logger.error("Erreur lors de l'impression du rapport de rétrocession", e);
                return false;
            }
        });
    }

    /**
     * Imprime un état des indicateurs réels
     */
    public CompletableFuture<Boolean> printEtatIndicateurs(LocalDate dateDebut, LocalDate dateFin) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Génération de l'état des indicateurs: {} à {}", dateDebut, dateFin);

                // Génération des données
                EtatIndicateursReelsDTO etat = rapportService.genererEtatIndicateursReels(dateDebut, dateFin);

                // Création du contenu HTML
                String htmlContent = generateHtmlEtatIndicateurs(etat);

                // Impression
                return printHtmlContent(htmlContent, "État des Indicateurs Réels");

            } catch (Exception e) {
                logger.error("Erreur lors de l'impression de l'état des indicateurs", e);
                return false;
            }
        });
    }

    /**
     * Imprime un reçu d'encaissement
     */
    public CompletableFuture<Boolean> printRecuEncaissement(Long encaissementId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Génération du reçu d'encaissement: {}", encaissementId);

                // Récupération de l'encaissement
                Encaissement encaissement = encaissementDAO.findById(encaissementId)
                        .orElseThrow(() -> new IllegalArgumentException("Encaissement non trouvé: " + encaissementId));

                // Récupération de l'affaire
                Affaire affaire = affaireDAO.findById(encaissement.getAffaireId())
                        .orElseThrow(() -> new IllegalArgumentException("Affaire non trouvée: " + encaissement.getAffaireId()));

                // Création du contenu HTML
                String htmlContent = generateHtmlRecuEncaissement(encaissement, affaire);

                // Impression
                return printHtmlContent(htmlContent, "Reçu d'Encaissement");

            } catch (Exception e) {
                logger.error("Erreur lors de l'impression du reçu d'encaissement", e);
                return false;
            }
        });
    }

    /**
     * Imprime une liste d'affaires
     */
    public CompletableFuture<Boolean> printListeAffaires(List<Affaire> affaires, String titre) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Impression d'une liste de {} affaires", affaires.size());

                // Création du contenu HTML
                String htmlContent = generateHtmlListeAffaires(affaires, titre);

                // Impression
                return printHtmlContent(htmlContent, titre);

            } catch (Exception e) {
                logger.error("Erreur lors de l'impression de la liste d'affaires", e);
                return false;
            }
        });
    }

    // ==================== IMPRESSION HTML ====================

    /**
     * Imprime du contenu HTML
     */
    private boolean printHtmlContent(String htmlContent, String jobName) {
        try {
            // Création de la WebView pour le rendu
            WebView webView = new WebView();
            WebEngine webEngine = webView.getEngine();

            // Chargement du contenu HTML
            webEngine.loadContent(htmlContent);

            // Attendre que le contenu soit chargé
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                    // Lancer l'impression
                    printNode(webView, jobName);
                }
            });

            return true;

        } catch (Exception e) {
            logger.error("Erreur lors de l'impression HTML", e);
            return false;
        }
    }

    /**
     * Imprime un nœud JavaFX
     */
    private boolean printNode(Node node, String jobName) {
        try {
            // Configuration de l'imprimante
            Printer printer = Printer.getDefaultPrinter();
            if (printer == null) {
                logger.error("Aucune imprimante disponible");
                return false;
            }

            // Configuration du job d'impression
            PrinterJob job = PrinterJob.createPrinterJob(printer);
            if (job == null) {
                logger.error("Impossible de créer le job d'impression");
                return false;
            }

            job.getJobSettings().setJobName(jobName);

            // Configuration de la page
            PageLayout pageLayout = printer.createPageLayout(
                    Paper.A4,
                    PageOrientation.PORTRAIT,
                    Printer.MarginType.DEFAULT
            );
            job.getJobSettings().setPageLayout(pageLayout);

            // Impression
            boolean success = job.printPage(node);
            if (success) {
                job.endJob();
                logger.info("Impression réussie: {}", jobName);
            } else {
                logger.error("Échec de l'impression: {}", jobName);
            }

            return success;

        } catch (Exception e) {
            logger.error("Erreur lors de l'impression du nœud", e);
            return false;
        }
    }

    // ==================== GÉNÉRATION HTML ====================

    /**
     * Génère le HTML pour le rapport de rétrocession
     */
    private String generateHtmlRapportRetrocession(EtatRepartitionAffairesDTO rapport) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html><html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<title>Rapport de Rétrocession</title>");
        html.append(getDefaultStyles());
        html.append("</head><body>");

        // En-tête
        html.append("<div class='header'>");
        html.append("<h1>RÉPUBLIQUE GABONAISE</h1>");
        html.append("<h2>MINISTÈRE DE L'ÉCONOMIE ET DE LA RELANCE</h2>");
        html.append("<h3>DIRECTION GÉNÉRALE DE LA CONCURRENCE ET DE LA CONSOMMATION</h3>");
        html.append("<br><h2>RAPPORT DE RÉTROCESSION</h2>");
        html.append("<p>Période du ").append(DateFormatter.formatDate(rapport.getDateDebut()));
        html.append(" au ").append(DateFormatter.formatDate(rapport.getDateFin())).append("</p>");
        html.append("</div>");

        // Tableau des affaires
        html.append("<table class='data-table'>");
        html.append("<thead>");
        html.append("<tr>");
        html.append("<th>N° Encaissement</th>");
        html.append("<th>Date</th>");
        html.append("<th>N° Affaire</th>");
        html.append("<th>Produit Disponible</th>");
        html.append("<th>Direction</th>");
        html.append("<th>Indicateur</th>");
        html.append("<th>Produit Net</th>");
        html.append("<th>FLCF</th>");
        html.append("<th>Trésor</th>");
        html.append("</tr>");
        html.append("</thead>");
        html.append("<tbody>");

        for (AffaireRepartitionCompleteDTO affaire : rapport.getAffaires()) {
            html.append("<tr>");
            html.append("<td>").append(affaire.getNumeroEncaissement()).append("</td>");
            html.append("<td>").append(DateFormatter.formatDate(affaire.getDateEncaissement())).append("</td>");
            html.append("<td>").append(affaire.getNumeroAffaire()).append("</td>");
            html.append("<td class='amount'>").append(CurrencyFormatter.format(affaire.getProduitDisponible())).append("</td>");
            html.append("<td>").append(affaire.getDirectionDepartementale()).append("</td>");
            html.append("<td class='amount'>").append(CurrencyFormatter.format(affaire.getIndicateur())).append("</td>");
            html.append("<td class='amount'>").append(CurrencyFormatter.format(affaire.getProduitNet())).append("</td>");
            html.append("<td class='amount'>").append(CurrencyFormatter.format(affaire.getFlcf())).append("</td>");
            html.append("<td class='amount'>").append(CurrencyFormatter.format(affaire.getTresor())).append("</td>");
            html.append("</tr>");
        }

        html.append("</tbody>");
        html.append("<tfoot>");
        html.append("<tr class='total-row'>");
        html.append("<td colspan='3'><strong>TOTAUX</strong></td>");
        html.append("<td class='amount'><strong>").append(CurrencyFormatter.format(rapport.getTotaux().getTotalProduitDisponible())).append("</strong></td>");
        html.append("<td></td>");
        html.append("<td class='amount'><strong>").append(CurrencyFormatter.format(rapport.getTotaux().getTotalIndicateur())).append("</strong></td>");
        html.append("<td class='amount'><strong>").append(CurrencyFormatter.format(rapport.getTotaux().getTotalProduitNet())).append("</strong></td>");
        html.append("<td class='amount'><strong>").append(CurrencyFormatter.format(rapport.getTotaux().getTotalFlcf())).append("</strong></td>");
        html.append("<td class='amount'><strong>").append(CurrencyFormatter.format(rapport.getTotaux().getTotalTresor())).append("</strong></td>");
        html.append("</tr>");
        html.append("</tfoot>");
        html.append("</table>");

        // Pied de page
        html.append("<div class='footer'>");
        html.append("<p>Rapport généré le ").append(DateFormatter.formatDateTime(rapport.getDateGeneration().atStartOfDay())).append("</p>");
        html.append("<p>Total des affaires: ").append(rapport.getTotalAffaires()).append("</p>");
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Génère le HTML pour l'état des indicateurs
     */
    private String generateHtmlEtatIndicateurs(EtatIndicateursReelsDTO etat) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html><html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<title>État des Indicateurs Réels</title>");
        html.append(getDefaultStyles());
        html.append("</head><body>");

        // En-tête
        html.append("<div class='header'>");
        html.append("<h1>RÉPUBLIQUE GABONAISE</h1>");
        html.append("<h2>MINISTÈRE DE L'ÉCONOMIE ET DE LA RELANCE</h2>");
        html.append("<h3>DIRECTION GÉNÉRALE DE LA CONCURRENCE ET DE LA CONSOMMATION</h3>");
        html.append("<br><h2>ÉTAT DES INDICATEURS RÉELS</h2>");
        html.append("<p>").append(etat.getPeriodeLibelle()).append("</p>");
        html.append("</div>");

        // Tableau par service
        for (ServiceIndicateurDTO service : etat.getServices()) {
            html.append("<h3>Service: ").append(service.getNomService()).append("</h3>");

            html.append("<table class='data-table'>");
            html.append("<thead>");
            html.append("<tr>");
            html.append("<th>Section</th>");
            html.append("<th>Nombre d'Affaires</th>");
            html.append("<th>Montant Encaissement</th>");
            html.append("<th>Part Indicateur</th>");
            html.append("</tr>");
            html.append("</thead>");
            html.append("<tbody>");

            for (SectionIndicateurDTO section : service.getSections()) {
                html.append("<tr>");
                html.append("<td>").append(section.getNomSection()).append("</td>");
                html.append("<td class='number'>").append(section.getNombreAffaires()).append("</td>");
                html.append("<td class='amount'>").append(CurrencyFormatter.format(section.getMontantEncaissement())).append("</td>");
                html.append("<td class='amount'>").append(CurrencyFormatter.format(section.getPartIndicateur())).append("</td>");
                html.append("</tr>");
            }

            html.append("</tbody>");
            html.append("<tfoot>");
            html.append("<tr class='total-row'>");
            html.append("<td><strong>Total ").append(service.getNomService()).append("</strong></td>");
            html.append("<td class='number'><strong>").append(service.getTotalAffairesService()).append("</strong></td>");
            html.append("<td class='amount'><strong>").append(CurrencyFormatter.format(service.getTotalMontantService())).append("</strong></td>");
            html.append("<td class='amount'><strong>").append(CurrencyFormatter.format(service.getTotalPartIndicateurService())).append("</strong></td>");
            html.append("</tr>");
            html.append("</tfoot>");
            html.append("</table>");
            html.append("<br>");
        }

        // Total général
        html.append("<table class='data-table total-general'>");
        html.append("<tfoot>");
        html.append("<tr class='total-row'>");
        html.append("<td><strong>TOTAL GÉNÉRAL</strong></td>");
        html.append("<td class='number'><strong>").append(etat.getTotalAffaires()).append("</strong></td>");
        html.append("<td class='amount'><strong>").append(CurrencyFormatter.format(etat.getTotalMontantEncaissement())).append("</strong></td>");
        html.append("<td class='amount'><strong>").append(CurrencyFormatter.format(etat.getTotalPartIndicateur())).append("</strong></td>");
        html.append("</tr>");
        html.append("</tfoot>");
        html.append("</table>");

        // Pied de page
        html.append("<div class='footer'>");
        html.append("<p>État généré le ").append(DateFormatter.formatDateTime(etat.getDateGeneration().atStartOfDay())).append("</p>");
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Génère le HTML pour un reçu d'encaissement
     */
    private String generateHtmlRecuEncaissement(Encaissement encaissement, Affaire affaire) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html><html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<title>Reçu d'Encaissement</title>");
        html.append(getReceiptStyles());
        html.append("</head><body>");

        // En-tête
        html.append("<div class='header'>");
        html.append("<h1>RÉPUBLIQUE GABONAISE</h1>");
        html.append("<h2>DIRECTION GÉNÉRALE DE LA CONCURRENCE ET DE LA CONSOMMATION</h2>");
        html.append("<br><h2>REÇU D'ENCAISSEMENT</h2>");
        html.append("<p>N° ").append(encaissement.getReference()).append("</p>");
        html.append("</div>");

        // Informations de l'affaire
        html.append("<div class='info-section'>");
        html.append("<h3>Informations de l'Affaire</h3>");
        html.append("<p><strong>Numéro d'affaire:</strong> ").append(affaire.getNumeroAffaire()).append("</p>");
        html.append("<p><strong>Date de création:</strong> ").append(DateFormatter.formatDate(affaire.getDateCreation())).append("</p>");
        html.append("<p><strong>Montant de l'amende:</strong> ").append(CurrencyFormatter.format(affaire.getMontantAmendeTotal())).append("</p>");
        html.append("</div>");

        // Informations de l'encaissement
        html.append("<div class='info-section'>");
        html.append("<h3>Détails de l'Encaissement</h3>");
        html.append("<p><strong>Date d'encaissement:</strong> ").append(DateFormatter.formatDate(encaissement.getDateEncaissement())).append("</p>");
        html.append("<p><strong>Montant encaissé:</strong> ").append(CurrencyFormatter.format(encaissement.getMontantEncaisse())).append("</p>");
        html.append("<p><strong>Mode de règlement:</strong> ").append(encaissement.getModeReglement().getLibelle()).append("</p>");
        if (encaissement.getObservations() != null && !encaissement.getObservations().trim().isEmpty()) {
            html.append("<p><strong>Observations:</strong> ").append(encaissement.getObservations()).append("</p>");
        }
        html.append("</div>");

        // Signature
        html.append("<div class='signature'>");
        html.append("<p>Date: ").append(DateFormatter.formatDate(LocalDate.now())).append("</p>");
        html.append("<br><br>");
        html.append("<p>L'Agent Comptable</p>");
        html.append("<br><br><br>");
        html.append("<p>_________________________</p>");
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Génère le HTML pour une liste d'affaires
     */
    private String generateHtmlListeAffaires(List<Affaire> affaires, String titre) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html><html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<title>").append(titre).append("</title>");
        html.append(getDefaultStyles());
        html.append("</head><body>");

        // En-tête
        html.append("<div class='header'>");
        html.append("<h1>RÉPUBLIQUE GABONAISE</h1>");
        html.append("<h2>DIRECTION GÉNÉRALE DE LA CONCURRENCE ET DE LA CONSOMMATION</h2>");
        html.append("<br><h2>").append(titre.toUpperCase()).append("</h2>");
        html.append("<p>Date d'édition: ").append(DateFormatter.formatDate(LocalDate.now())).append("</p>");
        html.append("</div>");

        // Tableau des affaires
        html.append("<table class='data-table'>");
        html.append("<thead>");
        html.append("<tr>");
        html.append("<th>N° Affaire</th>");
        html.append("<th>Date Création</th>");
        html.append("<th>Montant Amende</th>");
        html.append("<th>Statut</th>");
        html.append("</tr>");
        html.append("</thead>");
        html.append("<tbody>");

        double totalAmende = 0.0;
        for (Affaire affaire : affaires) {
            html.append("<tr>");
            html.append("<td>").append(affaire.getNumeroAffaire()).append("</td>");
            html.append("<td>").append(DateFormatter.formatDate(affaire.getDateCreation())).append("</td>");
            html.append("<td class='amount'>").append(CurrencyFormatter.format(affaire.getMontantAmendeTotal())).append("</td>");
            html.append("<td>").append(affaire.getStatut().getLibelle()).append("</td>");
            html.append("</tr>");

            totalAmende += affaire.getMontantAmendeTotal() != null ? affaire.getMontantAmendeTotal() : 0.0;
        }

        html.append("</tbody>");
        html.append("<tfoot>");
        html.append("<tr class='total-row'>");
        html.append("<td colspan='2'><strong>TOTAL (").append(affaires.size()).append(" affaires)</strong></td>");
        html.append("<td class='amount'><strong>").append(CurrencyFormatter.format(totalAmende)).append("</strong></td>");
        html.append("<td></td>");
        html.append("</tr>");
        html.append("</tfoot>");
        html.append("</table>");

        // Pied de page
        html.append("<div class='footer'>");
        html.append("<p>Rapport généré le ").append(DateFormatter.formatDateTime(LocalDate.now().atStartOfDay())).append("</p>");
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    // ==================== STYLES CSS ====================

    /**
     * Styles CSS par défaut pour les rapports
     */
    private String getDefaultStyles() {
        return """
            <style>
                body { 
                    font-family: Arial, sans-serif; 
                    font-size: 12px; 
                    margin: 20px; 
                    line-height: 1.4;
                }
                .header { 
                    text-align: center; 
                    margin-bottom: 30px; 
                    border-bottom: 2px solid #000;
                    padding-bottom: 20px;
                }
                .header h1 { 
                    font-size: 16px; 
                    margin: 5px 0; 
                    font-weight: bold;
                }
                .header h2 { 
                    font-size: 14px; 
                    margin: 3px 0; 
                    font-weight: bold;
                }
                .header h3 { 
                    font-size: 12px; 
                    margin: 3px 0; 
                    font-weight: normal;
                }
                .data-table { 
                    width: 100%; 
                    border-collapse: collapse; 
                    margin: 20px 0;
                }
                .data-table th, .data-table td { 
                    border: 1px solid #000; 
                    padding: 6px; 
                    text-align: left;
                }
                .data-table th { 
                    background-color: #f0f0f0; 
                    font-weight: bold; 
                    text-align: center;
                }
                .amount { 
                    text-align: right; 
                    font-family: 'Courier New', monospace;
                }
                .number { 
                    text-align: center;
                }
                .total-row { 
                    background-color: #e8e8e8; 
                    font-weight: bold;
                }
                .total-general {
                    margin-top: 30px;
                    border: 2px solid #000;
                }
                .footer { 
                    margin-top: 40px; 
                    text-align: center; 
                    font-size: 10px;
                    border-top: 1px solid #ccc;
                    padding-top: 10px;
                }
                @media print {
                    body { margin: 0; }
                    .header { page-break-after: avoid; }
                    .data-table { page-break-inside: avoid; }
                }
            </style>
        """;
    }

    /**
     * Styles CSS pour les reçus
     */
    private String getReceiptStyles() {
        return """
            <style>
                body { 
                    font-family: Arial, sans-serif; 
                    font-size: 14px; 
                    margin: 20px; 
                    line-height: 1.6;
                }
                .header { 
                    text-align: center; 
                    margin-bottom: 40px; 
                    border-bottom: 2px solid #000;
                    padding-bottom: 20px;
                }
                .header h1 { 
                    font-size: 18px; 
                    margin: 8px 0; 
                }
                .header h2 { 
                    font-size: 16px; 
                    margin: 5px 0; 
                }
                .info-section { 
                    margin: 30px 0; 
                    padding: 15px;
                    border: 1px solid #ccc;
                }
                .info-section h3 { 
                    background-color: #f0f0f0; 
                    margin: -15px -15px 15px -15px;
                    padding: 10px 15px;
                    font-size: 16px;
                }
                .info-section p { 
                    margin: 10px 0; 
                }
                .signature { 
                    margin-top: 60px; 
                    text-align: right;
                    width: 50%;
                    float: right;
                }
                @media print {
                    body { margin: 0; }
                }
            </style>
        """;
    }

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
     * Retourne les styles CSS pour l'impression
     */
    private String getStylesImpression() {
        return """
        @page { 
            size: A4; 
            margin: 2cm; 
        }
        body { 
            font-family: Arial, sans-serif; 
            font-size: 12px; 
            line-height: 1.4; 
        }
        .header { 
            text-align: center; 
            border-bottom: 2px solid #000; 
            padding-bottom: 10px; 
            margin-bottom: 20px; 
        }
        .header h1 { 
            margin: 0; 
            font-size: 18px; 
            font-weight: bold; 
        }
        .summary { 
            margin-bottom: 20px; 
        }
        .summary table { 
            width: 100%; 
            border-collapse: collapse; 
        }
        .summary td { 
            padding: 5px; 
            border-bottom: 1px solid #ddd; 
        }
        .affaires-table { 
            width: 100%; 
            border-collapse: collapse; 
            font-size: 10px; 
        }
        .affaires-table th, .affaires-table td { 
            border: 1px solid #000; 
            padding: 4px; 
            text-align: left; 
        }
        .affaires-table th { 
            background-color: #f0f0f0; 
            font-weight: bold; 
        }
        .montant { 
            text-align: right; 
        }
        .footer { 
            margin-top: 30px; 
            text-align: center; 
            font-size: 10px; 
            color: #666; 
        }
        """;
    }

    // ==================== UTILITAIRES ====================

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

    /**
     * Vérifie la disponibilité des imprimantes
     */
    public boolean isImprimanteDisponible() {
        return Printer.getDefaultPrinter() != null;
    }

    /**
     * Obtient la liste des imprimantes disponibles
     */
    public List<Printer> getImprimantesDisponibles() {
        return Printer.getAllPrinters().stream().toList();
    }

    /**
     * Obtient l'imprimante par défaut
     */
    public Printer getImprimanteParDefaut() {
        return Printer.getDefaultPrinter();
    }
}