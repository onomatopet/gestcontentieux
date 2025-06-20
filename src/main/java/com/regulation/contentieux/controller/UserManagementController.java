package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.Utilisateur;
import com.regulation.contentieux.model.enums.RoleUtilisateur;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.service.ValidationService;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.DateFormatter;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Contrôleur pour la gestion des utilisateurs - VERSION COMPLÈTE
 * Respecte le pattern établi des autres contrôleurs
 */
public class UserManagementController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(UserManagementController.class);

    // Filtres et recherche
    @FXML private TextField searchField;
    @FXML private ComboBox<RoleUtilisateur> roleFilterComboBox;
    @FXML private CheckBox activeOnlyCheckBox;
    @FXML private Button searchButton;
    @FXML private Button clearFiltersButton;

    // Actions principales
    @FXML private Button newUserButton;
    @FXML private Button exportButton;

    // Tableau et sélection
    @FXML private TableView<UtilisateurViewModel> utilisateursTableView;
    @FXML private CheckBox selectAllCheckBox;

    // Colonnes du tableau
    @FXML private TableColumn<UtilisateurViewModel, Boolean> selectColumn;
    @FXML private TableColumn<UtilisateurViewModel, String> loginColumn;
    @FXML private TableColumn<UtilisateurViewModel, String> nomColumn;
    @FXML private TableColumn<UtilisateurViewModel, String> prenomColumn;
    @FXML private TableColumn<UtilisateurViewModel, String> emailColumn;
    @FXML private TableColumn<UtilisateurViewModel, RoleUtilisateur> roleColumn;
    @FXML private TableColumn<UtilisateurViewModel, Boolean> actifColumn;
    @FXML private TableColumn<UtilisateurViewModel, LocalDateTime> dateCreationColumn;
    @FXML private TableColumn<UtilisateurViewModel, Void> actionsColumn;

    // Formulaire utilisateur
    @FXML private ScrollPane formulaireScrollPane;
    @FXML private Label formTitleLabel;
    @FXML private Label modeLabel;

    @FXML private TextField loginField;
    @FXML private TextField nomField;
    @FXML private TextField prenomField;
    @FXML private TextField emailField;
    @FXML private ComboBox<RoleUtilisateur> roleComboBox;
    @FXML private CheckBox actifCheckBox;
    @FXML private PasswordField motDePasseField;
    @FXML private PasswordField confirmMotDePasseField;

    // Actions du formulaire
    @FXML private Button enregistrerButton;
    @FXML private Button annulerButton;
    @FXML private Button supprimerButton;

    // Actions sur sélection
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button activateButton;
    @FXML private Button deactivateButton;

    // Informations
    @FXML private Label totalCountLabel;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;

    // Services et données
    private AuthenticationService authenticationService;
    private ValidationService validationService;
    private ObservableList<UtilisateurViewModel> utilisateurs;
    private ObservableList<UtilisateurViewModel> utilisateursFiltres;

    // État du formulaire
    private boolean modeCreation = true;
    private Utilisateur utilisateurEnCours;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        authenticationService = AuthenticationService.getInstance();
        validationService = new ValidationService();
        utilisateurs = FXCollections.observableArrayList();
        utilisateursFiltres = FXCollections.observableArrayList();

        setupUI();
        setupTableColumns();
        setupEventHandlers();
        setupFormValidation();
        loadData();

        logger.info("Contrôleur de gestion des utilisateurs initialisé");
    }

    /**
     * Configuration initiale de l'interface
     */
    private void setupUI() {
        // Configuration des ComboBox
        roleFilterComboBox.getItems().add(null); // Option "Tous les rôles"
        roleFilterComboBox.getItems().addAll(RoleUtilisateur.values());
        roleFilterComboBox.setConverter(createRoleStringConverter("Tous les rôles"));

        roleComboBox.getItems().addAll(RoleUtilisateur.values());
        roleComboBox.setConverter(createRoleStringConverter(null));

        // Configuration initiale
        formulaireScrollPane.setVisible(false);
        progressBar.setVisible(false);
        activeOnlyCheckBox.setSelected(true);

        // Initialisation de la liste filtrée
        utilisateursTableView.setItems(utilisateursFiltres);
    }

    private StringConverter<RoleUtilisateur> createRoleStringConverter(String nullValue) {
        return new StringConverter<RoleUtilisateur>() {
            @Override
            public String toString(RoleUtilisateur role) {
                if (role == null) return nullValue != null ? nullValue : "";
                return role.getLibelle();
            }

            @Override
            public RoleUtilisateur fromString(String string) {
                return null; // Pas utilisé
            }
        };
    }

    /**
     * Configuration des colonnes du tableau
     */
    private void setupTableColumns() {
        // Colonne de sélection
        selectColumn.setCellValueFactory(param -> param.getValue().selectedProperty());
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));

        // Colonnes de données
        loginColumn.setCellValueFactory(param -> param.getValue().loginProperty());
        nomColumn.setCellValueFactory(param -> param.getValue().nomProperty());
        prenomColumn.setCellValueFactory(param -> param.getValue().prenomProperty());
        emailColumn.setCellValueFactory(param -> param.getValue().emailProperty());

        roleColumn.setCellValueFactory(param -> param.getValue().roleProperty());
        roleColumn.setCellFactory(column -> new TableCell<UtilisateurViewModel, RoleUtilisateur>() {
            @Override
            protected void updateItem(RoleUtilisateur role, boolean empty) {
                super.updateItem(role, empty);
                setText(empty || role == null ? "" : role.getLibelle());
            }
        });

        actifColumn.setCellValueFactory(param -> param.getValue().actifProperty());
        actifColumn.setCellFactory(column -> new TableCell<UtilisateurViewModel, Boolean>() {
            @Override
            protected void updateItem(Boolean actif, boolean empty) {
                super.updateItem(actif, empty);
                if (empty || actif == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(actif ? "Actif" : "Inactif");
                    setStyle(actif ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
                }
            }
        });

        dateCreationColumn.setCellValueFactory(param -> param.getValue().dateCreationProperty());
        dateCreationColumn.setCellFactory(column -> new TableCell<UtilisateurViewModel, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime date, boolean empty) {
                super.updateItem(date, empty);
                setText(empty || date == null ? "" : DateFormatter.formatDateTime(date));
            }
        });

        // Colonne Actions
        actionsColumn.setCellFactory(createActionsCellFactory());

        // Configuration de la sélection
        utilisateursTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    private Callback<TableColumn<UtilisateurViewModel, Void>, TableCell<UtilisateurViewModel, Void>> createActionsCellFactory() {
        return new Callback<TableColumn<UtilisateurViewModel, Void>, TableCell<UtilisateurViewModel, Void>>() {
            @Override
            public TableCell<UtilisateurViewModel, Void> call(TableColumn<UtilisateurViewModel, Void> param) {
                return new TableCell<UtilisateurViewModel, Void>() {
                    private final Button btnEdit = new Button("Modifier");
                    private final Button btnToggle = new Button();
                    private final HBox buttons = new HBox(5, btnEdit, btnToggle);

                    {
                        btnEdit.getStyleClass().add("button-primary");
                        btnEdit.setOnAction(e -> {
                            UtilisateurViewModel user = getTableView().getItems().get(getIndex());
                            editUser(user);
                        });

                        btnToggle.setOnAction(e -> {
                            UtilisateurViewModel user = getTableView().getItems().get(getIndex());
                            toggleUserStatus(user);
                        });
                    }

                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            UtilisateurViewModel user = getTableView().getItems().get(getIndex());
                            btnToggle.setText(user.isActif() ? "Désactiver" : "Activer");
                            btnToggle.getStyleClass().clear();
                            btnToggle.getStyleClass().add(user.isActif() ? "button-danger" : "button-success");
                            setGraphic(buttons);
                        }
                    }
                };
            }
        };
    }

    /**
     * Configuration des gestionnaires d'événements
     */
    private void setupEventHandlers() {
        // Recherche et filtres
        searchButton.setOnAction(e -> applyFilters());
        clearFiltersButton.setOnAction(e -> clearFilters());
        searchField.setOnAction(e -> applyFilters());

        // Actions principales
        newUserButton.setOnAction(e -> createNewUser());
        exportButton.setOnAction(e -> exportUsers());

        // Sélection
        selectAllCheckBox.setOnAction(e -> toggleSelectAll());
        utilisateursTableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> updateSelectionButtons());

        // Formulaire
        enregistrerButton.setOnAction(e -> saveUser());
        annulerButton.setOnAction(e -> cancelForm());
        supprimerButton.setOnAction(e -> deleteCurrentUser());

        // Actions sur sélection
        editButton.setOnAction(e -> editSelectedUser());
        deleteButton.setOnAction(e -> deleteSelectedUsers());
        activateButton.setOnAction(e -> activateSelectedUsers());
        deactivateButton.setOnAction(e -> deactivateSelectedUsers());

        // Filtres en temps réel
        activeOnlyCheckBox.setOnAction(e -> applyFilters());
        roleFilterComboBox.setOnAction(e -> applyFilters());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.length() > 2) {
                applyFilters();
            } else if (newVal == null || newVal.isEmpty()) {
                applyFilters();
            }
        });
    }

    /**
     * Configuration de la validation du formulaire
     */
    private void setupFormValidation() {
        // Validation en temps réel
        loginField.textProperty().addListener((obs, oldVal, newVal) -> validateForm());
        nomField.textProperty().addListener((obs, oldVal, newVal) -> validateForm());
        prenomField.textProperty().addListener((obs, oldVal, newVal) -> validateForm());
        emailField.textProperty().addListener((obs, oldVal, newVal) -> validateForm());
        motDePasseField.textProperty().addListener((obs, oldVal, newVal) -> validateForm());
        confirmMotDePasseField.textProperty().addListener((obs, oldVal, newVal) -> validateForm());
        roleComboBox.valueProperty().addListener((obs, oldVal, newVal) -> validateForm());
    }

    /**
     * Chargement initial des données
     */
    private void loadData() {
        chargerUtilisateurs();
    }

    private void chargerUtilisateurs() {
        Task<List<Utilisateur>> task = new Task<List<Utilisateur>>() {
            @Override
            protected List<Utilisateur> call() throws Exception {
                return authenticationService.getAllUsers();
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    List<Utilisateur> users = getValue();
                    utilisateurs.clear();
                    utilisateurs.addAll(users.stream()
                            .map(UtilisateurViewModel::new)
                            .collect(Collectors.toList()));

                    applyFilters();
                    updateStatusLabel();
                    progressBar.setVisible(false);
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    AlertUtil.showErrorAlert("Erreur", "Chargement",
                            "Impossible de charger les utilisateurs: " + getException().getMessage());
                });
            }
        };

        progressBar.setVisible(true);
        new Thread(task).start();
    }

    // ==================== GESTION DES FILTRES ====================

    private void applyFilters() {
        String searchText = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        RoleUtilisateur roleFilter = roleFilterComboBox.getValue();
        boolean activeOnly = activeOnlyCheckBox.isSelected();

        utilisateursFiltres.clear();
        utilisateursFiltres.addAll(utilisateurs.stream()
                .filter(user -> {
                    // Filtre de recherche
                    if (!searchText.isEmpty()) {
                        String userText = (user.getLogin() + " " + user.getNom() + " " + user.getPrenom() + " " +
                                (user.getEmail() != null ? user.getEmail() : "")).toLowerCase();
                        if (!userText.contains(searchText)) {
                            return false;
                        }
                    }

                    // Filtre par rôle
                    if (roleFilter != null && !roleFilter.equals(user.getRole())) {
                        return false;
                    }

                    // Filtre actif uniquement
                    if (activeOnly && !user.isActif()) {
                        return false;
                    }

                    return true;
                })
                .collect(Collectors.toList()));

        updateStatusLabel();
    }

    private void clearFilters() {
        searchField.clear();
        roleFilterComboBox.setValue(null);
        activeOnlyCheckBox.setSelected(true);
        applyFilters();
    }

    // ==================== GESTION DU FORMULAIRE ====================

    private void createNewUser() {
        modeCreation = true;
        utilisateurEnCours = new Utilisateur();

        formTitleLabel.setText("Nouvel Utilisateur");
        modeLabel.setText("CRÉATION");

        viderFormulaire();
        supprimerButton.setVisible(false);

        formulaireScrollPane.setVisible(true);
        Platform.runLater(() -> loginField.requestFocus());
    }

    private void editUser(UtilisateurViewModel userViewModel) {
        // Trouver l'utilisateur complet
        authenticationService.findUserByLogin(userViewModel.getLogin())
                .ifPresent(user -> {
                    modeCreation = false;
                    utilisateurEnCours = user;

                    formTitleLabel.setText("Modifier Utilisateur");
                    modeLabel.setText("MODIFICATION");

                    remplirFormulaire(user);
                    supprimerButton.setVisible(true);

                    formulaireScrollPane.setVisible(true);
                });
    }

    private void editSelectedUser() {
        UtilisateurViewModel selected = utilisateursTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            editUser(selected);
        }
    }

    private void saveUser() {
        if (!validerSaisie()) return;

        try {
            // Remplir l'objet utilisateur
            utilisateurEnCours.setLogin(loginField.getText().trim());
            utilisateurEnCours.setNom(nomField.getText().trim());
            utilisateurEnCours.setPrenom(prenomField.getText().trim());
            utilisateurEnCours.setEmail(emailField.getText().trim().isEmpty() ? null : emailField.getText().trim());
            utilisateurEnCours.setRole(roleComboBox.getValue());
            utilisateurEnCours.setActif(actifCheckBox.isSelected());

            // Mot de passe uniquement si fourni
            if (!motDePasseField.getText().isEmpty()) {
                utilisateurEnCours.setMotDePasse(motDePasseField.getText());
            }

            // Sauvegarde
            if (modeCreation) {
                authenticationService.createUser(utilisateurEnCours);
                AlertUtil.showInfoAlert("Succès", "Création", "Utilisateur créé avec succès");
            } else {
                authenticationService.updateUser(utilisateurEnCours);
                AlertUtil.showInfoAlert("Succès", "Modification", "Utilisateur modifié avec succès");
            }

            cancelForm();
            chargerUtilisateurs();

        } catch (Exception e) {
            logger.error("Erreur lors de la sauvegarde de l'utilisateur", e);
            AlertUtil.showErrorAlert("Erreur", "Sauvegarde", "Erreur: " + e.getMessage());
        }
    }

    private void cancelForm() {
        formulaireScrollPane.setVisible(false);
        viderFormulaire();
        utilisateurEnCours = null;
    }

    private void deleteCurrentUser() {
        if (utilisateurEnCours == null) return;

        boolean confirmed = AlertUtil.showConfirmationAlert("Confirmation",
                "Supprimer l'utilisateur",
                String.format("Êtes-vous sûr de vouloir supprimer l'utilisateur %s ?",
                        utilisateurEnCours.getLogin()));

        if (confirmed) {
            try {
                authenticationService.deleteUser(utilisateurEnCours.getLogin());
                AlertUtil.showInfoAlert("Succès", "Suppression", "Utilisateur supprimé avec succès");
                cancelForm();
                chargerUtilisateurs();
            } catch (Exception e) {
                logger.error("Erreur lors de la suppression", e);
                AlertUtil.showErrorAlert("Erreur", "Suppression", "Erreur: " + e.getMessage());
            }
        }
    }

    // ==================== ACTIONS SUR SÉLECTION ====================

    private void deleteSelectedUsers() {
        List<UtilisateurViewModel> selected = getSelectedUsers();
        if (selected.isEmpty()) return;

        boolean confirmed = AlertUtil.showConfirmationAlert("Confirmation",
                "Supprimer les utilisateurs",
                String.format("Êtes-vous sûr de vouloir supprimer %d utilisateur(s) ?", selected.size()));

        if (confirmed) {
            int deleted = 0;
            for (UtilisateurViewModel user : selected) {
                try {
                    authenticationService.deleteUser(user.getLogin());
                    deleted++;
                } catch (Exception e) {
                    logger.error("Erreur lors de la suppression de " + user.getLogin(), e);
                }
            }

            AlertUtil.showInfoAlert("Suppression", "Résultat",
                    String.format("%d utilisateur(s) supprimé(s)", deleted));
            chargerUtilisateurs();
        }
    }

    private void activateSelectedUsers() {
        toggleSelectedUsersStatus(true);
    }

    private void deactivateSelectedUsers() {
        toggleSelectedUsersStatus(false);
    }

    private void toggleSelectedUsersStatus(boolean activate) {
        List<UtilisateurViewModel> selected = getSelectedUsers();
        if (selected.isEmpty()) return;

        int updated = 0;
        for (UtilisateurViewModel userViewModel : selected) {
            authenticationService.findUserByLogin(userViewModel.getLogin())
                    .ifPresent(user -> {
                        try {
                            user.setActif(activate);
                            authenticationService.updateUser(user);
                        } catch (Exception e) {
                            logger.error("Erreur lors de la mise à jour de " + user.getLogin(), e);
                        }
                    });
            updated++;
        }

        AlertUtil.showInfoAlert("Mise à jour", "Résultat",
                String.format("%d utilisateur(s) %s", updated, activate ? "activé(s)" : "désactivé(s)"));
        chargerUtilisateurs();
    }

    private void toggleUserStatus(UtilisateurViewModel userViewModel) {
        authenticationService.findUserByLogin(userViewModel.getLogin())
                .ifPresent(user -> {
                    try {
                        user.setActif(!user.isActif());
                        authenticationService.updateUser(user);
                        chargerUtilisateurs();
                    } catch (Exception e) {
                        logger.error("Erreur lors du changement de statut", e);
                        AlertUtil.showErrorAlert("Erreur", "Mise à jour", "Erreur: " + e.getMessage());
                    }
                });
    }

    // ==================== UTILITAIRES ====================

    private List<UtilisateurViewModel> getSelectedUsers() {
        return utilisateursFiltres.stream()
                .filter(UtilisateurViewModel::isSelected)
                .collect(Collectors.toList());
    }

    private void toggleSelectAll() {
        boolean selectAll = selectAllCheckBox.isSelected();
        utilisateursFiltres.forEach(user -> user.setSelected(selectAll));
    }

    private void updateSelectionButtons() {
        List<UtilisateurViewModel> selected = getSelectedUsers();
        boolean hasSelection = !selected.isEmpty();

        editButton.setDisable(selected.size() != 1);
        deleteButton.setDisable(!hasSelection);
        activateButton.setDisable(!hasSelection);
        deactivateButton.setDisable(!hasSelection);
    }

    private void updateStatusLabel() {
        totalCountLabel.setText(String.format("Total: %d utilisateur(s)", utilisateurs.size()));
        statusLabel.setText(String.format("Affichage: %d/%d utilisateur(s)",
                utilisateursFiltres.size(), utilisateurs.size()));
    }

    private void exportUsers() {
        // TODO: Implémenter l'export
        AlertUtil.showInfoAlert("Information", "Export", "Fonctionnalité d'export en cours de développement");
    }

    private void viderFormulaire() {
        loginField.clear();
        nomField.clear();
        prenomField.clear();
        emailField.clear();
        roleComboBox.setValue(null);
        actifCheckBox.setSelected(true);
        motDePasseField.clear();
        confirmMotDePasseField.clear();

        // Supprimer les styles d'erreur
        loginField.setStyle("");
        nomField.setStyle("");
        prenomField.setStyle("");
        emailField.setStyle("");
    }

    private void remplirFormulaire(Utilisateur utilisateur) {
        loginField.setText(utilisateur.getLogin());
        nomField.setText(utilisateur.getNom());
        prenomField.setText(utilisateur.getPrenom());
        emailField.setText(utilisateur.getEmail());
        roleComboBox.setValue(utilisateur.getRole());
        actifCheckBox.setSelected(utilisateur.isActif());

        // Ne pas pré-remplir les mots de passe en modification
        motDePasseField.clear();
        confirmMotDePasseField.clear();
    }

    private boolean validerSaisie() {
        StringBuilder erreurs = new StringBuilder();

        // Login obligatoire
        if (loginField.getText().trim().isEmpty()) {
            erreurs.append("- Le login est obligatoire\n");
        } else if (loginField.getText().trim().length() < 3) {
            erreurs.append("- Le login doit contenir au moins 3 caractères\n");
        }

        // Vérification unicité du login
        if (modeCreation || !loginField.getText().equals(utilisateurEnCours.getLogin())) {
            if (authenticationService.loginExists(loginField.getText().trim())) {
                erreurs.append("- Ce login existe déjà\n");
            }
        }

        // Mot de passe obligatoire en création
        if (modeCreation && motDePasseField.getText().isEmpty()) {
            erreurs.append("- Le mot de passe est obligatoire\n");
        }

        // Confirmation mot de passe
        if (!motDePasseField.getText().isEmpty()) {
            if (!motDePasseField.getText().equals(confirmMotDePasseField.getText())) {
                erreurs.append("- La confirmation du mot de passe ne correspond pas\n");
            }
            if (motDePasseField.getText().length() < 6) {
                erreurs.append("- Le mot de passe doit contenir au moins 6 caractères\n");
            }
        }

        // Email valide si fourni
        if (!emailField.getText().trim().isEmpty()) {
            if (!validationService.isValidEmail(emailField.getText().trim())) {
                erreurs.append("- L'adresse email n'est pas valide\n");
            }
        }

        if (erreurs.length() > 0) {
            AlertUtil.showWarningAlert("Validation", "Erreurs de saisie", erreurs.toString());
            return false;
        }

        return true;
    }

    private void validateForm() {
        boolean isValid = !loginField.getText().trim().isEmpty() &&
                loginField.getText().trim().length() >= 3 &&
                (motDePasseField.getText().isEmpty() ||
                        motDePasseField.getText().equals(confirmMotDePasseField.getText()));

        enregistrerButton.setDisable(!isValid);
    }

    /**
     * Méthodes d'accès externe
     */
    public void actualiserListe() {
        chargerUtilisateurs();
    }

    public void selectionnerUtilisateur(String login) {
        utilisateursTableView.getItems().stream()
                .filter(user -> user.getLogin().equals(login))
                .findFirst()
                .ifPresent(user -> {
                    utilisateursTableView.getSelectionModel().select(user);
                    utilisateursTableView.scrollTo(user);
                });
    }

    // ==================== CLASSE VIEWMODEL ====================

    /**
     * ViewModel pour les utilisateurs dans le tableau
     */
    public static class UtilisateurViewModel {
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private final Utilisateur utilisateur;

        public UtilisateurViewModel(Utilisateur utilisateur) {
            this.utilisateur = utilisateur;
        }

        // Propriétés pour JavaFX
        public BooleanProperty selectedProperty() { return selected; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean selected) { this.selected.set(selected); }

        // Propriétés dérivées de l'utilisateur
        public String getLogin() { return utilisateur.getLogin(); }
        public String getNom() { return utilisateur.getNom(); }
        public String getPrenom() { return utilisateur.getPrenom(); }
        public String getEmail() { return utilisateur.getEmail(); }
        public RoleUtilisateur getRole() { return utilisateur.getRole(); }
        public boolean isActif() { return utilisateur.isActif(); }
        public LocalDateTime getDateCreation() { return utilisateur.getDateCreation(); }

        // Propriétés observables pour le tableau
        public javafx.beans.property.StringProperty loginProperty() {
            return new javafx.beans.property.SimpleStringProperty(getLogin());
        }

        public javafx.beans.property.StringProperty nomProperty() {
            return new javafx.beans.property.SimpleStringProperty(getNom());
        }

        public javafx.beans.property.StringProperty prenomProperty() {
            return new javafx.beans.property.SimpleStringProperty(getPrenom());
        }

        public javafx.beans.property.StringProperty emailProperty() {
            return new javafx.beans.property.SimpleStringProperty(getEmail());
        }

        public javafx.beans.property.ObjectProperty<RoleUtilisateur> roleProperty() {
            return new javafx.beans.property.SimpleObjectProperty<>(getRole());
        }

        public javafx.beans.property.BooleanProperty actifProperty() {
            return new javafx.beans.property.SimpleBooleanProperty(isActif());
        }

        public javafx.beans.property.ObjectProperty<LocalDateTime> dateCreationProperty() {
            return new javafx.beans.property.SimpleObjectProperty<>(getDateCreation());
        }

        public Utilisateur getUtilisateur() {
            return utilisateur;
        }
    }
}