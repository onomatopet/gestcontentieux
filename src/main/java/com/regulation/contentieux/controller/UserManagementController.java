package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.Utilisateur;
import com.regulation.contentieux.model.enums.RoleUtilisateur;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.service.ValidationService;
import com.regulation.contentieux.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Contrôleur pour la gestion des utilisateurs
 * Gestion CRUD complète des utilisateurs système
 */
public class UserManagementController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(UserManagementController.class);

    // Interface principale
    @FXML private TableView<Utilisateur> utilisateursTableView;
    @FXML private TableColumn<Utilisateur, String> loginColumn;
    @FXML private TableColumn<Utilisateur, String> nomPrenomColumn;
    @FXML private TableColumn<Utilisateur, String> roleColumn;
    @FXML private TableColumn<Utilisateur, String> statutColumn;
    @FXML private TableColumn<Utilisateur, String> derniereConnexionColumn;

    // Formulaire d'édition
    @FXML private VBox formulaireBox;
    @FXML private TextField loginField;
    @FXML private PasswordField motDePasseField;
    @FXML private PasswordField confirmMotDePasseField;
    @FXML private TextField nomField;
    @FXML private TextField prenomField;
    @FXML private TextField emailField;
    @FXML private ComboBox<RoleUtilisateur> roleComboBox;
    @FXML private CheckBox actifCheckBox;

    // Boutons
    @FXML private Button nouveauButton;
    @FXML private Button modifierButton;
    @FXML private Button supprimerButton;
    @FXML private Button enregistrerButton;
    @FXML private Button annulerButton;
    @FXML private Button resetMotDePasseButton;

    // Recherche et filtrage
    @FXML private TextField rechercheField;
    @FXML private ComboBox<String> filtreRoleComboBox;
    @FXML private ComboBox<String> filtreStatutComboBox;

    // Informations
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;

    private final AuthenticationService authenticationService;
    private final ValidationService validationService;

    private ObservableList<Utilisateur> utilisateursOriginaux;
    private ObservableList<Utilisateur> utilisateursFiltres;
    private Utilisateur utilisateurEnCours;
    private boolean modeCreation = false;

    public UserManagementController() {
        this.authenticationService = new AuthenticationService();
        this.validationService = new ValidationService();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        setupComboBoxes();
        setupEventHandlers();
        setupFormValidation();
        setupInitialState();

        chargerUtilisateurs();

        logger.info("UserManagementController initialisé");
    }

    private void setupTableColumns() {
        loginColumn.setCellValueFactory(new PropertyValueFactory<>("login"));

        nomPrenomColumn.setCellValueFactory(cellData -> {
            Utilisateur user = cellData.getValue();
            String nomComplet = (user.getPrenom() != null ? user.getPrenom() : "") + " " +
                    (user.getNom() != null ? user.getNom() : "");
            return new SimpleStringProperty(nomComplet.trim());
        });

        roleColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getRole().getLibelle()));

        statutColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().isActif() ? "Actif" : "Inactif"));

        derniereConnexionColumn.setCellValueFactory(cellData -> {
            LocalDateTime derniere = cellData.getValue().getDerniereConnexion();
            return new SimpleStringProperty(derniere != null ?
                    derniere.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) :
                    "Jamais");
        });

        // Style conditionnel pour les utilisateurs inactifs
        utilisateursTableView.setRowFactory(tv -> {
            TableRow<Utilisateur> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldUser, newUser) -> {
                if (newUser != null && !newUser.isActif()) {
                    row.setStyle("-fx-background-color: #ffebee;");
                } else {
                    row.setStyle("");
                }
            });
            return row;
        });
    }

    private void setupComboBoxes() {
        // ComboBox des rôles
        roleComboBox.setItems(FXCollections.observableArrayList(RoleUtilisateur.values()));
        roleComboBox.setValue(RoleUtilisateur.GESTIONNAIRE); // Par défaut

        // Filtres
        filtreRoleComboBox.getItems().addAll("Tous les rôles", "SUPER_ADMIN", "ADMIN", "GESTIONNAIRE");
        filtreRoleComboBox.setValue("Tous les rôles");

        filtreStatutComboBox.getItems().addAll("Tous les statuts", "Actif", "Inactif");
        filtreStatutComboBox.setValue("Tous les statuts");
    }

    private void setupEventHandlers() {
        // Sélection dans la table
        utilisateursTableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    modifierButton.setDisable(newSelection == null);
                    supprimerButton.setDisable(newSelection == null);
                    resetMotDePasseButton.setDisable(newSelection == null);
                }
        );

        // Boutons d'action
        nouveauButton.setOnAction(e -> creerNouvelUtilisateur());
        modifierButton.setOnAction(e -> modifierUtilisateurSelectionne());
        supprimerButton.setOnAction(e -> supprimerUtilisateurSelectionne());
        enregistrerButton.setOnAction(e -> enregistrerUtilisateur());
        annulerButton.setOnAction(e -> annulerEdition());
        resetMotDePasseButton.setOnAction(e -> resetMotDePasse());

        // Filtrage en temps réel
        rechercheField.textProperty().addListener((obs, oldVal, newVal) -> appliquerFiltres());
        filtreRoleComboBox.valueProperty().addListener((obs, oldVal, newVal) -> appliquerFiltres());
        filtreStatutComboBox.valueProperty().addListener((obs, oldVal, newVal) -> appliquerFiltres());
    }

    private void setupFormValidation() {
        // Validation du login en temps réel
        loginField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty() && newVal.length() >= 3) {
                loginField.setStyle("-fx-border-color: green;");
            } else {
                loginField.setStyle("-fx-border-color: red;");
            }
            validerFormulaire();
        });

        // Validation de la confirmation du mot de passe
        confirmMotDePasseField.textProperty().addListener((obs, oldVal, newVal) -> {
            String motDePasse = motDePasseField.getText();
            if (!newVal.isEmpty() && newVal.equals(motDePasse)) {
                confirmMotDePasseField.setStyle("-fx-border-color: green;");
            } else {
                confirmMotDePasseField.setStyle("-fx-border-color: red;");
            }
            validerFormulaire();
        });

        // Validation email
        emailField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.isEmpty() || validationService.isValidEmail(newVal)) {
                emailField.setStyle("");
            } else {
                emailField.setStyle("-fx-border-color: orange;");
            }
        });
    }

    private void setupInitialState() {
        formulaireBox.setDisable(true);
        modifierButton.setDisable(true);
        supprimerButton.setDisable(true);
        resetMotDePasseButton.setDisable(true);
        progressBar.setVisible(false);
        statusLabel.setText("Prêt");
    }

    private void chargerUtilisateurs() {
        Task<List<Utilisateur>> task = new Task<List<Utilisateur>>() {
            @Override
            protected List<Utilisateur> call() throws Exception {
                updateMessage("Chargement des utilisateurs...");
                return authenticationService.getAllUsers();
            }
        };

        task.setOnSucceeded(e -> {
            List<Utilisateur> utilisateurs = task.getValue();
            utilisateursOriginaux = FXCollections.observableArrayList(utilisateurs);
            utilisateursFiltres = FXCollections.observableArrayList(utilisateurs);
            utilisateursTableView.setItems(utilisateursFiltres);

            statusLabel.setText(String.format("%d utilisateur(s) chargé(s)", utilisateurs.size()));
            progressBar.setVisible(false);
        });

        task.setOnFailed(e -> {
            Throwable exception = task.getException();
            logger.error("Erreur lors du chargement des utilisateurs", exception);
            AlertUtil.showErrorAlert("Erreur", "Chargement impossible",
                    "Impossible de charger les utilisateurs: " + exception.getMessage());
            progressBar.setVisible(false);
        });

        progressBar.setVisible(true);
        statusLabel.textProperty().bind(task.messageProperty());

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void creerNouvelUtilisateur() {
        utilisateurEnCours = new Utilisateur();
        modeCreation = true;

        afficherFormulaireEdition();
        viderFormulaire();

        loginField.requestFocus();
        statusLabel.setText("Création d'un nouvel utilisateur");
    }

    @FXML
    private void modifierUtilisateurSelectionne() {
        Utilisateur selected = utilisateursTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.showWarningAlert("Attention", "Aucune sélection",
                    "Veuillez sélectionner un utilisateur à modifier.");
            return;
        }

        utilisateurEnCours = selected;
        modeCreation = false;

        afficherFormulaireEdition();
        remplirFormulaire(selected);

        statusLabel.setText("Modification de l'utilisateur: " + selected.getLogin());
    }

    @FXML
    private void supprimerUtilisateurSelectionne() {
        Utilisateur selected = utilisateursTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        // Vérification de sécurité
        if (selected.getRole() == RoleUtilisateur.SUPER_ADMIN) {
            AlertUtil.showErrorAlert("Erreur", "Suppression interdite",
                    "Impossible de supprimer un Super Administrateur.");
            return;
        }

        Optional<ButtonType> result = AlertUtil.showConfirmationAlert(
                "Confirmation",
                "Supprimer l'utilisateur",
                "Êtes-vous sûr de vouloir supprimer l'utilisateur \"" + selected.getLogin() + "\" ?\n" +
                        "Cette action est irréversible."
        );

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                authenticationService.deleteUser(selected.getId());
                utilisateursOriginaux.remove(selected);
                appliquerFiltres();

                statusLabel.setText("Utilisateur supprimé: " + selected.getLogin());
                AlertUtil.showInfoAlert("Succès", "Suppression réussie",
                        "L'utilisateur a été supprimé avec succès.");

            } catch (Exception e) {
                logger.error("Erreur lors de la suppression de l'utilisateur", e);
                AlertUtil.showErrorAlert("Erreur", "Suppression échouée",
                        "Impossible de supprimer l'utilisateur: " + e.getMessage());
            }
        }
    }

    @FXML
    private void enregistrerUtilisateur() {
        try {
            if (!validerSaisie()) {
                return;
            }

            // Remplissage des données
            utilisateurEnCours.setLogin(loginField.getText().trim());
            utilisateurEnCours.setNom(nomField.getText().trim());
            utilisateurEnCours.setPrenom(prenomField.getText().trim());
            utilisateurEnCours.setEmail(emailField.getText().trim());
            utilisateurEnCours.setRole(roleComboBox.getValue());
            utilisateurEnCours.setActif(actifCheckBox.isSelected());

            // Gestion du mot de passe
            String motDePasse = motDePasseField.getText();
            if (!motDePasse.isEmpty()) {
                utilisateurEnCours.setMotDePasse(motDePasse); // Sera hashé dans le service
            }

            if (modeCreation) {
                authenticationService.createUser(utilisateurEnCours);
                utilisateursOriginaux.add(utilisateurEnCours);
                statusLabel.setText("Utilisateur créé: " + utilisateurEnCours.getLogin());
                AlertUtil.showInfoAlert("Succès", "Création réussie",
                        "L'utilisateur a été créé avec succès.");
            } else {
                authenticationService.updateUser(utilisateurEnCours);
                statusLabel.setText("Utilisateur modifié: " + utilisateurEnCours.getLogin());
                AlertUtil.showInfoAlert("Succès", "Modification réussie",
                        "L'utilisateur a été modifié avec succès.");
            }

            appliquerFiltres();
            masquerFormulaireEdition();

        } catch (Exception e) {
            logger.error("Erreur lors de l'enregistrement de l'utilisateur", e);
            AlertUtil.showErrorAlert("Erreur", "Enregistrement échoué",
                    "Impossible d'enregistrer l'utilisateur: " + e.getMessage());
        }
    }

    @FXML
    private void annulerEdition() {
        masquerFormulaireEdition();
        statusLabel.setText("Édition annulée");
    }

    @FXML
    private void resetMotDePasse() {
        Utilisateur selected = utilisateursTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        Optional<ButtonType> result = AlertUtil.showConfirmationAlert(
                "Confirmation",
                "Réinitialiser le mot de passe",
                "Voulez-vous réinitialiser le mot de passe de l'utilisateur \"" + selected.getLogin() + "\" ?\n" +
                        "Un nouveau mot de passe temporaire sera généré."
        );

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                String nouveauMotDePasse = authenticationService.resetUserPassword(selected.getId());

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Réinitialisation réussie");
                alert.setHeaderText("Nouveau mot de passe généré");
                alert.setContentText("Nouveau mot de passe temporaire pour \"" + selected.getLogin() + "\" :\n\n" +
                        nouveauMotDePasse + "\n\n" +
                        "L'utilisateur devra le changer à sa prochaine connexion.");
                alert.showAndWait();

                statusLabel.setText("Mot de passe réinitialisé pour: " + selected.getLogin());

            } catch (Exception e) {
                logger.error("Erreur lors de la réinitialisation du mot de passe", e);
                AlertUtil.showErrorAlert("Erreur", "Réinitialisation échouée",
                        "Impossible de réinitialiser le mot de passe: " + e.getMessage());
            }
        }
    }

    private void appliquerFiltres() {
        if (utilisateursOriginaux == null) {
            return;
        }

        String recherche = rechercheField.getText().toLowerCase().trim();
        String filtreRole = filtreRoleComboBox.getValue();
        String filtreStatut = filtreStatutComboBox.getValue();

        utilisateursFiltres.clear();

        utilisateursOriginaux.stream()
                .filter(user -> {
                    // Filtre de recherche
                    if (!recherche.isEmpty()) {
                        String searchText = (user.getLogin() + " " +
                                user.getNom() + " " +
                                user.getPrenom() + " " +
                                user.getEmail()).toLowerCase();
                        if (!searchText.contains(recherche)) {
                            return false;
                        }
                    }

                    // Filtre rôle
                    if (!"Tous les rôles".equals(filtreRole)) {
                        if (!user.getRole().name().equals(filtreRole)) {
                            return false;
                        }
                    }

                    // Filtre statut
                    if (!"Tous les statuts".equals(filtreStatut)) {
                        boolean estActif = "Actif".equals(filtreStatut);
                        if (user.isActif() != estActif) {
                            return false;
                        }
                    }

                    return true;
                })
                .forEach(utilisateursFiltres::add);

        statusLabel.setText(String.format("Affichage: %d/%d utilisateur(s)",
                utilisateursFiltres.size(), utilisateursOriginaux.size()));
    }

    private void afficherFormulaireEdition() {
        formulaireBox.setDisable(false);
        nouveauButton.setDisable(true);
        modifierButton.setDisable(true);
        supprimerButton.setDisable(true);
        resetMotDePasseButton.setDisable(true);
        utilisateursTableView.setDisable(true);
    }

    private void masquerFormulaireEdition() {
        formulaireBox.setDisable(true);
        nouveauButton.setDisable(false);
        modifierButton.setDisable(false);
        supprimerButton.setDisable(false);
        resetMotDePasseButton.setDisable(false);
        utilisateursTableView.setDisable(false);

        viderFormulaire();
        utilisateurEnCours = null;
    }

    private void viderFormulaire() {
        loginField.clear();
        motDePasseField.clear();
        confirmMotDePasseField.clear();
        nomField.clear();
        prenomField.clear();
        emailField.clear();
        roleComboBox.setValue(RoleUtilisateur.GESTIONNAIRE);
        actifCheckBox.setSelected(true);

        // Reset des styles
        loginField.setStyle("");
        confirmMotDePasseField.setStyle("");
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

    private void validerFormulaire() {
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
}