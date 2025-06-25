package com.regulation.contentieux.service;

import java.util.Map;

/**
 * Interface pour le moteur de templates HTML
 * Permet le rendu de templates avec des données dynamiques
 *
 * @author Équipe Contentieux
 * @since 1.0.0
 */
public interface TemplateEngine {

    /**
     * Rend un template à partir de son nom avec le contexte fourni
     *
     * @param templateName nom du template (sans extension)
     * @param context variables à injecter dans le template
     * @return HTML généré
     */
    String render(String templateName, Map<String, Object> context);

    /**
     * Rend un template à partir d'une chaîne avec le contexte fourni
     *
     * @param template contenu du template
     * @param context variables à injecter dans le template
     * @return HTML généré
     */
    String renderFromString(String template, Map<String, Object> context);
}