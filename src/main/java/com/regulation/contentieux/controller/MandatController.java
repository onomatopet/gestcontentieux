package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.Mandat;
import com.regulation.contentieux.model.enums.StatutMandat;
import com.regulation.contentieux.model.enums.RoleUtilisateur;
import com.regulation.contentieux.service.MandatService;
import com.regulation.contentieux.service.MandatService.MandatStatistiques;
import com.regulation.contentieux.service.AuthenticationService;
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
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;
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
    private final AuthenticationService authService = AuthenticationService.getInstance();

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
    /**
     * Configuration de l'interface utilisateur
     */
    private void setupUI() {
        // Configuration des dates par défaut
        LocalDate now = LocalDate.now();
        dateDebutPicker.setValue(now.withDayOfMonth(1));
        dateFinPicker.setValue(now.withDayOfMonth(now.lengthOfMonth()));

        // Vérifier si l'utilisateur est SUPER_ADMIN pour personnaliser l'interface
        var user = authService.getCurrentUser();
        if (user != null && user.getRole() == RoleUtilisateur.SUPER_ADMIN) {
            // Ajouter un indicateur visuel pour le SUPER_ADMIN
            Label superAdminLabel = new Label("Mode SUPER_ADMIN - Dates personnalisées autorisées");
            superAdminLabel.setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;");
            creationSection.getChildren().add(0, superAdminLabel);

            // Permettre la sélection de dates antérieures
            dateDebutPicker.setDayCellFactory(picker -> new DateCell() {
                @Override
                public void updateItem(LocalDate date, boolean empty) {
                    super.updateItem(date, empty);
                    // Pas de restriction pour SUPER_ADMIN
                }
            });
        } else {
            // Pour les autres utilisateurs, bloquer les dates antérieures
            dateDebutPicker.setDayCellFactory(picker -> new DateCell() {
                @Override
                public void updateItem(LocalDate date, boolean empty) {
                    super.updateItem(date, empty);
                    LocalDate moisEnCours = LocalDate.now().withDayOfMonth(1);
                    setDisable(empty || date.isBefore(moisEnCours));
                }
            });
        }

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
            private final Button modifierBtn = new Button("Modifier");
            private final Button statsBtn = new Button("Stats");
            private final HBox buttons = new HBox(5);

            {
                activerBtn.getStyleClass().add("button-success");
                cloturerBtn.getStyleClass().add("button-danger");
                modifierBtn.getStyleClass().add("button-warning");
                statsBtn.getStyleClass().add("button-info");

                activerBtn.setOnAction(e -> activerMandat(getTableRow().getItem()));
                cloturerBtn.setOnAction(e -> cloturerMandat(getTableRow().getItem()));
                modifierBtn.setOnAction(e -> modifierMandat(getTableRow().getItem()));
                statsBtn.setOnAction(e -> afficherStatistiques(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Mandat mandat = getTableRow().getItem();
                    var user = authService.getCurrentUser();
                    boolean isSuperAdmin = user != null && user.getRole() == RoleUtilisateur.SUPER_ADMIN;

                    // Réinitialiser la liste des boutons
                    buttons.getChildren().clear();

                    // Bouton Activer : visible si pas actif ET (pas clôturé OU super admin)
                    if (!mandat.isActif() && (mandat.getStatut() != StatutMandat.CLOTURE || isSuperAdmin)) {
                        buttons.getChildren().add(activerBtn);
                    }

                    // Bouton Clôturer : visible si actif
                    if (mandat.isActif()) {
                        buttons.getChildren().add(cloturerBtn);
                    }

                    // Bouton Modifier : visible seulement pour SUPER_ADMIN
                    if (isSuperAdmin) {
                        buttons.getChildren().add(modifierBtn);
                    }

                    // Bouton Stats : toujours visible
                    buttons.getChildren().add(statsBtn);

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

            // Récupérer les dates sélectionnées
            LocalDate dateDebut = dateDebutPicker.getValue();
            LocalDate dateFin = dateFinPicker.getValue();
            String description = descriptionField.getText();

            // Vérifier si c'est une période antérieure
            LocalDate moisEnCours = LocalDate.now().withDayOfMonth(1);
            boolean estPeriodeAnterieure = dateDebut.isBefore(moisEnCours);

            // Message de confirmation adapté
            String messageConfirmation = "Créer un nouveau mandat ?";
            if (estPeriodeAnterieure) {
                var user = authService.getCurrentUser();
                if (user == null || user.getRole() != RoleUtilisateur.SUPER_ADMIN) {
                    AlertUtil.showError("Droits insuffisants",
                            "Seul un SUPER_ADMIN peut créer un mandat antérieur",
                            "Vous essayez de créer un mandat pour une période passée.");
                    return;
                }
                messageConfirmation = "Créer un mandat ANTÉRIEUR ?\n" +
                        "Ce mandat sera créé avec le statut CLÔTURÉ.";
            }

            // Confirmation
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Création de mandat");
            confirm.setHeaderText(messageConfirmation);
            confirm.setContentText("Période : " + dateDebut.format(
                    DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " au " +
                    dateFin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }

            // Création
            createButton.setDisable(true);

            Task<Mandat> createTask = new Task<>() {
                @Override
                protected Mandat call() throws Exception {
                    // Déterminer quelle méthode utiliser
                    LocalDate now = LocalDate.now();
                    boolean utiliserDatesPersonnalisees =
                            !dateDebut.equals(now.withDayOfMonth(1)) ||
                                    !dateFin.equals(now.withDayOfMonth(now.lengthOfMonth()));

                    if (utiliserDatesPersonnalisees) {
                        // Utiliser la nouvelle méthode avec dates personnalisées
                        return mandatService.creerMandatAvecDates(description, dateDebut, dateFin);
                    } else {
                        // Utiliser la méthode standard pour le mois en cours
                        return mandatService.creerNouveauMandat(description);
                    }
                }
            };

            createTask.setOnSucceeded(e -> {
                Mandat nouveauMandat = createTask.getValue();
                Platform.runLater(() -> {
                    createButton.setDisable(false);
                    numeroGenereLabel.setText("Numéro généré : " + nouveauMandat.getNumeroMandat());

                    String messageSucces = "Mandat " + nouveauMandat.getNumeroMandat() + " créé avec succès";
                    if (nouveauMandat.getStatut() == StatutMandat.CLOTURE) {
                        messageSucces += " (CLÔTURÉ)";
                    }

                    AlertUtil.showSuccess("Succès", messageSucces);

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
    /**
     * Active un mandat
     */
    private void activerMandat(Mandat mandat) {
        if (mandat == null) return;

        // Vérifier les droits pour les mandats clôturés
        var user = authService.getCurrentUser();
        boolean isSuperAdmin = user != null && user.getRole() == RoleUtilisateur.SUPER_ADMIN;

        String messageConfirmation = "Activer le mandat " + mandat.getNumeroMandat() + " ?";
        String messageDetail = "Le mandat actuellement actif sera désactivé.";

        if (mandat.getStatut() == StatutMandat.CLOTURE) {
            if (!isSuperAdmin) {
                AlertUtil.showError("Droits insuffisants",
                        "Seul un SUPER_ADMIN peut réactiver un mandat clôturé",
                        "Ce mandat a été clôturé et nécessite des droits administrateur.");
                return;
            }

            messageConfirmation = "RÉACTIVER le mandat CLÔTURÉ " + mandat.getNumeroMandat() + " ?";
            messageDetail = "ATTENTION : Ce mandat a été clôturé. Sa réactivation permettra " +
                    "de créer des affaires sur cette période antérieure.\n\n" +
                    "Le mandat actuellement actif sera désactivé.";
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Activation de mandat");
        confirm.setHeaderText(messageConfirmation);
        confirm.setContentText(messageDetail);

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                mandatService.activerMandat(mandat.getNumeroMandat());

                String messageSucces = "Mandat activé avec succès";
                if (mandat.getStatut() == StatutMandat.CLOTURE) {
                    messageSucces = "Mandat CLÔTURÉ réactivé avec succès";
                }

                AlertUtil.showSuccess("Succès", messageSucces);
                loadData();
                updateMandatActif();
            } catch (Exception e) {
                logger.error("Erreur lors de l'activation du mandat", e);
                AlertUtil.showError("Erreur", "Impossible d'activer le mandat", e.getMessage());
            }
        }
    }

    /**
     * Modifie les dates d'un mandat (SUPER_ADMIN uniquement)
     */
    private void modifierMandat(Mandat mandat) {
        if (mandat == null) return;

        var user = authService.getCurrentUser();
        if (user == null || user.getRole() != RoleUtilisateur.SUPER_ADMIN) {
            AlertUtil.showError("Droits insuffisants",
                    "Seul un SUPER_ADMIN peut modifier les dates d'un mandat",
                    "Cette fonctionnalité est réservée aux administrateurs.");
            return;
        }

        // Créer un dialogue de modification
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Modifier le mandat");
        dialog.setHeaderText("Modifier le mandat " + mandat.getNumeroMandat());

        // Créer le contenu du dialogue
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField descriptionField = new TextField(mandat.getDescription());
        DatePicker dateDebutPicker = new DatePicker(mandat.getDateDebut());
        DatePicker dateFinPicker = new DatePicker(mandat.getDateFin());

        grid.add(new Label("Description:"), 0, 0);
        grid.add(descriptionField, 1, 0);
        grid.add(new Label("Date début:"), 0, 1);
        grid.add(dateDebutPicker, 1, 1);
        grid.add(new Label("Date fin:"), 0, 2);
        grid.add(dateFinPicker, 1, 2);

        if (mandat.isActif()) {
            Label warningLabel = new Label("⚠️ ATTENTION : Ce mandat est ACTIF");
            warningLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold;");
            grid.add(warningLabel, 0, 3, 2, 1);
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                Mandat mandatModifie = mandatService.modifierDatesMandat(
                        mandat.getNumeroMandat(),
                        descriptionField.getText(),
                        dateDebutPicker.getValue(),
                        dateFinPicker.getValue()
                );

                AlertUtil.showSuccess("Succès",
                        "Mandat modifié avec succès\nNouveau numéro : " + mandatModifie.getNumeroMandat());

                loadData();
                updateMandatActif();

            } catch (Exception e) {
                logger.error("Erreur lors de la modification du mandat", e);
                AlertUtil.showError("Erreur", "Impossible de modifier le mandat", e.getMessage());
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