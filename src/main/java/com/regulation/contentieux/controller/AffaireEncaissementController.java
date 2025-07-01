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
import org.slf4j.LoggerFactory;

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
    @FXML private Button newContrevenantBtn;
    @FXML private TextField nomContrevenantField;
    @FXML private TextField adresseField;
    @FXML private TextField telephoneField;

    // Infraction
    @FXML private ComboBox<Contravention> contraventionCombo;
    @FXML private TextField contraventionLibreField;
    @FXML private TextField montantAmendeField;
    @FXML private ComboBox<Centre> centreCombo;
    @FXML private ComboBox<Service> serviceCombo;
    @FXML private ComboBox<Bureau> bureauCombo;

    // Acteurs - NOUVEAU
    @FXML private TableView<ActeurViewModel> acteursTableView;
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

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/affaire-encaissement-dialog.fxml"));
            loader.setController(this);

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
        // Configuration de la TableView
        acteursTableView.setItems(acteursList);

        // Colonne Code
        TableColumn<ActeurViewModel, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(new PropertyValueFactory<>("codeAgent"));
        codeCol.setPrefWidth(100);

        // Colonne Nom
        TableColumn<ActeurViewModel, String> nomCol = new TableColumn<>("Nom de l'agent");
        nomCol.setCellValueFactory(new PropertyValueFactory<>("nomAgent"));
        nomCol.setPrefWidth(250);

        // Colonne Rôle
        TableColumn<ActeurViewModel, String> roleCol = new TableColumn<>("Rôle");
        roleCol.setCellValueFactory(new PropertyValueFactory<>("role"));
        roleCol.setPrefWidth(120);

        // Colonne Actions
        TableColumn<ActeurViewModel, Void> actionCol = new TableColumn<>("Action");
        actionCol.setPrefWidth(80);

        Callback<TableColumn<ActeurViewModel, Void>, TableCell<ActeurViewModel, Void>> cellFactory =
                new Callback<TableColumn<ActeurViewModel, Void>, TableCell<ActeurViewModel, Void>>() {
                    @Override
                    public TableCell<ActeurViewModel, Void> call(final TableColumn<ActeurViewModel, Void> param) {
                        final TableCell<ActeurViewModel, Void> cell = new TableCell<ActeurViewModel, Void>() {
                            private final Button btn = new Button("Retirer");
                            {
                                btn.setOnAction((ActionEvent event) -> {
                                    ActeurViewModel data = getTableView().getItems().get(getIndex());
                                    acteursList.remove(data);
                                });
                                btn.setStyle("-fx-font-size: 11px; -fx-padding: 2 5;");
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
                        };
                        return cell;
                    }
                };

        actionCol.setCellFactory(cellFactory);

        acteursTableView.getColumns().clear();
        acteursTableView.getColumns().addAll(codeCol, nomCol, roleCol, actionCol);
    }

    /**
     * Affiche le dialogue d'ajout d'acteur
     */
    @FXML
    private void showAddActeurDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Ajouter un acteur");
        dialog.setHeaderText("Sélectionner un agent et son rôle");
        dialog.initOwner(dialogStage);

        // Créer le contenu du dialogue
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // ComboBox éditable pour la recherche
        ComboBox<Agent> agentCombo = new ComboBox<>();
        agentCombo.setEditable(true);
        agentCombo.setPrefWidth(300);
        agentCombo.setPromptText("Rechercher un agent...");

        // Charger tous les agents actifs
        List<Agent> allAgents = agentService.findActiveAgents();
        ObservableList<Agent> agentsList = FXCollections.observableArrayList(allAgents);
        agentCombo.setItems(agentsList);

        // Configurer la recherche
        agentCombo.setConverter(new StringConverter<Agent>() {
            @Override
            public String toString(Agent agent) {
                return agent != null ?
                        agent.getCodeAgent() + " - " + agent.getNom() + " " + agent.getPrenom() : "";
            }

            @Override
            public Agent fromString(String string) {
                return null;
            }
        });

        // Auto-complétion
        agentCombo.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty()) {
                ObservableList<Agent> filteredList = FXCollections.observableArrayList(
                        allAgents.stream()
                                .filter(agent -> {
                                    String searchText = newValue.toLowerCase();
                                    return agent.getCodeAgent().toLowerCase().contains(searchText) ||
                                            agent.getNom().toLowerCase().contains(searchText) ||
                                            agent.getPrenom().toLowerCase().contains(searchText);
                                })
                                .collect(Collectors.toList())
                );
                agentCombo.setItems(filteredList);
                if (!filteredList.isEmpty()) {
                    agentCombo.show();
                }
            } else {
                agentCombo.setItems(FXCollections.observableArrayList(allAgents));
            }
        });

        // Checkboxes pour les rôles
        CheckBox saisissantCheck = new CheckBox("Saisissant");
        CheckBox chefCheck = new CheckBox("Chef");

        // Layout
        grid.add(new Label("Agent:"), 0, 0);
        grid.add(agentCombo, 1, 0, 2, 1);

        grid.add(new Label("Rôle(s):"), 0, 1);
        grid.add(saisissantCheck, 1, 1);
        grid.add(chefCheck, 2, 1);

        dialog.getDialogPane().setContent(grid);

        // Boutons
        ButtonType choisirButtonType = new ButtonType("Choisir", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(choisirButtonType, ButtonType.CANCEL);

        // Validation
        Button choisirButton = (Button) dialog.getDialogPane().lookupButton(choisirButtonType);
        choisirButton.setDisable(true);

        // Activer le bouton Choisir seulement si un agent est sélectionné et au moins un rôle coché
        Runnable validation = () -> {
            boolean valid = agentCombo.getValue() != null &&
                    (saisissantCheck.isSelected() || chefCheck.isSelected());
            choisirButton.setDisable(!valid);
        };

        agentCombo.valueProperty().addListener((obs, oldVal, newVal) -> validation.run());
        saisissantCheck.selectedProperty().addListener((obs, oldVal, newVal) -> validation.run());
        chefCheck.selectedProperty().addListener((obs, oldVal, newVal) -> validation.run());

        // Focus sur la combo
        Platform.runLater(() -> agentCombo.requestFocus());

        // Traiter le résultat
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == choisirButtonType) {
            Agent selectedAgent = agentCombo.getValue();

            // Ajouter comme saisissant si coché
            if (saisissantCheck.isSelected()) {
                if (!isAgentAlreadyInRole(selectedAgent, "SAISISSANT")) {
                    acteursList.add(new ActeurViewModel(selectedAgent, "SAISISSANT"));
                } else {
                    AlertUtil.showWarning("Doublon",
                            "Cet agent est déjà saisissant dans cette affaire.");
                }
            }

            // Ajouter comme chef si coché
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
        // Validation numérique pour les montants
        montantAmendeField.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) {
                montantAmendeField.setText(old);
            }
            updateSoldeRestant();
        });

        montantEncaisseField.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) {
                montantEncaisseField.setText(old);
            }
            updateSoldeRestant();
            validateEncaissement();
        });

        // Mode de règlement
        modeReglementCombo.valueProperty().addListener((obs, old, val) -> {
            chequeSection.setVisible(val == ModeReglement.CHEQUE);
            chequeSection.setManaged(val == ModeReglement.CHEQUE);
        });

        // Indicateur
        indicateurCheck.selectedProperty().addListener((obs, old, val) -> {
            nomIndicateurField.setDisable(!val);
            indicateurAgentCombo.setDisable(!val);
        });
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
                return contravention != null ?
                        contravention.getCode() + " - " + contravention.getLibelle() : "";
            }

            @Override
            public Contravention fromString(String string) {
                return null;
            }
        });

        // Mode de règlement
        modeReglementCombo.setItems(FXCollections.observableArrayList(ModeReglement.values()));
        modeReglementCombo.setValue(ModeReglement.ESPECES);

        // Configuration des autres ComboBox
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
        newContrevenantBtn.setOnAction(e -> {
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

        // Bouton ajouter acteur
        if (addActeurButton != null) {
            addActeurButton.setOnAction(e -> showAddActeurDialog());
        }

        // Boutons de contrôle
        validerBtn.setOnAction(e -> validerCreation());
        annulerBtn.setOnAction(e -> dialogStage.close());
    }

    /**
     * Charge les contrevenants
     */
    private void loadContrevenants() {
        Task<List<Contrevenant>> task = new Task<>() {
            @Override
            protected List<Contrevenant> call() throws Exception {
                return contrevenantService.getAllContrevenants(1, Integer.MAX_VALUE);
            }
        };

        task.setOnSucceeded(e -> {
            List<Contrevenant> contrevenants = task.getValue();
            contrevenantCombo.setItems(FXCollections.observableArrayList(contrevenants));
        });

        new Thread(task).start();
    }

    /**
     * Mise à jour du solde restant et de la barre de progression
     */
    private void updateSoldeRestant() {
        try {
            BigDecimal amende = new BigDecimal(montantAmendeField.getText().trim());
            BigDecimal encaisse = new BigDecimal(montantEncaisseField.getText().trim());
            BigDecimal solde = amende.subtract(encaisse);

            soldeRestantLabel.setText(CurrencyFormatter.format(solde));

            // Barre de progression
            double progress = encaisse.doubleValue() / amende.doubleValue();
            paiementProgress.setProgress(Math.min(progress, 1.0));

            // Style selon le statut
            if (progress >= 1.0) {
                paiementProgress.setStyle("-fx-accent: green;");
                soldeRestantLabel.setStyle("-fx-text-fill: green;");
            } else if (progress >= 0.5) {
                paiementProgress.setStyle("-fx-accent: orange;");
                soldeRestantLabel.setStyle("-fx-text-fill: orange;");
            } else {
                paiementProgress.setStyle("-fx-accent: red;");
                soldeRestantLabel.setStyle("-fx-text-fill: red;");
            }

        } catch (NumberFormatException e) {
            soldeRestantLabel.setText("0 FCFA");
            paiementProgress.setProgress(0);
        }
    }

    /**
     * Validation du montant encaissé
     */
    private void validateEncaissement() {
        try {
            BigDecimal amende = new BigDecimal(montantAmendeField.getText().trim());
            BigDecimal encaisse = new BigDecimal(montantEncaisseField.getText().trim());

            if (encaisse.compareTo(amende) > 0) {
                montantEncaisseField.setStyle("-fx-border-color: red;");
                statusLabel.setText("⚠️ Le montant encaissé ne peut pas dépasser l'amende");
                statusLabel.setStyle("-fx-text-fill: red;");
            } else {
                montantEncaisseField.setStyle("");
                statusLabel.setText("");
            }

        } catch (NumberFormatException e) {
            // Ignorer
        }
    }

    /**
     * Chargement des données initiales
     */
    private void loadInitialData() {
        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Chargement complet des données
                List<Contrevenant> contrevenants = contrevenantService.getAllContrevenants(1, Integer.MAX_VALUE);
                List<Contravention> contraventions = contraventionService.getAllContraventions(1, Integer.MAX_VALUE);
                List<Centre> centres = centreService.getAllCentres();
                List<Service> services = serviceService.getAllServices();
                List<Bureau> bureaux = bureauService.getAllBureaux();
                List<Banque> banques = banqueService.getAllBanques();
                List<Agent> agents = agentService.getAllAgents(1, Integer.MAX_VALUE);

                // Générer les numéros
                String numeroAffaire = affaireService.genererNumeroAffaire();
                String numeroEncaissement = encaissementService.generateNextNumeroEncaissement();

                Platform.runLater(() -> {
                    contrevenantCombo.setItems(FXCollections.observableArrayList(contrevenants));
                    contraventionCombo.setItems(FXCollections.observableArrayList(contraventions));
                    centreCombo.setItems(FXCollections.observableArrayList(centres));
                    serviceCombo.setItems(FXCollections.observableArrayList(services));
                    bureauCombo.setItems(FXCollections.observableArrayList(bureaux));
                    banqueCombo.setItems(FXCollections.observableArrayList(banques));

                    // Filtrer les agents actifs pour l'indicateur
                    List<Agent> agentsActifs = agents.stream()
                            .filter(Agent::isActif)
                            .collect(Collectors.toList());

                    indicateurAgentCombo.setItems(FXCollections.observableArrayList(agentsActifs));

                    // Afficher les numéros générés
                    numeroAffaireField.setText(numeroAffaire);
                    numeroEncaissementField.setText(numeroEncaissement);
                });

                return null;
            }
        };

        loadTask.setOnFailed(e -> {
            logger.error("Erreur chargement données", loadTask.getException());
            Platform.runLater(() ->
                    AlertUtil.showError("Erreur", "Erreur lors du chargement des données")
            );
        });

        new Thread(loadTask).start();
    }

    /**
     * Validation et création de l'affaire avec encaissement
     */
    private void validerCreation() {
        if (isCreating) return;

        // Validation complète
        List<String> errors = validateForm();
        if (!errors.isEmpty()) {
            AlertUtil.showError("Erreurs de validation", String.join("\n", errors));
            return;
        }

        isCreating = true;
        validerBtn.setDisable(true);
        statusLabel.setText("Création en cours...");

        Task<Affaire> createTask = new Task<>() {
            @Override
            protected Affaire call() throws Exception {
                // Créer l'affaire
                Affaire affaire = new Affaire();
                affaire.setNumeroAffaire(numeroAffaireField.getText());
                affaire.setDateCreation(dateCreationPicker.getValue());

                // Contrevenant
                if (contrevenantCombo.getValue() != null) {
                    affaire.setContrevenant(contrevenantCombo.getValue());
                    affaire.setContrevenantId(contrevenantCombo.getValue().getId());
                }

                // Montant
                affaire.setMontantAmendeTotal(new BigDecimal(montantAmendeField.getText()));
                affaire.setStatut(StatutAffaire.EN_COURS);

                // Localisation
                if (serviceCombo.getValue() != null) {
                    affaire.setService(serviceCombo.getValue());
                    affaire.setServiceId(serviceCombo.getValue().getId());
                }
                if (bureauCombo.getValue() != null) {
                    affaire.setBureau(bureauCombo.getValue());
                    affaire.setBureauId(bureauCombo.getValue().getId());
                }

                // Gestion de la relation many-to-many avec Centre
                List<AffaireCentre> centresAssocies = new ArrayList<>();
                if (centreCombo.getValue() != null) {
                    AffaireCentre affaireCentre = new AffaireCentre();
                    affaireCentre.setCentreId(centreCombo.getValue().getId());
                    affaireCentre.setCentre(centreCombo.getValue());
                    affaireCentre.setMontantBase(new BigDecimal(montantAmendeField.getText()));
                    affaireCentre.setMontantIndicateur(BigDecimal.ZERO);
                    centresAssocies.add(affaireCentre);
                }
                affaire.setCentresAssocies(centresAssocies);

                // Contraventions
                List<Contravention> contraventions = new ArrayList<>();
                if (contraventionCombo.getValue() != null) {
                    contraventions.add(contraventionCombo.getValue());
                } else if (!contraventionLibreField.getText().isEmpty()) {
                    Contravention libre = new Contravention();
                    libre.setLibelle(contraventionLibreField.getText());
                    libre.setMontant(new BigDecimal(montantAmendeField.getText()));
                    contraventions.add(libre);
                }
                affaire.setContraventions(contraventions);

                // Créer l'encaissement
                Encaissement encaissement = new Encaissement();
                encaissement.setReference(numeroEncaissementField.getText());
                encaissement.setDateEncaissement(dateEncaissementPicker.getValue());
                encaissement.setMontantEncaisse(new BigDecimal(montantEncaisseField.getText()));
                encaissement.setModeReglement(modeReglementCombo.getValue());
                encaissement.setStatut(StatutEncaissement.VALIDE);

                if (modeReglementCombo.getValue() == ModeReglement.CHEQUE) {
                    if (banqueCombo.getValue() != null) {
                        encaissement.setBanqueId(banqueCombo.getValue().getId());
                    }
                    encaissement.setNumeroPiece(numeroChequeField.getText());
                }

                // Créer les acteurs depuis la TableView
                List<AffaireActeur> acteurs = new ArrayList<>();
                for (ActeurViewModel acteurVM : acteursList) {
                    AffaireActeur acteur = new AffaireActeur();
                    acteur.setAgent(acteurVM.getAgent());
                    acteur.setAgentId(acteurVM.getAgent().getId());
                    acteur.setRoleSurAffaire(acteurVM.getRole());
                    acteurs.add(acteur);
                }

                // Gérer l'indicateur
                if (indicateurCheck.isSelected()) {
                    if (indicateurAgentCombo.getValue() != null) {
                        AffaireActeur indicateur = new AffaireActeur();
                        indicateur.setAgent(indicateurAgentCombo.getValue());
                        indicateur.setAgentId(indicateurAgentCombo.getValue().getId());
                        indicateur.setRoleSurAffaire("INDICATEUR");
                        acteurs.add(indicateur);
                    }
                }

                // Appeler le service pour créer le tout
                return affaireService.createAffaireAvecEncaissement(affaire, encaissement, acteurs);
            }
        };

        createTask.setOnSucceeded(e -> {
            createdAffaire = createTask.getValue();
            Platform.runLater(() -> {
                AlertUtil.showSuccess("Succès",
                        String.format("Affaire %s créée avec succès\nEncaissement de %s FCFA enregistré",
                                createdAffaire.getNumeroAffaire(),
                                montantEncaisseField.getText()));
                closeDialog();
            });
        });

        createTask.setOnFailed(e -> {
            isCreating = false;
            validerBtn.setDisable(false);
            statusLabel.setText("");

            Throwable error = createTask.getException();
            logger.error("Erreur lors de la création", error);
            AlertUtil.showError("Erreur", "Impossible de créer l'affaire", error.getMessage());
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
        try {
            BigDecimal amende = new BigDecimal(montantAmendeField.getText());
            if (amende.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("- Le montant de l'amende doit être positif");
            }
        } catch (Exception e) {
            errors.add("- Montant de l'amende invalide");
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