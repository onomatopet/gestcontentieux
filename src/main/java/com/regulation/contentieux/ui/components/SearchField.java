package com.regulation.contentieux.ui.components;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * ENRICHISSEMENT UI - Champ de recherche avec debounce
 * Évite les recherches trop fréquentes lors de la saisie
 */
public class SearchField extends HBox {

    private static final Logger logger = LoggerFactory.getLogger(SearchField.class);

    // Configuration
    private static final Duration DEBOUNCE_DURATION = Duration.millis(300);

    // Composants
    private final TextField textField;
    private final Label searchIcon;
    private final Label clearIcon;

    // Fonctionnalité
    private Timeline searchTimeline;
    private Consumer<String> onSearchAction;
    private String lastSearchText = "";

    /**
     * Constructeur
     */
    public SearchField() {
        super(5); // Espacement de 5px

        // Icône de recherche
        searchIcon = new Label("🔍");
        searchIcon.getStyleClass().add("search-icon");

        // Champ de texte
        textField = new TextField();
        textField.setPromptText("Rechercher...");
        textField.getStyleClass().add("search-field");
        HBox.setHgrow(textField, Priority.ALWAYS);

        // Icône de suppression
        clearIcon = new Label("✖");
        clearIcon.getStyleClass().add("clear-icon");
        clearIcon.setVisible(false);
        clearIcon.setOnMouseClicked(e -> clearSearch());

        // Assemblage
        getChildren().addAll(searchIcon, textField, clearIcon);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("search-container");

        setupEventHandlers();
    }

    /**
     * Configuration des gestionnaires d'événements
     */
    private void setupEventHandlers() {
        // Debounce sur la saisie
        textField.textProperty().addListener((obs, oldValue, newValue) -> {
            handleTextChange(newValue);
        });

        // Affichage de l'icône clear
        textField.textProperty().addListener((obs, oldValue, newValue) -> {
            clearIcon.setVisible(newValue != null && !newValue.trim().isEmpty());
        });

        // Action sur Enter
        textField.setOnAction(e -> performSearchNow());
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
     * Effectue la recherche
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

        // Exécuter l'action de recherche
        if (onSearchAction != null) {
            try {
                onSearchAction.accept(searchText);
            } catch (Exception e) {
                logger.error("Erreur lors de la recherche", e);
            }
        }
    }

    /**
     * Effectue la recherche immédiatement (Enter)
     */
    private void performSearchNow() {
        if (searchTimeline != null) {
            searchTimeline.stop();
        }
        performSearch(textField.getText());
    }

    /**
     * Efface la recherche
     */
    private void clearSearch() {
        textField.clear();
        textField.requestFocus();
    }

    // ==================== MÉTHODES PUBLIQUES ====================

    /**
     * Définit l'action de recherche
     */
    public void setOnSearch(Consumer<String> onSearchAction) {
        this.onSearchAction = onSearchAction;
    }

    /**
     * Obtient le champ de texte pour configuration avancée
     */
    public TextField getTextField() {
        return textField;
    }

    /**
     * Définit le texte de placeholder
     */
    public void setPromptText(String promptText) {
        textField.setPromptText(promptText);
    }

    /**
     * Obtient le texte actuel
     */
    public String getText() {
        return textField.getText();
    }

    /**
     * Définit le texte
     */
    public void setText(String text) {
        textField.setText(text);
    }

    /**
     * Focus sur le champ
     */
    public void requestFocus() {
        textField.requestFocus();
    }
}