package com.regulation.contentieux.controller;

import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.dao.ContrevenantDAO;
import com.regulation.contentieux.dao.ContraventionDAO;
import com.regulation.contentieux.dao.AgentDAO;
import com.regulation.contentieux.dao.BureauDAO;
import com.regulation.contentieux.dao.ServiceDAO;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.StatutAffaire;
import com.regulation.contentieux.model.enums.TypeContrevenant;
import com.regulation.contentieux.service.AffaireService;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.service.ValidationService;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Contrôleur pour le formulaire de création/modification d'affaire
 * Gère la saisie et la validation des données d'une affaire contentieuse
 */
public class AffaireFormController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AffaireFormController.class);

    // ==================== COMPOSANTS FXML ====================

    // En-tête du formulaire
    @FXML private Label formTitleLabel;
    @FXML private Label numeroAffaireLabel;
    @FXML private Label dateCreationLabel;
    @FXML private Label statutLabel;

    // Section Contrevenant
    @FXML private ComboBox<Contrevenant> contrevenantComboBox;
    @FXML private Button newContrevenantButton;
    @FXML private Button searchContrevenantButton;
    @FXML private VBox contrevenantDetailsBox;
    @FXML private Label typeContrevenantLabel;
    @FXML private Label nomContrevenantLabel;
    @FXML private Label adresseContrevenantLabel;
    @FXML private Label telephoneContrevenantLabel;
    @FXML private Label emailContrevenantLabel;

    // Section Informations générales
    @FXML private DatePicker dateConstatationPicker;
    @FXML private TextField lieuConstatationField;
    @FXML private ComboBox<Agent> agentVerbalisateurComboBox;
    @FXML private ComboBox<Bureau> bureauComboBox;
    @FXML private ComboBox<Service> serviceComboBox;
    @FXML private TextArea descriptionTextArea;

    // Section Contraventions
    @FXML private ComboBox<Contravention> contraventionComboBox;
    @FXML private Button addContraventionButton;
    @FXML private TableView<ContraventionViewModel> contraventionsTableView;
    @FXML private TableColumn<ContraventionViewModel, String> codeContraventionColumn;
    @FXML private TableColumn<ContraventionViewModel, String> libelleContraventionColumn;
    @FXML private TableColumn<ContraventionViewModel, String> montantContraventionColumn;
    @FXML private TableColumn<ContraventionViewModel, Void> actionsContraventionColumn;
    @FXML private Label montantTotalLabel;
    @FXML private Label nombreContraventionsLabel;

    // Section Observations
    @FXML private TextArea observationsTextArea;

    // Boutons d'action
    @FXML private Button enregistrerButton;
    @FXML private Button enregistrerEtNouveauButton;
    @FXML private Button annulerButton;

    // Indicateurs d'état
    @FXML private ProgressIndicator saveProgressIndicator;
    @FXML private Label statusLabel;

    // ==================== SERVICES ET DAO ====================

    private final AffaireService affaireService = new AffaireService();
    private final AffaireDAO affaireDAO = new AffaireDAO();
    private final ContrevenantDAO contrevenantDAO = new ContrevenantDAO();
    private final ContraventionDAO contraventionDAO = new ContraventionDAO();
    private final AgentDAO agentDAO = new AgentDAO();
    private final BureauDAO bureauDAO = new BureauDAO();
    private final ServiceDAO serviceDAO = new ServiceDAO();
    private final AuthenticationService authService = AuthenticationService.getInstance();
    private final ValidationService validationService = new ValidationService();

    // ==================== VARIABLES D'ÉTAT ====================

    private Affaire currentAffaire;
    private boolean isEditMode = false;
    private ObservableList<ContraventionViewModel> contraventionsList = FXCollections.observableArrayList();
    private Stage currentStage;
    private boolean hasUnsavedChanges = false;

    // ==================== INITIALISATION ====================

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisation du formulaire d'affaire");

        setupComboBoxes();
        setupTableView();
        setupValidation();
        setupEventHandlers();
        loadReferenceData();

        // État initial
        saveProgressIndicator.setVisible(false);
        statusLabel.setText("");
    }

    /**
     * Configure les ComboBox avec leurs converters et listeners
     */
    private void setupComboBoxes() {
        // Contrevenant ComboBox
        contrevenantComboBox.setConverter(new StringConverter<Contrevenant>() {
            @Override
            public String toString(Contrevenant contrevenant) {
                return contrevenant != null ? contrevenant.getDisplayName() : "";
            }

            @Override
            public Contrevenant fromString(String string) {
                return contrevenantComboBox.getItems().stream()
                        .filter(c -> c.getDisplayName().equals(string))
                        .findFirst()
                        .orElse(null);
            }
        });

        contrevenantComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                displayContrevenantDetails(newVal);
                hasUnsavedChanges = true;
            } else {
                hideContrevenantDetails();
            }
        });

        // Agent ComboBox
        agentVerbalisateurComboBox.setConverter(new StringConverter<Agent>() {
            @Override
            public String toString(Agent agent) {
                return agent != null ? agent.getDisplayName() : "";
            }

            @Override
            public Agent fromString(String string) {
                return null;
            }
        });

        // Bureau ComboBox
        bureauComboBox.setConverter(new StringConverter<Bureau>() {
            @Override
            public String toString(Bureau bureau) {
                return bureau != null ? bureau.getCode() + " - " + bureau.getLibelle() : "";
            }

            @Override
            public Bureau fromString(String string) {
                return null;
            }
        });

        // Service ComboBox
        serviceComboBox.setConverter(new StringConverter<Service>() {
            @Override
            public String toString(Service service) {
                return service != null ? service.getCode() + " - " + service.getLibelle() : "";
            }

            @Override
            public Service fromString(String string) {
                return null;
            }
        });

        // Contravention ComboBox
        contraventionComboBox.setConverter(new StringConverter<Contravention>() {
            @Override
            public String toString(Contravention contravention) {
                if (contravention != null) {
                    return String.format("%s - %s (%s)",
                            contravention.getCode(),
                            contravention.getLibelle(),
                            CurrencyFormatter.format(contravention.getMontant()));
                }
                return "";
            }

            @Override
            public Contravention fromString(String string) {
                return null;
            }
        });
    }

    /**
     * Configure le TableView des contraventions
     */
    private void setupTableView() {
        // Configuration des colonnes
        codeContraventionColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getContravention().getCode()));

        libelleContraventionColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getContravention().getLibelle()));

        montantContraventionColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(
                        CurrencyFormatter.format(cellData.getValue().getContravention().getMontant())));

        // Colonne d'actions
        actionsContraventionColumn.setCellFactory(param -> new TableCell<ContraventionViewModel, Void>() {
            private final Button removeButton = new Button("Retirer");

            {
                removeButton.getStyleClass().add("button-danger-small");
                removeButton.setOnAction(event -> {
                    ContraventionViewModel item = getTableView().getItems().get(getIndex());
                    removeContravention(item);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : removeButton);
            }
        });

        contraventionsTableView.setItems(contraventionsList);

        // Message quand la table est vide
        contraventionsTableView.setPlaceholder(
                new Label("Aucune contravention ajoutée. Sélectionnez une contravention ci-dessus."));
    }

    /**
     * Configure la validation du formulaire
     */
    private void setupValidation() {
        // Listeners pour la validation en temps réel
        contrevenantComboBox.valueProperty().addListener((obs, old, val) -> validateForm());
        dateConstatationPicker.valueProperty().addListener((obs, old, val) -> validateForm());
        lieuConstatationField.textProperty().addListener((obs, old, val) -> {
            hasUnsavedChanges = true;
            validateForm();
        });
        agentVerbalisateurComboBox.valueProperty().addListener((obs, old, val) -> {
            hasUnsavedChanges = true;
            validateForm();
        });
        bureauComboBox.valueProperty().addListener((obs, old, val) -> {
            hasUnsavedChanges = true;
            validateForm();
        });
        descriptionTextArea.textProperty().addListener((obs, old, val) -> hasUnsavedChanges = true);
        observationsTextArea.textProperty().addListener((obs, old, val) -> hasUnsavedChanges = true);

        // Validation initiale
        validateForm();
    }

    /**
     * Configure les gestionnaires d'événements
     */
    private void setupEventHandlers() {
        // Gestion de la fermeture de fenêtre
        Platform.runLater(() -> {
            currentStage = (Stage) annulerButton.getScene().getWindow();
            currentStage.setOnCloseRequest(event -> {
                if (hasUnsavedChanges) {
                    event.consume();
                    confirmExit();
                }
            });
        });
    }

    /**
     * Charge les données de référence
     */
    private void loadReferenceData() {
        Task<Void> loadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // Charger les contrevenants actifs
                List<Contrevenant> contrevenants = contrevenantDAO.findAllActifs();
                Platform.runLater(() ->
                        contrevenantComboBox.setItems(FXCollections.observableArrayList(contrevenants))
                );

                // Charger les agents actifs
                List<Agent> agents = agentDAO.findAllActifs();
                Platform.runLater(() ->
                        agentVerbalisateurComboBox.setItems(FXCollections.observableArrayList(agents))
                );

                // Charger les bureaux actifs
                List<Bureau> bureaux = bureauDAO.findAllActifs();
                Platform.runLater(() ->
                        bureauComboBox.setItems(FXCollections.observableArrayList(bureaux))
                );

                // Charger les services actifs
                List<Service> services = serviceDAO.findAllActifs();
                Platform.runLater(() ->
                        serviceComboBox.setItems(FXCollections.observableArrayList(services))
                );

                // Charger les contraventions actives
                List<Contravention> contraventions = contraventionDAO.findAllActives();
                Platform.runLater(() ->
                        contraventionComboBox.setItems(FXCollections.observableArrayList(contraventions))
                );

                return null;
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du chargement des données", getException());
                    AlertUtil.showErrorAlert("Erreur",
                            "Erreur de chargement",
                            "Impossible de charger les données de référence.");
                });
            }
        };

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    // ==================== MÉTHODES PUBLIQUES ====================

    /**
     * Initialise le formulaire pour une nouvelle affaire
     */
    public void initializeForNew() {
        isEditMode = false;
        currentAffaire = new Affaire();

        formTitleLabel.setText("Nouvelle affaire contentieuse");
        numeroAffaireLabel.setText("Sera généré automatiquement");
        dateCreationLabel.setText(DateFormatter.format(LocalDate.now()));
        statutLabel.setText("OUVERTE");
        statutLabel.getStyleClass().add("status-open");

        // Initialiser avec la date du jour
        dateConstatationPicker.setValue(LocalDate.now());

        // Effacer le formulaire
        clearForm();

        // Pré-remplir avec l'utilisateur connecté si c'est un agent
        preselectCurrentAgent();

        hasUnsavedChanges = false;
    }

    /**
     * Initialise le formulaire pour modifier une affaire existante
     */
    public void initializeForEdit(Affaire affaire) {
        if (affaire == null) {
            throw new IllegalArgumentException("L'affaire ne peut pas être null");
        }

        isEditMode = true;
        currentAffaire = affaire;

        formTitleLabel.setText("Modifier l'affaire");
        numeroAffaireLabel.setText(affaire.getNumeroAffaire());
        dateCreationLabel.setText(DateFormatter.format(affaire.getDateCreation()));
        statutLabel.setText(affaire.getStatut().toString());
        statutLabel.getStyleClass().removeAll("status-open", "status-closed", "status-cancelled");

        switch (affaire.getStatut()) {
            case OUVERTE:
            case EN_COURS:
                statutLabel.getStyleClass().add("status-open");
                break;
            case CLOSE:
                statutLabel.getStyleClass().add("status-closed");
                break;
            case ANNULEE:
                statutLabel.getStyleClass().add("status-cancelled");
                break;
        }

        // Remplir le formulaire
        fillForm(affaire);

        hasUnsavedChanges = false;
    }

    /**
     * Définit le stage actuel
     */
    public void setStage(Stage stage) {
        this.currentStage = stage;
    }

    /**
     * Méthode pour compatibilité avec AffaireListController
     */
    public void setAffaireToEdit(Affaire affaire) {
        initializeForEdit(affaire);
    }

    /**
     * Définit des valeurs par défaut
     */
    public void setDefaultValues(String numeroAffaire, Contrevenant contrevenant) {
        if (numeroAffaire != null && !numeroAffaire.trim().isEmpty()) {
            numeroAffaireLabel.setText(numeroAffaire);
        }
        if (contrevenant != null) {
            contrevenantComboBox.setValue(contrevenant);
        }
    }

    // ==================== ACTIONS FXML ====================

    @FXML
    private void onNewContrevenant() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/contrevenant/contrevenant-form.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Nouveau contrevenant");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(currentStage);

            Scene scene = new Scene(root);
            dialogStage.setScene(scene);

            // Passer le contrôleur si nécessaire
            ContrevenantFormController controller = loader.getController();
            controller.initializeForNew();

            dialogStage.showAndWait();

            // Recharger les contrevenants
            loadContrevenants();

        } catch (IOException e) {
            logger.error("Erreur lors de l'ouverture du formulaire de contrevenant", e);
            AlertUtil.showErrorAlert("Erreur",
                    "Impossible d'ouvrir le formulaire",
                    "Une erreur s'est produite lors de l'ouverture du formulaire de contrevenant.");
        }
    }

    @FXML
    private void onSearchContrevenant() {
        // TODO: Implémenter la recherche de contrevenant
        logger.info("Recherche de contrevenant - À implémenter");
        AlertUtil.showInfoAlert("Information",
                "Fonctionnalité en développement",
                "La recherche de contrevenant sera disponible prochainement.");
    }

    @FXML
    private void onAddContravention() {
        Contravention selected = contraventionComboBox.getValue();
        if (selected == null) {
            return;
        }

        // Vérifier si la contravention n'est pas déjà ajoutée
        boolean alreadyAdded = contraventionsList.stream()
                .anyMatch(vm -> vm.getContravention().getId().equals(selected.getId()));

        if (alreadyAdded) {
            AlertUtil.showWarningAlert("Attention",
                    "Contravention déjà ajoutée",
                    "Cette contravention est déjà dans la liste.");
            return;
        }

        // Ajouter la contravention
        contraventionsList.add(new ContraventionViewModel(selected));
        updateContraventionsInfo();
        contraventionComboBox.setValue(null);
        hasUnsavedChanges = true;
        validateForm();
    }

    @FXML
    private void onEnregistrer() {
        if (!validateForm()) {
            showValidationErrors();
            return;
        }

        saveAffaire(false);
    }

    @FXML
    private void onEnregistrerEtNouveau() {
        if (!validateForm()) {
            showValidationErrors();
            return;
        }

        saveAffaire(true);
    }

    @FXML
    private void onAnnuler() {
        if (hasUnsavedChanges) {
            confirmExit();
        } else {
            closeForm();
        }
    }

    // ==================== MÉTHODES PRIVÉES ====================

    /**
     * Affiche les détails du contrevenant sélectionné
     */
    private void displayContrevenantDetails(Contrevenant contrevenant) {
        typeContrevenantLabel.setText("Type: " + contrevenant.getType().getLibelle());

        if (contrevenant.getType() == TypeContrevenant.PERSONNE_PHYSIQUE) {
            nomContrevenantLabel.setText(contrevenant.getNom() + " " +
                    (contrevenant.getPrenom() != null ? contrevenant.getPrenom() : ""));
        } else {
            nomContrevenantLabel.setText(contrevenant.getRaisonSociale());
        }

        adresseContrevenantLabel.setText("Adresse: " +
                (contrevenant.getAdresse() != null ? contrevenant.getAdresse() : "Non renseignée"));

        telephoneContrevenantLabel.setText("Tél: " +
                (contrevenant.getTelephone() != null ? contrevenant.getTelephone() : "Non renseigné"));

        emailContrevenantLabel.setText("Email: " +
                (contrevenant.getEmail() != null ? contrevenant.getEmail() : "Non renseigné"));

        contrevenantDetailsBox.setVisible(true);
        contrevenantDetailsBox.setManaged(true);
    }

    /**
     * Cache les détails du contrevenant
     */
    private void hideContrevenantDetails() {
        contrevenantDetailsBox.setVisible(false);
        contrevenantDetailsBox.setManaged(false);
    }

    /**
     * Retire une contravention de la liste
     */
    private void removeContravention(ContraventionViewModel contravention) {
        contraventionsList.remove(contravention);
        updateContraventionsInfo();
        hasUnsavedChanges = true;
        validateForm();
    }

    /**
     * Met à jour les informations sur les contraventions
     */
    private void updateContraventionsInfo() {
        // Calculer le montant total
        BigDecimal total = contraventionsList.stream()
                .map(vm -> vm.getContravention().getMontant())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        montantTotalLabel.setText(CurrencyFormatter.format(total));

        // Nombre de contraventions
        int count = contraventionsList.size();
        nombreContraventionsLabel.setText(count + " contravention" + (count > 1 ? "s" : ""));
    }

    /**
     * Prépare l'objet Affaire avant la sauvegarde
     */
    private void prepareAffaire() {
        currentAffaire.setContrevenant(contrevenantComboBox.getValue());
        currentAffaire.setDateConstatation(dateConstatationPicker.getValue());
        currentAffaire.setLieuConstatation(lieuConstatationField.getText().trim());
        currentAffaire.setAgentVerbalisateur(agentVerbalisateurComboBox.getValue());
        currentAffaire.setBureau(bureauComboBox.getValue());
        currentAffaire.setService(serviceComboBox.getValue());
        currentAffaire.setDescription(descriptionTextArea.getText().trim());
        currentAffaire.setObservations(observationsTextArea.getText().trim());

        // Convertir les ViewModels en entités
        List<Contravention> contraventions = contraventionsList.stream()
                .map(ContraventionViewModel::getContravention)
                .collect(Collectors.toList());
        currentAffaire.setContraventions(contraventions);
    }

    /**
     * Sauvegarde l'affaire
     */
    private void saveAffaire(boolean createNew) {
        // Désactiver les boutons pendant la sauvegarde
        setFormEnabled(false);
        saveProgressIndicator.setVisible(true);
        statusLabel.setText("Enregistrement en cours...");

        // Préparer l'affaire
        prepareAffaire();

        Task<Affaire> saveTask = new Task<Affaire>() {
            @Override
            protected Affaire call() throws Exception {
                if (isEditMode) {
                    return affaireService.updateAffaire(currentAffaire);
                } else {
                    return affaireService.createAffaire(currentAffaire);
                }
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    saveProgressIndicator.setVisible(false);
                    statusLabel.setText("Enregistrement réussi");
                    hasUnsavedChanges = false;

                    AlertUtil.showSuccessAlert("Succès",
                            isEditMode ? "Affaire modifiée" : "Affaire créée",
                            "L'affaire " + getValue().getNumeroAffaire() + " a été enregistrée avec succès.");

                    if (createNew) {
                        initializeForNew();
                        setFormEnabled(true);
                    } else {
                        closeForm();
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    saveProgressIndicator.setVisible(false);
                    statusLabel.setText("Erreur lors de l'enregistrement");
                    setFormEnabled(true);

                    logger.error("Erreur lors de l'enregistrement", getException());
                    AlertUtil.showErrorAlert("Erreur",
                            "Erreur lors de l'enregistrement",
                            "Une erreur s'est produite : " + getException().getMessage());
                });
            }
        };

        Thread saveThread = new Thread(saveTask);
        saveThread.setDaemon(true);
        saveThread.start();
    }

    /**
     * Remplit le formulaire avec les données d'une affaire
     */
    private void fillForm(Affaire affaire) {
        // Informations principales
        contrevenantComboBox.setValue(affaire.getContrevenant());
        dateConstatationPicker.setValue(affaire.getDateConstatation());
        lieuConstatationField.setText(affaire.getLieuConstatation());
        agentVerbalisateurComboBox.setValue(affaire.getAgentVerbalisateur());
        bureauComboBox.setValue(affaire.getBureau());
        serviceComboBox.setValue(affaire.getService());
        descriptionTextArea.setText(affaire.getDescription());
        observationsTextArea.setText(affaire.getObservations());

        // Contraventions
        contraventionsList.clear();
        if (affaire.getContraventions() != null) {
            for (Contravention c : affaire.getContraventions()) {
                contraventionsList.add(new ContraventionViewModel(c));
            }
        }

        updateContraventionsInfo();
    }

    /**
     * Efface le formulaire
     */
    private void clearForm() {
        contrevenantComboBox.setValue(null);
        lieuConstatationField.clear();
        agentVerbalisateurComboBox.setValue(null);
        bureauComboBox.setValue(null);
        serviceComboBox.setValue(null);
        descriptionTextArea.clear();
        observationsTextArea.clear();
        contraventionsList.clear();
        updateContraventionsInfo();
        hideContrevenantDetails();
    }

    /**
     * Valide le formulaire
     */
    private boolean validateForm() {
        boolean valid = true;
        StringBuilder errors = new StringBuilder();

        // Contrevenant obligatoire
        if (contrevenantComboBox.getValue() == null) {
            valid = false;
            errors.append("- Le contrevenant est obligatoire\n");
        }

        // Date de constatation obligatoire
        if (dateConstatationPicker.getValue() == null) {
            valid = false;
            errors.append("- La date de constatation est obligatoire\n");
        } else if (dateConstatationPicker.getValue().isAfter(LocalDate.now())) {
            valid = false;
            errors.append("- La date de constatation ne peut pas être dans le futur\n");
        }

        // Lieu de constatation obligatoire
        if (lieuConstatationField.getText().trim().isEmpty()) {
            valid = false;
            errors.append("- Le lieu de constatation est obligatoire\n");
        }

        // Agent verbalisateur obligatoire
        if (agentVerbalisateurComboBox.getValue() == null) {
            valid = false;
            errors.append("- L'agent verbalisateur est obligatoire\n");
        }

        // Au moins une contravention
        if (contraventionsList.isEmpty()) {
            valid = false;
            errors.append("- Au moins une contravention doit être ajoutée\n");
        }

        // Activer/désactiver les boutons
        enregistrerButton.setDisable(!valid);
        enregistrerEtNouveauButton.setDisable(!valid);

        return valid;
    }

    /**
     * Affiche les erreurs de validation
     */
    private void showValidationErrors() {
        StringBuilder errors = new StringBuilder("Veuillez corriger les erreurs suivantes :\n\n");

        if (contrevenantComboBox.getValue() == null) {
            errors.append("- Sélectionnez un contrevenant\n");
        }

        if (dateConstatationPicker.getValue() == null) {
            errors.append("- Saisissez la date de constatation\n");
        }

        if (lieuConstatationField.getText().trim().isEmpty()) {
            errors.append("- Saisissez le lieu de constatation\n");
        }

        if (agentVerbalisateurComboBox.getValue() == null) {
            errors.append("- Sélectionnez l'agent verbalisateur\n");
        }

        if (contraventionsList.isEmpty()) {
            errors.append("- Ajoutez au moins une contravention\n");
        }

        AlertUtil.showWarningAlert("Validation",
                "Formulaire incomplet",
                errors.toString());
    }

    /**
     * Active ou désactive le formulaire
     */
    private void setFormEnabled(boolean enabled) {
        contrevenantComboBox.setDisable(!enabled);
        newContrevenantButton.setDisable(!enabled);
        searchContrevenantButton.setDisable(!enabled);
        dateConstatationPicker.setDisable(!enabled);
        lieuConstatationField.setDisable(!enabled);
        agentVerbalisateurComboBox.setDisable(!enabled);
        bureauComboBox.setDisable(!enabled);
        serviceComboBox.setDisable(!enabled);
        descriptionTextArea.setDisable(!enabled);
        contraventionComboBox.setDisable(!enabled);
        addContraventionButton.setDisable(!enabled);
        observationsTextArea.setDisable(!enabled);
        enregistrerButton.setDisable(!enabled);
        enregistrerEtNouveauButton.setDisable(!enabled);
        annulerButton.setDisable(!enabled);
    }

    /**
     * Confirme la sortie si des modifications non sauvegardées existent
     */
    private void confirmExit() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText("Modifications non sauvegardées");
        alert.setContentText("Des modifications n'ont pas été sauvegardées. Voulez-vous vraiment quitter ?");

        ButtonType buttonSave = new ButtonType("Enregistrer et quitter");
        ButtonType buttonDiscard = new ButtonType("Quitter sans enregistrer");
        ButtonType buttonCancel = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(buttonSave, buttonDiscard, buttonCancel);

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent()) {
            if (result.get() == buttonSave) {
                saveAffaire(false);
            } else if (result.get() == buttonDiscard) {
                hasUnsavedChanges = false;
                closeForm();
            }
        }
    }

    /**
     * Ferme le formulaire
     */
    private void closeForm() {
        if (currentStage != null) {
            currentStage.close();
        }
    }

    /**
     * Pré-sélectionne l'agent connecté si applicable
     */
    private void preselectCurrentAgent() {
        // TODO: Vérifier si l'utilisateur connecté est un agent
        // et le pré-sélectionner dans le ComboBox
    }

    /**
     * Recharge la liste des contrevenants
     */
    private void loadContrevenants() {
        Task<List<Contrevenant>> loadTask = new Task<List<Contrevenant>>() {
            @Override
            protected List<Contrevenant> call() throws Exception {
                return contrevenantDAO.findAllActifs();
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    contrevenantComboBox.setItems(FXCollections.observableArrayList(getValue()));
                });
            }
        };

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    // ==================== CLASSES INTERNES ====================

    /**
     * ViewModel pour les contraventions dans le tableau
     */
    public static class ContraventionViewModel {
        private final Contravention contravention;

        public ContraventionViewModel(Contravention contravention) {
            this.contravention = contravention;
        }

        public Contravention getContravention() {
            return contravention;
        }

        @Override
        public String toString() {
            return contravention.getCode() + " - " + contravention.getLibelle();
        }
    }
}