package com.regulation.contentieux.controller;

import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.dao.AgentDAO;
import com.regulation.contentieux.dao.ContrevenantDAO;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Agent;
import com.regulation.contentieux.model.AffaireActeur;
import com.regulation.contentieux.model.Contrevenant;
import com.regulation.contentieux.model.enums.RoleSurAffaire;
import com.regulation.contentieux.model.enums.StatutAffaire;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.service.ValidationService;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import com.regulation.contentieux.util.FXMLLoaderUtil;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Contrôleur pour le formulaire d'affaire - SUIT LE PATTERN ÉTABLI
 * Respecte exactement la logique des contrôleurs existants
 */
public class AffaireFormController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AffaireFormController.class);

    // FXML - Informations générales
    @FXML private Label formTitleLabel;
    @FXML private Label modeLabel;
    @FXML private TextField numeroAffaireField;
    @FXML private Button generateNumeroButton;
    @FXML private DatePicker dateCreationPicker;
    @FXML private TextField montantAmendeField;
    @FXML private ComboBox<StatutAffaire> statutComboBox;

    // FXML - Contrevenant et Contravention
    @FXML private ComboBox<Contrevenant> contrevenantComboBox;
    @FXML private Button newContrevenantButton;
    @FXML private ComboBox<Object> contraventionComboBox; // TODO: Contravention class
    @FXML private Button newContraventionButton;
    @FXML private VBox contrevenantDetailsBox;
    @FXML private Label contrevenantDetailsLabel;

    // FXML - Organisation
    @FXML private ComboBox<Object> bureauComboBox; // TODO: Bureau class
    @FXML private ComboBox<Object> serviceComboBox; // TODO: Service class

    // FXML - Agents
    @FXML private Button assignAgentButton;
    @FXML private TableView<AgentAssignmentViewModel> agentsTableView;
    @FXML private TableColumn<AgentAssignmentViewModel, String> agentNomColumn;
    @FXML private TableColumn<AgentAssignmentViewModel, RoleSurAffaire> agentRoleColumn;
    @FXML private TableColumn<AgentAssignmentViewModel, LocalDateTime> agentDateColumn;
    @FXML private TableColumn<AgentAssignmentViewModel, Void> agentActionsColumn;

    // FXML - Actions
    @FXML private Button cancelButton;
    @FXML private Button resetButton;
    @FXML private Button saveButton;

    // Services et données - SUIT LE PATTERN ÉTABLI
    private AffaireDAO affaireDAO;
    private ContrevenantDAO contrevenantDAO;
    private AgentDAO agentDAO;
    private AuthenticationService authService;
    private ValidationService validationService;

    // État du formulaire
    private boolean isEditMode = false;
    private Affaire currentAffaire;
    private ObservableList<Contrevenant> contrevenants;
    private ObservableList<Agent> agents;
    private ObservableList<AgentAssignmentViewModel> assignedAgents;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialisation des services - SUIT LE PATTERN ÉTABLI
        affaireDAO = new AffaireDAO();
        contrevenantDAO = new ContrevenantDAO();
        agentDAO = new AgentDAO();
        authService = AuthenticationService.getInstance();
        validationService = new ValidationService();

        // Initialisation des listes
        contrevenants = FXCollections.observableArrayList();
        agents = FXCollections.observableArrayList();
        assignedAgents = FXCollections.observableArrayList();

        setupUI();
        setupEventHandlers();
        setupTableColumns();
        loadFormData();

        logger.info("Contrôleur de formulaire d'affaire initialisé");
    }

    /**
     * Configuration initiale de l'interface - SUIT LE PATTERN ÉTABLI
     */
    private void setupUI() {
        // Configuration des ComboBox
        setupStatutComboBox();
        setupContrevenantComboBox();

        // Configuration des champs
        dateCreationPicker.setValue(LocalDate.now());
        montantAmendeField.setPromptText("Montant en FCFA");

        // Configuration initiale
        updateUIMode();
    }

    /**
     * Configuration des gestionnaires d'événements - SUIT LE PATTERN ÉTABLI
     */
    private void setupEventHandlers() {
        // Génération du numéro d'affaire
        generateNumeroButton.setOnAction(e -> generateNumeroAffaire());

        // Validation en temps réel du montant
        montantAmendeField.textProperty().addListener((obs, oldVal, newVal) -> validateMontant(newVal));

        // Sélection du contrevenant
        contrevenantComboBox.setOnAction(e -> handleContrevenantSelection());

        // Boutons d'action
        cancelButton.setOnAction(e -> handleCancel());
        resetButton.setOnAction(e -> handleReset());
        saveButton.setOnAction(e -> handleSave());

        // Assignation d'agent
        assignAgentButton.setOnAction(e -> handleAssignAgent());

        // Boutons de création
        newContrevenantButton.setOnAction(e -> handleNewContrevenant());
        newContraventionButton.setOnAction(e -> handleNewContravention());
    }

    /**
     * Configuration des colonnes du tableau - SUIT LE PATTERN ÉTABLI
     */
    private void setupTableColumns() {
        // Colonne nom de l'agent
        agentNomColumn.setCellValueFactory(new PropertyValueFactory<>("agentNom"));

        // Colonne rôle
        agentRoleColumn.setCellValueFactory(new PropertyValueFactory<>("role"));
        agentRoleColumn.setCellFactory(col -> new TableCell<AgentAssignmentViewModel, RoleSurAffaire>() {
            @Override
            protected void updateItem(RoleSurAffaire role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setText(null);
                } else {
                    setText(role.getLibelle());
                }
            }
        });

        // Colonne date
        agentDateColumn.setCellValueFactory(new PropertyValueFactory<>("assignedAt"));
        agentDateColumn.setCellFactory(col -> new TableCell<AgentAssignmentViewModel, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    setText(DateFormatter.formatDateTimeShort(date));
                }
            }
        });

        // Colonne actions
        agentActionsColumn.setCellFactory(createAgentActionsCellFactory());

        // Données du tableau
        agentsTableView.setItems(assignedAgents);
    }

    /**
     * Crée la factory pour les boutons d'action des agents - SUIT LE PATTERN ÉTABLI
     */
    private Callback<TableColumn<AgentAssignmentViewModel, Void>, TableCell<AgentAssignmentViewModel, Void>>
    createAgentActionsCellFactory() {
        return param -> new TableCell<AgentAssignmentViewModel, Void>() {
            private final Button removeButton = new Button("✕");

            {
                removeButton.getStyleClass().add("button-danger");
                removeButton.setTooltip(new Tooltip("Retirer"));
                removeButton.setOnAction(e -> {
                    AgentAssignmentViewModel assignment = getTableView().getItems().get(getIndex());
                    removeAgentAssignment(assignment);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(removeButton);
                }
            }
        };
    }

    /**
     * Configuration du ComboBox des statuts - SUIT LE PATTERN ÉTABLI
     */
    private void setupStatutComboBox() {
        statutComboBox.getItems().addAll(StatutAffaire.values());
        statutComboBox.setValue(StatutAffaire.OUVERTE);
        statutComboBox.setConverter(new StringConverter<StatutAffaire>() {
            @Override
            public String toString(StatutAffaire statut) {
                return statut != null ? statut.getLibelle() : "";
            }

            @Override
            public StatutAffaire fromString(String string) {
                return null;
            }
        });
    }

    /**
     * Configuration du ComboBox des contrevenants - SUIT LE PATTERN ÉTABLI
     */
    private void setupContrevenantComboBox() {
        contrevenantComboBox.setItems(contrevenants);
        contrevenantComboBox.setConverter(new StringConverter<Contrevenant>() {
            @Override
            public String toString(Contrevenant contrevenant) {
                return contrevenant != null ?
                        contrevenant.getCode() + " - " + contrevenant.getNomComplet() : "";
            }

            @Override
            public Contrevenant fromString(String string) {
                return null;
            }
        });
    }

    /**
     * Charge les données du formulaire - SUIT LE PATTERN ÉTABLI
     */
    private void loadFormData() {
        Task<Void> loadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // Chargement des contrevenants
                List<Contrevenant> contrevenantsList = contrevenantDAO.findAll();

                // Chargement des agents
                List<Agent> agentsList = agentDAO.findAll();

                Platform.runLater(() -> {
                    contrevenants.clear();
                    contrevenants.addAll(contrevenantsList);

                    agents.clear();
                    agents.addAll(agentsList);

                    logger.info("Données du formulaire chargées: {} contrevenants, {} agents",
                            contrevenantsList.size(), agentsList.size());
                });

                return null;
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du chargement des données", getException());
                    AlertUtil.showErrorAlert("Erreur de chargement",
                            "Impossible de charger les données",
                            "Vérifiez la connexion à la base de données.");
                });
            }
        };

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    // Actions du formulaire - SUIT LE PATTERN ÉTABLI

    private void generateNumeroAffaire() {
        try {
            String nextNumero = affaireDAO.generateNextNumeroAffaire();
            numeroAffaireField.setText(nextNumero);
            logger.info("Numéro d'affaire généré: {}", nextNumero);
        } catch (Exception e) {
            logger.error("Erreur lors de la génération du numéro", e);
            AlertUtil.showErrorAlert("Erreur de génération",
                    "Impossible de générer le numéro d'affaire",
                    "Vérifiez la connexion à la base de données.");
        }
    }

    private void validateMontant(String newValue) {
        if (newValue != null && !newValue.trim().isEmpty()) {
            try {
                double montant = Double.parseDouble(newValue.replace(",", "."));
                if (montant < 0) {
                    montantAmendeField.setStyle("-fx-border-color: red;");
                } else {
                    montantAmendeField.setStyle("");
                }
            } catch (NumberFormatException e) {
                montantAmendeField.setStyle("-fx-border-color: red;");
            }
        } else {
            montantAmendeField.setStyle("");
        }
    }

    private void handleContrevenantSelection() {
        Contrevenant selected = contrevenantComboBox.getValue();
        if (selected != null) {
            String details = String.format("Code: %s\nType: %s\nTéléphone: %s\nEmail: %s",
                    selected.getCode(),
                    selected.getTypePersonne(),
                    selected.getTelephone() != null ? selected.getTelephone() : "Non renseigné",
                    selected.getEmail() != null ? selected.getEmail() : "Non renseigné");

            contrevenantDetailsLabel.setText(details);
            contrevenantDetailsBox.setVisible(true);
            contrevenantDetailsBox.setManaged(true);
        } else {
            contrevenantDetailsBox.setVisible(false);
            contrevenantDetailsBox.setManaged(false);
        }
    }

    private void handleAssignAgent() {
        // TODO: Ouvrir un dialogue de sélection d'agent
        logger.info("Assignation d'agent demandée");
        AlertUtil.showInfoAlert("Assignation d'agent",
                "Fonctionnalité en développement",
                "Le dialogue d'assignation sera disponible prochainement.");
    }

    private void removeAgentAssignment(AgentAssignmentViewModel assignment) {
        if (AlertUtil.showConfirmAlert("Confirmation",
                "Retirer l'assignation",
                "Voulez-vous vraiment retirer " + assignment.getAgentNom() + " de cette affaire ?")) {

            assignedAgents.remove(assignment);
            logger.info("Agent retiré: {}", assignment.getAgentNom());
        }
    }

    private void handleNewContrevenant() {
        logger.info("Création d'un nouveau contrevenant demandée");
        AlertUtil.showInfoAlert("Nouveau contrevenant",
                "Fonctionnalité en développement",
                "Le formulaire de création sera disponible prochainement.");
    }

    private void handleNewContravention() {
        logger.info("Création d'une nouvelle contravention demandée");
        AlertUtil.showInfoAlert("Nouvelle contravention",
                "Fonctionnalité en développement",
                "Le formulaire de création sera disponible prochainement.");
    }

    private void handleSave() {
        if (validateForm()) {
            saveAffaire();
        }
    }

    private void handleReset() {
        if (AlertUtil.showConfirmAlert("Confirmation",
                "Réinitialiser le formulaire",
                "Voulez-vous vraiment réinitialiser tous les champs ?")) {
            resetForm();
        }
    }

    private void handleCancel() {
        if (hasUnsavedChanges()) {
            if (AlertUtil.showConfirmAlert("Confirmation",
                    "Annuler les modifications",
                    "Vous avez des modifications non sauvegardées. Voulez-vous vraiment annuler ?")) {
                closeForm();
            }
        } else {
            closeForm();
        }
    }

    /**
     * Validation du formulaire - SUIT LE PATTERN ÉTABLI
     */
    private boolean validateForm() {
        StringBuilder errors = new StringBuilder();

        // Validation du numéro d'affaire
        if (numeroAffaireField.getText() == null || numeroAffaireField.getText().trim().isEmpty()) {
            errors.append("• Le numéro d'affaire est obligatoire\n");
        }

        // Validation de la date
        if (dateCreationPicker.getValue() == null) {
            errors.append("• La date de création est obligatoire\n");
        }

        // Validation du montant
        if (montantAmendeField.getText() == null || montantAmendeField.getText().trim().isEmpty()) {
            errors.append("• Le montant de l'amende est obligatoire\n");
        } else {
            try {
                double montant = Double.parseDouble(montantAmendeField.getText().replace(",", "."));
                if (montant <= 0) {
                    errors.append("• Le montant de l'amende doit être positif\n");
                }
            } catch (NumberFormatException e) {
                errors.append("• Le montant de l'amende doit être un nombre valide\n");
            }
        }

        // Validation du contrevenant
        if (contrevenantComboBox.getValue() == null) {
            errors.append("• Le contrevenant est obligatoire\n");
        }

        // Validation du statut
        if (statutComboBox.getValue() == null) {
            errors.append("• Le statut est obligatoire\n");
        }

        if (errors.length() > 0) {
            AlertUtil.showErrorAlert("Erreurs de validation",
                    "Veuillez corriger les erreurs suivantes :",
                    errors.toString());
            return false;
        }

        return true;
    }

    /**
     * Sauvegarde de l'affaire - SUIT LE PATTERN ÉTABLI
     */
    private void saveAffaire() {
        Task<Affaire> saveTask = new Task<Affaire>() {
            @Override
            protected Affaire call() throws Exception {
                Affaire affaire = isEditMode ? currentAffaire : new Affaire();

                // Remplissage des données
                affaire.setNumeroAffaire(numeroAffaireField.getText().trim());
                affaire.setDateCreation(dateCreationPicker.getValue());
                affaire.setMontantAmendeTotal(Double.parseDouble(montantAmendeField.getText().replace(",", ".")));
                affaire.setStatut(statutComboBox.getValue());
                affaire.setContrevenantId(contrevenantComboBox.getValue().getId());

                // Métadonnées
                String currentUser = authService.getCurrentUser().getUsername();
                if (isEditMode) {
                    affaire.setUpdatedBy(currentUser);
                    return affaireDAO.update(affaire);
                } else {
                    affaire.setCreatedBy(currentUser);
                    return affaireDAO.save(affaire);
                }
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    Affaire savedAffaire = getValue();
                    String message = isEditMode ? "Affaire modifiée avec succès" : "Affaire créée avec succès";

                    AlertUtil.showSuccessAlert("Sauvegarde réussie", message,
                            "Numéro d'affaire: " + savedAffaire.getNumeroAffaire());

                    // Mise à jour de l'état
                    currentAffaire = savedAffaire;
                    isEditMode = true;
                    updateUIMode();

                    logger.info("Affaire sauvegardée: {}", savedAffaire.getNumeroAffaire());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors de la sauvegarde", getException());
                    AlertUtil.showErrorAlert("Erreur de sauvegarde",
                            "Impossible de sauvegarder l'affaire",
                            "Vérifiez les données et réessayez.");
                });
            }
        };

        Thread saveThread = new Thread(saveTask);
        saveThread.setDaemon(true);
        saveThread.start();
    }

    /**
     * Réinitialise le formulaire - SUIT LE PATTERN ÉTABLI
     */
    private void resetForm() {
        numeroAffaireField.clear();
        dateCreationPicker.setValue(LocalDate.now());
        montantAmendeField.clear();
        statutComboBox.setValue(StatutAffaire.OUVERTE);
        contrevenantComboBox.setValue(null);
        contraventionComboBox.setValue(null);
        bureauComboBox.setValue(null);
        serviceComboBox.setValue(null);

        assignedAgents.clear();
        contrevenantDetailsBox.setVisible(false);
        contrevenantDetailsBox.setManaged(false);

        // Réinitialisation des styles d'erreur
        montantAmendeField.setStyle("");

        logger.info("Formulaire réinitialisé");
    }

    /**
     * Vérifie s'il y a des modifications non sauvegardées
     */
    private boolean hasUnsavedChanges() {
        if (currentAffaire == null) {
            // Mode création - vérifier si des champs sont remplis
            return !numeroAffaireField.getText().trim().isEmpty() ||
                    !montantAmendeField.getText().trim().isEmpty() ||
                    contrevenantComboBox.getValue() != null ||
                    contraventionComboBox.getValue() != null;
        } else {
            // Mode édition - comparer avec les valeurs originales
            return !numeroAffaireField.getText().equals(currentAffaire.getNumeroAffaire()) ||
                    !dateCreationPicker.getValue().equals(currentAffaire.getDateCreation()) ||
                    !montantAmendeField.getText().equals(String.valueOf(currentAffaire.getMontantAmendeTotal())) ||
                    !statutComboBox.getValue().equals(currentAffaire.getStatut());
        }
    }

    /**
     * Met à jour l'interface selon le mode - SUIT LE PATTERN ÉTABLI
     */
    private void updateUIMode() {
        if (isEditMode) {
            formTitleLabel.setText("Modifier l'affaire");
            modeLabel.setText("Mode édition");
            saveButton.setText("Mettre à jour");
        } else {
            formTitleLabel.setText("Nouvelle affaire");
            modeLabel.setText("Mode création");
            saveButton.setText("Enregistrer");
        }
    }

    /**
     * Ferme le formulaire
     */
    private void closeForm() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    // Méthodes publiques pour l'intégration

    /**
     * Configure le formulaire en mode édition - SUIT LE PATTERN ÉTABLI
     */
    public void setAffaireToEdit(Affaire affaire) {
        this.currentAffaire = affaire;
        this.isEditMode = true;

        Platform.runLater(() -> {
            fillFormWithAffaire(affaire);
            updateUIMode();
        });
    }

    /**
     * Remplit le formulaire avec les données d'une affaire - SUIT LE PATTERN ÉTABLI
     */
    private void fillFormWithAffaire(Affaire affaire) {
        numeroAffaireField.setText(affaire.getNumeroAffaire());
        dateCreationPicker.setValue(affaire.getDateCreation());
        montantAmendeField.setText(String.valueOf(affaire.getMontantAmendeTotal()));
        statutComboBox.setValue(affaire.getStatut());

        // Sélectionner le contrevenant correspondant
        contrevenants.stream()
                .filter(c -> c.getId().equals(affaire.getContrevenantId()))
                .findFirst()
                .ifPresent(contrevenantComboBox::setValue);

        logger.info("Formulaire rempli avec l'affaire: {}", affaire.getNumeroAffaire());
    }

    /**
     * Configure le formulaire en mode création avec des valeurs par défaut
     */
    public void setDefaultValues(String numeroAffaire, Contrevenant contrevenant) {
        Platform.runLater(() -> {
            if (numeroAffaire != null) {
                numeroAffaireField.setText(numeroAffaire);
            }
            if (contrevenant != null) {
                contrevenantComboBox.setValue(contrevenant);
                handleContrevenantSelection();
            }
        });
    }

    /**
     * Classe ViewModel pour l'assignation d'agents - SUIT LE PATTERN ÉTABLI
     */
    public static class AgentAssignmentViewModel {
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private Long agentId;
        private String agentNom;
        private RoleSurAffaire role;
        private LocalDateTime assignedAt;

        public AgentAssignmentViewModel() {}

        public AgentAssignmentViewModel(Long agentId, String agentNom, RoleSurAffaire role) {
            this.agentId = agentId;
            this.agentNom = agentNom;
            this.role = role;
            this.assignedAt = LocalDateTime.now();
        }

        // Getters et setters
        public boolean isSelected() { return selected.get(); }
        public BooleanProperty selectedProperty() { return selected; }
        public void setSelected(boolean selected) { this.selected.set(selected); }

        public Long getAgentId() { return agentId; }
        public void setAgentId(Long agentId) { this.agentId = agentId; }

        public String getAgentNom() { return agentNom; }
        public void setAgentNom(String agentNom) { this.agentNom = agentNom; }

        public RoleSurAffaire getRole() { return role; }
        public void setRole(RoleSurAffaire role) { this.role = role; }

        public LocalDateTime getAssignedAt() { return assignedAt; }
        public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
    }
}