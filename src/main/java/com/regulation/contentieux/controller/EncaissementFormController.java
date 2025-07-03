package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.enums.StatutAffaire;
import com.regulation.contentieux.dao.EncaissementDAO;
import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.model.Encaissement;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.enums.ModeReglement;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.service.EncaissementService;
import com.regulation.contentieux.service.AffaireService;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Contrôleur pour le formulaire d'encaissement - SUIT LE PATTERN ÉTABLI
 * Respecte exactement la logique des autres contrôleurs de formulaire
 */
public class EncaissementFormController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(EncaissementFormController.class);

    // FXML - En-tête
    @FXML private Label formTitleLabel;
    @FXML private Label modeLabel;

    // FXML - Informations générales
    @FXML private TextField referenceField;
    @FXML private Button generateReferenceButton;
    @FXML private DatePicker dateEncaissementPicker;
    @FXML private TextField montantEncaisseField;
    @FXML private ComboBox<StatutEncaissement> statutComboBox;

    // FXML - Affaire liée
    @FXML private ComboBox<Affaire> affaireComboBox;
    @FXML private Button searchAffaireButton;
    @FXML private VBox affaireDetailsBox;
    @FXML private Label contrevenantLabel;
    @FXML private Label montantTotalLabel;
    @FXML private Label montantEncaisseAnterieurLabel;
    @FXML private Label soldeRestantLabel;

    // FXML - Mode de règlement
    @FXML private ComboBox<ModeReglement> modeReglementComboBox;
    @FXML private ComboBox<Object> banqueComboBox; // TODO: Banque class
    @FXML private VBox modeReglementInfoBox;
    @FXML private Label modeReglementInfoLabel;
    @FXML private GridPane modeReglementFieldsGrid;
    @FXML private Label referenceExterneLabel;
    @FXML private TextField referenceExterneField;
    @FXML private Label numeroChequeLabel;
    @FXML private TextField numeroChequeField;

    // FXML - Observations
    @FXML private TextArea observationsField;

    // FXML - Historique
    @FXML private VBox encaissementsHistoryBox;
    @FXML private Label encaissementsCountLabel;
    @FXML private TableView<EncaissementHistoryViewModel> encaissementsTableView;
    @FXML private TableColumn<EncaissementHistoryViewModel, String> histReferenceColumn;
    @FXML private TableColumn<EncaissementHistoryViewModel, LocalDate> histDateColumn;
    @FXML private TableColumn<EncaissementHistoryViewModel, Double> histMontantColumn;
    @FXML private TableColumn<EncaissementHistoryViewModel, ModeReglement> histModeColumn;
    @FXML private TableColumn<EncaissementHistoryViewModel, StatutEncaissement> histStatutColumn;

    // FXML - Actions
    @FXML private Button cancelButton;
    @FXML private Button resetButton;
    @FXML private Button saveButton;

    // Services et données - SUIT LE PATTERN ÉTABLI
    private EncaissementService encaissementService;
    private EncaissementDAO encaissementDAO;
    private AffaireDAO affaireDAO;
    private AffaireService affaireService;
    private AuthenticationService authService;

    // État du formulaire
    private boolean isEditMode = false;
    private Encaissement currentEncaissement;
    private ObservableList<Affaire> affaires;
    private ObservableList<EncaissementHistoryViewModel> encaissementsHistory;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialisation des services - SUIT LE PATTERN ÉTABLI
        encaissementService = new EncaissementService();
        encaissementDAO = new EncaissementDAO();
        affaireDAO = new AffaireDAO();
        affaireService = new AffaireService();
        authService = AuthenticationService.getInstance();

        // Initialisation des listes
        affaires = FXCollections.observableArrayList();
        encaissementsHistory = FXCollections.observableArrayList();

        setupUI();
        setupEventHandlers();
        setupTableColumns();
        loadFormData();

        logger.info("Contrôleur de formulaire d'encaissement initialisé");
    }

    /**
     * Configuration initiale de l'interface - SUIT LE PATTERN ÉTABLI
     */
    private void setupUI() {
        // Configuration des ComboBox
        setupStatutComboBox();
        setupModeReglementComboBox();
        setupAffaireComboBox();

        // Configuration des champs
        dateEncaissementPicker.setValue(LocalDate.now());
        montantEncaisseField.setPromptText("Montant en FCFA");
        observationsField.setWrapText(true);

        // Configuration initiale
        updateUIMode();
    }

    /**
     * Configuration des gestionnaires d'événements - SUIT LE PATTERN ÉTABLI
     */
    private void setupEventHandlers() {
        // Génération de la référence
        generateReferenceButton.setOnAction(e -> generateReference());

        // Validation en temps réel du montant
        montantEncaisseField.textProperty().addListener((obs, oldVal, newVal) -> validateMontant(newVal));

        // Sélection de l'affaire
        affaireComboBox.setOnAction(e -> handleAffaireSelection());

        // Changement de mode de règlement
        modeReglementComboBox.setOnAction(e -> handleModeReglementChange());

        // Boutons d'action
        cancelButton.setOnAction(e -> handleCancel());
        resetButton.setOnAction(e -> handleReset());
        saveButton.setOnAction(e -> handleSave());

        // Recherche d'affaire
        searchAffaireButton.setOnAction(e -> handleSearchAffaire());
    }

    /**
     * Configuration des colonnes du tableau - SUIT LE PATTERN ÉTABLI
     */
    private void setupTableColumns() {
        histReferenceColumn.setCellValueFactory(new PropertyValueFactory<>("reference"));

        histDateColumn.setCellValueFactory(new PropertyValueFactory<>("dateEncaissement"));
        histDateColumn.setCellFactory(col -> new TableCell<EncaissementHistoryViewModel, LocalDate>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setText(empty || date == null ? null : DateFormatter.formatDate(date));
            }
        });

        histMontantColumn.setCellValueFactory(new PropertyValueFactory<>("montantEncaisse"));
        histMontantColumn.setCellFactory(col -> new TableCell<EncaissementHistoryViewModel, Double>() {
            @Override
            protected void updateItem(Double montant, boolean empty) {
                super.updateItem(montant, empty);
                setText(empty || montant == null ? null : CurrencyFormatter.format(montant));
            }
        });

        histModeColumn.setCellValueFactory(new PropertyValueFactory<>("modeReglement"));
        histModeColumn.setCellFactory(col -> new TableCell<EncaissementHistoryViewModel, ModeReglement>() {
            @Override
            protected void updateItem(ModeReglement mode, boolean empty) {
                super.updateItem(mode, empty);
                setText(empty || mode == null ? null : mode.getLibelle());
            }
        });

        histStatutColumn.setCellValueFactory(new PropertyValueFactory<>("statut"));
        histStatutColumn.setCellFactory(col -> new TableCell<EncaissementHistoryViewModel, StatutEncaissement>() {
            @Override
            protected void updateItem(StatutEncaissement statut, boolean empty) {
                super.updateItem(statut, empty);
                if (empty || statut == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(statut.getLibelle());
                    setStyle("-fx-text-fill: " + statut.getCouleur() + "; -fx-font-weight: bold;");
                }
            }
        });

        encaissementsTableView.setItems(encaissementsHistory);
    }

    /**
     * Charge l'historique des encaissements de l'affaire sélectionnée - SUIT LE PATTERN ÉTABLI
     */
    private void loadAffaireEncaissements(Affaire affaire) {
        if (affaire == null) {
            encaissementsHistory.clear();
            encaissementsHistoryBox.setVisible(false);
            encaissementsHistoryBox.setManaged(false);
            return;
        }

        Task<List<Encaissement>> loadTask = new Task<List<Encaissement>>() {
            @Override
            protected List<Encaissement> call() throws Exception {
                return encaissementDAO.findByAffaireId(affaire.getId());
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    List<Encaissement> encaissements = getValue();

                    // Filtrer l'encaissement courant si on est en mode édition
                    if (isEditMode && currentEncaissement != null) {
                        encaissements = encaissements.stream()
                                .filter(e -> !e.getId().equals(currentEncaissement.getId()))
                                .toList();
                    }

                    // Conversion vers ViewModel
                    encaissementsHistory.clear();
                    encaissements.forEach(e -> {
                        EncaissementHistoryViewModel vm = new EncaissementHistoryViewModel();
                        vm.setReference(e.getReference());
                        vm.setDateEncaissement(e.getDateEncaissement());
                        // CORRECTION: Utiliser setMontant() qui est la méthode correcte dans EncaissementHistoryViewModel
                        BigDecimal montantEncaisse = e.getMontantEncaisse();
                        vm.setMontant(montantEncaisse != null ? montantEncaisse.doubleValue() : null);
                        vm.setModeReglement(e.getModeReglement());
                        vm.setStatut(e.getStatut());
                        encaissementsHistory.add(vm);
                    });

                    // Mise à jour du compteur
                    encaissementsCountLabel.setText("(" + encaissements.size() + " encaissement(s))");

                    // Afficher la section si on a des données
                    if (!encaissements.isEmpty()) {
                        encaissementsHistoryBox.setVisible(true);
                        encaissementsHistoryBox.setManaged(true);
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du chargement de l'historique", getException());
                });
            }
        };

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void loadEncaissementsHistory(Long affaireId) {
        Task<List<Encaissement>> loadTask = new Task<List<Encaissement>>() {
            @Override
            protected List<Encaissement> call() throws Exception {
                return encaissementService.findByAffaireId(affaireId);
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    List<Encaissement> encaissements = getValue();

                    // Filtrer l'encaissement actuel si on est en mode édition
                    if (isEditMode && currentEncaissement != null) {
                        encaissements = encaissements.stream()
                                .filter(e -> !e.getId().equals(currentEncaissement.getId()))
                                .toList();
                    }

                    // Conversion vers ViewModel
                    encaissementsHistory.clear();
                    encaissements.forEach(e -> {
                        EncaissementHistoryViewModel vm = new EncaissementHistoryViewModel();
                        vm.setReference(e.getReference());
                        vm.setDateEncaissement(e.getDateEncaissement());
                        // CORRECTION: Utiliser setMontant() qui est la méthode correcte
                        BigDecimal montantEncaisse = e.getMontantEncaisse();
                        vm.setMontant(montantEncaisse != null ? montantEncaisse.doubleValue() : null);
                        vm.setModeReglement(e.getModeReglement());
                        vm.setStatut(e.getStatut());
                        encaissementsHistory.add(vm);
                    });

                    // Mise à jour du compteur
                    encaissementsCountLabel.setText("(" + encaissements.size() + " encaissement(s))");

                    // Afficher la section si on a des données
                    if (!encaissements.isEmpty()) {
                        encaissementsHistoryBox.setVisible(true);
                        encaissementsHistoryBox.setManaged(true);
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du chargement de l'historique", getException());
                });
            }
        };

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    /**
     * Collecte les données du formulaire pour créer/modifier un encaissement - SUIT LE PATTERN ÉTABLI
     */
    private Encaissement collectFormData() {
        Encaissement encaissement = new Encaissement();

        // Référence
        encaissement.setReference(referenceField.getText().trim());

        // Date d'encaissement
        encaissement.setDateEncaissement(dateEncaissementPicker.getValue());

        // Montant encaissé
        try {
            String montantStr = montantEncaisseField.getText().trim().replace(" ", "").replace(",", ".");
            BigDecimal montant = new BigDecimal(montantStr);
            encaissement.setMontantEncaisse(montant);
        } catch (NumberFormatException e) {
            logger.error("Montant invalide : {}", montantEncaisseField.getText());
            throw new IllegalArgumentException("Le montant saisi n'est pas valide");
        }

        // Statut
        encaissement.setStatut(statutComboBox.getValue());

        // Mode de règlement
        encaissement.setModeReglement(modeReglementComboBox.getValue());

        // Informations bancaires si mode chèque
        if (modeReglementComboBox.getValue() == ModeReglement.CHEQUE) {
            if (banqueComboBox.getValue() != null) {
                // TODO: Gérer l'objet Banque quand la classe sera disponible
                // encaissement.setBanqueId(((Banque) banqueComboBox.getValue()).getId());
            }
            if (!numeroChequeField.getText().trim().isEmpty()) {
                encaissement.setNumeroPiece(numeroChequeField.getText().trim());
            }
        }

        // Référence externe si présente
        if (!referenceExterneField.getText().trim().isEmpty()) {
            // TODO: Ajouter le champ referenceExterne dans le modèle Encaissement si nécessaire
            // encaissement.setReferenceExterne(referenceExterneField.getText().trim());
        }

        // Observations
        if (!observationsField.getText().trim().isEmpty()) {
            encaissement.setObservations(observationsField.getText().trim());
        }

        // Affaire associée
        if (affaireComboBox.getValue() != null) {
            encaissement.setAffaire(affaireComboBox.getValue());
            encaissement.setAffaireId(affaireComboBox.getValue().getId());
        }

        return encaissement;
    }

    /**
     * Sauvegarde de l'encaissement - SUIT LE PATTERN ÉTABLI
     */
    @FXML
    private void saveEncaissement() {
        if (!validateForm()) {
            return;
        }

        try {
            // Collecter les données
            Encaissement encaissement = collectFormData();
            Affaire affaire = affaireComboBox.getValue();

            // Sauvegarder via le service
            if (isEditMode) {
                encaissement.setId(currentEncaissement.getId());
                encaissement = encaissementService.updateEncaissement(encaissement);
            } else {
                // Pour un nouvel encaissement, utiliser la méthode du service qui gère tout
                encaissement = affaireService.addEncaissementToAffaire(affaire.getId(), encaissement);
            }

            // Message de succès avec information sur le statut
            String message = "L'encaissement " + encaissement.getReference() + " a été enregistré avec succès.";

            // Vérifier si l'affaire est maintenant soldée
            BigDecimal nouveauTotal = affaire.getMontantEncaisseTotal().add(encaissement.getMontantEncaisse());
            if (nouveauTotal.compareTo(affaire.getMontantAmendeTotal()) >= 0) {
                message += "\n\nL'affaire " + affaire.getNumeroAffaire() + " est maintenant SOLDÉE.";
            }

            AlertUtil.showSuccessAlert("Succès",
                    "Encaissement enregistré",
                    message);

            // Fermer le formulaire
            closeForm();

        } catch (Exception e) {
            logger.error("Erreur lors de l'enregistrement", e);
            AlertUtil.showErrorAlert("Erreur",
                    "Échec de l'enregistrement",
                    "Impossible d'enregistrer l'encaissement : " + e.getMessage());
        }
    }

    // Les méthodes manquantes ou partielles - SUIT LE PATTERN ÉTABLI

    private void generateReference() {
        logger.info("Génération de la référence d'encaissement");
        // TODO: Implémenter la génération automatique de référence
    }

    private void validateMontant(String newVal) {
        // Validation du montant en temps réel
        if (newVal != null && !newVal.trim().isEmpty()) {
            try {
                new BigDecimal(newVal.replace(",", "."));
                montantEncaisseField.setStyle("");
            } catch (NumberFormatException e) {
                montantEncaisseField.setStyle("-fx-border-color: red;");
            }
        }
    }

    private void handleAffaireSelection() {
        Affaire selectedAffaire = affaireComboBox.getValue();
        if (selectedAffaire != null) {
            updateAffaireDetails(selectedAffaire);
            loadAffaireEncaissements(selectedAffaire);
        } else {
            hideAffaireDetails();
        }
    }

    private void handleModeReglementChange() {
        ModeReglement selected = modeReglementComboBox.getValue();
        if (selected != null) {
            updateModeReglementInfo(selected);
        } else {
            hideModeReglementInfo();
        }
    }

    private void handleCancel() {
        if (hasUnsavedChanges()) {
            if (AlertUtil.showConfirmAlert("Confirmation",
                    "Modifications non sauvegardées",
                    "Des modifications n'ont pas été sauvegardées. Voulez-vous vraiment quitter ?")) {
                closeForm();
            }
        } else {
            closeForm();
        }
    }

    private void handleReset() {
        if (AlertUtil.showConfirmAlert("Confirmation",
                "Réinitialiser le formulaire",
                "Voulez-vous vraiment réinitialiser tous les champs ?")) {
            resetForm();
        }
    }

    private void handleSave() {
        if (validateForm()) {
            saveEncaissement();
        }
    }

    private void handleSearchAffaire() {
        logger.info("Recherche d'affaire demandée");
        AlertUtil.showInfoAlert("Recherche d'affaire",
                "Fonctionnalité en développement",
                "La recherche avancée d'affaires sera disponible prochainement.");
    }

    private boolean validateForm() {
        StringBuilder errors = new StringBuilder();

        // Vérifier la référence
        if (referenceField.getText().trim().isEmpty()) {
            errors.append("- La référence est obligatoire\n");
        }

        // Vérifier l'affaire
        if (affaireComboBox.getValue() == null) {
            errors.append("- L'affaire liée est obligatoire\n");
        }

        // Vérifier le montant
        if (montantEncaisseField.getText().trim().isEmpty()) {
            errors.append("- Le montant encaissé est obligatoire\n");
        } else {
            try {
                BigDecimal montant = new BigDecimal(montantEncaisseField.getText().replace(",", "."));
                if (montant.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.append("- Le montant doit être supérieur à 0\n");
                }
            } catch (NumberFormatException e) {
                errors.append("- Le montant saisi n'est pas valide\n");
            }
        }

        // Vérifier la date
        if (dateEncaissementPicker.getValue() == null) {
            errors.append("- La date d'encaissement est obligatoire\n");
        } else if (dateEncaissementPicker.getValue().isAfter(LocalDate.now())) {
            errors.append("- La date d'encaissement ne peut pas être dans le futur\n");
        }

        // Vérifier le mode de règlement
        if (modeReglementComboBox.getValue() == null) {
            errors.append("- Le mode de règlement est obligatoire\n");
        } else if (modeReglementComboBox.getValue() == ModeReglement.CHEQUE) {
            // Vérifier les informations du chèque
            if (numeroChequeField.getText().trim().isEmpty()) {
                errors.append("- Le numéro de chèque est obligatoire pour un paiement par chèque\n");
            }
        }

        // Afficher les erreurs s'il y en a
        if (!errors.toString().isEmpty()) {
            AlertUtil.showErrorAlert("Erreur de validation",
                    "Formulaire incomplet",
                    errors.toString());
            return false;
        }

        return true;
    }

    // Les autres méthodes utilitaires nécessaires...

    private void setupStatutComboBox() {
        statutComboBox.getItems().addAll(StatutEncaissement.values());
        statutComboBox.setValue(StatutEncaissement.EN_ATTENTE);
        statutComboBox.setConverter(new StringConverter<StatutEncaissement>() {
            @Override
            public String toString(StatutEncaissement statut) {
                return statut != null ? statut.getLibelle() : "";
            }

            @Override
            public StatutEncaissement fromString(String string) {
                return null;
            }
        });
    }

    private void setupModeReglementComboBox() {
        modeReglementComboBox.getItems().addAll(ModeReglement.values());
        modeReglementComboBox.setConverter(new StringConverter<ModeReglement>() {
            @Override
            public String toString(ModeReglement mode) {
                return mode != null ? mode.getLibelle() : "";
            }

            @Override
            public ModeReglement fromString(String string) {
                return null;
            }
        });
    }

    private void setupAffaireComboBox() {
        affaireComboBox.setConverter(new StringConverter<Affaire>() {
            @Override
            public String toString(Affaire affaire) {
                return affaire != null ? affaire.getNumeroAffaire() + " - " +
                        CurrencyFormatter.format(affaire.getMontantAmendeTotal().doubleValue()) : "";
            }

            @Override
            public Affaire fromString(String string) {
                return null;
            }
        });
    }

    private void loadFormData() {
        Task<List<Affaire>> loadTask = new Task<List<Affaire>>() {
            @Override
            protected List<Affaire> call() throws Exception {
                // Utiliser findAll avec pagination pour charger toutes les affaires
                return affaireDAO.findAll(0, 1000); // Charger jusqu'à 1000 affaires
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    affaires.clear();
                    affaires.addAll(getValue());
                    affaireComboBox.setItems(affaires);
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du chargement des affaires", getException());
                    AlertUtil.showErrorAlert("Erreur",
                            "Impossible de charger les affaires",
                            "Vérifiez la connexion à la base de données.");
                });
            }
        };

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void updateAffaireDetails(Affaire affaire) {
        // TODO: Récupérer le nom du contrevenant via ContrevenantDAO
        contrevenantLabel.setText("Contrevenant #" + affaire.getContrevenantId());

        BigDecimal montantTotal = affaire.getMontantAmendeTotal();
        montantTotalLabel.setText(CurrencyFormatter.format(montantTotal.doubleValue()));

        // Calculer le montant déjà encaissé
        BigDecimal totalEncaisse = encaissementService.getTotalEncaisseByAffaire(affaire.getId());
        BigDecimal totalEncaisseNonNull = totalEncaisse != null ? totalEncaisse : BigDecimal.ZERO;
        montantEncaisseAnterieurLabel.setText(CurrencyFormatter.format(totalEncaisseNonNull.doubleValue()));

        // Calculer le solde restant
        BigDecimal soldeRestant = montantTotal.subtract(totalEncaisseNonNull);
        soldeRestantLabel.setText(CurrencyFormatter.format(soldeRestant.doubleValue()));

        // Afficher la section
        affaireDetailsBox.setVisible(true);
        affaireDetailsBox.setManaged(true);
    }

    private void hideAffaireDetails() {
        affaireDetailsBox.setVisible(false);
        affaireDetailsBox.setManaged(false);

        encaissementsHistoryBox.setVisible(false);
        encaissementsHistoryBox.setManaged(false);
    }

    private void updateModeReglementInfo(ModeReglement mode) {
        // Mise à jour du texte d'information
        StringBuilder info = new StringBuilder();
        info.append("Mode: ").append(mode.getLibelle()).append(". ");

        if (mode.isNecessiteBanque()) {
            info.append("Banque obligatoire. ");
        }
        if (mode.isNecessiteReference()) {
            info.append("Référence externe obligatoire. ");
        }
        if (mode.getDelaiEncaissement() > 0) {
            info.append("Délai d'encaissement: ").append(mode.getDelaiEncaissement()).append(" jour(s).");
        }

        modeReglementInfoLabel.setText(info.toString());

        // Affichage conditionnel des champs
        updateConditionalFields(mode);

        // Afficher la section
        modeReglementInfoBox.setVisible(true);
        modeReglementInfoBox.setManaged(true);
    }

    private void updateConditionalFields(ModeReglement mode) {
        // Champ référence externe
        boolean needsReference = mode.isNecessiteReference();
        referenceExterneLabel.setVisible(needsReference);
        referenceExterneLabel.setManaged(needsReference);
        referenceExterneField.setVisible(needsReference);
        referenceExterneField.setManaged(needsReference);

        // ComboBox banque
        banqueComboBox.setDisable(!mode.isNecessiteBanque());
        if (!mode.isNecessiteBanque()) {
            banqueComboBox.setValue(null);
        }
    }

    private void hideModeReglementInfo() {
        modeReglementInfoBox.setVisible(false);
        modeReglementInfoBox.setManaged(false);
    }

    private void resetForm() {
        referenceField.clear();
        dateEncaissementPicker.setValue(LocalDate.now());
        montantEncaisseField.clear();
        statutComboBox.setValue(StatutEncaissement.EN_ATTENTE);
        affaireComboBox.setValue(null);
        modeReglementComboBox.setValue(null);
        banqueComboBox.setValue(null);
        referenceExterneField.clear();
        observationsField.clear();

        encaissementsHistory.clear();
        hideAffaireDetails();
        hideModeReglementInfo();

        // Réinitialisation des styles d'erreur
        montantEncaisseField.setStyle("");

        logger.info("Formulaire réinitialisé");
    }

    private boolean hasUnsavedChanges() {
        if (currentEncaissement == null) {
            // Mode création - vérifier si des champs sont remplis
            return !referenceField.getText().trim().isEmpty() ||
                    !montantEncaisseField.getText().trim().isEmpty() ||
                    affaireComboBox.getValue() != null ||
                    modeReglementComboBox.getValue() != null;
        } else {
            // Mode édition - comparer avec les valeurs originales
            return true; // Simplification pour l'instant
        }
    }

    private void updateUIMode() {
        if (isEditMode) {
            formTitleLabel.setText("Modifier l'encaissement");
            modeLabel.setText("Mode édition");
            saveButton.setText("Mettre à jour");
        } else {
            formTitleLabel.setText("Nouvel encaissement");
            modeLabel.setText("Mode création");
            saveButton.setText("Enregistrer");
        }
    }

    private void closeForm() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    // Méthodes publiques pour l'intégration

    public void setEncaissementToEdit(Encaissement encaissement) {
        this.currentEncaissement = encaissement;
        this.isEditMode = true;

        Platform.runLater(() -> {
            fillFormWithEncaissement(encaissement);
            updateUIMode();
        });
    }

    private void fillFormWithEncaissement(Encaissement encaissement) {
        referenceField.setText(encaissement.getReference());
        dateEncaissementPicker.setValue(encaissement.getDateEncaissement());
        montantEncaisseField.setText(String.valueOf(encaissement.getMontantEncaisse()));
        statutComboBox.setValue(encaissement.getStatut());
        modeReglementComboBox.setValue(encaissement.getModeReglement());

        // Sélectionner l'affaire correspondante
        affaires.stream()
                .filter(a -> a.getId().equals(encaissement.getAffaireId()))
                .findFirst()
                .ifPresent(affaireComboBox::setValue);

        // Déclencher les mises à jour
        handleAffaireSelection();
        handleModeReglementChange();

        logger.info("Formulaire rempli avec l'encaissement: {}", encaissement.getReference());
    }

    public void setDefaultValues(String reference, Affaire affaire) {
        Platform.runLater(() -> {
            if (reference != null) {
                referenceField.setText(reference);
            }
            if (affaire != null) {
                affaireComboBox.setValue(affaire);
                handleAffaireSelection();
            }
        });
    }

    /**
     * ViewModel pour l'affichage de l'historique des encaissements
     * À ajouter comme classe interne dans EncaissementFormController
     */
    public static class EncaissementHistoryViewModel {
        private String reference;
        private LocalDate dateEncaissement;
        private Double montant;
        private ModeReglement modeReglement;
        private StatutEncaissement statut;

        // Getters et setters
        public String getReference() { return reference; }
        public void setReference(String reference) { this.reference = reference; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) {
            this.dateEncaissement = dateEncaissement;
        }

        public Double getMontant() { return montant; }
        public void setMontant(Double montant) { this.montant = montant; }

        public ModeReglement getModeReglement() { return modeReglement; }
        public void setModeReglement(ModeReglement modeReglement) {
            this.modeReglement = modeReglement;
        }

        public StatutEncaissement getStatut() { return statut; }
        public void setStatut(StatutEncaissement statut) { this.statut = statut; }
    }
}