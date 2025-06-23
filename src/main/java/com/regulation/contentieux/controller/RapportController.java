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
import java.time.Month;
import java.time.YearMonth;
import java.util.ResourceBundle;

/**
 * Contrôleur pour la génération et l'affichage des rapports
 * ENRICHI : Gère les 8 types de rapports conformes aux templates
 */
public class RapportController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(RapportController.class);

    // Sélection du type de rapport
    @FXML private ComboBox<TypeRapport> typeRapportComboBox;
    @FXML private Label descriptionLabel;

    // Paramètres de période
    @FXML private ComboBox<String> periodeTypeComboBox;
    @FXML private DatePicker dateDebutPicker;
    @FXML private DatePicker dateFinPicker;
    @FXML private ComboBox<Month> moisComboBox;
    @FXML private ComboBox<Integer> anneeComboBox;
    @FXML private VBox periodePersonnaliseeBox;
    @FXML private VBox periodeMensuelleBox;

    // Filtres additionnels
    @FXML private VBox filtresAdditionnelsBox;
    @FXML private ComboBox<String> bureauFilterComboBox;
    @FXML private ComboBox<String> serviceFilterComboBox;
    @FXML private CheckBox includeDetailsCheckBox;

    // Actions
    @FXML private Button genererButton;
    @FXML private Button previewButton;
    @FXML private Button imprimerButton;
    @FXML private Button exportPdfButton;
    @FXML private Button exportExcelButton;

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
     * Configure le ComboBox des types de rapport avec les 8 types
     */
    private void setupTypeRapportComboBox() {
        // Utiliser l'enum TypeRapport du model
        typeRapportComboBox.getItems().addAll(TypeRapport.values());

        typeRapportComboBox.setConverter(new StringConverter<TypeRapport>() {
            @Override
            public String toString(TypeRapport type) {
                return type != null ? type.getLibelle() : "";
            }

            @Override
            public TypeRapport fromString(String string) {
                return TypeRapport.fromLibelle(string);
            }
        });

        // Listener pour mettre à jour la description
        typeRapportComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                descriptionLabel.setText(newVal.getDescription());
                // Adapter les filtres selon le type
                adaptFiltresSelonType(newVal);
            }
        });

        // Sélection par défaut
        typeRapportComboBox.getSelectionModel().selectFirst();
    }

    /**
     * Configure les contrôles de période
     */
    private void setupPeriodeControls() {
        // Types de période
        periodeTypeComboBox.getItems().addAll(
                "Période personnalisée",
                "Mois en cours",
                "Mois précédent",
                "Trimestre en cours",
                "Année en cours"
        );

        periodeTypeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            updatePeriodeVisibility(newVal);
            updatePeriodeAutomatique(newVal);
        });

        // Mois
        moisComboBox.getItems().addAll(Month.values());
        moisComboBox.setConverter(new StringConverter<Month>() {
            @Override
            public String toString(Month month) {
                return month != null ?
                        month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH) : "";
            }

            @Override
            public Month fromString(String string) {
                return null;
            }
        });

        // Années
        int currentYear = LocalDate.now().getYear();
        for (int year = currentYear - 5; year <= currentYear; year++) {
            anneeComboBox.getItems().add(year);
        }
        anneeComboBox.setValue(currentYear);

        // Validation des dates
        dateDebutPicker.valueProperty().addListener((obs, oldVal, newVal) -> validateDates());
        dateFinPicker.valueProperty().addListener((obs, oldVal, newVal) -> validateDates());

        // Sélection par défaut
        periodeTypeComboBox.getSelectionModel().selectFirst();
    }

    /**
     * Configure les actions des boutons
     */
    private void setupActions() {
        genererButton.setOnAction(e -> genererRapport());
        previewButton.setOnAction(e -> afficherApercu());
        imprimerButton.setOnAction(e -> imprimerRapport());
        exportPdfButton.setOnAction(e -> exporterPdf());
        exportExcelButton.setOnAction(e -> exporterExcel());
    }

    /**
     * Adapte les filtres selon le type de rapport sélectionné
     */
    private void adaptFiltresSelonType(TypeRapport type) {
        // Réinitialiser
        bureauFilterComboBox.setVisible(false);
        serviceFilterComboBox.setVisible(false);
        includeDetailsCheckBox.setVisible(true);

        // Adapter selon le type
        switch (type) {
            case TABLEAU_AMENDES_SERVICE:
            case INDICATEURS_REELS:
                serviceFilterComboBox.setVisible(true);
                break;

            case CENTRE_REPARTITION:
                bureauFilterComboBox.setVisible(true);
                break;

            case MANDATEMENT_AGENTS:
                includeDetailsCheckBox.setText("Inclure le détail par encaissement");
                break;

            default:
                includeDetailsCheckBox.setText("Inclure les détails");
                break;
        }
    }

    /**
     * Met à jour la visibilité des contrôles de période
     */
    private void updatePeriodeVisibility(String typePeriode) {
        if (typePeriode == null) return;

        periodePersonnaliseeBox.setVisible("Période personnalisée".equals(typePeriode));
        periodeMensuelleBox.setVisible(false); // Pour une future implémentation
    }

    /**
     * Met à jour automatiquement les dates selon le type de période
     */
    private void updatePeriodeAutomatique(String typePeriode) {
        LocalDate debut = null;
        LocalDate fin = null;
        LocalDate aujourd = LocalDate.now();

        switch (typePeriode) {
            case "Mois en cours":
                debut = aujourd.withDayOfMonth(1);
                fin = aujourd.withDayOfMonth(aujourd.lengthOfMonth());
                break;

            case "Mois précédent":
                LocalDate moisPrecedent = aujourd.minusMonths(1);
                debut = moisPrecedent.withDayOfMonth(1);
                fin = moisPrecedent.withDayOfMonth(moisPrecedent.lengthOfMonth());
                break;

            case "Trimestre en cours":
                int trimestre = (aujourd.getMonthValue() - 1) / 3;
                debut = LocalDate.of(aujourd.getYear(), trimestre * 3 + 1, 1);
                fin = debut.plusMonths(2).withDayOfMonth(
                        debut.plusMonths(2).lengthOfMonth());
                break;

            case "Année en cours":
                debut = LocalDate.of(aujourd.getYear(), 1, 1);
                fin = LocalDate.of(aujourd.getYear(), 12, 31);
                break;
        }

        if (debut != null && fin != null) {
            dateDebutPicker.setValue(debut);
            dateFinPicker.setValue(fin);
        }
    }

    /**
     * Valide les dates sélectionnées
     */
    private void validateDates() {
        LocalDate debut = dateDebutPicker.getValue();
        LocalDate fin = dateFinPicker.getValue();

        if (debut != null && fin != null) {
            if (debut.isAfter(fin)) {
                dateFinPicker.setValue(debut);
            }
        }
    }

    /**
     * Met à jour l'état des boutons
     */
    private void updateButtonStates(boolean rapportGenere) {
        previewButton.setDisable(!rapportGenere);
        imprimerButton.setDisable(!rapportGenere);
        exportPdfButton.setDisable(!rapportGenere);
        exportExcelButton.setDisable(!rapportGenere);
    }

    /**
     * Génère le rapport selon le type sélectionné
     */
    @FXML
    private void genererRapport() {
        TypeRapport type = typeRapportComboBox.getValue();
        LocalDate dateDebut = dateDebutPicker.getValue();
        LocalDate dateFin = dateFinPicker.getValue();

        // Validation
        if (type == null) {
            AlertUtil.showWarningAlert("Sélection requise",
                    "Type de rapport manquant",
                    "Veuillez sélectionner un type de rapport.");
            return;
        }

        if (dateDebut == null || dateFin == null) {
            AlertUtil.showWarningAlert("Dates requises",
                    "Période incomplète",
                    "Veuillez sélectionner les dates de début et de fin.");
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
                // Générer selon le type avec les nouvelles méthodes
                switch (type) {
                    case REPARTITION_RETROCESSION:
                        return rapportService.genererHtmlEtatRepartition(dateDebut, dateFin);

                    case ETAT_MANDATEMENT:
                        return rapportService.genererHtmlEtatMandatement(dateDebut, dateFin);

                    case CENTRE_REPARTITION:
                        return rapportService.genererHtmlEtatParCentre(dateDebut, dateFin);

                    case INDICATEURS_REELS:
                        return rapportService.genererHtmlIndicateursReels(dateDebut, dateFin);

                    case REPARTITION_PRODUIT:
                        return rapportService.genererHtmlRepartitionProduit(dateDebut, dateFin);

                    case SITUATION_GENERALE:
                        // Utiliser la méthode existante pour la situation générale
                        RapportService.SituationGeneraleDTO situationData =
                                rapportService.genererSituationGenerale(dateDebut, dateFin);
                        dernierRapportData = situationData;
                        return rapportService.genererHtmlSituationGenerale(situationData);

                    case TABLEAU_AMENDES_SERVICE:
                        return rapportService.genererHtmlTableauAmendesServices(dateDebut, dateFin);

                    case MANDATEMENT_AGENTS:
                        return rapportService.genererHtmlMandatementAgents(dateDebut, dateFin);

                    case ENCAISSEMENTS_PERIODE:
                        // Utiliser la méthode existante
                        dernierRapportData = rapportService.genererRapportEncaissements(dateDebut, dateFin);
                        return rapportService.genererHtmlEncaissements(dernierRapportData);

                    case AFFAIRES_NON_SOLDEES:
                        // Utiliser la méthode existante
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

                    logger.info("✅ Rapport {} généré avec succès", type.getLibelle());
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
            stage.setTitle("Aperçu du rapport - " + typeRapportComboBox.getValue().getLibelle());
            stage.initModality(Modality.APPLICATION_MODAL);

            // Créer une WebView pour l'aperçu
            WebView webView = new WebView();
            webView.getEngine().loadContent(dernierRapportGenere);

            // Barre d'outils pour l'aperçu
            ToolBar toolBar = new ToolBar();

            Button printButton = new Button("Imprimer");
            printButton.setOnAction(e -> printService.printWebView(webView));

            Button closeButton = new Button("Fermer");
            closeButton.setOnAction(e -> stage.close());

            toolBar.getItems().addAll(printButton, new Separator(), closeButton);

            // Layout
            VBox vbox = new VBox();
            vbox.getChildren().addAll(toolBar, webView);
            VBox.setVgrow(webView, javafx.scene.layout.Priority.ALWAYS);

            Scene scene = new Scene(vbox, 1000, 700);
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
                        logger.info("✅ Impression du rapport réussie");
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

        // Nom de fichier par défaut avec le type de rapport
        String nomFichier = String.format("%s_%s.pdf",
                typeRapportComboBox.getValue().name().toLowerCase(),
                DateFormatter.formatForFilename(LocalDate.now())
        );
        fileChooser.setInitialFileName(nomFichier);

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
        if (dernierRapportGenere == null) {
            AlertUtil.showWarningAlert("Attention",
                    "Aucun rapport",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Enregistrer le rapport Excel");

        // Nom de fichier par défaut avec le type de rapport
        String nomFichier = String.format("%s_%s.xlsx",
                typeRapportComboBox.getValue().name().toLowerCase(),
                DateFormatter.formatForFilename(LocalDate.now())
        );
        fileChooser.setInitialFileName(nomFichier);

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
                        logger.info("✅ Export PDF réussi: {}", file.getAbsolutePath());
                    } else {
                        statusLabel.setText("Échec de l'export PDF");
                        AlertUtil.showErrorAlert("Erreur",
                                "Export échoué",
                                "Impossible d'exporter le rapport en PDF.");
                    }
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
                // TODO: Implémenter l'export Excel selon le type de rapport
                // Pour l'instant, export générique
                return exportService.exportGenericToExcel(dernierRapportData, file.getAbsolutePath());
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
                        logger.info("✅ Export Excel réussi: {}", file.getAbsolutePath());
                    } else {
                        statusLabel.setText("Échec de l'export Excel");
                        AlertUtil.showErrorAlert("Erreur",
                                "Export échoué",
                                "Impossible d'exporter le rapport en Excel.");
                    }
                });
            }
        };

        Thread exportThread = new Thread(exportTask);
        exportThread.setDaemon(true);
        exportThread.start();
    }
}