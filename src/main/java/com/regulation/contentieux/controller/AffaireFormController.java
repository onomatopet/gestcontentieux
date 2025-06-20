package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.*;
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
 * Contrôleur pour le formulaire d'affaire - TYPES GÉNÉRIQUES CORRIGÉS
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

    // FXML - Contrevenant et Contravention - TYPES CORRIGÉS
    @FXML private ComboBox<Contrevenant> contrevenantComboBox;
    @FXML private Button newContrevenantButton;
    @FXML private ComboBox<Contravention> contraventionComboBox; // CORRIGÉ: Object -> Contravention
    @FXML private Button newContraventionButton;
    @FXML private VBox contrevenantDetailsBox;
    @FXML private Label contrevenantDetailsLabel;

    // FXML - Organisation - TYPES CORRIGÉS
    @FXML private ComboBox<Bureau> bureauComboBox; // CORRIGÉ: Object -> Bureau
    @FXML private ComboBox<Service> serviceComboBox; // CORRIGÉ: Object -> Service

    // FXML - Agents
    @FXML private Button assignAgentButton;
    @FXML private TableView<AgentAssignmentViewModel> agentsTableView;
    @FXML private TableColumn<AgentAssignmentViewModel, String> agentNomColumn;
    @FXML private TableColumn<AgentAssignmentViewModel, RoleSurAffaire> agentRoleColumn;
    @FXML private TableColumn<AgentAssignmentViewModel, LocalDateTime> agentDateColumn;
    @FXML private TableColumn<AgentAssignmentViewModel, Void> agentActionsColumn;

    // FXML - Actions
    @FXML private Button enregistrerButton;
    @FXML private Button annulerButton;
    @FXML private Button supprimerButton;

    // Données de travail
    private boolean modeCreation = true;
    private Affaire affaireEnCours;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisation AffaireFormController");

        try {
            initialiserComposants();
            configurerEvenements();
            configurerValidation();

            logger.info("✅ AffaireFormController initialisé avec succès");
        } catch (Exception e) {
            logger.error("❌ Erreur lors de l'initialisation d'AffaireFormController", e);
        }
    }

    private void initialiserComposants() {
        // Configuration des ComboBox avec StringConverter appropriés
        configurerComboBoxStatut();
        configurerComboBoxContrevenant();
        configurerComboBoxContravention();
        configurerComboBoxBureau();
        configurerComboBoxService();

        // Configuration du tableau des agents
        configurerTableauAgents();

        // Initialisation du mode création
        changerModeCreation();
    }

    private void configurerComboBoxStatut() {
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

    private void configurerComboBoxContrevenant() {
        contrevenantComboBox.setConverter(new StringConverter<Contrevenant>() {
            @Override
            public String toString(Contrevenant contrevenant) {
                return contrevenant != null ? contrevenant.toString() : "";
            }

            @Override
            public Contrevenant fromString(String string) {
                return null;
            }
        });
    }

    private void configurerComboBoxContravention() {
        contraventionComboBox.setConverter(new StringConverter<Contravention>() {
            @Override
            public String toString(Contravention contravention) {
                return contravention != null ? contravention.toString() : "";
            }

            @Override
            public Contravention fromString(String string) {
                return null;
            }
        });
    }

    private void configurerComboBoxBureau() {
        bureauComboBox.setConverter(new StringConverter<Bureau>() {
            @Override
            public String toString(Bureau bureau) {
                return bureau != null ? bureau.toString() : "";
            }

            @Override
            public Bureau fromString(String string) {
                return null;
            }
        });
    }

    private void configurerComboBoxService() {
        serviceComboBox.setConverter(new StringConverter<Service>() {
            @Override
            public String toString(Service service) {
                return service != null ? service.toString() : "";
            }

            @Override
            public Service fromString(String string) {
                return null;
            }
        });
    }

    private void configurerTableauAgents() {
        // Configuration des colonnes
        agentNomColumn.setCellValueFactory(new PropertyValueFactory<>("nomAgent"));
        agentRoleColumn.setCellValueFactory(new PropertyValueFactory<>("role"));
        agentDateColumn.setCellValueFactory(new PropertyValueFactory<>("dateAssignation"));

        // Colonne Actions
        agentActionsColumn.setCellFactory(new Callback<TableColumn<AgentAssignmentViewModel, Void>, TableCell<AgentAssignmentViewModel, Void>>() {
            @Override
            public TableCell<AgentAssignmentViewModel, Void> call(TableColumn<AgentAssignmentViewModel, Void> param) {
                return new TableCell<AgentAssignmentViewModel, Void>() {
                    private final Button btnSupprimer = new Button("Supprimer");

                    {
                        btnSupprimer.setOnAction(e -> {
                            AgentAssignmentViewModel agent = getTableView().getItems().get(getIndex());
                            supprimerAgent(agent);
                        });
                    }

                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        setGraphic(empty ? null : btnSupprimer);
                    }
                };
            }
        });
    }

    private void configurerEvenements() {
        // Génération automatique du numéro d'affaire
        generateNumeroButton.setOnAction(e -> genererNumeroAffaire());

        // Nouveau contrevenant
        newContrevenantButton.setOnAction(e -> ouvrirFormulaireContrevenant());

        // Nouvelle contravention
        newContraventionButton.setOnAction(e -> ouvrirFormulaireContravention());

        // Assigner agent
        assignAgentButton.setOnAction(e -> ouvrirFormulaireAssignationAgent());

        // Actions principales
        enregistrerButton.setOnAction(e -> enregistrerAffaire());
        annulerButton.setOnAction(e -> annulerFormulaire());
        supprimerButton.setOnAction(e -> supprimerAffaire());

        // Changement de contrevenant
        contrevenantComboBox.setOnAction(e -> afficherDetailsContrevenant());
    }

    private void configurerValidation() {
        // Validation en temps réel
        numeroAffaireField.textProperty().addListener((obs, oldVal, newVal) -> validerFormulaire());
        montantAmendeField.textProperty().addListener((obs, oldVal, newVal) -> validerFormulaire());
        contrevenantComboBox.valueProperty().addListener((obs, oldVal, newVal) -> validerFormulaire());
        contraventionComboBox.valueProperty().addListener((obs, oldVal, newVal) -> validerFormulaire());
    }

    // Méthodes d'action (à implémenter)
    private void genererNumeroAffaire() {
        // TODO: Implémenter la génération automatique
        logger.info("Génération numéro d'affaire");
    }

    private void ouvrirFormulaireContrevenant() {
        // TODO: Ouvrir formulaire contrevenant
        logger.info("Ouverture formulaire contrevenant");
    }

    private void ouvrirFormulaireContravention() {
        // TODO: Ouvrir formulaire contravention
        logger.info("Ouverture formulaire contravention");
    }

    private void ouvrirFormulaireAssignationAgent() {
        // TODO: Ouvrir formulaire assignation agent
        logger.info("Ouverture formulaire assignation agent");
    }

    private void enregistrerAffaire() {
        // TODO: Implémenter la sauvegarde
        logger.info("Enregistrement affaire");
    }

    private void annulerFormulaire() {
        fermerFormulaire();
    }

    private void supprimerAffaire() {
        // TODO: Implémenter la suppression
        logger.info("Suppression affaire");
    }

    private void afficherDetailsContrevenant() {
        Contrevenant contrevenant = contrevenantComboBox.getValue();
        if (contrevenant != null) {
            contrevenantDetailsLabel.setText(
                    String.format("Type: %s | Tél: %s | Email: %s",
                            contrevenant.getTypePersonne(),
                            contrevenant.getTelephone() != null ? contrevenant.getTelephone() : "N/A",
                            contrevenant.getEmail() != null ? contrevenant.getEmail() : "N/A")
            );
            contrevenantDetailsBox.setVisible(true);
        } else {
            contrevenantDetailsBox.setVisible(false);
        }
    }

    private void supprimerAgent(AgentAssignmentViewModel agent) {
        agentsTableView.getItems().remove(agent);
        logger.info("Agent supprimé: {}", agent.getNomAgent());
    }

    private void validerFormulaire() {
        boolean valide = !numeroAffaireField.getText().trim().isEmpty() &&
                !montantAmendeField.getText().trim().isEmpty() &&
                contrevenantComboBox.getValue() != null &&
                contraventionComboBox.getValue() != null;

        enregistrerButton.setDisable(!valide);
    }

    private void changerModeCreation() {
        modeCreation = true;
        modeLabel.setText("CRÉATION");
        formTitleLabel.setText("Nouvelle Affaire");
        supprimerButton.setVisible(false);

        // Valeurs par défaut
        dateCreationPicker.setValue(LocalDate.now());
        statutComboBox.setValue(StatutAffaire.OUVERTE);
    }

    private void changerModeModification(Affaire affaire) {
        modeCreation = false;
        modeLabel.setText("MODIFICATION");
        formTitleLabel.setText("Modifier Affaire");
        supprimerButton.setVisible(true);

        // Remplir les champs avec les données de l'affaire
        // TODO: Implémenter le remplissage
    }

    private void fermerFormulaire() {
        Stage stage = (Stage) annulerButton.getScene().getWindow();
        stage.close();
    }

    // Classe interne pour les ViewModels des agents
    public static class AgentAssignmentViewModel {
        private String nomAgent;
        private RoleSurAffaire role;
        private LocalDateTime dateAssignation;

        public AgentAssignmentViewModel(String nomAgent, RoleSurAffaire role, LocalDateTime dateAssignation) {
            this.nomAgent = nomAgent;
            this.role = role;
            this.dateAssignation = dateAssignation;
        }

        // Getters
        public String getNomAgent() { return nomAgent; }
        public RoleSurAffaire getRole() { return role; }
        public LocalDateTime getDateAssignation() { return dateAssignation; }
    }

    /**
     * Définit les valeurs par défaut pour une nouvelle affaire
     */
    public void setDefaultValues(String numeroAffaire, Contrevenant contrevenant) {
        if (numeroAffaireField != null && numeroAffaire != null) {
            numeroAffaireField.setText(numeroAffaire);
        }

        if (contrevenantComboBox != null && contrevenant != null) {
            contrevenantComboBox.setValue(contrevenant);
        }

        if (dateCreationPicker != null) {
            dateCreationPicker.setValue(LocalDate.now());
        }

        if (statutComboBox != null) {
            statutComboBox.setValue(StatutAffaire.OUVERTE);
        }
    }

    /**
     * Définit l'affaire à éditer
     */
    public void setAffaireToEdit(Affaire affaire) {
        if (affaire == null) return;

        this.currentAffaire = affaire;
        this.isEditMode = true;

        // Remplissage des champs
        if (numeroAffaireField != null) {
            numeroAffaireField.setText(affaire.getNumeroAffaire());
        }

        if (dateCreationPicker != null) {
            dateCreationPicker.setValue(affaire.getDateCreation());
        }

        if (montantAmendeField != null) {
            montantAmendeField.setText(String.valueOf(affaire.getMontantAmende()));
        }

        if (statutComboBox != null) {
            statutComboBox.setValue(affaire.getStatut());
        }

        // Charger le contrevenant
        if (contrevenantComboBox != null && affaire.getContrevenantId() != null) {
            try {
                Contrevenant contrevenant = contrevenantDAO.findById(affaire.getContrevenantId());
                contrevenantComboBox.setValue(contrevenant);
            } catch (Exception e) {
                logger.error("Erreur lors du chargement du contrevenant", e);
            }
        }
    }
}