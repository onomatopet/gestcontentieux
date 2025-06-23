package com.regulation.contentieux.ui.components;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ENRICHISSEMENT UI - Phase 3 Partie 2
 * ComboBox avec auto-complétion avancée selon le cahier des charges
 * "Auto-complétion sur champs de référence"
 */
public class AutoCompleteComboBox<T> extends ComboBox<T> {

    private static final Logger logger = LoggerFactory.getLogger(com.regulation.contentieux.ui.essaie.AutoCompleteComboBox.class);

    // Configuration
    private static final Duration DEBOUNCE_DURATION = Duration.millis(300);
    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_SUGGESTIONS = 10;

    // Composants internes
    private Timeline searchTimeline;
    private final ObservableList<T> originalItems = FXCollections.observableArrayList();
    private final ObservableList<T> filteredItems = FXCollections.observableArrayList();

    // Configuration fonctionnelle
    private Function<String, List<T>> searchFunction;
    private Function<T, String> displayFunction;
    private boolean caseSensitive = false;
    private boolean remoteSearch = false;

    // État
    private boolean ignoreTextChange = false;
    private String lastSearchText = "";

    /**
     * Constructeur par défaut
     */
    public AutoCompleteComboBox() {
        super();
        initialize();
    }

    /**
     * Constructeur avec données initiales
     */
    public AutoCompleteComboBox(ObservableList<T> items) {
        super();
        setOriginalItems(items);
        initialize();
    }

    /**
     * Initialisation du composant
     */
    private void initialize() {
        setEditable(true);
        setupEventHandlers();
        setupStyling();

        logger.debug("AutoCompleteComboBox initialisé");
    }

