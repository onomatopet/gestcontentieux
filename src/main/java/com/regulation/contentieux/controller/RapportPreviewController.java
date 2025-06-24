package com.regulation.contentieux.controller;

import com.regulation.contentieux.service.ExportService;
import com.regulation.contentieux.service.PrintService;
import com.regulation.contentieux.util.AlertUtil;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/**
 * Contrôleur pour la fenêtre d'aperçu des rapports
 * Gère l'affichage, le zoom, la navigation et les actions d'export/impression
 */
public class RapportPreviewController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(RapportPreviewController.class);

    // === COMPOSANTS FXML ===

    // En-tête
    @FXML private Label titreRapportLabel;
    @FXML private Label detailsRapportLabel;
    @FXML private Label nombrePagesLabel;
    @FXML private Label tailleDocumentLabel;

    // Contrôles de zoom
    @FXML private Button zoomOutButton;
    @FXML private Button zoomInButton;
    @FXML private Button zoomFitButton;
    @FXML private Label zoomLabel;

    // Navigation
    @FXML private Button premierePage;
    @FXML private Button pagePrecedente;
    @FXML private TextField pageActuelleField;
    @FXML private Label totalPagesLabel;
    @FXML private Button pageSuivante;
    @FXML private Button dernierePage;

    // Options d'affichage
    @FXML private ToggleButton modePortraitButton;
    @FXML private ToggleButton modePaysageButton;
    @FXML private CheckBox afficherReglesCheckBox;

    // WebView et overlays
    @FXML private ScrollPane previewScrollPane;
    @FXML private WebView previewWebView;
    @FXML private VBox loadingOverlay;
    @FXML private VBox errorOverlay;
    @FXML private Label errorMessageLabel;
    @FXML private Button retryButton;

    // Miniatures (pour future implémentation)
    @FXML private VBox thumbnailPanel;
    @FXML private VBox thumbnailContainer;

    // Actions
    @FXML private Button imprimerButton;
    @FXML private Button exportPdfButton;
    @FXML private Button exportExcelButton;
    @FXML private Button fermerButton;

    // Barre de statut
    @FXML private Label statutLabel;
    @FXML private Label tempsGenerationLabel;
    @FXML private Label tailleMemoryLabel;
    @FXML private Label formatDocumentLabel;

    // === SERVICES ===
    private final ExportService exportService = new ExportService();
    private final PrintService printService = new PrintService();

    // === ÉTAT ===
    private WebEngine webEngine;
    private String htmlContent;
    private String titreRapport;
    private double zoomLevel = 1.0;
    private int pageActuelle = 1;
    private int totalPages = 1;
    private Stage dialogStage;

    // Données du rapport
    private Object rapportData;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisation du RapportPreviewController");

        initializeWebView();
        initializeZoomControls();
        initializeNavigationControls();
        initializeDisplayControls();
        setupEventHandlers();

        // État initial
        updateZoomLabel();
        updateNavigationState();
        showLoadingOverlay(false);
        showErrorOverlay(false);

        logger.info("RapportPreviewController initialisé avec succès");
    }

    /**
     * Initialise la WebView et son moteur
     */
    private void initializeWebView() {
        if (previewWebView != null) {
            webEngine = previewWebView.getEngine();

            // Listener pour le chargement des pages
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    onDocumentLoaded();
                } else if (newState == Worker.State.FAILED) {
                    onDocumentLoadFailed();
                }
            });

            // Configuration de la WebView
            previewWebView.setZoom(zoomLevel);
            previewWebView.setContextMenuEnabled(true);
        }
    }

    /**
     * Configure les contrôles de zoom
     */
    private void initializeZoomControls() {
        if (zoomOutButton != null) {
            zoomOutButton.setOnAction(e -> zoomOut());
        }
        if (zoomInButton != null) {
            zoomInButton.setOnAction(e -> zoomIn());
        }
        if (zoomFitButton != null) {
            zoomFitButton.setOnAction(e -> zoomToFit());
        }
    }

    /**
     * Configure les contrôles de navigation
     */
    private void initializeNavigationControls() {
        if (premierePage != null) {
            premierePage.setOnAction(e -> goToFirstPage());
        }
        if (pagePrecedente != null) {
            pagePrecedente.setOnAction(e -> goToPreviousPage());
        }
        if (pageSuivante != null) {
            pageSuivante.setOnAction(e -> goToNextPage());
        }
        if (dernierePage != null) {
            dernierePage.setOnAction(e -> goToLastPage());
        }

        // Validation du champ page actuelle
        if (pageActuelleField != null) {
            pageActuelleField.setOnAction(e -> goToPage());
            pageActuelleField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal.matches("\\d*")) {
                    pageActuelleField.setText(oldVal);
                }
            });
        }
    }

    /**
     * Configure les contrôles d'affichage
     */
    private void initializeDisplayControls() {
        // Mode portrait/paysage (future implémentation)
        if (modePortraitButton != null) {
            modePortraitButton.setOnAction(e -> setPortraitMode());
        }
        if (modePaysageButton != null) {
            modePaysageButton.setOnAction(e -> setLandscapeMode());
        }

        // Affichage des règles (future implémentation)
        if (afficherReglesCheckBox != null) {
            afficherReglesCheckBox.setOnAction(e -> toggleRulers());
        }
    }

    /**
     * Configure les gestionnaires d'événements
     */
    private void setupEventHandlers() {
        if (imprimerButton != null) {
            imprimerButton.setOnAction(e -> handleImprimer());
        }
        if (exportPdfButton != null) {
            exportPdfButton.setOnAction(e -> handleExportPdf());
        }
        if (exportExcelButton != null) {
            exportExcelButton.setOnAction(e -> handleExportExcel());
        }
        if (fermerButton != null) {
            fermerButton.setOnAction(e -> handleFermer());
        }
        if (retryButton != null) {
            retryButton.setOnAction(e -> handleRetry());
        }
    }

    // === MÉTHODES PUBLIQUES ===

    /**
     * Charge le contenu HTML dans la WebView
     */
    public void loadContent(String htmlContent, String titre, Object rapportData) {
        this.htmlContent = htmlContent;
        this.titreRapport = titre;
        this.rapportData = rapportData;

        Platform.runLater(() -> {
            updateHeaderInfo();
            showLoadingOverlay(true);

            if (webEngine != null && htmlContent != null) {
                webEngine.loadContent(htmlContent);
            } else {
                showErrorOverlay(true, "Contenu HTML invalide");
            }
        });
    }

    /**
     * Définit la référence à la fenêtre du dialogue
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    // === GESTIONNAIRES D'ÉVÉNEMENTS ===

    private void handleImprimer() {
        if (htmlContent != null) {
            try {
                boolean success = printService.printHtml(htmlContent);
                if (success) {
                    updateStatus("Document envoyé à l'imprimante", true);
                } else {
                    updateStatus("Impression annulée ou échouée", false);
                }
            } catch (Exception e) {
                logger.error("Erreur lors de l'impression", e);
                updateStatus("Erreur d'impression: " + e.getMessage(), false);
            }
        }
    }

    private void handleExportPdf() {
        if (htmlContent == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter en PDF");
        fileChooser.setInitialFileName("rapport_" + getCurrentTimestamp() + ".pdf");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichiers PDF", "*.pdf")
        );

        File file = fileChooser.showSaveDialog(dialogStage);
        if (file != null) {
            try {
                File result = exportService.exportReportToPDF(htmlContent, titreRapport, file.getName());
                if (result != null) {
                    updateStatus("PDF exporté avec succès", true);
                    AlertUtil.showSuccess("Export réussi", "Le rapport a été exporté en PDF");
                } else {
                    updateStatus("Échec de l'export PDF", false);
                }
            } catch (Exception e) {
                logger.error("Erreur lors de l'export PDF", e);
                updateStatus("Erreur d'export: " + e.getMessage(), false);
            }
        }
    }

    private void handleExportExcel() {
        if (rapportData == null) {
            AlertUtil.showWarningAlert("Export impossible", "Aucune donnée",
                    "Aucune donnée disponible pour l'export Excel");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter en Excel");
        fileChooser.setInitialFileName("rapport_" + getCurrentTimestamp() + ".xlsx");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichiers Excel", "*.xlsx")
        );

        File file = fileChooser.showSaveDialog(dialogStage);
        if (file != null) {
            try {
                boolean success = exportService.exportGenericToExcel(rapportData, file.getAbsolutePath());
                if (success) {
                    updateStatus("Excel exporté avec succès", true);
                    AlertUtil.showSuccess("Export réussi", "Le rapport a été exporté en Excel");
                } else {
                    updateStatus("Échec de l'export Excel", false);
                }
            } catch (Exception e) {
                logger.error("Erreur lors de l'export Excel", e);
                updateStatus("Erreur d'export: " + e.getMessage(), false);
            }
        }
    }

    private void handleFermer() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private void handleRetry() {
        if (htmlContent != null) {
            loadContent(htmlContent, titreRapport, rapportData);
        }
    }

    // === CONTRÔLES DE ZOOM ===

    private void zoomIn() {
        zoomLevel = Math.min(zoomLevel * 1.25, 5.0);
        applyZoom();
    }

    private void zoomOut() {
        zoomLevel = Math.max(zoomLevel * 0.8, 0.25);
        applyZoom();
    }

    private void zoomToFit() {
        zoomLevel = 1.0;
        applyZoom();
    }

    private void applyZoom() {
        if (previewWebView != null) {
            previewWebView.setZoom(zoomLevel);
            updateZoomLabel();
        }
    }

    private void updateZoomLabel() {
        if (zoomLabel != null) {
            zoomLabel.setText(String.format("%.0f%%", zoomLevel * 100));
        }
    }

    // === NAVIGATION ===

    private void goToFirstPage() {
        pageActuelle = 1;
        updateNavigationState();
    }

    private void goToPreviousPage() {
        if (pageActuelle > 1) {
            pageActuelle--;
            updateNavigationState();
        }
    }

    private void goToNextPage() {
        if (pageActuelle < totalPages) {
            pageActuelle++;
            updateNavigationState();
        }
    }

    private void goToLastPage() {
        pageActuelle = totalPages;
        updateNavigationState();
    }

    private void goToPage() {
        try {
            int page = Integer.parseInt(pageActuelleField.getText());
            if (page >= 1 && page <= totalPages) {
                pageActuelle = page;
                updateNavigationState();
            }
        } catch (NumberFormatException e) {
            pageActuelleField.setText(String.valueOf(pageActuelle));
        }
    }

    private void updateNavigationState() {
        Platform.runLater(() -> {
            if (pageActuelleField != null) {
                pageActuelleField.setText(String.valueOf(pageActuelle));
            }
            if (premierePage != null) {
                premierePage.setDisable(pageActuelle <= 1);
            }
            if (pagePrecedente != null) {
                pagePrecedente.setDisable(pageActuelle <= 1);
            }
            if (pageSuivante != null) {
                pageSuivante.setDisable(pageActuelle >= totalPages);
            }
            if (dernierePage != null) {
                dernierePage.setDisable(pageActuelle >= totalPages);
            }
        });
    }

    // === MODES D'AFFICHAGE (FUTURES IMPLÉMENTATIONS) ===

    private void setPortraitMode() {
        // Future implémentation
        logger.debug("Mode portrait sélectionné");
    }

    private void setLandscapeMode() {
        // Future implémentation
        logger.debug("Mode paysage sélectionné");
    }

    private void toggleRulers() {
        // Future implémentation
        logger.debug("Basculement des règles");
    }

    // === GESTION DES OVERLAYS ===

    private void showLoadingOverlay(boolean show) {
        Platform.runLater(() -> {
            if (loadingOverlay != null) {
                loadingOverlay.setVisible(show);
                loadingOverlay.setManaged(show);
            }
        });
    }

    private void showErrorOverlay(boolean show, String message) {
        Platform.runLater(() -> {
            if (errorOverlay != null) {
                errorOverlay.setVisible(show);
                errorOverlay.setManaged(show);
                if (show && errorMessageLabel != null && message != null) {
                    errorMessageLabel.setText(message);
                }
            }
        });
    }

    private void showErrorOverlay(boolean show) {
        showErrorOverlay(show, null);
    }

    // === CALLBACKS ===

    private void onDocumentLoaded() {
        Platform.runLater(() -> {
            showLoadingOverlay(false);
            showErrorOverlay(false);
            updateStatus("Document chargé avec succès", true);
            updateDocumentInfo();
        });
    }

    private void onDocumentLoadFailed() {
        Platform.runLater(() -> {
            showLoadingOverlay(false);
            showErrorOverlay(true, "Échec du chargement du document");
            updateStatus("Erreur de chargement", false);
        });
    }

    // === MÉTHODES UTILITAIRES ===

    private void updateHeaderInfo() {
        Platform.runLater(() -> {
            if (titreRapportLabel != null) {
                titreRapportLabel.setText(titreRapport != null ? titreRapport : "Rapport");
            }
            if (detailsRapportLabel != null) {
                String details = String.format("Généré le %s",
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                detailsRapportLabel.setText(details);
            }
        });
    }

    private void updateDocumentInfo() {
        Platform.runLater(() -> {
            if (nombrePagesLabel != null) {
                nombrePagesLabel.setText(String.format("Page %d sur %d", pageActuelle, totalPages));
            }
            if (totalPagesLabel != null) {
                totalPagesLabel.setText(String.valueOf(totalPages));
            }
            if (tailleDocumentLabel != null) {
                String taille = htmlContent != null ?
                        String.format("%.1f KB", htmlContent.length() / 1024.0) : "--";
                tailleDocumentLabel.setText("Taille: " + taille);
            }
        });
    }

    private void updateStatus(String message, boolean success) {
        Platform.runLater(() -> {
            if (statutLabel != null) {
                statutLabel.setText((success ? "✅ " : "❌ ") + message);
                statutLabel.getStyleClass().removeAll("status-success", "status-error");
                statutLabel.getStyleClass().add(success ? "status-success" : "status-error");
            }
        });
    }

    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
}