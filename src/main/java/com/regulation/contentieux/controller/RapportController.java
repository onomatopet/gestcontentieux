package com.regulation.contentieux.controller;

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

/**
 * Contrôleur pour la génération et l'affichage des rapports
 * Gère les différents types de rapports et leurs exports
 */
public class RapportController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(RapportController.class);

    // Énumération des types de rapport
    public enum TypeRapport {
        REPARTITION_RETROCESSION("Répartition et rétrocession", "Rapport détaillé des montants répartis entre l'État et les collectivités"),
        SITUATION_GENERALE("Situation générale", "Vue d'ensemble des affaires et encaissements"),
        TABLEAU_AMENDES_SERVICE("Tableau des amendes par service", "Répartition des amendes selon les services verbalisateurs"),
        ENCAISSEMENTS_PERIODE("Encaissements par période", "Liste détaillée des encaissements pour une période donnée"),
        AFFAIRES_NON_SOLDEES("Affaires non soldées", "Liste des affaires en cours ou non réglées");

        private final String libelle;
        private final String description;

        TypeRapport(String libelle, String description) {
            this.libelle = libelle;
            this.description = description;
        }

        public String getLibelle() { return libelle; }
        public String getDescription() { return description; }
    }

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
                for (TypeRapport type : TypeRapport.values()) {
                    if (type.getLibelle().equals(string)) {
                        return type;
                    }
                }
                return null;
            }
        });

        typeRapportComboBox.getItems().addAll(TypeRapport.values());
        typeRapportComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                descriptionLabel.setText(newVal.getDescription());
                configureFiltresAdditionnels(newVal);
            } else {
                descriptionLabel.setText("");
            }
        });
    }

    /**
     * Configure les contrôles de période
     */
    private void setupPeriodeControls() {
        // Périodes rapides
        periodeRapideComboBox.getItems().addAll(
                "Aujourd'hui", "Cette semaine", "Ce mois", "Ce trimestre", "Cette année",
                "Semaine dernière", "Mois dernier", "Trimestre dernier", "Année dernière"
        );

        periodeRapideComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !periodePersonnaliseeCheckBox.isSelected()) {
                setPeriodeRapide(newVal);
            }
        });

        // Checkbox période personnalisée
        periodePersonnaliseeCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dateDebutPicker.setDisable(!newVal);
            dateFinPicker.setDisable(!newVal);
            periodeRapideComboBox.setDisable(newVal);

            if (!newVal) {
                // Réinitialiser avec la période rapide sélectionnée
                String periodeRapide = periodeRapideComboBox.getValue();
                if (periodeRapide != null) {
                    setPeriodeRapide(periodeRapide);
                }
            }
        });

        // Valeurs par défaut
        dateDebutPicker.setValue(LocalDate.now().withDayOfMonth(1)); // Début du mois
        dateFinPicker.setValue(LocalDate.now()); // Aujourd'hui
        periodeRapideComboBox.setValue("Ce mois");
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
                // debut et fin sont déjà aujourd'hui
                break;
            case "Cette semaine":
                debut = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
                fin = LocalDate.now().with(java.time.DayOfWeek.SUNDAY);
                break;
            case "Ce mois":
                debut = LocalDate.now().withDayOfMonth(1);
                fin = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
                break;
            case "Ce trimestre":
                int currentMonth = LocalDate.now().getMonthValue();
                int quarterStart = ((currentMonth - 1) / 3) * 3 + 1;
                debut = LocalDate.now().withMonth(quarterStart).withDayOfMonth(1);
                fin = debut.plusMonths(2).withDayOfMonth(debut.plusMonths(2).lengthOfMonth());
                break;
            case "Cette année":
                debut = LocalDate.now().withDayOfYear(1);
                fin = LocalDate.now().withDayOfYear(LocalDate.now().lengthOfYear());
                break;
            case "Semaine dernière":
                debut = LocalDate.now().minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
                fin = LocalDate.now().minusWeeks(1).with(java.time.DayOfWeek.SUNDAY);
                break;
            case "Mois dernier":
                debut = LocalDate.now().minusMonths(1).withDayOfMonth(1);
                fin = debut.withDayOfMonth(debut.lengthOfMonth());
                break;
            case "Trimestre dernier":
                LocalDate lastQuarter = LocalDate.now().minusMonths(3);
                int lastQuarterMonth = lastQuarter.getMonthValue();
                int lastQuarterStart = ((lastQuarterMonth - 1) / 3) * 3 + 1;
                debut = lastQuarter.withMonth(lastQuarterStart).withDayOfMonth(1);
                fin = debut.plusMonths(2).withDayOfMonth(debut.plusMonths(2).lengthOfMonth());
                break;
            case "Année dernière":
                debut = LocalDate.now().minusYears(1).withDayOfYear(1);
                fin = debut.withDayOfYear(debut.lengthOfYear());
                break;
        }

        dateDebutPicker.setValue(debut);
        dateFinPicker.setValue(fin);
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
     * Génère le rapport sélectionné
     */
    @FXML
    private void genererRapport() {
        TypeRapport type = typeRapportComboBox.getValue();
        if (type == null) {
            AlertUtil.showWarningAlert("Attention",
                    "Type de rapport non sélectionné",
                    "Veuillez sélectionner un type de rapport.");
            return;
        }

        LocalDate dateDebut = dateDebutPicker.getValue();
        LocalDate dateFin = dateFinPicker.getValue();

        if (dateDebut == null || dateFin == null) {
            AlertUtil.showWarningAlert("Attention",
                    "Période non définie",
                    "Veuillez définir une période pour le rapport.");
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
                        // CORRECTION LIGNE 532: Utilisation du nom complet de la classe
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
            // Créer une nouvelle fenêtre pour l'aperçu
            Stage stage = new Stage();
            stage.setTitle("Aperçu du rapport");
            stage.initModality(Modality.APPLICATION_MODAL);

            // Créer une WebView pour l'aperçu
            WebView webView = new WebView();
            webView.getEngine().loadContent(dernierRapportGenere);

            Scene scene = new Scene(webView, 1000, 700);
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();

        } catch (Exception e) {
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
                    statusLabel.setText("Erreur lors de l'export PDF");
                    logger.error("Erreur lors de l'export PDF", getException());
                    AlertUtil.showErrorAlert("Erreur",
                            "Export échoué",
                            "Erreur lors de l'export: " + getException().getMessage());
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

                // CORRECTION LIGNES 565, 569, 573: Utiliser les méthodes correctes d'ExportService
                switch (type) {
                    case REPARTITION_RETROCESSION:
                        return exportService.exportRepartitionToExcel(
                                (RapportService.RapportRepartitionDTO) dernierRapportData,
                                file.getAbsolutePath());

                    case SITUATION_GENERALE:
                        // CORRECTION LIGNE 574: Le ExportService attend la classe du package séparé,
                        // mais RapportService retourne sa classe interne. Il faut soit :
                        // 1. Modifier ExportService pour accepter RapportService.SituationGeneraleDTO
                        // 2. Ou convertir les données
                        // Pour l'instant, on utilise la méthode générique
                        return exportService.exportGenericToExcel(
                                dernierRapportData,
                                file.getAbsolutePath());

                    case TABLEAU_AMENDES_SERVICE:
                        return exportService.exportTableauAmendesToExcel(
                                (RapportService.TableauAmendesParServicesDTO) dernierRapportData,
                                file.getAbsolutePath());

                    case ENCAISSEMENTS_PERIODE:
                    case AFFAIRES_NON_SOLDEES:
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
                                "Fichier Excel créé",
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
                    statusLabel.setText("Erreur lors de l'export Excel");
                    logger.error("Erreur lors de l'export Excel", getException());
                    AlertUtil.showErrorAlert("Erreur",
                            "Export échoué",
                            "Erreur lors de l'export: " + getException().getMessage());
                });
            }
        };

        Thread exportThread = new Thread(exportTask);
        exportThread.setDaemon(true);
        exportThread.start();
    }

    /**
     * Méthode de fallback pour l'export Excel si le service n'est pas encore implémenté
     */
    private Boolean exportToExcelFallback(Object data, String filePath) {
        try {
            logger.warn("Export Excel - Utilisation de la méthode de fallback");

            // Afficher un message informatif à l'utilisateur
            Platform.runLater(() -> {
                AlertUtil.showInfoAlert("Export Excel",
                        "Fonctionnalité en développement",
                        "L'export Excel sera disponible dans une prochaine version.\n\n" +
                                "En attendant, vous pouvez utiliser l'export PDF ou copier les données depuis l'aperçu.");
            });

            return false; // Indiquer que l'export n'a pas réussi
        } catch (Exception e) {
            logger.error("Erreur dans la méthode de fallback Excel", e);
            return false;
        }
    }
}