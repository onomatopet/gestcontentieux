package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.*;
import com.regulation.contentieux.service.*;
import com.regulation.contentieux.util.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import com.regulation.contentieux.util.CurrencyFormatter;
import org.slf4j.LoggerFactory;

import java.math.RoundingMode;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Contrôleur pour le dialogue unifié de création d'affaire avec encaissement obligatoire
 * Implémente le workflow complet en une seule interface
 */
public class AffaireEncaissementController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AffaireEncaissementController.class);

    // === SECTION AFFAIRE ===
    @FXML private TextField numeroAffaireField;
    @FXML private DatePicker dateCreationPicker;
    @FXML private Label mandatActifLabel;

    // Contrevenant
    @FXML private ComboBox<Contrevenant> contrevenantCombo;
    @FXML private Button nouveauContrevenantBtn;  // Corriger le nom
    @FXML private TextField nomContrevenantField;
    @FXML private TextField adresseField;
    @FXML private TextField telephoneField;

    // Infraction
    @FXML private ComboBox<Contravention> contraventionCombo;
    @FXML private TextField contraventionLibreField;
    @FXML private Label montantTotalLabel;  // C'est un Label dans le FXML, pas un TextField
    @FXML private Button ajouterContraventionBtn;  // Ajouter ce bouton qui existe dans le FXML
    @FXML private ComboBox<Centre> centreCombo;
    @FXML private ComboBox<Service> serviceCombo;
    @FXML private ComboBox<Bureau> bureauCombo;

    // Variables pour stocker les montants
    private BigDecimal montantAmendeTotal = BigDecimal.ZERO;

    // Acteurs - Une seule TableView comme demandé
    @FXML private TableView<ActeurViewModel> acteursTableView;
    @FXML private TableColumn<ActeurViewModel, String> codeAgentColumn;
    @FXML private TableColumn<ActeurViewModel, String> nomAgentColumn;
    @FXML private TableColumn<ActeurViewModel, String> roleAgentColumn;
    @FXML private TableColumn<ActeurViewModel, Void> actionColumn;
    @FXML private Button addActeurButton;
    private ObservableList<ActeurViewModel> acteursList = FXCollections.observableArrayList();

    // Indicateur
    @FXML private CheckBox indicateurCheck;
    @FXML private TextField nomIndicateurField;
    @FXML private ComboBox<Agent> indicateurAgentCombo;

    // === SECTION ENCAISSEMENT ===
    @FXML private TextField numeroEncaissementField;
    @FXML private DatePicker dateEncaissementPicker;
    @FXML private TextField montantEncaisseField;
    @FXML private Label soldeRestantLabel;
    @FXML private ProgressBar paiementProgress;

    // Mode de règlement
    @FXML private ComboBox<ModeReglement> modeReglementCombo;
    @FXML private VBox chequeSection;
    @FXML private ComboBox<Banque> banqueCombo;
    @FXML private TextField numeroChequeField;

    // Boutons
    @FXML private Button validerBtn;
    @FXML private Button annulerBtn;
    @FXML private Label statusLabel;

    // Services
    private final AffaireService affaireService = new AffaireService();
    private final EncaissementService encaissementService = new EncaissementService();
    private final ContrevenantService contrevenantService = new ContrevenantService();
    private final AgentService agentService = new AgentService();
    private final ContraventionService contraventionService = new ContraventionService();
    private final CentreService centreService = new CentreService();
    private final ServiceService serviceService = new ServiceService();
    private final BureauService bureauService = new BureauService();
    private final BanqueService banqueService = new BanqueService();
    private final MandatService mandatService = MandatService.getInstance();

    // État
    private Affaire createdAffaire;
    private boolean isCreating = false;
    private Stage dialogStage;

    /**
     * Classe interne pour le ViewModel des acteurs
     */
    public static class ActeurViewModel {
        private final SimpleStringProperty codeAgent;
        private final SimpleStringProperty nomAgent;
        private final SimpleStringProperty role;
        private final Agent agent;

        public ActeurViewModel(Agent agent, String role) {
            this.agent = agent;
            this.codeAgent = new SimpleStringProperty(agent.getCodeAgent());
            this.nomAgent = new SimpleStringProperty(agent.getNom() + " " + agent.getPrenom());
            this.role = new SimpleStringProperty(role);
        }

        public String getCodeAgent() { return codeAgent.get(); }
        public String getNomAgent() { return nomAgent.get(); }
        public String getRole() { return role.get(); }
        public Agent getAgent() { return agent; }

        public SimpleStringProperty codeAgentProperty() { return codeAgent; }
        public SimpleStringProperty nomAgentProperty() { return nomAgent; }
        public SimpleStringProperty roleProperty() { return role; }
    }

    /**
     * Initialise et affiche le dialogue
     */
    public void showDialog(Stage owner) {
        try {
            dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(owner);
            dialogStage.setTitle("Nouvelle Affaire Contentieuse");
            dialogStage.setResizable(true);
            dialogStage.setWidth(900);
            dialogStage.setHeight(750);

            // IMPORTANT: Créer le loader SANS charger encore le FXML
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/view/affaire-encaissement-dialog.fxml"));
            loader.setController(this);

            // MAINTENANT charger le FXML
            BorderPane root = loader.load();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());

            dialogStage.setScene(scene);
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Erreur lors du chargement du formulaire", e);
            AlertUtil.showError("Erreur", "Impossible de charger le formulaire");
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisation du dialogue unifié affaire/encaissement");

        setupValidation();
        setupComboBoxes();
        setupActeursTable();
        setupEventHandlers();
        loadInitialData();

        // Valeurs par défaut
        dateCreationPicker.setValue(LocalDate.now());
        dateEncaissementPicker.setValue(LocalDate.now());

        // Afficher le mandat actif
        Mandat mandatActif = mandatService.getMandatActif();
        if (mandatActif != null) {
            mandatActifLabel.setText(mandatActif.getNumeroMandat());
        } else {
            mandatActifLabel.setText("AUCUN MANDAT ACTIF");
            mandatActifLabel.setStyle("-fx-text-fill: red;");
            validerBtn.setDisable(true);

            AlertUtil.showError("Erreur", "Aucun mandat actif",
                    "Vous devez activer un mandat avant de créer des affaires.");
        }
    }

    /**
     * Configuration de la TableView des acteurs
     */
    private void setupActeursTable() {
        if (acteursTableView != null) {
            // Configurer la TableView
            acteursTableView.setItems(acteursList);

            // Si les colonnes sont définies dans le FXML, les configurer
            if (codeAgentColumn != null) {
                codeAgentColumn.setCellValueFactory(new PropertyValueFactory<>("codeAgent"));
            }

            if (nomAgentColumn != null) {
                nomAgentColumn.setCellValueFactory(new PropertyValueFactory<>("nomAgent"));
            }

            if (roleAgentColumn != null) {
                roleAgentColumn.setCellValueFactory(new PropertyValueFactory<>("role"));
            }

            // Colonne Action avec bouton Retirer
            if (actionColumn != null) {
                actionColumn.setCellFactory(param -> new TableCell<ActeurViewModel, Void>() {
                    private final Button btn = new Button("Retirer");

                    {
                        btn.setOnAction(event -> {
                            ActeurViewModel data = getTableView().getItems().get(getIndex());
                            acteursList.remove(data);
                        });
                        btn.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white;");
                    }

                    @Override
                    public void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            setGraphic(btn);
                        }
                    }
                });
            }
        }
    }

    /**
     * Affiche le dialogue d'ajout d'acteur
     */
    private void showAddActeurDialog() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Ajouter un agent");
        dialog.setHeaderText("Sélectionnez un agent et son(ses) rôle(s)");

        // Boutons
        ButtonType addButtonType = new ButtonType("Choisir", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Contenu
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // ComboBox éditable pour la recherche
        ComboBox<Agent> agentCombo = new ComboBox<>();
        agentCombo.setEditable(true);
        agentCombo.setPrefWidth(300);
        agentCombo.setPromptText("Tapez pour rechercher un agent...");

        // Configuration du converter pour l'affichage
        agentCombo.setConverter(new StringConverter<Agent>() {
            @Override
            public String toString(Agent agent) {
                return agent != null ? agent.getCodeAgent() + " - " + agent.getNom() + " " + agent.getPrenom() : "";
            }

            @Override
            public Agent fromString(String string) {
                // Recherche dans la liste des agents
                return agentCombo.getItems().stream()
                        .filter(agent -> agent.getCodeAgent().toLowerCase().contains(string.toLowerCase()) ||
                                agent.getNom().toLowerCase().contains(string.toLowerCase()) ||
                                agent.getPrenom().toLowerCase().contains(string.toLowerCase()))
                        .findFirst()
                        .orElse(null);
            }
        });

        // CheckBox pour les rôles
        CheckBox saisissantCheck = new CheckBox("Saisissant");
        CheckBox chefCheck = new CheckBox("Chef");

        grid.add(new Label("Agent:"), 0, 0);
        grid.add(agentCombo, 1, 0, 2, 1);
        grid.add(new Label("Rôle(s):"), 0, 1);
        grid.add(saisissantCheck, 1, 1);
        grid.add(chefCheck, 2, 1);

        dialog.getDialogPane().setContent(grid);

        // Charger les agents
        Task<List<Agent>> loadAgentsTask = new Task<>() {
            @Override
            protected List<Agent> call() throws Exception {
                return agentService.findActiveAgents();
            }
        };

        loadAgentsTask.setOnSucceeded(e -> {
            List<Agent> agents = loadAgentsTask.getValue();
            agentCombo.getItems().setAll(agents);

            // Activer la recherche automatique lors de la saisie
            agentCombo.getEditor().textProperty().addListener((obs, oldText, newText) -> {
                if (newText != null && !newText.isEmpty()) {
                    // Filtrer les agents selon le texte saisi
                    List<Agent> filtered = agents.stream()
                            .filter(agent -> agent.getCodeAgent().toLowerCase().contains(newText.toLowerCase()) ||
                                    agent.getNom().toLowerCase().contains(newText.toLowerCase()) ||
                                    agent.getPrenom().toLowerCase().contains(newText.toLowerCase()))
                            .collect(Collectors.toList());

                    Platform.runLater(() -> {
                        agentCombo.getItems().setAll(filtered);
                        if (!filtered.isEmpty()) {
                            agentCombo.show();
                        }
                    });
                } else {
                    agentCombo.getItems().setAll(agents);
                }
            });
        });

        new Thread(loadAgentsTask).start();

        // Désactiver le bouton OK si aucun agent sélectionné ou aucun rôle coché
        Node addButton = dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(true);

        // Validation
        agentCombo.valueProperty().addListener((obs, old, newVal) -> {
            addButton.setDisable(newVal == null || (!saisissantCheck.isSelected() && !chefCheck.isSelected()));
        });

        saisissantCheck.selectedProperty().addListener((obs, old, newVal) -> {
            addButton.setDisable(agentCombo.getValue() == null || (!newVal && !chefCheck.isSelected()));
        });

        chefCheck.selectedProperty().addListener((obs, old, newVal) -> {
            addButton.setDisable(agentCombo.getValue() == null || (!newVal && !saisissantCheck.isSelected()));
        });

        // Convertir le résultat
        dialog.setResultConverter(dialogButton -> {
            return dialogButton == addButtonType;
        });

        Optional<Boolean> result = dialog.showAndWait();

        if (result.isPresent() && result.get()) {
            Agent selectedAgent = agentCombo.getValue();
            if (selectedAgent != null) {
                // Ajouter comme saisissant si coché
                if (saisissantCheck.isSelected()) {
                    if (!isAgentAlreadyInRole(selectedAgent, "SAISISSANT")) {
                        acteursList.add(new ActeurViewModel(selectedAgent, "SAISISSANT"));
                    } else {
                        AlertUtil.showWarning("Doublon",
                                "Cet agent est déjà saisissant dans cette affaire.");
                    }
                }

                // Ajouter comme chef si coché (ligne séparée)
                if (chefCheck.isSelected()) {
                    if (!isAgentAlreadyInRole(selectedAgent, "CHEF")) {
                        acteursList.add(new ActeurViewModel(selectedAgent, "CHEF"));
                    } else {
                        AlertUtil.showWarning("Doublon",
                                "Cet agent est déjà chef dans cette affaire.");
                    }
                }
            }
        }
    }

    /**
     * Vérifie si un agent a déjà un rôle spécifique
     */
    private boolean isAgentAlreadyInRole(Agent agent, String role) {
        return acteursList.stream()
                .anyMatch(acteur -> acteur.getAgent().getId().equals(agent.getId()) &&
                        acteur.getRole().equals(role));
    }

    /**
     * Configuration de la validation temps réel
     */
    private void setupValidation() {
        // Validation numérique pour les montants - uniquement pour montantEncaisseField
        if (montantEncaisseField != null) {
            montantEncaisseField.textProperty().addListener((obs, old, val) -> {
                if (!val.matches("\\d*")) {
                    montantEncaisseField.setText(old);
                }
                updateSoldeRestant();
                validateEncaissement();
            });
        }

        // Mode de règlement
        if (modeReglementCombo != null) {
            modeReglementCombo.valueProperty().addListener((obs, old, val) -> {
                if (chequeSection != null) {
                    chequeSection.setVisible(val == ModeReglement.CHEQUE);
                    chequeSection.setManaged(val == ModeReglement.CHEQUE);
                }
            });
        }

        // Indicateur
        if (indicateurCheck != null) {
            indicateurCheck.selectedProperty().addListener((obs, old, val) -> {
                if (nomIndicateurField != null) nomIndicateurField.setDisable(!val);
                if (indicateurAgentCombo != null) indicateurAgentCombo.setDisable(!val);
            });
        }

        // Listener pour la sélection de contravention
        if (contraventionCombo != null) {
            contraventionCombo.valueProperty().addListener((obs, old, val) -> {
                if (val != null) {
                    montantAmendeTotal = val.getMontant();
                    updateMontantTotalLabel();
                    updateSoldeRestant();
                }
            });
        }
    }

    /**
     * Met à jour l'affichage du montant total
     */
    private void updateMontantTotalLabel() {
        if (montantTotalLabel != null) {
            montantTotalLabel.setText(CurrencyFormatter.format(montantAmendeTotal) + " FCFA");
        }
    }

    /**
     * Configuration des ComboBox
     */
    private void setupComboBoxes() {
        // Configuration du StringConverter pour Contrevenant
        contrevenantCombo.setConverter(new StringConverter<Contrevenant>() {
            @Override
            public String toString(Contrevenant contrevenant) {
                return contrevenant != null ? contrevenant.getNomComplet() : "";
            }

            @Override
            public Contrevenant fromString(String string) {
                return null;
            }
        });

        // Configuration du StringConverter pour Contravention
        contraventionCombo.setConverter(new StringConverter<Contravention>() {
            @Override
            public String toString(Contravention contravention) {
                return contravention != null ? contravention.getDescription() : "";
            }

            @Override
            public Contravention fromString(String string) {
                return null;
            }
        });

        // Configuration du StringConverter pour Centre
        centreCombo.setConverter(new StringConverter<Centre>() {
            @Override
            public String toString(Centre centre) {
                return centre != null ? centre.getNomCentre() : "";
            }

            @Override
            public Centre fromString(String string) {
                return null;
            }
        });

        // Configuration du StringConverter pour Service
        serviceCombo.setConverter(new StringConverter<Service>() {
            @Override
            public String toString(Service service) {
                return service != null ? service.getNomService() : "";
            }

            @Override
            public Service fromString(String string) {
                return null;
            }
        });

        // Configuration du StringConverter pour Bureau
        bureauCombo.setConverter(new StringConverter<Bureau>() {
            @Override
            public String toString(Bureau bureau) {
                return bureau != null ? bureau.getNomBureau() : "";
            }

            @Override
            public Bureau fromString(String string) {
                return null;
            }
        });

        // Configuration du StringConverter pour ModeReglement
        modeReglementCombo.setConverter(new StringConverter<ModeReglement>() {
            @Override
            public String toString(ModeReglement mode) {
                return mode != null ? mode.getLibelle() : "";
            }

            @Override
            public ModeReglement fromString(String string) {
                return null;
            }
        });

        // Configuration du StringConverter pour Banque
        banqueCombo.setConverter(new StringConverter<Banque>() {
            @Override
            public String toString(Banque banque) {
                return banque != null ? banque.getNomBanque() : "";
            }

            @Override
            public Banque fromString(String string) {
                return null;
            }
        });

        indicateurAgentCombo.setConverter(new StringConverter<Agent>() {
            @Override
            public String toString(Agent agent) {
                return agent != null ? agent.getNom() + " " + agent.getPrenom() : "";
            }

            @Override
            public Agent fromString(String string) {
                return null;
            }
        });

        // Listeners
        contrevenantCombo.setOnAction(e -> {
            Contrevenant selected = contrevenantCombo.getValue();
            if (selected != null) {
                nomContrevenantField.setText(selected.getNomComplet());
                adresseField.setText(selected.getAdresse());
                telephoneField.setText(selected.getTelephone());
            }
        });
    }

    /**
     * Configuration des gestionnaires d'événements
     */
    private void setupEventHandlers() {
        // Bouton nouveau contrevenant
        if (nouveauContrevenantBtn != null) {
            nouveauContrevenantBtn.setOnAction(e -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/contrevenant-form.fxml"));
                    Parent root = loader.load();

                    Stage stage = new Stage();
                    stage.initModality(Modality.APPLICATION_MODAL);
                    stage.initOwner(dialogStage);
                    stage.setTitle("Nouveau Contrevenant");
                    stage.setScene(new Scene(root));

                    ContrevenantFormController controller = loader.getController();
                    controller.initializeForNew();

                    stage.showAndWait();

                    // Rafraîchir la liste
                    loadContrevenants();
                } catch (Exception ex) {
                    logger.error("Erreur ouverture dialogue contrevenant", ex);
                    AlertUtil.showError("Erreur", "Impossible d'ouvrir le formulaire");
                }
            });
        }

        // Bouton ajouter acteur
        if (addActeurButton != null) {
            addActeurButton.setOnAction(e -> showAddActeurDialog());
        }
    }

    /**
     * Charge les contrevenants
     */
    private void loadContrevenants() {
        Task<List<Contrevenant>> task = new Task<>() {
            @Override
            protected List<Contrevenant> call() throws Exception {
                return contrevenantService.getAllContrevenants();
            }
        };

        task.setOnSucceeded(e -> {
            contrevenantCombo.getItems().setAll(task.getValue());
        });

        new Thread(task).start();
    }

    /**
     * Charge les contraventions
     */
    private void loadContraventions() {
        Task<List<Contravention>> task = new Task<>() {
            @Override
            protected List<Contravention> call() throws Exception {
                return contraventionService.getAllContraventions();
            }
        };

        task.setOnSucceeded(e -> {
            contraventionCombo.getItems().setAll(task.getValue());
        });

        new Thread(task).start();
    }

    /**
     * Charge les centres
     */
    private void loadCentres() {
        Task<List<Centre>> task = new Task<>() {
            @Override
            protected List<Centre> call() throws Exception {
                return centreService.getAllCentres();
            }
        };

        task.setOnSucceeded(e -> {
            centreCombo.getItems().setAll(task.getValue());
        });

        new Thread(task).start();
    }

    /**
     * Charge les services
     */
    private void loadServices() {
        Task<List<Service>> task = new Task<>() {
            @Override
            protected List<Service> call() throws Exception {
                return serviceService.getAllServices();
            }
        };

        task.setOnSucceeded(e -> {
            serviceCombo.getItems().setAll(task.getValue());
        });

        new Thread(task).start();
    }

    /**
     * Charge les bureaux
     */
    private void loadBureaux() {
        Task<List<Bureau>> task = new Task<>() {
            @Override
            protected List<Bureau> call() throws Exception {
                return bureauService.getAllBureaux();
            }
        };

        task.setOnSucceeded(e -> {
            bureauCombo.getItems().setAll(task.getValue());
        });

        new Thread(task).start();
    }

    /**
     * Charge les banques
     */
    private void loadBanques() {
        Task<List<Banque>> task = new Task<>() {
            @Override
            protected List<Banque> call() throws Exception {
                return banqueService.getAllBanques();
            }
        };

        task.setOnSucceeded(e -> {
            banqueCombo.getItems().setAll(task.getValue());
        });

        new Thread(task).start();
    }

    /**
     * Charge les agents pour l'indicateur
     */
    private void loadIndicateurAgents() {
        Task<List<Agent>> task = new Task<>() {
            @Override
            protected List<Agent> call() throws Exception {
                return agentService.findActiveAgents();
            }
        };

        task.setOnSucceeded(e -> {
            indicateurAgentCombo.getItems().setAll(task.getValue());
        });

        new Thread(task).start();
    }

    /**
     * Charge toutes les données initiales
     */
    private void loadInitialData() {
        loadContrevenants();
        loadContraventions();
        loadCentres();
        loadServices();
        loadBureaux();
        loadBanques();
        loadIndicateurAgents();

        // Modes de règlement
        modeReglementCombo.getItems().setAll(ModeReglement.values());
    }

    /**
     * Met à jour le solde restant
     */
    private void updateSoldeRestant() {
        try {
            BigDecimal encaisse = BigDecimal.ZERO;
            if (montantEncaisseField != null && !montantEncaisseField.getText().isEmpty()) {
                encaisse = new BigDecimal(montantEncaisseField.getText());
            }

            BigDecimal solde = montantAmendeTotal.subtract(encaisse);

            if (soldeRestantLabel != null) {
                soldeRestantLabel.setText(CurrencyFormatter.format(solde));
            }

            // Mettre à jour la barre de progression
            if (paiementProgress != null && montantAmendeTotal.compareTo(BigDecimal.ZERO) > 0) {
                double progress = encaisse.divide(montantAmendeTotal, 2, RoundingMode.HALF_UP).doubleValue();
                paiementProgress.setProgress(progress);
            }

            // Colorer selon le solde
            if (soldeRestantLabel != null) {
                if (solde.compareTo(BigDecimal.ZERO) == 0) {
                    soldeRestantLabel.setStyle("-fx-text-fill: green;");
                } else if (solde.compareTo(BigDecimal.ZERO) > 0) {
                    soldeRestantLabel.setStyle("-fx-text-fill: orange;");
                } else {
                    soldeRestantLabel.setStyle("-fx-text-fill: red;");
                }
            }
        } catch (Exception e) {
            if (soldeRestantLabel != null) soldeRestantLabel.setText("0");
            if (paiementProgress != null) paiementProgress.setProgress(0);
        }
    }

    /**
     * Valide l'encaissement
     */
    private void validateEncaissement() {
        try {
            BigDecimal encaisse = new BigDecimal(montantEncaisseField.getText());

            if (statusLabel != null) {
                if (encaisse.compareTo(montantAmendeTotal) > 0) {
                    statusLabel.setText("Attention : Montant encaissé supérieur à l'amende");
                    statusLabel.setStyle("-fx-text-fill: orange;");
                } else {
                    statusLabel.setText("");
                }
            }
        } catch (Exception e) {
            // Ignorer les erreurs de parsing
        }
    }

    /**
     * Handler pour le bouton Valider
     */
    @FXML
    private void handleValider() {
        validerCreation();
    }

    /**
     * Handler pour le bouton Annuler
     */
    @FXML
    private void handleAnnuler() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * Valide et crée l'affaire avec encaissement
     */
    private void validerCreation() {
        // Valider le formulaire
        List<String> errors = validateForm();
        if (!errors.isEmpty()) {
            AlertUtil.showError("Erreur de validation",
                    "Veuillez corriger les erreurs suivantes :",
                    String.join("\n", errors));
            return;
        }

        // Désactiver les boutons pendant la création
        validerBtn.setDisable(true);
        annulerBtn.setDisable(true);
        statusLabel.setText("Création en cours...");
        statusLabel.setStyle("-fx-text-fill: blue;");

        // Créer l'affaire et l'encaissement
        Task<Void> createTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Créer l'affaire
                Affaire affaire = new Affaire();
                affaire.setNumeroAffaire(numeroAffaireField.getText());
                affaire.setDateCreation(dateCreationPicker.getValue());
                affaire.setContrevenant(contrevenantCombo.getValue());
                affaire.setStatut(StatutAffaire.OUVERTE);

                // Infraction
                if (contraventionCombo.getValue() != null) {
                    // L'affaire a maintenant une liste de contraventions, pas une seule
                    List<Contravention> contraventions = new ArrayList<>();
                    contraventions.add(contraventionCombo.getValue());
                    affaire.setContraventions(contraventions);
                } else {
                    // Créer une contravention libre
                    Contravention contraventionLibre = new Contravention();
                    contraventionLibre.setDescription(contraventionLibreField.getText());
                    List<Contravention> contraventions = new ArrayList<>();
                    contraventions.add(contraventionLibre);
                    affaire.setContraventions(contraventions);
                }

                affaire.setMontantAmendeTotal(montantAmendeTotal);

                // Centre n'est pas directement sur l'affaire, il est lié via le service ou le bureau
                affaire.setService(serviceCombo.getValue());
                affaire.setBureau(bureauCombo.getValue());

                // Créer l'encaissement
                Encaissement encaissement = new Encaissement();
                encaissement.setReference(numeroEncaissementField.getText());
                encaissement.setDateEncaissement(dateEncaissementPicker.getValue());
                encaissement.setMontantEncaisse(new BigDecimal(montantEncaisseField.getText()));
                encaissement.setModeReglement(modeReglementCombo.getValue());

                if (modeReglementCombo.getValue() == ModeReglement.CHEQUE) {
                    encaissement.setBanqueId(banqueCombo.getValue().getId());
                    encaissement.setNumeroPiece(numeroChequeField.getText());
                }

                // Préparer les acteurs
                List<AffaireActeur> acteurs = new ArrayList<>();
                for (ActeurViewModel vm : acteursList) {
                    AffaireActeur acteur = new AffaireActeur();
                    acteur.setAgent(vm.getAgent());
                    acteur.setRoleSurAffaire(vm.getRole());
                    acteurs.add(acteur);
                }

                // Indicateur
                if (indicateurCheck.isSelected()) {
                    if (indicateurAgentCombo.getValue() != null) {
                        AffaireActeur indicateur = new AffaireActeur();
                        indicateur.setAgent(indicateurAgentCombo.getValue());
                        indicateur.setRoleSurAffaire("INDICATEUR");
                        acteurs.add(indicateur);
                    } else if (!nomIndicateurField.getText().isEmpty()) {
                        // Note: Le nom de l'indicateur externe n'est pas stocké directement dans l'affaire
                        // Vous devrez peut-être ajouter cette propriété au modèle Affaire si nécessaire
                    }
                }

                // Sauvegarder via le service
                createdAffaire = affaireService.createAffaireAvecEncaissement(affaire, encaissement, acteurs);

                return null;
            }
        };

        createTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                statusLabel.setText("Affaire créée avec succès !");
                statusLabel.setStyle("-fx-text-fill: green;");

                AlertUtil.showSuccess("Succès",
                        "L'affaire " + createdAffaire.getNumeroAffaire() + " a été créée avec succès.");

                closeDialog();
            });
        });

        createTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                validerBtn.setDisable(false);
                annulerBtn.setDisable(false);
                statusLabel.setText("Erreur lors de la création");
                statusLabel.setStyle("-fx-text-fill: red;");

                logger.error("Erreur création affaire", createTask.getException());
                AlertUtil.showError("Erreur",
                        "Impossible de créer l'affaire",
                        createTask.getException().getMessage());
            });
        });

        new Thread(createTask).start();
    }

    /**
     * Validation du formulaire
     */
    private List<String> validateForm() {
        List<String> errors = new ArrayList<>();

        // Contrevenant
        if (contrevenantCombo.getValue() == null) {
            errors.add("- Sélectionnez ou créez un contrevenant");
        }

        // Infraction
        if (contraventionCombo.getValue() == null && contraventionLibreField.getText().isEmpty()) {
            errors.add("- Sélectionnez ou saisissez une contravention");
        }

        // Montants
        if (montantAmendeTotal.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("- Le montant de l'amende doit être sélectionné");
        }

        try {
            BigDecimal encaisse = new BigDecimal(montantEncaisseField.getText());
            if (encaisse.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("- Le montant encaissé doit être positif");
            }
        } catch (Exception e) {
            errors.add("- Montant encaissé invalide");
        }

        // Centre
        if (centreCombo.getValue() == null) {
            errors.add("- Sélectionnez un centre");
        }

        // Acteurs - NOUVELLE VALIDATION
        if (acteursList.isEmpty()) {
            errors.add("- Sélectionnez au moins un acteur (saisissant ou chef)");
        }

        // Mode chèque
        if (modeReglementCombo.getValue() == ModeReglement.CHEQUE) {
            if (banqueCombo.getValue() == null) {
                errors.add("- Sélectionnez une banque pour le chèque");
            }
            if (numeroChequeField.getText().isEmpty()) {
                errors.add("- Saisissez le numéro de chèque");
            }
        }

        return errors;
    }

    /**
     * Ferme le dialogue
     */
    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * Retourne l'affaire créée
     */
    public Affaire getCreatedAffaire() {
        return createdAffaire;
    }
}