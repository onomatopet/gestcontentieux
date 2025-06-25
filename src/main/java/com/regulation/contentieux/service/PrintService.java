package com.regulation.contentieux.service;

// Import manquants pour l'aperçu
import javafx.collections.ObservableSet;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.transform.Scale;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service d'impression pour l'application
 * Gère l'impression des rapports et documents
 */
public class PrintService {

    private static final Logger logger = LoggerFactory.getLogger(PrintService.class);

    /**
     * Imprime le contenu d'une WebView
     */
    public boolean printWebView(WebView webView) {
        if (webView == null) {
            logger.error("WebView null, impossible d'imprimer");
            return false;
        }

        // Créer le job d'impression
        PrinterJob printerJob = PrinterJob.createPrinterJob();
        if (printerJob == null) {
            logger.error("Impossible de créer le job d'impression");
            return false;
        }

        // Afficher le dialogue d'impression
        boolean proceed = printerJob.showPrintDialog(webView.getScene().getWindow());
        if (!proceed) {
            logger.info("Impression annulée par l'utilisateur");
            return false;
        }

        // Configuration de la page
        PageLayout pageLayout = printerJob.getJobSettings().getPageLayout();
        Paper paper = pageLayout.getPaper();

        // Marges (en points - 72 points = 1 inch)
        double marginTop = 36; // 0.5 inch
        double marginBottom = 36;
        double marginLeft = 36;
        double marginRight = 36;

        // Zone imprimable
        double printableWidth = paper.getWidth() - marginLeft - marginRight;
        double printableHeight = paper.getHeight() - marginTop - marginBottom;

        // Configuration de l'impression
        printerJob.getJobSettings().setJobName("Rapport Contentieux");

        // Attendre que le contenu soit complètement chargé
        WebEngine engine = webView.getEngine();
        if (engine.getLoadWorker().getState() != Worker.State.SUCCEEDED) {
            logger.warn("Le contenu n'est pas complètement chargé");

            // Attendre le chargement
            CompletableFuture<Boolean> loadFuture = new CompletableFuture<>();
            engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED || newState == Worker.State.FAILED) {
                    loadFuture.complete(newState == Worker.State.SUCCEEDED);
                }
            });

            try {
                boolean loaded = loadFuture.get(10, TimeUnit.SECONDS);
                if (!loaded) {
                    logger.error("Échec du chargement du contenu");
                    printerJob.cancelJob();
                    return false;
                }
            } catch (Exception e) {
                logger.error("Timeout lors du chargement", e);
                printerJob.cancelJob();
                return false;
            }
        }

        // Effectuer l'impression
        boolean success = false;
        try {
            // Ajuster l'échelle si nécessaire
            double scaleX = printableWidth / webView.getBoundsInLocal().getWidth();
            double scaleY = printableHeight / webView.getBoundsInLocal().getHeight();
            double scale = Math.min(scaleX, scaleY);

            if (scale < 1.0) {
                // Appliquer l'échelle pour que le contenu tienne sur la page
                webView.getTransforms().add(new Scale(scale, scale));
            }

            // Imprimer
            success = printerJob.printPage(webView);

            if (success) {
                printerJob.endJob();
                logger.info("Impression réussie");
            } else {
                logger.error("Échec de l'impression de la page");
                printerJob.cancelJob();
            }

            // Retirer la transformation d'échelle
            if (scale < 1.0) {
                webView.getTransforms().clear();
            }

        } catch (Exception e) {
            logger.error("Erreur lors de l'impression", e);
            printerJob.cancelJob();
        }

        return success;
    }



    /**
     * Imprime du contenu HTML
     */
    public boolean printHtmlContent(String htmlContent, String jobName) {
        // Créer une WebView temporaire
        WebView tempWebView = new WebView();
        WebEngine engine = tempWebView.getEngine();

        // Charger le contenu
        engine.loadContent(wrapHtmlForPrint(htmlContent));

        // Attendre le chargement complet
        CompletableFuture<Boolean> printFuture = new CompletableFuture<>();

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                Platform.runLater(() -> {
                    boolean success = printWebViewWithJobName(tempWebView, jobName);
                    printFuture.complete(success);
                });
            } else if (newState == Worker.State.FAILED) {
                printFuture.complete(false);
            }
        });

        try {
            return printFuture.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Erreur lors de l'impression du HTML", e);
            return false;
        }
    }

    /**
     * Imprime une WebView avec un nom de job spécifique
     */
    private boolean printWebViewWithJobName(WebView webView, String jobName) {
        PrinterJob printerJob = PrinterJob.createPrinterJob();
        if (printerJob == null) {
            return false;
        }

        printerJob.getJobSettings().setJobName(jobName != null ? jobName : "Document");

        if (printerJob.showPrintDialog(null)) {
            boolean success = printerJob.printPage(webView);
            if (success) {
                printerJob.endJob();
            }
            return success;
        }

        return false;
    }

    /**
     * Imprime un nœud JavaFX
     */
    public boolean printNode(Node node, String jobName) {
        if (node == null) {
            logger.error("Node null, impossible d'imprimer");
            return false;
        }

        PrinterJob printerJob = PrinterJob.createPrinterJob();
        if (printerJob == null) {
            logger.error("Impossible de créer le job d'impression");
            return false;
        }

        printerJob.getJobSettings().setJobName(jobName != null ? jobName : "Document");

        if (printerJob.showPrintDialog(node.getScene().getWindow())) {
            // Configuration de la mise en page
            PageLayout pageLayout = printerJob.getJobSettings().getPageLayout();
            double scaleX = pageLayout.getPrintableWidth() / node.getBoundsInParent().getWidth();
            double scaleY = pageLayout.getPrintableHeight() / node.getBoundsInParent().getHeight();
            double scale = Math.min(scaleX, scaleY);

            if (scale < 1.0) {
                node.getTransforms().add(new Scale(scale, scale));
            }

            boolean success = printerJob.printPage(node);

            if (scale < 1.0) {
                node.getTransforms().clear();
            }

            if (success) {
                printerJob.endJob();
                logger.info("Impression du nœud réussie");
            } else {
                logger.error("Échec de l'impression du nœud");
            }

            return success;
        }

        return false;
    }

    /**
     * Configure les paramètres d'impression par défaut
     */
    public void configurePrintSettings(PrinterJob job) {
        JobSettings jobSettings = job.getJobSettings();

        // Orientation
        jobSettings.setPageLayout(job.getPrinter().createPageLayout(
                Paper.A4,
                PageOrientation.PORTRAIT,
                Printer.MarginType.DEFAULT
        ));

        // Qualité
        jobSettings.setPrintQuality(PrintQuality.HIGH);

        // Couleur
        jobSettings.setPrintColor(PrintColor.COLOR);

        // Recto-verso si disponible
        if (job.getPrinter().getPrinterAttributes().getSupportedPrintSides().contains(PrintSides.DUPLEX)) {
            jobSettings.setPrintSides(PrintSides.DUPLEX);
        }
    }

    /**
     * Obtient la liste des imprimantes disponibles
     */
    public ObservableSet<Printer> getAvailablePrinters() {
        return Printer.getAllPrinters();
    }

    /**
     * Obtient l'imprimante par défaut
     */
    public Printer getDefaultPrinter() {
        return Printer.getDefaultPrinter();
    }

    /**
     * Enveloppe le HTML pour l'impression
     */
    private String wrapHtmlForPrint(String htmlContent) {
        if (htmlContent.contains("<html>")) {
            return htmlContent;
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    @media print {
                        body { 
                            margin: 0;
                            font-family: Arial, sans-serif;
                            font-size: 10pt;
                            line-height: 1.4;
                        }
                        table { 
                            page-break-inside: avoid;
                            border-collapse: collapse;
                            width: 100%;
                        }
                        tr { page-break-inside: avoid; }
                        td, th { 
                            padding: 4px 8px;
                            border: 1px solid #ddd;
                        }
                        th {
                            background-color: #f0f0f0;
                            font-weight: bold;
                        }
                        h1, h2, h3 { page-break-after: avoid; }
                        .no-print { display: none; }
                        .page-break { page-break-after: always; }
                    }
                    
                    @page {
                        size: A4;
                        margin: 1.5cm;
                    }
                </style>
            </head>
            <body>
            """ + htmlContent + """
            </body>
            </html>
            """;
    }

    /**
     * Aperçu avant impression
     */
    public void showPrintPreview(String htmlContent, String title) {
        Platform.runLater(() -> {
            try {
                // Créer une nouvelle fenêtre pour l'aperçu
                Stage previewStage = new Stage();
                previewStage.setTitle("Aperçu avant impression - " + title);

                WebView previewWebView = new WebView();
                WebEngine engine = previewWebView.getEngine();
                engine.loadContent(wrapHtmlForPrint(htmlContent));

                // Barre d'outils
                ToolBar toolBar = new ToolBar();

                Button printButton = new Button("Imprimer");
                printButton.setOnAction(e -> {
                    if (printWebView(previewWebView)) {
                        previewStage.close();
                    }
                });

                Button closeButton = new Button("Fermer");
                closeButton.setOnAction(e -> previewStage.close());

                toolBar.getItems().addAll(printButton, new Separator(), closeButton);

                // Layout
                VBox root = new VBox();
                root.getChildren().addAll(toolBar, previewWebView);
                VBox.setVgrow(previewWebView, Priority.ALWAYS);

                Scene scene = new Scene(root, 800, 600);
                previewStage.setScene(scene);
                previewStage.show();

            } catch (Exception e) {
                logger.error("Erreur lors de l'affichage de l'aperçu", e);
            }
        });
    }
}