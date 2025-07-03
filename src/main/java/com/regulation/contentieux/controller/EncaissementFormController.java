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
     * Configuration du ComboBox des statuts - SUIT LE PATTERN ÉTABLI
     */
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

    /**
     * Configuration du ComboBox des modes de règlement - SUIT LE PATTERN ÉTABLI
     */
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

    /**
     * Configuration du ComboBox des affaires - SUIT LE PATTERN ÉTABLI
     */
    private void setupAffaireComboBox() {
        affaireComboBox.setItems(affaires);
        affaireComboBox.setConverter(new StringConverter<Affaire>() {
            @Override
            public String toString(Affaire affaire) {
                if (affaire == null) {
                    return "";
                }

                // Calculer le solde restant
                BigDecimal totalEncaisse = affaire.getMontantEncaisseTotal();
                BigDecimal soldeRestant = affaire.getMontantAmendeTotal().subtract(totalEncaisse);

                // Format: "N°Affaire - Contrevenant - Solde: XXX FCFA"
                String contrevenant = affaire.getContrevenant() != null ?
                        affaire.getContrevenant().getNomComplet() : "Inconnu";

                return String.format("%s - %s - Solde: %s",
                        affaire.getNumeroAffaire(),
                        contrevenant,
                        CurrencyFormatter.format(soldeRestant));
            }

            @Override
            public Affaire fromString(String string) {
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
                // MODIFICATION : Charger uniquement les affaires EN_COURS (non soldées)
                List<Affaire> affairesList = affaireDAO.findByStatut(StatutAffaire.EN_COURS);

                // Si la méthode findByStatut n'existe pas, utiliser findAll avec filtre
                if (affairesList == null) {
                    affairesList = affaireDAO.findAll().stream()
                            .filter(a -> a.getStatut() == StatutAffaire.EN_COURS)
                            .toList();
                }

                Platform.runLater(() -> {
                    affaires.clear();
                    affaires.addAll(affairesList);

                    logger.info("Affaires non soldées chargées: {} affaires disponibles", affairesList.size());

                    if (affairesList.isEmpty()) {
                        AlertUtil.showInfoAlert("Information",
                                "Aucune affaire en cours",
                                "Il n'y a aucune affaire non soldée pouvant recevoir un encaissement.\n\n" +
                                        "Pour créer une nouvelle affaire, utilisez le menu 'Affaires > Nouvelle Affaire'.");
                    }
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

    private void generateReference() {
        try {
            String nextReference = encaissementService.generateNextNumeroEncaissement();
            referenceField.setText(nextReference);
            logger.info("Référence d'encaissement générée: {}", nextReference);
        } catch (Exception e) {
            logger.error("Erreur lors de la génération de la référence", e);
            AlertUtil.showErrorAlert("Erreur de génération",
                    "Impossible de générer la référence d'encaissement",
                    "Vérifiez la connexion à la base de données.");
        }
    }

    private void validateMontant(String newValue) {
        if (newValue != null && !newValue.trim().isEmpty()) {
            try {
                double montant = Double.parseDouble(newValue.replace(",", "."));
                if (montant <= 0) {
                    montantEncaisseField.setStyle("-fx-border-color: red;");
                } else {
                    montantEncaisseField.setStyle("");
                    // Vérifier si le montant dépasse le solde restant
                    checkMontantVsSolde(montant);
                }
            } catch (NumberFormatException e) {
                montantEncaisseField.setStyle("-fx-border-color: red;");
            }
        } else {
            montantEncaisseField.setStyle("");
        }
    }

    private void checkMontantVsSolde(double montant) {
        if (affaireComboBox.getValue() != null) {
            Affaire affaire = affaireComboBox.getValue();
            BigDecimal totalEncaisse = encaissementService.getTotalEncaisseByAffaire(affaire.getId());
            BigDecimal totalEncaisseNonNull = totalEncaisse != null ? totalEncaisse : BigDecimal.ZERO;
            BigDecimal montantTotal = affaire.getMontantAmendeTotal();
            BigDecimal soldeRestant = montantTotal.subtract(totalEncaisse != null ? totalEncaisse : BigDecimal.ZERO);

            if (BigDecimal.valueOf(montant).compareTo(soldeRestant) > 0) {
                montantEncaisseField.setStyle("-fx-border-color: orange;");
                // Note: Orange pour avertissement, pas erreur bloquante
            }
        }
    }

    @FXML
    private void handleAffaireSelection() {
        Affaire selectedAffaire = affaireComboBox.getValue();

        if (selectedAffaire == null) {
            // Masquer les détails si aucune affaire sélectionnée
            affaireDetailsBox.setVisible(false);
            affaireDetailsBox.setManaged(false);

            // Réinitialiser le champ montant
            montantEncaisseField.clear();
            montantEncaisseField.setDisable(false);

            return;
        }

        // Afficher les détails de l'affaire
        affaireDetailsBox.setVisible(true);
        affaireDetailsBox.setManaged(true);

        // Remplir les informations
        String contrevenant = selectedAffaire.getContrevenant() != null ?
                selectedAffaire.getContrevenant().getNomComplet() : "Non renseigné";
        contrevenantLabel.setText(contrevenant);

        // Montant total de l'amende
        BigDecimal montantTotal = selectedAffaire.getMontantAmendeTotal();
        montantTotalLabel.setText(CurrencyFormatter.format(montantTotal));

        // Calculer le montant déjà encaissé
        BigDecimal montantDejaEncaisse = selectedAffaire.getMontantEncaisseTotal();
        montantEncaisseAnterieurLabel.setText(CurrencyFormatter.format(montantDejaEncaisse));

        // Calculer le solde restant
        BigDecimal soldeRestant = montantTotal.subtract(montantDejaEncaisse);
        soldeRestantLabel.setText(CurrencyFormatter.format(soldeRestant));

        // Configurer le style du solde
        if (soldeRestant.compareTo(BigDecimal.ZERO) > 0) {
            soldeRestantLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold;"); // Rouge
        } else {
            soldeRestantLabel.setStyle("-fx-text-fill: #51cf66; -fx-font-weight: bold;"); // Vert
        }

        // Limiter le montant maximum au solde restant
        montantEncaisseField.setPromptText("Maximum: " + CurrencyFormatter.format(soldeRestant));

        // Ajouter un listener pour valider le montant en temps réel
        montantEncaisseField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty()) {
                try {
                    BigDecimal montantSaisi = new BigDecimal(newVal.replace(" ", "").replace(",", "."));
                    if (montantSaisi.compareTo(soldeRestant) > 0) {
                        montantEncaisseField.setStyle("-fx-border-color: red;");
                        AlertUtil.showWarningAlert("Montant incorrect",
                                "Dépassement du solde",
                                "Le montant saisi (" + CurrencyFormatter.format(montantSaisi) +
                                        ") dépasse le solde restant (" + CurrencyFormatter.format(soldeRestant) + ")");
                    } else {
                        montantEncaisseField.setStyle("");
                    }
                } catch (NumberFormatException e) {
                    // Ignorer si ce n'est pas un nombre valide
                }
            }
        });

        // Charger l'historique des encaissements si disponible
        loadEncaissementHistory(selectedAffaire);

        logger.debug("Affaire sélectionnée: {} - Solde restant: {}",
                selectedAffaire.getNumeroAffaire(), soldeRestant);
    }

    /**
     * Charge l'historique des encaissements pour l'affaire sélectionnée
     */
    private void loadEncaissementHistory(Affaire affaire) {
        if (encaissementsTableView == null || !encaissementsTableView.isVisible()) {
            return;
        }

        Task<List<Encaissement>> historyTask = new Task<List<Encaissement>>() {
            @Override
            protected List<Encaissement> call() throws Exception {
                return encaissementDAO.findByAffaireId(affaire.getId());
            }
        };

        historyTask.setOnSucceeded(e -> {
            List<Encaissement> encaissements = historyTask.getValue();
            ObservableList<EncaissementHistoryViewModel> historyItems = FXCollections.observableArrayList();

            for (Encaissement enc : encaissements) {
                EncaissementHistoryViewModel vm = new EncaissementHistoryViewModel();
                vm.setReference(enc.getReference());
                vm.setDateEncaissement(enc.getDateEncaissement());
                vm.setMontant(enc.getMontantEncaisse().doubleValue());
                vm.setModeReglement(enc.getModeReglement());
                vm.setStatut(enc.getStatut());
                historyItems.add(vm);
            }

            encaissementsTableView.setItems(historyItems);
            encaissementsCountLabel.setText(encaissements.size() + " encaissement(s) précédent(s)");

            // Afficher la section historique s'il y a des encaissements
            encaissementsHistoryBox.setVisible(!encaissements.isEmpty());
            encaissementsHistoryBox.setManaged(!encaissements.isEmpty());
        });

        new Thread(historyTask).start();
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

    private void handleModeReglementChange() {
        ModeReglement selected = modeReglementComboBox.getValue();

        if (selected != null) {
            updateModeReglementInfo(selected);
        } else {
            hideModeReglementInfo();
        }
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

        // Mise à jour du prompt selon le mode
        if (needsReference) {
            switch (mode) {
                case CHEQUE -> referenceExterneField.setPromptText("Numéro du chèque");
                case VIREMENT -> referenceExterneField.setPromptText("Référence du virement");
                case MANDAT -> referenceExterneField.setPromptText("Numéro du mandat");
                default -> referenceExterneField.setPromptText("Référence externe");
            }
        }

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
                        // CORRECTION: Convertir BigDecimal vers Double si nécessaire
                        if (e.getMontantEncaisse() instanceof BigDecimal) {
                            vm.setMontantEncaisse(((BigDecimal) e.getMontantEncaisse()).doubleValue());
                        } else {
                            BigDecimal montantEncaisse = e.getMontantEncaisse();
                            vm.setMontantEncaisse(montantEncaisse != null ? montantEncaisse.doubleValue() : null);
                        }
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

    private void handleSearchAffaire() {
        logger.info("Recherche d'affaire demandée");
        AlertUtil.showInfoAlert("Recherche d'affaire",
                "Fonctionnalité en développement",
                "La recherche avancée d'affaires sera disponible prochainement.");
    }

    private void handleSave() {
        if (validateForm()) {
            saveEncaissement();
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

        // Vérifier la référence
        if (referenceField.getText().trim().isEmpty()) {
            errors.append("- La référence est obligatoire\n");
        }

        // Vérifier l'affaire
        Affaire selectedAffaire = affaireComboBox.getValue();
        if (selectedAffaire == null) {
            errors.append("- Veuillez sélectionner une affaire\n");
            if (!errors.toString().isEmpty()) {
                AlertUtil.showErrorAlert("Erreur de validation",
                        "Formulaire incomplet",
                        errors.toString());
                return false;
            }
        }

        // Vérifier le montant
        try {
            String montantText = montantEncaisseField.getText().replace(" ", "").replace(",", ".");
            if (montantText.isEmpty()) {
                errors.append("- Le montant est obligatoire\n");
            } else {
                BigDecimal montant = new BigDecimal(montantText);

                if (montant.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.append("- Le montant doit être supérieur à zéro\n");
                }

                // IMPORTANT : Vérifier que le montant ne dépasse pas le solde restant
                if (selectedAffaire != null) {
                    BigDecimal soldeRestant = selectedAffaire.getMontantAmendeTotal()
                            .subtract(selectedAffaire.getMontantEncaisseTotal());

                    if (montant.compareTo(soldeRestant) > 0) {
                        errors.append("- Le montant ne peut pas dépasser le solde restant (" +
                                CurrencyFormatter.format(soldeRestant) + ")\n");
                    }
                }
            }
        } catch (NumberFormatException e) {
            errors.append("- Le montant n'est pas valide\n");
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

    /**
     * Réinitialise le formulaire - SUIT LE PATTERN ÉTABLI
     */
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

    /**
     * Vérifie s'il y a des modifications non sauvegardées
     */
    private boolean hasUnsavedChanges() {
        if (currentEncaissement == null) {
            // Mode création - vérifier si des champs sont remplis
            return !referenceField.getText().trim().isEmpty() ||
                    !montantEncaisseField.getText().trim().isEmpty() ||
                    affaireComboBox.getValue() != null ||
                    modeReglementComboBox.getValue() != null;
        } else {
            // Mode édition - comparer avec les valeurs originales
            BigDecimal currentMontant = currentEncaissement.getMontantEncaisse();

            // CORRECTION LIGNE 642: Gérer la comparaison BigDecimal correctement
            boolean montantChanged = false;
            if (!montantEncaisseField.getText().trim().isEmpty()) {
                try {
                    BigDecimal fieldMontant = new BigDecimal(montantEncaisseField.getText().replace(",", "."));
                    montantChanged = !fieldMontant.equals(currentMontant);
                } catch (NumberFormatException e) {
                    montantChanged = true; // Si le champ n'est pas un nombre valide, considérer qu'il y a des changements
                }
            } else {
                montantChanged = currentMontant != null && !currentMontant.equals(BigDecimal.ZERO);
            }

            return !referenceField.getText().equals(currentEncaissement.getReference()) ||
                    !dateEncaissementPicker.getValue().equals(currentEncaissement.getDateEncaissement()) ||
                    montantChanged ||
                    !statutComboBox.getValue().equals(currentEncaissement.getStatut());
        }
    }

    /**
     * Met à jour l'interface selon le mode - SUIT LE PATTERN ÉTABLI
     */
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
    public void setEncaissementToEdit(Encaissement encaissement) {
        this.currentEncaissement = encaissement;
        this.isEditMode = true;

        Platform.runLater(() -> {
            fillFormWithEncaissement(encaissement);
            updateUIMode();
        });
    }

    /**
     * Remplit le formulaire avec les données d'un encaissement - SUIT LE PATTERN ÉTABLI
     */
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

    /**
     * Configure le formulaire en mode création avec des valeurs par défaut
     */
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