    /**
     * Configuration des gestionnaires d'événements
     */
    private void setupEventHandlers() {
        // Gestionnaire de saisie avec debounce
        getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            if (!ignoreTextChange) {
                handleTextChange(newValue);
            }
        });

        // Gestionnaire de touches
        getEditor().setOnKeyPressed(this::handleKeyPressed);

        // Gestionnaire de focus
        getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused && getItems().isEmpty() && !originalItems.isEmpty()) {
                setItems(FXCollections.observableArrayList(
                        originalItems.subList(0, Math.min(originalItems.size(), MAX_SUGGESTIONS))
                ));
                show();
            }
        });

        // Gestionnaire de sélection
        getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null && displayFunction != null) {
                ignoreTextChange = true;
                getEditor().setText(displayFunction.apply(newItem));
                ignoreTextChange = false;
            }
        });
    }

    /**
     * Configuration du style
     */
    private void setupStyling() {
        getStyleClass().add("auto-complete-combobox");
        getEditor().getStyleClass().add("auto-complete-editor");

        // Placeholder text
        if (getPromptText() == null) {
            setPromptText("Tapez pour rechercher...");
        }
    }

    /**
     * Gestion du changement de texte avec debounce
     */
    private void handleTextChange(String newValue) {
        // Annuler la recherche précédente
        if (searchTimeline != null) {
            searchTimeline.stop();
        }

        // Programmer une nouvelle recherche
        searchTimeline = new Timeline(new KeyFrame(DEBOUNCE_DURATION, e -> {
            performSearch(newValue);
        }));
        searchTimeline.play();
    }

    /**
     * Gestion des touches spéciales
     */
    private void handleKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();

        switch (code) {
            case DOWN:
                if (!isShowing() && !getItems().isEmpty()) {
                    show();
                    event.consume();
                }
                break;

            case UP:
                if (isShowing() && getSelectionModel().getSelectedIndex() <= 0) {
                    hide();
                    event.consume();
                }
                break;

            case ENTER:
                if (isShowing() && getSelectionModel().getSelectedItem() != null) {
                    selectItem(getSelectionModel().getSelectedItem());
                    hide();
                    event.consume();
                }
                break;

            case ESCAPE:
                if (isShowing()) {
                    hide();
                    event.consume();
                }
                break;
        }
    }

    /**
     * Effectue la recherche selon la configuration
     */
    private void performSearch(String searchText) {
        if (searchText == null) {
            searchText = "";
        }

        searchText = searchText.trim();

        // Éviter les recherches répétitives
        if (searchText.equals(lastSearchText)) {
            return;
        }
        lastSearchText = searchText;

        // Recherche locale ou distante
        if (remoteSearch && searchFunction != null) {
            performRemoteSearch(searchText);
        } else {
            performLocalSearch(searchText);
        }
    }

    /**
     * Recherche locale dans les données
     */
    private void performLocalSearch(String searchText) {
        filteredItems.clear();

        if (searchText.isEmpty()) {
            // Afficher les premiers éléments
            filteredItems.addAll(originalItems.subList(0,
                    Math.min(originalItems.size(), MAX_SUGGESTIONS)));
        } else if (searchText.length() >= MIN_SEARCH_LENGTH) {
            // Filtrer selon le texte
            List<T> matches = originalItems.stream()
                    .filter(item -> matchesSearchText(item, searchText))
                    .limit(MAX_SUGGESTIONS)
                    .collect(Collectors.toList());

            filteredItems.addAll(matches);
        }

        // Mettre à jour l'affichage
        Platform.runLater(() -> {
            setItems(FXCollections.observableArrayList(filteredItems));

            if (!filteredItems.isEmpty() && !searchText.isEmpty()) {
                if (!isShowing()) {
                    show();
                }
            } else if (searchText.isEmpty() || filteredItems.isEmpty()) {
                hide();
            }
        });
    }

    /**
     * Recherche distante asynchrone
     */
    private void performRemoteSearch(String searchText) {
        if (searchText.length() < MIN_SEARCH_LENGTH) {
            hide();
            return;
        }

        Task<List<T>> searchTask = new Task<>() {
            @Override
            protected List<T> call() throws Exception {
                return searchFunction.apply(searchText);
            }
        };

        searchTask.setOnSucceeded(e -> {
            List<T> results = searchTask.getValue();
            if (results != null) {
                Platform.runLater(() -> {
                    filteredItems.clear();
                    filteredItems.addAll(results.subList(0,
                            Math.min(results.size(), MAX_SUGGESTIONS)));

                    setItems(FXCollections.observableArrayList(filteredItems));

                    if (!filteredItems.isEmpty()) {
                        show();
                    }
                });
            }
        });

        searchTask.setOnFailed(e -> {
            Throwable exception = searchTask.getException();
            logger.warn("Erreur lors de la recherche distante: {}", exception.getMessage());
        });

        // Lancer la recherche dans un thread séparé
        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }

    /**
     * Vérifie si un élément correspond au texte de recherche
     */
    private boolean matchesSearchText(T item, String searchText) {
        if (item == null || searchText == null) {
            return false;
        }

        String itemText = displayFunction != null ?
                displayFunction.apply(item) :
                item.toString();

        if (itemText == null) {
            return false;
        }

        if (!caseSensitive) {
            itemText = itemText.toLowerCase();
            searchText = searchText.toLowerCase();
        }

        return itemText.contains(searchText);
    }

    /**
     * Sélectionne un élément
     */
    private void selectItem(T item) {
        getSelectionModel().select(item);

        if (displayFunction != null) {
            ignoreTextChange = true;
            getEditor().setText(displayFunction.apply(item));
            ignoreTextChange = false;
        }
    }

    // ==================== MÉTHODES DE CONFIGURATION ====================

    /**
     * Définit les données originales
     */
    public void setOriginalItems(ObservableList<T> items) {
        this.originalItems.clear();
        if (items != null) {
            this.originalItems.addAll(items);
        }

        // Afficher les premiers éléments par défaut
        if (!this.originalItems.isEmpty()) {
            List<T> initialItems = this.originalItems.subList(0,
                    Math.min(this.originalItems.size(), MAX_SUGGESTIONS));
            setItems(FXCollections.observableArrayList(initialItems));
        }
    }

    /**
     * Définit la fonction de recherche pour recherche distante
     */
    public void setSearchFunction(Function<String, List<T>> searchFunction) {
        this.searchFunction = searchFunction;
        this.remoteSearch = true;
    }

    /**
     * Définit la fonction d'affichage
     */
    public void setDisplayFunction(Function<T, String> displayFunction) {
        this.displayFunction = displayFunction;

        // Configurer le StringConverter
        setConverter(new StringConverter<T>() {
            @Override
            public String toString(T item) {
                return item != null && displayFunction != null ?
                        displayFunction.apply(item) :
                        (item != null ? item.toString() : "");
            }

            @Override
            public T fromString(String string) {
                // Rechercher l'élément correspondant
                return originalItems.stream()
                        .filter(item -> toString(item).equals(string))
                        .findFirst()
                        .orElse(null);
            }
        });
    }

    /**
     * Configure la sensibilité à la casse
     */
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    /**
     * Configure la longueur minimale de recherche
     */
    public void setMinSearchLength(int minLength) {
        // Cette constante pourrait être rendue configurable si nécessaire
    }

    // ==================== MÉTHODES STATIQUES DE CRÉATION ====================

    /**
     * Crée un ComboBox auto-complete pour les agents
     */
    public static com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<Agent> createForAgents(List<Agent> agents) {
        com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<Agent> comboBox = new com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<>();

        if (agents != null) {
            comboBox.setOriginalItems(FXCollections.observableArrayList(agents));
        }

        comboBox.setDisplayFunction(agent ->
                String.format("%s %s (%s)", agent.getPrenom(), agent.getNom(), agent.getMatricule()));

        comboBox.setPromptText("Sélectionnez un agent...");

        return comboBox;
    }

    /**
     * Crée un ComboBox auto-complete pour les contrevenants
     */
    public static com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<Contrevenant> createForContrevenants(List<Contrevenant> contrevenants) {
        com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<Contrevenant> comboBox = new com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<>();

        if (contrevenants != null) {
            comboBox.setOriginalItems(FXCollections.observableArrayList(contrevenants));
        }

        comboBox.setDisplayFunction(contrevenant ->
                String.format("%s (%s)", contrevenant.getNomComplet(), contrevenant.getCode()));

        comboBox.setPromptText("Sélectionnez un contrevenant...");

        return comboBox;
    }

    /**
     * Crée un ComboBox auto-complete pour les services
     */
    public static com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<Service> createForServices(List<Service> services) {
        com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<Service> comboBox = new com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<>();

        if (services != null) {
            comboBox.setOriginalItems(FXCollections.observableArrayList(services));
        }

        comboBox.setDisplayFunction(service ->
                String.format("%s (%s)", service.getNomService(), service.getCodeService()));

        comboBox.setPromptText("Sélectionnez un service...");

        return comboBox;
    }

    /**
     * Crée un ComboBox auto-complete pour les centres
     */
    public static com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<Centre> createForCentres(List<Centre> centres) {
        com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<Centre> comboBox = new com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<>();

        if (centres != null) {
            comboBox.setOriginalItems(FXCollections.observableArrayList(centres));
        }

        comboBox.setDisplayFunction(centre ->
                String.format("%s (%s)", centre.getNomCentre(), centre.getCodeCentre()));

        comboBox.setPromptText("Sélectionnez un centre...");

        return comboBox;
    }

    /**
     * Crée un ComboBox auto-complete pour les banques
     */
    public static com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<Banque> createForBanques(List<Banque> banques) {
        com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<Banque> comboBox = new com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<>();

        if (banques != null) {
            comboBox.setOriginalItems(FXCollections.observableArrayList(banques));
        }

        comboBox.setDisplayFunction(banque ->
                String.format("%s (%s)", banque.getNomBanque(), banque.getCodeBanque()));

        comboBox.setPromptText("Sélectionnez une banque...");

        return comboBox;
    }

    /**
     * Crée un ComboBox auto-complete avec recherche distante
     */
    public static <T> com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<T> createWithRemoteSearch(
            Function<String, List<T>> searchFunction,
            Function<T, String> displayFunction,
            String promptText) {

        com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<T> comboBox = new com.regulation.contentieux.ui.essaie.AutoCompleteComboBox<>();
        comboBox.setSearchFunction(searchFunction);
        comboBox.setDisplayFunction(displayFunction);
        comboBox.setPromptText(promptText);

        return comboBox;
    }
}


