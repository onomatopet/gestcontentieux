package com.regulation.contentieux.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implémentation simple du moteur de templates
 * Support des variables {{variable}}, boucles {{#each}} et conditions {{#if}}
 *
 * @author Équipe Contentieux
 * @since 1.0.0
 */
public class SimpleTemplateEngine implements TemplateEngine {

    private static final Logger logger = LoggerFactory.getLogger(SimpleTemplateEngine.class);
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    @Override
    public String render(String templateName, Map<String, Object> context) {
        String template = loadTemplate(templateName);
        return renderFromString(template, context);
    }

    @Override
    public String renderFromString(String template, Map<String, Object> context) {
        if (template == null || template.isEmpty()) {
            return "";
        }

        String result = template;

        // 1. Traitement des boucles {{#each items}}...{{/each}}
        result = processLoops(result, context);

        // 2. Traitement des conditions {{#if condition}}...{{/if}}
        result = processConditions(result, context);

        // 3. Remplacement des variables {{variable}}
        result = replaceVariables(result, context);

        return result;
    }

    /**
     * Charge un template depuis les ressources
     */
    private String loadTemplate(String templateName) {
        return templateCache.computeIfAbsent(templateName, this::loadTemplateFromResource);
    }

    /**
     * Charge un template depuis le fichier de ressources
     */
    private String loadTemplateFromResource(String templateName) {
        try {
            String resourcePath = "/templates/" + templateName + ".html";
            InputStream is = getClass().getResourceAsStream(resourcePath);

            if (is == null) {
                logger.warn("Template non trouvé: {}", resourcePath);
                return getDefaultTemplate(templateName);
            }

            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            logger.debug("Template chargé avec succès: {}", templateName);
            return content;

        } catch (Exception e) {
            logger.error("Erreur lors du chargement du template: {}", templateName, e);
            return getDefaultTemplate(templateName);
        }
    }

    /**
     * Template par défaut en cas d'erreur
     */
    private String getDefaultTemplate(String templateName) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>{{titre}}</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; text-align: center; }
                    .header { margin-bottom: 30px; }
                    .info { background: #f0f8ff; padding: 20px; border: 1px solid #ccc; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>{{titre}}</h1>
                    <p><strong>Période :</strong> {{dateDebut}} au {{dateFin}}</p>
                </div>
                <div class="info">
                    <h3>Template en cours de développement</h3>
                    <p>Template <strong>""" + templateName + """
                    </strong> sera disponible prochainement.</p>
                    <p><em>Les données ont été générées avec succès.</em></p>
                </div>
                <div style="margin-top: 30px; font-size: 12px;">
                    Généré le {{dateGeneration}}
                </div>
            </body>
            </html>
            """;
    }

    /**
     * Traite les boucles {{#each collection}}...{{/each}}
     */
    private String processLoops(String template, Map<String, Object> context) {
        Pattern loopPattern = Pattern.compile("\\{\\{#each\\s+(\\w+)\\}\\}(.*?)\\{\\{/each\\}\\}", Pattern.DOTALL);
        Matcher matcher = loopPattern.matcher(template);

        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String listName = matcher.group(1);
            String loopTemplate = matcher.group(2);

            Object listObj = context.get(listName);
            StringBuilder loopResult = new StringBuilder();

            if (listObj instanceof Collection<?> collection) {
                int index = 0;
                for (Object item : collection) {
                    Map<String, Object> itemContext = new HashMap<>(context);
                    itemContext.put("this", item);
                    itemContext.put("index", index);
                    itemContext.put("isFirst", index == 0);
                    itemContext.put("isLast", index == collection.size() - 1);

                    // Ajouter les propriétés de l'objet au contexte
                    if (item != null) {
                        addObjectProperties(itemContext, item);
                    }

                    String renderedItem = renderFromString(loopTemplate, itemContext);
                    loopResult.append(renderedItem);
                    index++;
                }
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(loopResult.toString()));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Traite les conditions {{#if variable}}...{{/if}}
     */
    private String processConditions(String template, Map<String, Object> context) {
        Pattern condPattern = Pattern.compile("\\{\\{#if\\s+(\\w+)\\}\\}(.*?)\\{\\{/if\\}\\}", Pattern.DOTALL);
        Matcher matcher = condPattern.matcher(template);

        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String condition = matcher.group(1);
            String conditionTemplate = matcher.group(2);

            Object value = context.get(condition);
            boolean isTrue = isConditionTrue(value);

            String replacement = isTrue ? renderFromString(conditionTemplate, context) : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Remplace les variables {{variable}}
     */
    private String replaceVariables(String template, Map<String, Object> context) {
        Pattern varPattern = Pattern.compile("\\{\\{([^#/][^}]*)\\}\\}");
        Matcher matcher = varPattern.matcher(template);

        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String variablePath = matcher.group(1).trim();
            Object value = resolveVariablePath(variablePath, context);
            String formattedValue = formatValue(value);

            matcher.appendReplacement(result, Matcher.quoteReplacement(formattedValue));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Résout un chemin de variable (ex: "rapport.totalMontant")
     */
    private Object resolveVariablePath(String path, Map<String, Object> context) {
        String[] parts = path.split("\\.");
        Object current = context.get(parts[0]);

        for (int i = 1; i < parts.length && current != null; i++) {
            current = getProperty(current, parts[i]);
        }

        return current;
    }

    /**
     * Récupère une propriété d'un objet par réflexion
     */
    private Object getProperty(Object obj, String propertyName) {
        if (obj == null) return null;

        try {
            Class<?> clazz = obj.getClass();

            // Essayer getter standard
            String getterName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            try {
                Method getter = clazz.getMethod(getterName);
                return getter.invoke(obj);
            } catch (NoSuchMethodException ignored) {}

            // Essayer getter boolean
            String boolGetterName = "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            try {
                Method boolGetter = clazz.getMethod(boolGetterName);
                return boolGetter.invoke(obj);
            } catch (NoSuchMethodException ignored) {}

            logger.debug("Propriété non trouvée: {} sur {}", propertyName, clazz.getSimpleName());
            return null;

        } catch (Exception e) {
            logger.warn("Erreur lors de l'accès à la propriété {} sur {}: {}",
                    propertyName, obj.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Évalue si une condition est vraie
     */
    private boolean isConditionTrue(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) return !((String) value).isEmpty();
        if (value instanceof Collection) return !((Collection<?>) value).isEmpty();
        return true;
    }

    /**
     * Ajoute les propriétés d'un objet au contexte
     */
    private void addObjectProperties(Map<String, Object> context, Object obj) {
        try {
            Class<?> clazz = obj.getClass();
            for (Method method : clazz.getMethods()) {
                String methodName = method.getName();

                // Getters
                if (methodName.startsWith("get") && methodName.length() > 3 &&
                        method.getParameterCount() == 0 && !methodName.equals("getClass")) {

                    String propertyName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                    Object value = method.invoke(obj);
                    context.put(propertyName, value);
                }

                // Boolean getters (is...)
                if (methodName.startsWith("is") && methodName.length() > 2 &&
                        method.getParameterCount() == 0 && method.getReturnType() == boolean.class) {

                    String propertyName = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
                    Object value = method.invoke(obj);
                    context.put(propertyName, value);
                }
            }
        } catch (Exception e) {
            logger.warn("Erreur lors de l'extraction des propriétés de: {}", obj.getClass().getSimpleName(), e);
        }
    }

    /**
     * Formate une valeur pour l'affichage HTML
     */
    private String formatValue(Object value) {
        if (value == null) return "";

        if (value instanceof BigDecimal) {
            return String.format("%,.0f", ((BigDecimal) value).doubleValue());
        }

        if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }

        if (value instanceof Collection) {
            return ""; // Les collections sont traitées dans les boucles
        }

        return value.toString();
    }

    private String resolvePlaceholder(String key, Map<String, Object> context) {
        if (key.contains(".")) {
            String[] parts = key.split("\\.", 2);
            Object obj = context.get(parts[0]);

            if (obj != null) {
                try {
                    // Pour les propriétés imbriquées
                    String propertyName = parts[1];
                    String getterName = "get" + propertyName.substring(0, 1).toUpperCase() +
                            propertyName.substring(1);

                    Method getter = obj.getClass().getMethod(getterName);
                    Object value = getter.invoke(obj);

                    return formatValue(value);
                } catch (Exception e) {
                    logger.warn("Impossible de résoudre: {}", key);
                    return "";
                }
            }
        }

        Object value = context.get(key);
        return formatValue(value);
    }
}