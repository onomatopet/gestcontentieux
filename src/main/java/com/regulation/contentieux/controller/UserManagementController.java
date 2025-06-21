package com.regulation.contentieux.controller;

import com.regulation.contentieux.dao.UtilisateurDAO;
import com.regulation.contentieux.model.Utilisateur;
import com.regulation.contentieux.model.enums.RoleUtilisateur;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.service.ValidationService;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.DateFormatter;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Contrôleur pour la gestion des utilisateurs
 * Version corrigée avec toutes les méthodes requises
 */
public class UserManagementController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(UserManagementController.class);

    // Tableau des utilisateurs
    @FXML private TableView<UtilisateurViewModel> utilisateursTableView;
    @FXML private TableColumn<UtilisateurViewModel, Boolean> selectColumn;
    @FXML private TableColumn<UtilisateurViewModel, String> loginColumn;
    @FXML private TableColumn<UtilisateurViewModel, String> nomCompletColumn;
    @FXML private TableColumn<UtilisateurViewModel, String> roleColumn;
    @FXML private TableColumn<UtilisateurViewModel, String> emailColumn;
    @FXML private TableColumn<UtilisateurViewModel, String> statutColumn;
    @FXML private TableColumn<UtilisateurViewModel, String> derniereConnexionColumn;
    @FXML private TableColumn<UtilisateurViewModel, Void> actionsColumn;

    // Filtres et recherche
    @FXML private TextField searchField;
    @FXML private ComboBox<RoleUtilisateur> roleFilterComboBox;
    @FXML private CheckBox actifOnlyCheckBox;
    @FXML private Button searchButton;
    @FXML private Button clearFiltersButton;

    // Actions
    @FXML private Button newUserButton;
    @FXML private Button toggleStatusButton;
    @FXML private Button deleteButton;
    @FXML private Button refreshButton;

    // Formulaire
    @FXML private Label formTitleLabel;
    @FXML private TextField loginField;
    @FXML private TextField nomField;
    @FXML private TextField prenomField;
    @FXML private TextField emailField;
    @FXML private ComboBox<RoleUtilisateur> roleComboBox;
    @FXML private CheckBox actifCheckBox;
    @FXML private PasswordField motDePasseField;
    @FXML private PasswordField confirmMotDePasseField;
    @FXML private Button enregistrerButton;
    @FXML private Button annulerButton;

    // Services et DAO
    private final UtilisateurDAO utilisateurDAO = new UtilisateurDAO();
    private final AuthenticationService authenticationService = AuthenticationService.getInstance();
    private final ValidationService validationService = new ValidationService();

    // État
    private ObservableList<UtilisateurViewModel> utilisateurs = FXCollections.observableArrayList();
    private Utilisateur utilisateurEnCours;
    private boolean modeCreation = true;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableView();
        setupFormValidation();
        setupFilters();
        chargerUtilisateurs();
    }

    private void setupTableView() {
        // Configuration des colonnes
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
        selectColumn.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());

        loginColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getUtilisateur().getLogin()));

        nomCompletColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getUtilisateur().getDisplayName()));

        roleColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getUtilisateur().getRole().getLibelle()));

        emailColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getUtilisateur().getEmail()));

        statutColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getUtilisateur().isActif() ? "Actif" : "Inactif"));

        derniereConnexionColumn.setCellValueFactory(cellData -> {
            LocalDateTime derniere = cellData.getValue().getUtilisateur().getDerniereConnexion();
            return new SimpleStringProperty(derniere != null ?
                    DateFormatter.formatDateTime(derniere) : "Jamais");
        });

        // Colonne d'actions
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button editButton = new Button("Modifier");
            private final Button resetPasswordButton = new Button("Réinitialiser");
            private final HBox buttons = new HBox(5, editButton, resetPasswordButton);

            {
                editButton.getStyleClass().add("button-edit");
                resetPasswordButton.getStyleClass().add("button-reset");

                editButton.setOnAction(event -> {
                    UtilisateurViewModel user = getTableView().getItems().get(getIndex());
                    editerUtilisateur(user.getUtilisateur());
                });

                resetPasswordButton.setOnAction(event -> {
                    UtilisateurViewModel user = getTableView().getItems().get(getIndex());
                    reinitialiserMotDePasse(user.getUtilisateur());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : buttons);
            }
        });

        utilisateursTableView.setItems(utilisateurs);
    }

    private void setupFormValidation() {
        // Converter pour les ComboBox
        StringConverter<RoleUtilisateur> roleConverter = new StringConverter<>() {
            @Override
            public String toString(RoleUtilisateur role) {
                return role != null ? role.getLibelle() : "";
            }

            @Override
            public RoleUtilisateur fromString(String string) {
                return roleComboBox.getItems().stream()
                        .filter(role -> role.getLibelle().equals(string))
                        .findFirst()
                        .orElse(null);
            }
        };

        roleComboBox.setConverter(roleConverter);
        roleFilterComboBox.setConverter(roleConverter);

        // Remplir les ComboBox
        roleComboBox.setItems(FXCollections.observableArrayList(RoleUtilisateur.values()));
        roleFilterComboBox.setItems(FXCollections.observableArrayList(RoleUtilisateur.values()));

        // Validation en temps réel
        loginField.textProperty().addListener((obs, old, newVal) -> validateForm());
        nomField.textProperty().addListener((obs, old, newVal) -> validateForm());
        prenomField.textProperty().addListener((obs, old, newVal) -> validateForm());
        emailField.textProperty().addListener((obs, old, newVal) -> validateForm());
        motDePasseField.textProperty().addListener((obs, old, newVal) -> validateForm());
        confirmMotDePasseField.textProperty().addListener((obs, old, newVal) -> validateForm());
    }

    private void setupFilters() {
        searchField.setOnAction(e -> rechercher());
        searchButton.setOnAction(e -> rechercher());
        clearFiltersButton.setOnAction(e -> effacerFiltres());
        actifOnlyCheckBox.setOnAction(e -> rechercher());
        roleFilterComboBox.setOnAction(e -> rechercher());
    }

    // ==================== ACTIONS ====================

    @FXML
    private void nouvelUtilisateur() {
        modeCreation = true;
        utilisateurEnCours = null;
        formTitleLabel.setText("Nouvel utilisateur");
        viderFormulaire();
        motDePasseField.setDisable(false);
        confirmMotDePasseField.setDisable(false);
    }

    @FXML
    private void enregistrer() {
        if (!validerSaisie()) {
            return;
        }

        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (modeCreation) {
                    Utilisateur newUser = new Utilisateur();
                    newUser.setLogin(loginField.getText().trim());
                    newUser.setNom(nomField.getText().trim());
                    newUser.setPrenom(prenomField.getText().trim());
                    newUser.setEmail(emailField.getText().trim());
                    newUser.setRole(roleComboBox.getValue());
                    newUser.setActif(actifCheckBox.isSelected());

                    authenticationService.createUser(
                            newUser.getLogin(),
                            motDePasseField.getText(),
                            newUser.getNom(),
                            newUser.getPrenom(),
                            newUser.getEmail(),
                            newUser.getRole()
                    );
                } else {
                    utilisateurEnCours.setNom(nomField.getText().trim());
                    utilisateurEnCours.setPrenom(prenomField.getText().trim());
                    utilisateurEnCours.setEmail(emailField.getText().trim());
                    utilisateurEnCours.setRole(roleComboBox.getValue());
                    utilisateurEnCours.setActif(actifCheckBox.isSelected());

                    if (!motDePasseField.getText().isEmpty()) {
                        authenticationService.resetPassword(
                                utilisateurEnCours.getLogin(),
                                motDePasseField.getText()
                        );
                    }

                    utilisateurDAO.update(utilisateurEnCours);
                }
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    AlertUtil.showSuccessAlert(
                            "Succès",
                            modeCreation ? "Utilisateur créé" : "Utilisateur modifié",
                            "L'utilisateur a été enregistré avec succès."
                    );
                    viderFormulaire();
                    chargerUtilisateurs();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors de l'enregistrement", getException());
                    AlertUtil.showErrorAlert(
                            "Erreur",
                            "Erreur lors de l'enregistrement",
                            "Une erreur s'est produite : " + getException().getMessage()
                    );
                });
            }
        };

        new Thread(saveTask).start();
    }

    @FXML
    private void annuler() {
        viderFormulaire();
        utilisateurEnCours = null;
    }

    @FXML
    private void toggleStatus() {
        List<UtilisateurViewModel> selected = getSelectedUsers();
        if (selected.isEmpty()) {
            AlertUtil.showWarningAlert(
                    "Aucune sélection",
                    "Sélectionnez des utilisateurs",
                    "Veuillez sélectionner au moins un utilisateur."
            );
            return;
        }

        Task<Void> toggleTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                for (UtilisateurViewModel userVm : selected) {
                    Utilisateur user = userVm.getUtilisateur();
                    user.setActif(!user.isActif());
                    utilisateurDAO.update(user);
                }
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    AlertUtil.showSuccessAlert(
                            "Succès",
                            "Statut modifié",
                            selected.size() + " utilisateur(s) modifié(s)."
                    );
                    chargerUtilisateurs();
                });
            }
        };

        new Thread(toggleTask).start();
    }

    @FXML
    private void deleteUsers() {
        List<UtilisateurViewModel> selected = getSelectedUsers();
        if (selected.isEmpty()) {
            return;
        }

        String message = selected.size() == 1 ?
                "Voulez-vous vraiment supprimer l'utilisateur " + selected.get(0).getUtilisateur().getLogin() + " ?" :
                "Voulez-vous vraiment supprimer " + selected.size() + " utilisateurs ?";

        if (AlertUtil.showConfirmAlert("Confirmation", "Supprimer les utilisateurs", message)) {
            Task<Void> deleteTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    for (UtilisateurViewModel userVm : selected) {
                        utilisateurDAO.deleteById(userVm.getUtilisateur().getId());
                    }
                    return null;
                }

                @Override
                protected void succeeded() {
                    Platform.runLater(() -> {
                        AlertUtil.showSuccessAlert(
                                "Succès",
                                "Suppression réussie",
                                selected.size() + " utilisateur(s) supprimé(s)."
                        );
                        chargerUtilisateurs();
                    });
                }
            };

            new Thread(deleteTask).start();
        }
    }

    // ==================== MÉTHODES PRIVÉES ====================

    private void chargerUtilisateurs() {
        Task<List<Utilisateur>> loadTask = new Task<>() {
            @Override
            protected List<Utilisateur> call() throws Exception {
                return utilisateurDAO.findAll();
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    utilisateurs.clear();
                    getValue().forEach(user ->
                            utilisateurs.add(new UtilisateurViewModel(user))
                    );
                });
            }
        };

        new Thread(loadTask).start();
    }

    private void rechercher() {
        String searchTerm = searchField.getText();
        RoleUtilisateur roleFilter = roleFilterComboBox.getValue();
        Boolean actifOnly = actifOnlyCheckBox.isSelected() ? true : null;

        Task<List<Utilisateur>> searchTask = new Task<>() {
            @Override
            protected List<Utilisateur> call() throws Exception {
                return utilisateurDAO.searchUtilisateurs(searchTerm, roleFilter, actifOnly, 0, 1000);
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    utilisateurs.clear();
                    getValue().forEach(user ->
                            utilisateurs.add(new UtilisateurViewModel(user))
                    );
                });
            }
        };

        new Thread(searchTask).start();
    }

    private void effacerFiltres() {
        searchField.clear();
        roleFilterComboBox.setValue(null);
        actifOnlyCheckBox.setSelected(false);
        chargerUtilisateurs();
    }

    private void editerUtilisateur(Utilisateur utilisateur) {
        modeCreation = false;
        utilisateurEnCours = utilisateur;
        formTitleLabel.setText("Modifier l'utilisateur");
        remplirFormulaire(utilisateur);
        motDePasseField.setDisable(false);
        confirmMotDePasseField.setDisable(false);
        loginField.setDisable(true); // Le login ne peut pas être modifié
    }

    private void reinitialiserMotDePasse(Utilisateur utilisateur) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Réinitialisation du mot de passe");
        dialog.setHeaderText("Nouveau mot de passe pour " + utilisateur.getLogin());
        dialog.setContentText("Mot de passe :");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(password -> {
            if (password.length() >= 6) {
                if (authenticationService.resetPassword(utilisateur.getLogin(), password)) {
                    AlertUtil.showSuccessAlert(
                            "Succès",
                            "Mot de passe réinitialisé",
                            "Le mot de passe a été réinitialisé avec succès."
                    );
                } else {
                    AlertUtil.showErrorAlert(
                            "Erreur",
                            "Échec de la réinitialisation",
                            "Impossible de réinitialiser le mot de passe."
                    );
                }
            } else {
                AlertUtil.showWarningAlert(
                        "Mot de passe invalide",
                        "Mot de passe trop court",
                        "Le mot de passe doit contenir au moins 6 caractères."
                );
            }
        });
    }

    private void viderFormulaire() {
        loginField.clear();
        loginField.setDisable(false);
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

    private List<UtilisateurViewModel> getSelectedUsers() {
        return utilisateurs.stream()
                .filter(UtilisateurViewModel::isSelected)
                .collect(Collectors.toList());
    }

    /**
     * Méthodes d'accès externe
     */
    public void actualiserListe() {
        chargerUtilisateurs();
    }

    public void selectionnerUtilisateur(String login) {
        utilisateursTableView.getItems().stream()
                .filter(user -> user.getUtilisateur().getLogin().equals(login))
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

        public Utilisateur getUtilisateur() { return utilisateur; }
    }
}