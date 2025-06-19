package com.regulation.contentieux.controller;

import com.regulation.contentieux.service.RapportService;
import com.regulation.contentieux.service.RapportService.RapportRepartitionDTO;
import com.regulation.contentieux.service.ExportService;
import com.regulation.contentieux.service.PrintService;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.FXMLLoaderUtil;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Contrôleur principal pour la gestion des rapports
 * Interface pour sélectionner, générer et imprimer les rapports
 */
public class RapportController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(RapportController.class);

    @FXML private ComboBox<String> typeRapportComboBox;
    @FXML private ComboBox<String> periodeTypeComboBox;
    @FXML private ComboBox<Integer> anneeComboBox;
    @FXML private ComboBox<Month> moisComboBox;
    @FXML private ComboBox<Integer> trimestreComboBox;
    @FXML private DatePicker dateDebutPicker;
    @FXML private DatePicker dateFinPicker;

    @FXML private Button genererButton;
    @FXML private Button apercuButton;
    @FXML private Button imprimerButton;
    @FXML private Button exportExcelButton;
    @FXML private Button exportCSVButton;

    @FXML private VBox parametresCustomBox;
    @FXML private VBox parametresMoisBox;
    @FXML private VBox parametresTrimestreBox;

    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private TextArea previewTextArea;

    private final RapportService rapportService;
    private final ExportService exportService;
    private final PrintService printService;

    private RapportRepartitionDTO dernierRapportGenere;

    public RapportController() {
        this.rapportService = new RapportService();
        this.exportService = new ExportService();
        this.printService = new PrintService();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupComboBoxes();
        setupEventHandlers();
        setupInitialState();

        logger.info("RapportController initialisé");
    }

    private void setupComboBoxes() {
        // Types de rapports
        typeRapportComboBox.setItems(FXCollections.observableArrayList(
                "Rapport de Rétrocession",
                "Rapport Statistique",
                "Rapport d'Activité"
        ));
        typeRapportComboBox.setValue("Rapport de Rétrocession");

        // Types de périodes
        periodeTypeComboBox.setItems(FXCollections.observableArrayList(
                "Personnalisée",
                "Mensuelle",
                "Trimestrielle",
                "Annuelle"
        ));
        periodeTypeComboBox.setValue("Mensuelle");

        // Années (5 dernières années)
        int currentYear = Year.now().getValue();
        for (int year = currentYear; year >= currentYear - 5; year--) {
            anneeComboBox.getItems().add(year);
        }
        anneeComboBox.setValue(currentYear);

        // Mois
        moisComboBox.setItems(FXCollections.observableArrayList(Month.values()));
        moisComboBox.setValue(LocalDate.now().getMonth());

        // Trimestres
        trimestreComboBox.setItems(FXCollections.observableArrayList(1, 2, 3, 4));
        trimestreComboBox.setValue(getCurrentTrimestre());

        // Dates par défaut
        LocalDate now = LocalDate.now();
        dateDebutPicker.setValue(now.withDayOfMonth(1));
        dateFinPicker.setValue(now.withDayOfMonth(now.lengthOfMonth()));
    }

    private void setupEventHandlers() {
        // Changement de type de période
        periodeTypeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateParametresVisibility(newVal);
        });

        // Actions des boutons
        genererButton.setOnAction(e -> genererRapport());
        apercuButton.setOnAction(e -> afficherApercu());
        imprimerButton.setOnAction(e -> imprimerRapport());
        exportExcelButton.setOnAction(e -> exporterExcel());
        exportCSVButton.setOnAction(e -> exporterCSV());

        // Validation des dates
        dateDebutPicker.valueProperty().addListener((obs, oldVal, newVal) -> validerDates());
        dateFinPicker.valueProperty().addListener((obs, oldVal, newVal) -> validerDates());
    }

    private void setupInitialState() {
        updateParametresVisibility("Mensuelle");

        // État initial des boutons
        apercuButton.setDisable(true);
        imprimerButton.setDisable(true);
        exportExcelButton.setDisable(true);
        exportCSVButton.setDisable(true);

        progressBar.setVisible(false);
        statusLabel.setText("Prêt à générer un rapport");
    }

    private void updateParametresVisibility(String periodeType) {
        parametresCustomBox.setVisible("Personnalisée".equals(periodeType));
        parametresMoisBox.setVisible("Mensuelle".equals(periodeType));
        parametresTrimestreBox.setVisible("Trimestrielle".equals(periodeType));

        // Les paramètres annuels utilisent juste l'année
        boolean showAnnee = !"Personnalisée".equals(periodeType);
        anneeComboBox.setVisible(showAnnee);
    }

    @FXML
    private void genererRapport() {
        try {
            if (!validerParametres()) {
                return;
            }

            // Récupération des paramètres
            LocalDate dateDebut = getDateDebut();
            LocalDate dateFin = getDateFin();

            logger.info("Génération du rapport du {} au {}", dateDebut, dateFin);

            // Affichage du progrès
            progressBar.setVisible(true);
            progressBar.setProgress(-1); // Mode indéterminé
            statusLabel.setText("Génération du rapport en cours...");
            genererButton.setDisable(true);

            // Tâche asynchrone
            Task<RapportRepartitionDTO> task = new Task<RapportRepartitionDTO>() {
                @Override
                protected RapportRepartitionDTO call() throws Exception {
                    updateMessage("Collecte des données...");
                    updateProgress(25, 100);

                    RapportRepartitionDTO rapport = rapportService.genererRapportRepartition(dateDebut, dateFin);

                    updateMessage("Calcul des statistiques...");
                    updateProgress(75, 100);

                    // Simulation d'un petit délai pour l'UX
                    Thread.sleep(500);

                    updateMessage("Rapport généré avec succès");
                    updateProgress(100, 100);

                    return rapport;
                }
            };

            task.setOnSucceeded(e -> {
                dernierRapportGenere = task.getValue();
                onRapportGenereAvecSucces();
            });

            task.setOnFailed(e -> {
                Throwable exception = task.getException();
                logger.error("Erreur lors de la génération du rapport", exception);
                onErreurGenerationRapport(exception.getMessage());
            });

            // Binding des messages
            statusLabel.textProperty().bind(task.messageProperty());
            progressBar.progressProperty().bind(task.progressProperty());

            // Exécution
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();

        } catch (Exception e) {
            logger.error("Erreur lors du lancement de la génération", e);
            AlertUtil.showErrorAlert("Erreur", "Génération impossible",
                    "Une erreur est survenue: " + e.getMessage());
        }
    }

    private void onRapportGenereAvecSucces() {
        // Réinitialisation de l'interface
        progressBar.setVisible(false);
        statusLabel.textProperty().unbind();
        statusLabel.setText(String.format("Rapport généré: %d affaires, %s",
                dernierRapportGenere.getNombreAffaires(),
                dernierRapportGenere.getPeriodeLibelle()));

        genererButton.setDisable(false);

        // Activation des boutons d'action
        apercuButton.setDisable(false);
        imprimerButton.setDisable(false);
        exportExcelButton.setDisable(false);
        exportCSVButton.setDisable(false);

        // Aperçu textuel dans la zone de prévisualisation
        afficherApercuTextuel();

        AlertUtil.showInfoAlert("Succès", "Rapport généré",
                "Le rapport a été généré avec succès. Vous pouvez maintenant l'imprimer ou l'exporter.");
    }

    private void onErreurGenerationRapport(String message) {
        progressBar.setVisible(false);
        statusLabel.textProperty().unbind();
        statusLabel.setText("Erreur lors de la génération");
        genererButton.setDisable(false);

        AlertUtil.showErrorAlert("Erreur", "Génération échouée",
                "Impossible de générer le rapport: " + message);
    }

    @FXML
    private void afficherApercu() {
        if (dernierRapportGenere == null) {
            AlertUtil.showWarningAlert("Attention", "Aucun rapport",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        try {
            // Création de la fenêtre d'aperçu
            Stage apercuStage = new Stage();
            apercuStage.setTitle("Aperçu du Rapport - " + dernierRapportGenere.getPeriodeLibelle());
            apercuStage.initModality(Modality.APPLICATION_MODAL);

            // Génération de l'aperçu
            WebView webView = printService.genererApercuRapport(dernierRapportGenere);
            webView.setPrefSize(800, 600);

            // Layout
            VBox root = new VBox(10);

            // Boutons d'action dans l'aperçu
            Button imprimerFromPreviewBtn = new Button("Imprimer");
            Button fermerBtn = new Button("Fermer");

            imprimerFromPreviewBtn.setOnAction(e -> {
                imprimerRapport();
                apercuStage.close();
            });

            fermerBtn.setOnAction(e -> apercuStage.close());

            ToolBar toolbar = new ToolBar(imprimerFromPreviewBtn, new Separator(), fermerBtn);

            root.getChildren().addAll(toolbar, webView);

            Scene scene = new Scene(root, 850, 700);
            apercuStage.setScene(scene);
            apercuStage.show();

            logger.info("Aperçu affiché pour le rapport: {}", dernierRapportGenere.getPeriodeLibelle());

        } catch (Exception e) {
            logger.error("Erreur lors de l'affichage de l'aperçu", e);
            AlertUtil.showErrorAlert("Erreur", "Aperçu impossible",
                    "Impossible d'afficher l'aperçu: " + e.getMessage());
        }
    }

    @FXML
    private void imprimerRapport() {
        if (dernierRapportGenere == null) {
            AlertUtil.showWarningAlert("Attention", "Aucun rapport",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        if (!printService.isImprimanteDisponible()) {
            AlertUtil.showErrorAlert("Erreur", "Aucune imprimante",
                    "Aucune imprimante n'est disponible sur ce système.");
            return;
        }

        try {
            statusLabel.setText("Impression en cours...");

            CompletableFuture<Boolean> printTask = printService.imprimerRapport(dernierRapportGenere);

            printTask.thenAccept(success -> {
                javafx.application.Platform.runLater(() -> {
                    if (success) {
                        statusLabel.setText("Impression terminée avec succès");
                        AlertUtil.showInfoAlert("Succès", "Impression terminée",
                                "Le rapport a été envoyé à l'imprimante.");
                    } else {
                        statusLabel.setText("Erreur lors de l'impression");
                        AlertUtil.showErrorAlert("Erreur", "Impression échouée",
                                "L'impression n'a pas pu être réalisée.");
                    }
                });
            }).exceptionally(throwable -> {
                javafx.application.Platform.runLater(() -> {
                    logger.error("Erreur lors de l'impression", throwable);
                    statusLabel.setText("Erreur lors de l'impression");
                    AlertUtil.showErrorAlert("Erreur", "Impression échouée",
                            "Une erreur est survenue: " + throwable.getMessage());
                });
                return null;
            });

        } catch (Exception e) {
            logger.error("Erreur lors du lancement de l'impression", e);
            AlertUtil.showErrorAlert("Erreur", "Impression impossible",
                    "Impossible de lancer l'impression: " + e.getMessage());
        }
    }

    @FXML
    private void exporterExcel() {
        if (dernierRapportGenere == null) {
            AlertUtil.showWarningAlert("Attention", "Aucun rapport",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        try {
            // Dialogue de sauvegarde
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Exporter le rapport Excel");
            fileChooser.setInitialFileName(exportService.genererNomFichier("Excel",
                    dernierRapportGenere.getDateDebut(), dernierRapportGenere.getDateFin()));
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Fichiers Excel", "*.xlsx"));

            File file = fileChooser.showSaveDialog(genererButton.getScene().getWindow());
            if (file == null) {
                return; // Annulé par l'utilisateur
            }

            statusLabel.setText("Export Excel en cours...");

            // Tâche asynchrone pour l'export
            Task<Void> exportTask = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    updateMessage("Génération du fichier Excel...");
                    byte[] excelData = exportService.exporterRapportVersExcel(dernierRapportGenere);

                    updateMessage("Sauvegarde du fichier...");
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(excelData);
                    }

                    updateMessage("Export terminé");
                    return null;
                }
            };

            exportTask.setOnSucceeded(e -> {
                statusLabel.setText("Export Excel terminé: " + file.getName());
                AlertUtil.showInfoAlert("Succès", "Export terminé",
                        "Le rapport a été exporté vers: " + file.getAbsolutePath());
            });

            exportTask.setOnFailed(e -> {
                Throwable exception = exportTask.getException();
                logger.error("Erreur lors de l'export Excel", exception);
                statusLabel.setText("Erreur lors de l'export Excel");
                AlertUtil.showErrorAlert("Erreur", "Export échoué",
                        "Impossible d'exporter le rapport: " + exception.getMessage());
            });

            Thread thread = new Thread(exportTask);
            thread.setDaemon(true);
            thread.start();

        } catch (Exception e) {
            logger.error("Erreur lors de l'export Excel", e);
            AlertUtil.showErrorAlert("Erreur", "Export impossible",
                    "Une erreur est survenue: " + e.getMessage());
        }
    }

    @FXML
    private void exporterCSV() {
        if (dernierRapportGenere == null) {
            AlertUtil.showWarningAlert("Attention", "Aucun rapport",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        try {
            // Dialogue de sauvegarde
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Exporter le rapport CSV");
            fileChooser.setInitialFileName(exportService.genererNomFichier("CSV",
                    dernierRapportGenere.getDateDebut(), dernierRapportGenere.getDateFin()));
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Fichiers CSV", "*.csv"));

            File file = fileChooser.showSaveDialog(genererButton.getScene().getWindow());
            if (file == null) {
                return;
            }

            String csvContent = exportService.exporterRapportVersCSV(dernierRapportGenere);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(csvContent.getBytes("UTF-8"));
            }

            statusLabel.setText("Export CSV terminé: " + file.getName());
            AlertUtil.showInfoAlert("Succès", "Export terminé",
                    "Le rapport a été exporté vers: " + file.getAbsolutePath());

        } catch (IOException e) {
            logger.error("Erreur lors de l'export CSV", e);
            AlertUtil.showErrorAlert("Erreur", "Export échoué",
                    "Impossible d'exporter le rapport: " + e.getMessage());
        }
    }

    /**
     * Méthodes utilitaires
     */
    private boolean validerParametres() {
        String periodeType = periodeTypeComboBox.getValue();

        if ("Personnalisée".equals(periodeType)) {
            LocalDate debut = dateDebutPicker.getValue();
            LocalDate fin = dateFinPicker.getValue();

            if (debut == null || fin == null) {
                AlertUtil.showWarningAlert("Validation", "Dates manquantes",
                        "Veuillez sélectionner les dates de début et de fin.");
                return false;
            }

            if (debut.isAfter(fin)) {
                AlertUtil.showWarningAlert("Validation", "Dates invalides",
                        "La date de début doit être antérieure à la date de fin.");
                return false;
            }

            if (!rapportService.validerParametresRapport(debut, fin)) {
                AlertUtil.showWarningAlert("Validation", "Période invalide",
                        "La période sélectionnée n'est pas valide.");
                return false;
            }
        }

        return true;
    }

    private LocalDate getDateDebut() {
        String periodeType = periodeTypeComboBox.getValue();

        switch (periodeType) {
            case "Personnalisée":
                return dateDebutPicker.getValue();

            case "Mensuelle":
                int annee = anneeComboBox.getValue();
                Month mois = moisComboBox.getValue();
                return LocalDate.of(annee, mois, 1);

            case "Trimestrielle":
                int anneeT = anneeComboBox.getValue();
                int trimestre = trimestreComboBox.getValue();
                int moisDebut = (trimestre - 1) * 3 + 1;
                return LocalDate.of(anneeT, moisDebut, 1);

            case "Annuelle":
                int anneeA = anneeComboBox.getValue();
                return LocalDate.of(anneeA, 1, 1);

            default:
                return LocalDate.now().withDayOfMonth(1);
        }
    }

    private LocalDate getDateFin() {
        String periodeType = periodeTypeComboBox.getValue();

        switch (periodeType) {
            case "Personnalisée":
                return dateFinPicker.getValue();

            case "Mensuelle":
                LocalDate debutMois = getDateDebut();
                return debutMois.withDayOfMonth(debutMois.lengthOfMonth());

            case "Trimestrielle":
                LocalDate debutTrimestre = getDateDebut();
                return debutTrimestre.plusMonths(2).withDayOfMonth(
                        debutTrimestre.plusMonths(2).lengthOfMonth());

            case "Annuelle":
                int annee = anneeComboBox.getValue();
                return LocalDate.of(annee, 12, 31);

            default:
                return LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        }
    }

    private void validerDates() {
        if (dateDebutPicker.getValue() != null && dateFinPicker.getValue() != null) {
            if (dateDebutPicker.getValue().isAfter(dateFinPicker.getValue())) {
                dateFinPicker.setStyle("-fx-border-color: red;");
                dateDebutPicker.setStyle("-fx-border-color: red;");
            } else {
                dateFinPicker.setStyle("");
                dateDebutPicker.setStyle("");
            }
        }
    }

    private int getCurrentTrimestre() {
        int mois = LocalDate.now().getMonthValue();
        return (mois - 1) / 3 + 1;
    }

    private void afficherApercuTextuel() {
        if (dernierRapportGenere == null) {
            return;
        }

        StringBuilder apercu = new StringBuilder();
        apercu.append("=== RAPPORT DE RÉTROCESSION ===\n");
        apercu.append("Période: ").append(dernierRapportGenere.getPeriodeLibelle()).append("\n");
        apercu.append("Généré le: ").append(dernierRapportGenere.getDateGeneration()).append("\n\n");

        apercu.append("RÉSUMÉ:\n");
        apercu.append("- Nombre d'affaires: ").append(dernierRapportGenere.getNombreAffaires()).append("\n");
        apercu.append("- Nombre d'encaissements: ").append(dernierRapportGenere.getNombreEncaissements()).append("\n");
        apercu.append("- Total encaissé: ").append(dernierRapportGenere.getTotalEncaisse()).append(" FCFA\n");
        apercu.append("- Part État (60%): ").append(dernierRapportGenere.getTotalEtat()).append(" FCFA\n");
        apercu.append("- Part Collectivités (40%): ").append(dernierRapportGenere.getTotalCollectivite()).append(" FCFA\n\n");

        if (!dernierRapportGenere.getRepartitionParBureau().isEmpty()) {
            apercu.append("RÉPARTITION PAR BUREAU:\n");
            dernierRapportGenere.getRepartitionParBureau().entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .forEach(entry -> apercu.append("- ").append(entry.getKey())
                            .append(": ").append(entry.getValue()).append(" FCFA\n"));
            apercu.append("\n");
        }

        apercu.append("Utilisez les boutons ci-dessus pour afficher l'aperçu complet, imprimer ou exporter le rapport.");

        previewTextArea.setText(apercu.toString());
    }

    /**
     * Méthodes appelées depuis l'extérieur
     */
    public void chargerRapportExistant(RapportRepartitionDTO rapport) {
        this.dernierRapportGenere = rapport;
        onRapportGenereAvecSucces();
    }

    public void configurerPourPeriode(LocalDate debut, LocalDate fin) {
        periodeTypeComboBox.setValue("Personnalisée");
        dateDebutPicker.setValue(debut);
        dateFinPicker.setValue(fin);
    }

    public void configurerPourMois(int annee, Month mois) {
        periodeTypeComboBox.setValue("Mensuelle");
        anneeComboBox.setValue(annee);
        moisComboBox.setValue(mois);
    }

    public void configurerPourTrimestre(int annee, int trimestre) {
        periodeTypeComboBox.setValue("Trimestrielle");
        anneeComboBox.setValue(annee);
        trimestreComboBox.setValue(trimestre);
    }

    public void configurerPourAnnee(int annee) {
        periodeTypeComboBox.setValue("Annuelle");
        anneeComboBox.setValue(annee);
    }

    /**
     * Getters pour l'accès externe
     */
    public RapportRepartitionDTO getDernierRapportGenere() {
        return dernierRapportGenere;
    }

    public boolean hasRapportGenere() {
        return dernierRapportGenere != null;
    }

    /**
     * Méthode pour rafraîchir les données de référence
     */
    public void rafraichirDonnees() {
        // Réinitialiser le dernier rapport
        dernierRapportGenere = null;

        // Désactiver les boutons d'action
        apercuButton.setDisable(true);
        imprimerButton.setDisable(true);
        exportExcelButton.setDisable(true);
        exportCSVButton.setDisable(true);

        // Vider l'aperçu
        previewTextArea.clear();

        statusLabel.setText("Prêt à générer un rapport");

        logger.info("Données de rapport rafraîchies");
    }

    /**
     * Validation et nettoyage lors de la fermeture
     */
    public void cleanup() {
        // Annuler les tâches en cours si nécessaire
        if (progressBar.isVisible()) {
            statusLabel.setText("Arrêt en cours...");
        }

        dernierRapportGenere = null;
        logger.info("Nettoyage du contrôleur de rapports effectué");
    }
}