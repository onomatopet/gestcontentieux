package com.regulation.contentieux.controller;

import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.*;
import com.regulation.contentieux.service.*;
import com.regulation.contentieux.util.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
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
        setupContraventionHandlers(); // Pour gérer les montants modifiables
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

        // Ajouter un délai pour le débogage après le chargement
        Platform.runLater(() -> {
            try {
                Thread.sleep(1000); // Attendre 1 seconde
            } catch (InterruptedException e) {
                // Ignorer
            }
            debugComboBoxes();
        });
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
        Dialog<AgentRoleSelection> dialog = new Dialog<>();
        dialog.setTitle("Ajouter un agent");
        dialog.setHeaderText("Sélectionnez un agent et son(ses) rôle(s)");

        // Boutons
        ButtonType addButtonType = new ButtonType("Ajouter", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Contenu
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // TextField pour la recherche
        TextField searchField = new TextField();
        searchField.setPromptText("Rechercher par nom ou code (min. 2 caractères)");
        searchField.setPrefWidth(300);

        // ListView pour afficher les résultats
        ListView<Agent> agentListView = new ListView<>();
        agentListView.setPrefHeight(200);
        agentListView.setCellFactory(lv -> new ListCell<Agent>() {
            @Override
            protected void updateItem(Agent agent, boolean empty) {
                super.updateItem(agent, empty);
                if (empty || agent == null) {
                    setText(null);
                } else {
                    setText(agent.getCodeAgent() + " - " + agent.getNom() + " " + agent.getPrenom());
                }
            }
        });

        // Label pour afficher la sélection
        Label selectionLabel = new Label("Aucun agent sélectionné");
        selectionLabel.setStyle("-fx-font-weight: bold;");

        // CheckBox pour les rôles
        CheckBox saisissantCheck = new CheckBox("Saisissant");
        CheckBox chefCheck = new CheckBox("Chef");

        // Organisation du layout
        grid.add(new Label("Recherche:"), 0, 0);
        grid.add(searchField, 1, 0, 2, 1);
        grid.add(new Label("Résultats:"), 0, 1);
        grid.add(agentListView, 1, 1, 2, 2);
        grid.add(new Label("Sélection:"), 0, 3);
        grid.add(selectionLabel, 1, 3, 2, 1);
        grid.add(new Label("Rôle(s):"), 0, 4);
        grid.add(saisissantCheck, 1, 4);
        grid.add(chefCheck, 2, 4);

        dialog.getDialogPane().setContent(grid);

        // Variables pour la gestion
        final Agent[] selectedAgent = {null};
        final Timeline searchTimer = new Timeline(new KeyFrame(Duration.millis(300), ae -> {}));
        searchTimer.setCycleCount(1);

        // Gestion de la sélection dans la liste
        agentListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedAgent[0] = newVal;
                selectionLabel.setText(newVal.getCodeAgent() + " - " + newVal.getNom() + " " + newVal.getPrenom());
                selectionLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
            }
        });

        // Recherche avec timer
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            searchTimer.stop();

            if (newText != null && newText.trim().length() >= 2) {
                searchTimer.setOnFinished(event -> {
                    Task<List<Agent>> searchTask = new Task<>() {
                        @Override
                        protected List<Agent> call() throws Exception {
                            return agentService.searchAgents(newText.trim(), 50);
                        }
                    };

                    searchTask.setOnSucceeded(e -> {
                        Platform.runLater(() -> {
                            agentListView.getItems().clear();
                            agentListView.getItems().addAll(searchTask.getValue());
                            if (!searchTask.getValue().isEmpty()) {
                                agentListView.getSelectionModel().selectFirst();
                            }
                        });
                    });

                    searchTask.setOnFailed(e -> {
                        logger.error("Erreur recherche agents", searchTask.getException());
                    });

                    new Thread(searchTask).start();
                });
                searchTimer.play();
            } else {
                agentListView.getItems().clear();
                selectedAgent[0] = null;
                selectionLabel.setText("Aucun agent sélectionné");
                selectionLabel.setStyle("-fx-font-weight: bold;");
            }
        });

        // Validation du bouton
        Node addButton = dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(true);

        ChangeListener<Object> validationListener = (obs, old, newVal) -> {
            addButton.setDisable(selectedAgent[0] == null ||
                    (!saisissantCheck.isSelected() && !chefCheck.isSelected()));
        };

        agentListView.getSelectionModel().selectedItemProperty().addListener(validationListener);
        saisissantCheck.selectedProperty().addListener(validationListener);
        chefCheck.selectedProperty().addListener(validationListener);

        // Focus initial
        Platform.runLater(() -> searchField.requestFocus());

        // Convertir le résultat
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType && selectedAgent[0] != null) {
                return new AgentRoleSelection(selectedAgent[0],
                        saisissantCheck.isSelected(),
                        chefCheck.isSelected());
            }
            return null;
        });

        Optional<AgentRoleSelection> result = dialog.showAndWait();

        result.ifPresent(selection -> {
            // Ajouter comme saisissant si coché
            if (selection.isSaisissant) {
                if (!isAgentAlreadyInRole(selection.agent, "SAISISSANT")) {
                    acteursList.add(new ActeurViewModel(selection.agent, "SAISISSANT"));
                } else {
                    AlertUtil.showWarning("Doublon",
                            "Cet agent est déjà saisissant dans cette affaire.");
                }
            }

            // Ajouter comme chef si coché
            if (selection.isChef) {
                if (!isAgentAlreadyInRole(selection.agent, "CHEF")) {
                    acteursList.add(new ActeurViewModel(selection.agent, "CHEF"));
                } else {
                    AlertUtil.showWarning("Doublon",
                            "Cet agent est déjà chef dans cette affaire.");
                }
            }
        });
    }

    /**
     * Classe interne pour stocker la sélection
     */
    private static class AgentRoleSelection {
        final Agent agent;
        final boolean isSaisissant;
        final boolean isChef;

        AgentRoleSelection(Agent agent, boolean isSaisissant, boolean isChef) {
            this.agent = agent;
            this.isSaisissant = isSaisissant;
            this.isChef = isChef;
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
     * Configuration améliorée de la gestion des contraventions avec montant modifiable
     * Cette méthode remplace la configuration existante dans setupValidation()
     */
    private void setupContraventionHandlers() {
        // Si le bouton ajouter contravention existe
        if (ajouterContraventionBtn != null) {
            ajouterContraventionBtn.setOnAction(e -> {
                if (contraventionCombo.getValue() != null) {
                    showMontantDialog(contraventionCombo.getValue());
                } else if (!contraventionLibreField.getText().trim().isEmpty()) {
                    // Pour une contravention libre, créer un objet temporaire
                    Contravention contraventionLibre = new Contravention();
                    contraventionLibre.setDescription(contraventionLibreField.getText());
                    contraventionLibre.setMontant(BigDecimal.ZERO);
                    showMontantDialog(contraventionLibre);
                } else {
                    AlertUtil.showWarning("Attention", "Veuillez sélectionner ou saisir une contravention");
                }
            });
        }

        // Listener pour la sélection de contravention
        if (contraventionCombo != null) {
            contraventionCombo.valueProperty().addListener((obs, old, val) -> {
                if (val != null) {
                    // Ne plus mettre à jour automatiquement le montant total
                    // Attendre que l'utilisateur clique sur "Ajouter"
                    ajouterContraventionBtn.setDisable(false);
                } else {
                    ajouterContraventionBtn.setDisable(true);
                }
            });
        }

        // Listener pour la contravention libre
        if (contraventionLibreField != null) {
            contraventionLibreField.textProperty().addListener((obs, old, newVal) -> {
                if (newVal != null && !newVal.trim().isEmpty()) {
                    ajouterContraventionBtn.setDisable(false);
                    contraventionCombo.setValue(null); // Désélectionner la contravention
                } else if (contraventionCombo.getValue() == null) {
                    ajouterContraventionBtn.setDisable(true);
                }
            });
        }
    }

    /**
     * Méthode alternative : Afficher une boîte de dialogue pour modifier le montant
     */
    private void showMontantDialog(Contravention contravention) {
        Dialog<BigDecimal> dialog = new Dialog<>();
        dialog.setTitle("Montant de l'amende");
        dialog.setHeaderText("Définir le montant pour : " + contravention.getDescription());

        // Boutons
        ButtonType validerType = new ButtonType("Valider", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(validerType, ButtonType.CANCEL);

        // Contenu
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField montantField = new TextField();
        if (contravention.getMontant() != null && contravention.getMontant().compareTo(BigDecimal.ZERO) > 0) {
            montantField.setText(contravention.getMontant().toString());
        }
        montantField.setPromptText("Montant en FCFA");

        // Ajouter un label d'information
        Label infoLabel = new Label("Le montant peut être négocié et ajusté selon l'accord avec le contrevenant");
        infoLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11px; -fx-wrap-text: true;");
        infoLabel.setMaxWidth(300);

        grid.add(new Label("Montant négocié:"), 0, 0);
        grid.add(montantField, 1, 0);
        grid.add(infoLabel, 0, 1, 2, 1);

        dialog.getDialogPane().setContent(grid);

        // Validation
        Node validerButton = dialog.getDialogPane().lookupButton(validerType);
        validerButton.setDisable(true);

        montantField.textProperty().addListener((obs, old, newVal) -> {
            try {
                BigDecimal montant = new BigDecimal(newVal.replace(" ", "").replace(",", "."));
                validerButton.setDisable(montant.compareTo(BigDecimal.ZERO) <= 0);
                montantField.setStyle("");
            } catch (Exception e) {
                validerButton.setDisable(true);
                montantField.setStyle("-fx-border-color: red;");
            }
        });

        // Focus
        Platform.runLater(() -> {
            montantField.requestFocus();
            montantField.selectAll();
        });

        // Convertir le résultat
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == validerType) {
                try {
                    return new BigDecimal(montantField.getText().replace(" ", "").replace(",", "."));
                } catch (Exception e) {
                    return BigDecimal.ZERO;
                }
            }
            return null;
        });

        Optional<BigDecimal> result = dialog.showAndWait();

        result.ifPresent(montant -> {
            montantAmendeTotal = montant;
            updateMontantTotalLabel();
            updateSoldeRestant();

            // Afficher un message de confirmation
            statusLabel.setText("Montant de l'amende défini : " + CurrencyFormatter.format(montant));
            statusLabel.setStyle("-fx-text-fill: green;");

            // Après 3 secondes, effacer le message
            Timeline timeline = new Timeline(new KeyFrame(
                    Duration.seconds(3),
                    ae -> {
                        statusLabel.setText("");
                    }
            ));
            timeline.play();
        });
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

        // SUPPRIMER ou COMMENTER ce listener qui écrase le montant négocié
    /*
    if (contraventionCombo != null) {
        contraventionCombo.valueProperty().addListener((obs, old, val) -> {
            if (val != null) {
                montantAmendeTotal = val.getMontant();
                updateMontantTotalLabel();
                updateSoldeRestant();
            }
        });
    }
    */
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
     * Ajoutez cette méthode dans votre classe AffaireEncaissementController
     */
    private void setupComboBoxes() {
        // Configuration du StringConverter pour Contrevenant
        contrevenantCombo.setConverter(new StringConverter<Contrevenant>() {
            @Override
            public String toString(Contrevenant contrevenant) {
                return contrevenant != null ?
                        contrevenant.getNomComplet() + " (" + contrevenant.getCode() + ")" : "";
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
                        contravention.getLibelle() + " - " + CurrencyFormatter.format(contravention.getMontant()) : "";
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
                return service != null ?
                        service.getCodeService() + " - " + service.getNomService() : "";
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
                return bureau != null ?
                        bureau.getCodeBureau() + " - " + bureau.getNomBureau() : "";
            }

            @Override
            public Bureau fromString(String string) {
                return null;
            }
        });

        // Configuration du StringConverter pour Mode de Règlement
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

        // Listener pour la sélection de contrevenant
        // SUPPRESSION de la mise à jour des champs inexistants
        contrevenantCombo.setOnAction(e -> {
            Contrevenant selected = contrevenantCombo.getValue();
            if (selected != null) {
                logger.debug("Contrevenant sélectionné: {}", selected.getNomComplet());
                // Les champs nomContrevenantField, adresseField et telephoneField
                // n'existent pas dans le FXML, donc on ne peut pas les mettre à jour
            }
        });
    }

    /**
     * Configuration des ComboBox
     */
    private void setupComboBoxesOptimized() {
        // Configuration du StringConverter pour Contrevenant (optimisé)
        contrevenantCombo.setConverter(new StringConverter<Contrevenant>() {
            @Override
            public String toString(Contrevenant contrevenant) {
                if (contrevenant == null) return "";
                return contrevenant.getCode() + " - " + contrevenant.getNomComplet();
            }

            @Override
            public Contrevenant fromString(String string) {
                return null;
            }
        });

        // Configuration CORRIGÉE du StringConverter pour Contravention
        contraventionCombo.setConverter(new StringConverter<Contravention>() {
            @Override
            public String toString(Contravention contravention) {
                if (contravention == null) return "";

                // CORRECTION : Utiliser getLibelle() au lieu de getDescription()
                String code = contravention.getCode() != null ? contravention.getCode() : "???";
                String libelle = contravention.getLibelle() != null ? contravention.getLibelle() : "Sans libellé";

                return code + " - " + libelle;
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
    // Ajoutez ces méthodes dans votre classe AffaireEncaissementController

    /**
     * Méthode alternative pour créer un contrevenant rapidement
     */
    private void showQuickContrevenantDialog() {
        Dialog<Contrevenant> dialog = new Dialog<>();
        dialog.setTitle("Nouveau contrevenant rapide");
        dialog.setHeaderText("Créer un contrevenant rapidement");

        // Boutons
        ButtonType createType = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        // Formulaire
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 100, 10, 10));

        TextField nomField = new TextField();
        nomField.setPromptText("Nom complet");
        TextField adresseField = new TextField();
        adresseField.setPromptText("Adresse");
        TextField telephoneField = new TextField();
        telephoneField.setPromptText("Téléphone");

        grid.add(new Label("Nom complet:"), 0, 0);
        grid.add(nomField, 1, 0);
        grid.add(new Label("Adresse:"), 0, 1);
        grid.add(adresseField, 1, 1);
        grid.add(new Label("Téléphone:"), 0, 2);
        grid.add(telephoneField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        // Validation
        Node createButton = dialog.getDialogPane().lookupButton(createType);
        createButton.setDisable(true);

        nomField.textProperty().addListener((obs, old, newVal) -> {
            createButton.setDisable(newVal.trim().isEmpty());
        });

        Platform.runLater(() -> nomField.requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createType) {
                try {
                    Contrevenant contrevenant = new Contrevenant();
                    contrevenant.setNomComplet(nomField.getText().trim());
                    contrevenant.setAdresse(adresseField.getText().trim());
                    contrevenant.setTelephone(telephoneField.getText().trim());
                    contrevenant.setTypePersonne("Physique");

                    // Sauvegarder
                    Contrevenant saved = contrevenantService.saveContrevenant(contrevenant);
                    logger.info("Contrevenant créé rapidement: {}", saved.getNomComplet());
                    return saved;
                } catch (Exception e) {
                    logger.error("Erreur création contrevenant rapide", e);
                    AlertUtil.showError("Erreur", "Impossible de créer le contrevenant", e.getMessage());
                    return null;
                }
            }
            return null;
        });

        Optional<Contrevenant> result = dialog.showAndWait();

        result.ifPresent(contrevenant -> {
            // Recharger la liste et sélectionner le nouveau
            loadContrevenants();
            Platform.runLater(() -> {
                contrevenantCombo.setValue(contrevenant);
            });
        });
    }

    /**
     * Méthode pour vérifier si les ComboBox sont correctement configurées
     */
    private void debugComboBoxes() {
        logger.info("=== DEBUG COMBOBOXES ===");
        if (contrevenantCombo != null) {
            logger.info("ContrevenantCombo - Items: {}, Converter: {}",
                    contrevenantCombo.getItems().size(),
                    contrevenantCombo.getConverter() != null);
        }

        if (contraventionCombo != null) {
            logger.info("ContraventionCombo - Items: {}, Converter: {}",
                    contraventionCombo.getItems().size(),
                    contraventionCombo.getConverter() != null);
        }

        if (centreCombo != null) {
            logger.info("CentreCombo - Items: {}", centreCombo.getItems().size());
        }

        if (serviceCombo != null) {
            logger.info("ServiceCombo - Items: {}", serviceCombo.getItems().size());
        }

        if (bureauCombo != null) {
            logger.info("BureauCombo - Items: {}", bureauCombo.getItems().size());
        }
    }

    /**
     * Configuration des gestionnaires d'événements
     */
    private void setupEventHandlers() {
        // Bouton nouveau contrevenant (existant)
        if (nouveauContrevenantBtn != null) {
            nouveauContrevenantBtn.setOnAction(e -> showQuickContrevenantDialog());
        }

        // NOUVEAU : Ajouter un bouton de recherche pour les contrevenants
        Button searchContrevenantBtn = new Button("🔍");
        searchContrevenantBtn.setTooltip(new Tooltip("Rechercher un contrevenant"));
        searchContrevenantBtn.setOnAction(e -> showContrevenantSearchDialog());

        // Ajouter le bouton à côté de la ComboBox
        if (contrevenantCombo != null && contrevenantCombo.getParent() instanceof GridPane) {
            GridPane parent = (GridPane) contrevenantCombo.getParent();
            Integer row = GridPane.getRowIndex(contrevenantCombo);
            Integer col = GridPane.getColumnIndex(contrevenantCombo);
            if (row != null && col != null) {
                HBox comboBox = new HBox(5);
                comboBox.getChildren().addAll(contrevenantCombo, searchContrevenantBtn);
                parent.add(comboBox, col, row);
            }
        }

        // NOUVEAU : Ajouter un bouton de recherche pour les contraventions
        Button searchContraventionBtn = new Button("🔍");
        searchContraventionBtn.setTooltip(new Tooltip("Rechercher une contravention"));
        searchContraventionBtn.setOnAction(e -> showContraventionSearchDialog());

        // Ajouter le bouton à côté de la ComboBox
        if (contraventionCombo != null && contraventionCombo.getParent() instanceof GridPane) {
            GridPane parent = (GridPane) contraventionCombo.getParent();
            Integer row = GridPane.getRowIndex(contraventionCombo);
            Integer col = GridPane.getColumnIndex(contraventionCombo);
            if (row != null && col != null) {
                HBox comboBox = new HBox(5);
                comboBox.getChildren().addAll(contraventionCombo, searchContraventionBtn);
                parent.add(comboBox, col, row);
            }
        }

        // Bouton ajouter acteur (existant)
        if (addActeurButton != null) {
            addActeurButton.setOnAction(e -> showAddActeurDialog());
        }
    }

    /**
     * Dialogue de recherche pour les contrevenants
     */
    private void showContrevenantSearchDialog() {
        Dialog<Contrevenant> dialog = new Dialog<>();
        dialog.setTitle("Rechercher un contrevenant");
        dialog.setHeaderText("Tapez au moins 2 caractères pour rechercher");

        ButtonType selectType = new ButtonType("Sélectionner", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(selectType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);

        TextField searchField = new TextField();
        searchField.setPromptText("Nom ou code du contrevenant...");

        ListView<Contrevenant> listView = new ListView<>();
        listView.setPrefHeight(300);
        listView.setCellFactory(lv -> new ListCell<Contrevenant>() {
            @Override
            protected void updateItem(Contrevenant item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getCode() + " - " + item.getNomComplet());
                }
            }
        });

        Label countLabel = new Label("Entrez au moins 2 caractères");

        content.getChildren().addAll(
                new Label("Recherche:"),
                searchField,
                countLabel,
                listView
        );

        dialog.getDialogPane().setContent(content);

        // Timer pour recherche différée
        Timeline searchTimer = new Timeline(new KeyFrame(Duration.millis(300), e -> {}));
        searchTimer.setCycleCount(1);

        // Recherche
        searchField.textProperty().addListener((obs, old, newVal) -> {
            searchTimer.stop();

            if (newVal != null && newVal.trim().length() >= 2) {
                searchTimer.setOnFinished(event -> {
                    String search = newVal.trim().toLowerCase();

                    Task<List<Contrevenant>> searchTask = new Task<>() {
                        @Override
                        protected List<Contrevenant> call() {
                            return contrevenantService.getAllContrevenants().stream()
                                    .filter(c ->
                                            c.getCode().toLowerCase().contains(search) ||
                                                    c.getNomComplet().toLowerCase().contains(search))
                                    .limit(100)
                                    .collect(Collectors.toList());
                        }
                    };

                    searchTask.setOnSucceeded(e -> {
                        List<Contrevenant> results = searchTask.getValue();
                        listView.getItems().setAll(results);
                        countLabel.setText(results.size() + " résultats trouvés");
                    });

                    new Thread(searchTask).start();
                });

                searchTimer.play();
            } else {
                listView.getItems().clear();
                countLabel.setText("Entrez au moins 2 caractères");
            }
        });

        // Validation
        Node selectButton = dialog.getDialogPane().lookupButton(selectType);
        selectButton.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            selectButton.setDisable(newVal == null);
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == selectType) {
                return listView.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        Platform.runLater(() -> searchField.requestFocus());

        Optional<Contrevenant> result = dialog.showAndWait();

        result.ifPresent(contrevenant -> {
            contrevenantCombo.setValue(contrevenant);
            // Les listeners existants mettront à jour les champs
        });
    }

    /**
     * Dialogue de recherche pour les contraventions
     */
    private void showContraventionSearchDialog() {
        Dialog<Contravention> dialog = new Dialog<>();
        dialog.setTitle("Rechercher une contravention");
        dialog.setHeaderText("Tapez au moins 2 caractères pour rechercher");

        ButtonType selectType = new ButtonType("Sélectionner", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(selectType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setPrefWidth(600);

        TextField searchField = new TextField();
        searchField.setPromptText("Code ou libellé de la contravention...");

        ListView<Contravention> listView = new ListView<>();
        listView.setPrefHeight(300);
        listView.setCellFactory(lv -> new ListCell<Contravention>() {
            @Override
            protected void updateItem(Contravention item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String montant = item.getMontant() != null ?
                            " - " + CurrencyFormatter.format(item.getMontant()) + " FCFA" : "";
                    setText(item.getCode() + " - " + item.getLibelle() + montant);
                }
            }
        });

        Label countLabel = new Label("Entrez au moins 2 caractères");

        content.getChildren().addAll(
                new Label("Recherche:"),
                searchField,
                countLabel,
                listView
        );

        dialog.getDialogPane().setContent(content);

        // Timer pour recherche différée
        Timeline searchTimer = new Timeline(new KeyFrame(Duration.millis(300), e -> {}));
        searchTimer.setCycleCount(1);

        // Recherche
        searchField.textProperty().addListener((obs, old, newVal) -> {
            searchTimer.stop();

            if (newVal != null && newVal.trim().length() >= 2) {
                searchTimer.setOnFinished(event -> {
                    String search = newVal.trim().toLowerCase();

                    Task<List<Contravention>> searchTask = new Task<>() {
                        @Override
                        protected List<Contravention> call() {
                            return contraventionService.getAllContraventions().stream()
                                    .filter(c ->
                                            c.getCode().toLowerCase().contains(search) ||
                                                    c.getLibelle().toLowerCase().contains(search))
                                    .limit(100)
                                    .collect(Collectors.toList());
                        }
                    };

                    searchTask.setOnSucceeded(e -> {
                        List<Contravention> results = searchTask.getValue();
                        listView.getItems().setAll(results);
                        countLabel.setText(results.size() + " résultats trouvés");
                    });

                    new Thread(searchTask).start();
                });

                searchTimer.play();
            } else {
                listView.getItems().clear();
                countLabel.setText("Entrez au moins 2 caractères");
            }
        });

        // Validation
        Node selectButton = dialog.getDialogPane().lookupButton(selectType);
        selectButton.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            selectButton.setDisable(newVal == null);
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == selectType) {
                return listView.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        Platform.runLater(() -> searchField.requestFocus());

        Optional<Contravention> result = dialog.showAndWait();

        result.ifPresent(contravention -> {
            // Si vous voulez afficher le dialogue du montant directement
            showMontantDialog(contravention);
        });
    }

    /**
     * Version améliorée de loadContrevenants() avec débogage et gestion d'erreur
     */
    private void loadContrevenants() {
        logger.info("Chargement limité des contrevenants...");

        Task<List<Contrevenant>> task = new Task<>() {
            @Override
            protected List<Contrevenant> call() throws Exception {
                // Ne charger que les 50 derniers contrevenants
                List<Contrevenant> all = contrevenantService.getAllContrevenants();
                return all.stream()
                        .sorted((a, b) -> b.getId().compareTo(a.getId())) // Plus récents d'abord
                        .limit(50)
                        .collect(Collectors.toList());
            }
        };

        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                contrevenantCombo.getItems().clear();
                contrevenantCombo.getItems().addAll(task.getValue());
                contrevenantCombo.setPromptText("50 derniers (utilisez 🔍 pour plus)");
            });
        });

        new Thread(task).start();
    }

    /**
     * Charge les contraventions
     */
    private void loadContraventions() {
        logger.info("Chargement limité des contraventions...");

        Task<List<Contravention>> task = new Task<>() {
            @Override
            protected List<Contravention> call() throws Exception {
                // Ne charger que les 50 contraventions les plus courantes
                return contraventionService.getAllContraventions().stream()
                        .limit(50)
                        .collect(Collectors.toList());
            }
        };

        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                contraventionCombo.getItems().clear();
                contraventionCombo.getItems().addAll(task.getValue());
                contraventionCombo.setPromptText("50 plus courantes (utilisez 🔍 pour plus)");
            });
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

        // Infraction - Validation modifiée pour ne plus utiliser contraventionLibreField
        if (contraventionCombo.getValue() == null) {
            errors.add("- Sélectionnez une contravention");
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