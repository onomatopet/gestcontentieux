package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.Mandat;
import com.regulation.contentieux.model.enums.StatutMandat;
import com.regulation.contentieux.service.MandatService;
import com.regulation.contentieux.service.MandatService.MandatStatistiques;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.CurrencyFormatter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Contrôleur pour la gestion des mandats
 * Permet de créer, activer, clôturer et consulter les mandats
 */
public class MandatController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MandatController.class);

    // Composants FXML
    @FXML private Label titleLabel;
    @FXML private Label mandatActifLabel;
    @FXML private Label periodeActifLabel;
    @FXML private Label statutActifLabel;

    // Section création
    @FXML private VBox creationSection;
    @FXML private TextField descriptionField;
    @FXML private DatePicker dateDebutPicker;
    @FXML private DatePicker dateFinPicker;
    @FXML private Button createButton;
    @FXML private Label numeroGenereLabel;

    // Tableau des mandats
    @FXML private TableView<Mandat> mandatsTable;
    @FXML private TableColumn<Mandat, String> numeroColumn;
    @FXML private TableColumn<Mandat, String> periodeColumn;
    @FXML private TableColumn<Mandat, String> statutColumn;
    @FXML private TableColumn<Mandat, Integer> affairesColumn;
    @FXML private TableColumn<Mandat, String> montantColumn;
    @FXML private TableColumn<Mandat, Void> actionsColumn;

    // Filtres
    @FXML private ComboBox<StatutMandat> statutFilterCombo;
    @FXML private CheckBox seulementActifsCheck;
    @FXML private Button refreshButton;

    // Section statistiques
    @FXML private VBox statisticsSection;
    @FXML private Label statsNombreAffaires;
    @FXML private Label statsAffairesSoldees;
    @FXML private Label statsAffairesEnCours;
    @FXML private Label statsMontantTotal;
    @FXML private Label statsNombreAgents;

    // Services
    private final MandatService mandatService = MandatService.getInstance();
    private final ObservableList<Mandat> mandatsList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisation du contrôleur de gestion des mandats");

        setupUI();
        setupEventHandlers();
        loadData();
        updateMandatActif();
    }

    /**
     * Configuration de l'interface utilisateur
     */
    private void setupUI() {
        // Configuration des dates par défaut
        LocalDate now = LocalDate.now();
        dateDebutPicker.setValue(now.withDayOfMonth(1));
        dateFinPicker.setValue(now.withDayOfMonth(now.lengthOfMonth()));

        // Configuration du filtre de statut
        statutFilterCombo.getItems().add(null); // Tous
        statutFilterCombo.getItems().addAll(StatutMandat.values());
        statutFilterCombo.setConverter(new StringConverter<StatutMandat>() {
            @Override
            public String toString(StatutMandat statut) {
                return statut == null ? "Tous les statuts" : statut.getLibelle();
            }

            @Override
            public StatutMandat fromString(String string) {
                return null;
            }
        });

        // Configuration du tableau
        setupTableColumns();
        mandatsTable.setItems(mandatsList);

        // Style pour le mandat actif
        mandatsTable.setRowFactory(tv -> {
            TableRow<Mandat> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null && newItem.isActif()) {
                    row.getStyleClass().add("active-row");
                } else {
                    row.getStyleClass().remove("active-row");
                }
            });
            return row;
        });
    }

    /**
     * Configuration des colonnes du tableau
     */
    private void setupTableColumns() {
        numeroColumn.setCellValueFactory(new PropertyValueFactory<>("numeroMandat"));

        periodeColumn.setCellValueFactory(cellData -> {
            Mandat mandat = cellData.getValue();
            return javafx.beans.binding.Bindings.createStringBinding(
                    () -> mandat.getPeriodeFormatee()
            );
        });

        statutColumn.setCellValueFactory(cellData -> {
            Mandat mandat = cellData.getValue();
            return javafx.beans.binding.Bindings.createStringBinding(
                    () -> mandat.getStatut().getLibelle()
            );
        });

        affairesColumn.setCellValueFactory(new PropertyValueFactory<>("nombreAffaires"));

        montantColumn.setCellValueFactory(cellData -> {
            Mandat mandat = cellData.getValue();
            return javafx.beans.binding.Bindings.createStringBinding(
                    () -> mandat.getMontantTotal() != null ?
                            CurrencyFormatter.format(mandat.getMontantTotal()) : "0"
            );
        });

        // Colonne actions
        actionsColumn.setCellFactory(column -> new TableCell<>() {
            private final Button activerBtn = new Button("Activer");
            private final Button cloturerBtn = new Button("Clôturer");
            private final Button statsBtn = new Button("Stats");
            private final HBox buttons = new HBox(5, activerBtn, cloturerBtn, statsBtn);

            {
                activerBtn.getStyleClass().add("button-success");
                cloturerBtn.getStyleClass().add("button-danger");
                statsBtn.getStyleClass().add("button-info");

                activerBtn.setOnAction(e -> activerMandat(getTableRow().getItem()));
                cloturerBtn.setOnAction(e -> cloturerMandat(getTableRow().getItem()));
                statsBtn.setOnAction(e -> afficherStatistiques(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Mandat mandat = getTableRow().getItem();

                    // Visibilité des boutons selon le statut
                    activerBtn.setVisible(mandat.peutEtreActive() && !mandat.isActif());
                    activerBtn.setManaged(mandat.peutEtreActive() && !mandat.isActif());

                    cloturerBtn.setVisible(mandat.isActif());
                    cloturerBtn.setManaged(mandat.isActif());

                    setGraphic(buttons);
                }
            }
        });
    }

    /**
     * Configuration des gestionnaires d'événements
     */
    private void setupEventHandlers() {
        // Bouton créer
        createButton.setOnAction(e -> creerNouveauMandat());

        // Mise à jour automatique de la date de fin
        dateDebutPicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate != null) {
                dateFinPicker.setValue(newDate.withDayOfMonth(
                        newDate.lengthOfMonth()));
            }
        });

        // Filtres
        statutFilterCombo.setOnAction(e -> loadData());
        seulementActifsCheck.setOnAction(e -> loadData());
        refreshButton.setOnAction(e -> loadData());

        // Sélection dans le tableau
        mandatsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        afficherStatistiques(newSelection);
                    }
                }
        );
    }

    /**
     * Création d'un nouveau mandat
     */
    private void creerNouveauMandat() {
        try {
            // Validation
            if (dateDebutPicker.getValue() == null || dateFinPicker.getValue() == null) {
                AlertUtil.showWarning("Validation", "Veuillez sélectionner les dates du mandat");
                return;
            }

            if (dateFinPicker.getValue().isBefore(dateDebutPicker.getValue())) {
                AlertUtil.showWarning("Validation", "La date de fin doit être après la date de début");
                return;
            }

            // Confirmation
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Création de mandat");
            confirm.setHeaderText("Créer un nouveau mandat ?");
            confirm.setContentText("Période : " + dateDebutPicker.getValue().format(
                    DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " au " +
                    dateFinPicker.getValue().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }

            // Création
            createButton.setDisable(true);

            Task<Mandat> createTask = new Task<>() {
                @Override
                protected Mandat call() throws Exception {
                    return mandatService.creerNouveauMandat(descriptionField.getText());
                }
            };

            createTask.setOnSucceeded(e -> {
                Mandat nouveauMandat = createTask.getValue();
                Platform.runLater(() -> {
                    createButton.setDisable(false);
                    numeroGenereLabel.setText("Numéro généré : " + nouveauMandat.getNumeroMandat());

                    AlertUtil.showSuccess("Succès",
                            "Mandat " + nouveauMandat.getNumeroMandat() + " créé avec succès");

                    // Réinitialiser le formulaire
                    descriptionField.clear();
                    LocalDate now = LocalDate.now();
                    dateDebutPicker.setValue(now.withDayOfMonth(1));
                    dateFinPicker.setValue(now.withDayOfMonth(now.lengthOfMonth()));

                    // Recharger la liste
                    loadData();
                });
            });

            createTask.setOnFailed(e -> {
                Platform.runLater(() -> {
                    createButton.setDisable(false);
                    Throwable error = createTask.getException();
                    logger.error("Erreur lors de la création du mandat", error);
                    AlertUtil.showError("Erreur", "Impossible de créer le mandat",
                            error.getMessage());
                });
            });

            new Thread(createTask).start();

        } catch (Exception e) {
            logger.error("Erreur lors de la création du mandat", e);
            AlertUtil.showError("Erreur", "Erreur lors de la création", e.getMessage());
        }
    }

    /**
     * Active un mandat
     */
    private void activerMandat(Mandat mandat) {
        if (mandat == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Activation de mandat");
        confirm.setHeaderText("Activer le mandat " + mandat.getNumeroMandat() + " ?");
        confirm.setContentText("Le mandat actuellement actif sera désactivé.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                mandatService.activerMandat(mandat.getNumeroMandat());
                AlertUtil.showSuccess("Succès", "Mandat activé avec succès");
                loadData();
                updateMandatActif();
            } catch (Exception e) {
                logger.error("Erreur lors de l'activation du mandat", e);
                AlertUtil.showError("Erreur", "Impossible d'activer le mandat", e.getMessage());
            }
        }
    }

    /**
     * Clôture un mandat
     */
    private void cloturerMandat(Mandat mandat) {
        if (mandat == null) return;

        Alert confirm = new Alert(Alert.AlertType.WARNING);
        confirm.setTitle("Clôture de mandat");
        confirm.setHeaderText("Clôturer définitivement le mandat " + mandat.getNumeroMandat() + " ?");
        confirm.setContentText("Cette action est irréversible. Le mandat ne pourra plus être modifié.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                mandatService.cloturerMandatActif();
                AlertUtil.showSuccess("Succès", "Mandat clôturé avec succès");
                loadData();
                updateMandatActif();
            } catch (Exception e) {
                logger.error("Erreur lors de la clôture du mandat", e);
                AlertUtil.showError("Erreur", "Impossible de clôturer le mandat", e.getMessage());
            }
        }
    }

    /**
     * Affiche les statistiques d'un mandat
     */
    private void afficherStatistiques(Mandat mandat) {
        if (mandat == null) {
            statisticsSection.setVisible(false);
            return;
        }

        statisticsSection.setVisible(true);

        Task<MandatStatistiques> statsTask = new Task<>() {
            @Override
            protected MandatStatistiques call() throws Exception {
                return mandatService.getStatistiques(mandat.getNumeroMandat());
            }
        };

        statsTask.setOnSucceeded(e -> {
            MandatStatistiques stats = statsTask.getValue();
            Platform.runLater(() -> {
                statsNombreAffaires.setText(String.valueOf(stats.getNombreAffaires()));
                statsAffairesSoldees.setText(String.valueOf(stats.getAffairesSoldees()));
                statsAffairesEnCours.setText(String.valueOf(stats.getAffairesEnCours()));
                statsMontantTotal.setText(CurrencyFormatter.format(stats.getMontantTotalEncaisse()));
                statsNombreAgents.setText(String.valueOf(stats.getNombreAgents()));
            });
        });

        new Thread(statsTask).start();
    }

    /**
     * Charge les données
     */
    private void loadData() {
        Task<List<Mandat>> loadTask = new Task<>() {
            @Override
            protected List<Mandat> call() throws Exception {
                return mandatService.listerMandats(
                        seulementActifsCheck.isSelected(),
                        statutFilterCombo.getValue()
                );
            }
        };

        loadTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                mandatsList.clear();
                mandatsList.addAll(loadTask.getValue());

                // Sélectionner le mandat actif
                mandatsList.stream()
                        .filter(Mandat::isActif)
                        .findFirst()
                        .ifPresent(m -> mandatsTable.getSelectionModel().select(m));
            });
        });

        new Thread(loadTask).start();
    }

    /**
     * Met à jour l'affichage du mandat actif
     */
    private void updateMandatActif() {
        Mandat mandatActif = mandatService.getMandatActif();

        if (mandatActif != null) {
            mandatActifLabel.setText(mandatActif.getNumeroMandat());
            periodeActifLabel.setText(mandatActif.getPeriodeFormatee());
            statutActifLabel.setText(mandatActif.getStatut().getLibelle());
            statutActifLabel.getStyleClass().clear();
            statutActifLabel.getStyleClass().add("label");
            statutActifLabel.getStyleClass().add("label-success");
        } else {
            mandatActifLabel.setText("Aucun");
            periodeActifLabel.setText("-");
            statutActifLabel.setText("Inactif");
            statutActifLabel.getStyleClass().clear();
            statutActifLabel.getStyleClass().add("label");
            statutActifLabel.getStyleClass().add("label-danger");
        }
    }

    /**
     * Ferme la fenêtre
     */
    @FXML
    private void close() {
        Stage stage = (Stage) titleLabel.getScene().getWindow();
        stage.close();
    }
}