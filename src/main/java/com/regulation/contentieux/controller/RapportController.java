package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.enums.TypeRapport;
import com.regulation.contentieux.service.RapportService;
import com.regulation.contentieux.service.ExportService;
import com.regulation.contentieux.service.PrintService;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.DateFormatter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Contrôleur pour la génération et l'affichage des rapports
 * Gère les différents types de rapports et leurs exports
 */
public class RapportController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(RapportController.class);

    // Sélection du type de rapport
    @FXML private ComboBox<TypeRapport> typeRapportComboBox;
    @FXML private Label descriptionLabel;

    // Paramètres de période
    @FXML private DatePicker dateDebutPicker;
    @FXML private DatePicker dateFinPicker;
    @FXML private CheckBox periodePersonnaliseeCheckBox;
    @FXML private ComboBox<String> periodeRapideComboBox;

    // Filtres additionnels
    @FXML private VBox filtresAdditionnelsBox;
    @FXML private ComboBox<String> bureauFilterComboBox;
    @FXML private ComboBox<String> serviceFilterComboBox;
    @FXML private CheckBox includeDetailsCheckBox;

    // Actions
    @FXML private Button genererButton;
    @FXML private Button previewButton;
    @FXML private Button imprimerButton;
    @FXML private Button exporterPdfButton;
    @FXML private Button exporterExcelButton;

    // Aperçu
    @FXML private WebView previewWebView;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;

    // Services
    private final RapportService rapportService = new RapportService();
    private final ExportService exportService = new ExportService();
    private final PrintService printService = new PrintService();

    // État
    private WebEngine webEngine;
    private String dernierRapportGenere;
    private Object dernierRapportData;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        webEngine = previewWebView.getEngine();
        setupTypeRapportComboBox();
        setupPeriodeControls();
        setupActions();

        // État initial
        progressIndicator.setVisible(false);
        updateButtonStates(false);
    }

    /**
     * Configure le ComboBox des types de rapport
     */
    private void setupTypeRapportComboBox() {
        typeRapportComboBox.setConverter(new StringConverter<TypeRapport>() {
            @Override
            public String toString(TypeRapport type) {
                return type != null ? type.getLibelle() : "";
            }

            @Override
            public TypeRapport fromString(String string) {
                return null;
            }
        });

        typeRapportComboBox.getItems().addAll(TypeRapport.values());

        typeRapportComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                descriptionLabel.setText(newVal.getDescription());
                configureFiltresAdditionnels(newVal);
                clearPreview();
            }
        });
    }

    /**
     * Configure les contrôles de période
     */
    private void setupPeriodeControls() {
        // Périodes rapides
        periodeRapideComboBox.getItems().addAll(
                "Aujourd'hui",
                "Cette semaine",
                "Ce mois",
                "Mois dernier",
                "Ce trimestre",
                "Cette année",
                "Année dernière"
        );

        periodeRapideComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !periodePersonnaliseeCheckBox.isSelected()) {
                setPeriodeRapide(newVal);
            }
        });

        // Période personnalisée
        periodePersonnaliseeCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dateDebutPicker.setDisable(!newVal);
            dateFinPicker.setDisable(!newVal);
            periodeRapideComboBox.setDisable(newVal);

            if (!newVal && periodeRapideComboBox.getValue() != null) {
                setPeriodeRapide(periodeRapideComboBox.getValue());
            }
        });

        // Valeurs par défaut
        periodeRapideComboBox.setValue("Ce mois");
        periodePersonnaliseeCheckBox.setSelected(false);
    }

    /**
     * Configure les actions
     */
    private void setupActions() {
        genererButton.setOnAction(e -> genererRapport());
        previewButton.setOnAction(e -> afficherApercu());
        imprimerButton.setOnAction(e -> imprimerRapport());
        exporterPdfButton.setOnAction(e -> exporterPdf());
        exporterExcelButton.setOnAction(e -> exporterExcel());
    }

    /**
     * Configure les filtres additionnels selon le type de rapport
     */
    private void configureFiltresAdditionnels(TypeRapport type) {
        // Réinitialiser
        bureauFilterComboBox.setVisible(false);
        serviceFilterComboBox.setVisible(false);
        includeDetailsCheckBox.setVisible(false);

        switch (type) {
            case REPARTITION_RETROCESSION:
            case SITUATION_GENERALE:
                bureauFilterComboBox.setVisible(true);
                serviceFilterComboBox.setVisible(true);
                includeDetailsCheckBox.setVisible(true);
                break;

            case TABLEAU_AMENDES_SERVICE:
                serviceFilterComboBox.setVisible(true);
                break;

            case ENCAISSEMENTS_PERIODE:
            case AFFAIRES_NON_SOLDEES:
                includeDetailsCheckBox.setVisible(true);
                break;
        }

        filtresAdditionnelsBox.setVisible(
                bureauFilterComboBox.isVisible() ||
                        serviceFilterComboBox.isVisible() ||
                        includeDetailsCheckBox.isVisible()
        );
    }

    /**
     * Définit une période rapide
     */
    private void setPeriodeRapide(String periode) {
        LocalDate debut = LocalDate.now();
        LocalDate fin = LocalDate.now();

        switch (periode) {
            case "Aujourd'hui":
                // Déjà défini
                break;

            case "Cette semaine":
                debut = debut.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                fin = fin.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
                break;

            case "Ce mois":
                debut = debut.withDayOfMonth(1);
                fin = debut.plusMonths(1).minusDays(1);
                break;

            case "Mois dernier":
                debut = debut.minusMonths(1).withDayOfMonth(1);
                fin = debut.plusMonths(1).minusDays(1);
                break;

            case "Ce trimestre":
                int mois = debut.getMonthValue();
                int trimestre = (mois - 1) / 3;
                debut = LocalDate.of(debut.getYear(), trimestre * 3 + 1, 1);
                fin = debut.plusMonths(3).minusDays(1);
                break;

            case "Cette année":
                debut = debut.withDayOfYear(1);
                fin = debut.withDayOfYear(debut.lengthOfYear());
                break;

            case "Année dernière":
                debut = debut.minusYears(1).withDayOfYear(1);
                fin = debut.withDayOfYear(debut.lengthOfYear());
                break;
        }

        dateDebutPicker.setValue(debut);
        dateFinPicker.setValue(fin);
    }

    /**
     * Génère le rapport sélectionné
     */
    @FXML
    private void genererRapport() {
        TypeRapport type = typeRapportComboBox.getValue();
        if (type == null) {
            AlertUtil.showWarningAlert("Sélection requise",
                    "Type de rapport",
                    "Veuillez sélectionner un type de rapport.");
            return;
        }

        LocalDate dateDebut = dateDebutPicker.getValue();
        LocalDate dateFin = dateFinPicker.getValue();

        if (dateDebut == null || dateFin == null) {
            AlertUtil.showWarningAlert("Période requise",
                    "Dates manquantes",
                    "Veuillez sélectionner une période.");
            return;
        }

        if (dateDebut.isAfter(dateFin)) {
            AlertUtil.showWarningAlert("Période invalide",
                    "Dates incorrectes",
                    "La date de début doit être antérieure à la date de fin.");
            return;
        }

        // Lancer la génération
        progressIndicator.setVisible(true);
        statusLabel.setText("Génération du rapport en cours...");
        updateButtonStates(false);

        Task<String> generateTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                switch (type) {
                    case REPARTITION_RETROCESSION:
                        dernierRapportData = rapportService.genererRapportRepartition(dateDebut, dateFin);
                        return rapportService.genererHtmlRapportRepartition(
                                (RapportService.RapportRepartitionDTO) dernierRapportData);

                    case SITUATION_GENERALE:
                        RapportService.SituationGeneraleDTO situationData = rapportService.genererSituationGenerale(dateDebut, dateFin);
                        dernierRapportData = situationData;
                        return rapportService.genererHtmlSituationGenerale(situationData);

                    case TABLEAU_AMENDES_SERVICE:
                        dernierRapportData = rapportService.genererTableauAmendesParServices(dateDebut, dateFin);
                        return rapportService.genererHtmlTableauAmendes(
                                (RapportService.TableauAmendesParServicesDTO) dernierRapportData);

                    case ENCAISSEMENTS_PERIODE:
                        dernierRapportData = rapportService.genererRapportEncaissements(dateDebut, dateFin);
                        return rapportService.genererHtmlEncaissements(dernierRapportData);

                    case AFFAIRES_NON_SOLDEES:
                        dernierRapportData = rapportService.genererRapportAffairesNonSoldees();
                        return rapportService.genererHtmlAffairesNonSoldees(dernierRapportData);

                    default:
                        throw new UnsupportedOperationException("Type de rapport non supporté: " + type);
                }
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    statusLabel.setText("Rapport généré avec succès");
                    dernierRapportGenere = getValue();

                    // Afficher automatiquement l'aperçu
                    webEngine.loadContent(dernierRapportGenere);
                    updateButtonStates(true);
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    statusLabel.setText("Erreur lors de la génération");
                    updateButtonStates(false);

                    logger.error("Erreur lors de la génération du rapport", getException());
                    AlertUtil.showErrorAlert("Erreur",
                            "Génération échouée",
                            "Impossible de générer le rapport: " + getException().getMessage());
                });
            }
        };

        Thread generateThread = new Thread(generateTask);
        generateThread.setDaemon(true);
        generateThread.start();
    }

    /**
     * Affiche l'aperçu dans une nouvelle fenêtre
     */
    @FXML
    private void afficherApercu() {
        if (dernierRapportGenere == null) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/rapport/rapport-preview.fxml"));
            Parent root = loader.load();

            RapportPreviewController controller = loader.getController();
            controller.loadHtmlContent(dernierRapportGenere);

            Stage stage = new Stage();
            stage.setTitle("Aperçu du rapport");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.show();

        } catch (IOException e) {
            logger.error("Erreur lors de l'ouverture de l'aperçu", e);
            AlertUtil.showErrorAlert("Erreur",
                    "Aperçu impossible",
                    "Impossible d'afficher l'aperçu du rapport.");
        }
    }

    /**
     * Imprime le rapport
     */
    @FXML
    private void imprimerRapport() {
        if (dernierRapportGenere == null) {
            return;
        }

        statusLabel.setText("Préparation de l'impression...");

        // Créer une WebView temporaire pour l'impression
        WebView printWebView = new WebView();
        WebEngine printEngine = printWebView.getEngine();

        // Charger le contenu
        printEngine.loadContent(dernierRapportGenere);

        // Attendre le chargement complet
        printEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                Platform.runLater(() -> {
                    boolean success = printService.printWebView(printWebView);
                    if (success) {
                        statusLabel.setText("Impression terminée");
                    } else {
                        statusLabel.setText("Impression annulée");
                    }
                });
            }
        });
    }

    /**
     * Exporte le rapport en PDF
     */
    @FXML
    private void exporterPdf() {
        if (dernierRapportGenere == null) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Enregistrer le rapport PDF");
        fileChooser.setInitialFileName("rapport_" +
                DateFormatter.formatForFilename(LocalDate.now()) + ".pdf");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Documents PDF", "*.pdf")
        );

        File file = fileChooser.showSaveDialog(genererButton.getScene().getWindow());
        if (file != null) {
            exportToPdf(file);
        }
    }

    /**
     * Exporte le rapport en Excel
     */
    @FXML
    private void exporterExcel() {
        if (dernierRapportData == null) {
            AlertUtil.showWarningAlert("Attention",
                    "Aucun rapport",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Enregistrer le rapport Excel");
        fileChooser.setInitialFileName("rapport_" +
                DateFormatter.formatForFilename(LocalDate.now()) + ".xlsx");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichiers Excel", "*.xlsx")
        );

        File file = fileChooser.showSaveDialog(genererButton.getScene().getWindow());
        if (file != null) {
            exportToExcel(file);
        }
    }

    /**
     * Effectue l'export PDF
     */
    private void exportToPdf(File file) {
        progressIndicator.setVisible(true);
        statusLabel.setText("Export PDF en cours...");

        Task<Boolean> exportTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                return exportService.exportToPdf(dernierRapportGenere, file.getAbsolutePath());
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    if (getValue()) {
                        statusLabel.setText("Export PDF réussi");
                        AlertUtil.showSuccessAlert("Export réussi",
                                "PDF créé",
                                "Le rapport a été exporté avec succès.");
                    } else {
                        statusLabel.setText("Échec de l'export PDF");
                        AlertUtil.showErrorAlert("Erreur",
                                "Export échoué",
                                "Impossible d'exporter le rapport en PDF.");
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    statusLabel.setText("Erreur lors de l'export");
                    logger.error("Erreur lors de l'export PDF", getException());
                    AlertUtil.showErrorAlert("Erreur",
                            "Export échoué",
                            "Une erreur s'est produite: " + getException().getMessage());
                });
            }
        };

        Thread exportThread = new Thread(exportTask);
        exportThread.setDaemon(true);
        exportThread.start();
    }

    /**
     * Effectue l'export Excel
     */
    private void exportToExcel(File file) {
        progressIndicator.setVisible(true);
        statusLabel.setText("Export Excel en cours...");

        Task<Boolean> exportTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                TypeRapport type = typeRapportComboBox.getValue();

                switch (type) {
                    case REPARTITION_RETROCESSION:
                        return exportService.exportRepartitionToExcel(
                                (RapportService.RapportRepartitionDTO) dernierRapportData,
                                file.getAbsolutePath());

                    case SITUATION_GENERALE:
                        return exportService.exportSituationToExcel(
                                (RapportService.SituationGeneraleDTO) dernierRapportData,
                                file.getAbsolutePath());

                    case TABLEAU_AMENDES_SERVICE:
                        return exportService.exportTableauAmendesToExcel(
                                (RapportService.TableauAmendesParServicesDTO) dernierRapportData,
                                file.getAbsolutePath());

                    default:
                        return exportService.exportGenericToExcel(
                                dernierRapportData,
                                file.getAbsolutePath());
                }
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    if (getValue()) {
                        statusLabel.setText("Export Excel réussi");
                        AlertUtil.showSuccessAlert("Export réussi",
                                "Excel créé",
                                "Le rapport a été exporté avec succès.");
                    } else {
                        statusLabel.setText("Échec de l'export Excel");
                        AlertUtil.showErrorAlert("Erreur",
                                "Export échoué",
                                "Impossible d'exporter le rapport en Excel.");
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    statusLabel.setText("Erreur lors de l'export");
                    logger.error("Erreur lors de l'export Excel", getException());
                    AlertUtil.showErrorAlert("Erreur",
                            "Export échoué",
                            "Une erreur s'est produite: " + getException().getMessage());
                });
            }
        };

        Thread exportThread = new Thread(exportTask);
        exportThread.setDaemon(true);
        exportThread.start();
    }

    /**
     * Met à jour l'état des boutons
     */
    private void updateButtonStates(boolean rapportGenere) {
        previewButton.setDisable(!rapportGenere);
        imprimerButton.setDisable(!rapportGenere);
        exporterPdfButton.setDisable(!rapportGenere);
        exporterExcelButton.setDisable(!rapportGenere);
    }

    /**
     * Efface l'aperçu
     */
    private void clearPreview() {
        webEngine.loadContent("<html><body style='font-family:Arial;padding:20px;'>" +
                "<p style='color:#666;'>Sélectionnez un type de rapport et cliquez sur Générer.</p>" +
                "</body></html>");
        dernierRapportGenere = null;
        dernierRapportData = null;
        updateButtonStates(false);
    }

    /**
     * Classe interne pour l'aperçu du rapport
     */
    public static class RapportPreviewController {
        @FXML private WebView webView;
        private WebEngine engine;

        @FXML
        public void initialize() {
            engine = webView.getEngine();
        }

        public void loadHtmlContent(String html) {
            if (engine != null) {
                engine.loadContent(html);
            }
        }
    }
}