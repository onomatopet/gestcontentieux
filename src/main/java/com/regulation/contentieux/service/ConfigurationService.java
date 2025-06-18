package com.regulation.contentieux.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Service de configuration de l'application
 */
public class ConfigurationService {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);
    private static ConfigurationService instance;
    private Properties properties;

    private ConfigurationService() {
        loadConfiguration();
    }

    public static ConfigurationService getInstance() {
        if (instance == null) {
            instance = new ConfigurationService();
        }
        return instance;
    }

    private void loadConfiguration() {
        properties = new Properties();

        // Charger application.properties
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
                logger.info("Configuration chargée depuis application.properties");
            } else {
                logger.warn("Fichier application.properties non trouvé");
            }
        } catch (IOException e) {
            logger.error("Erreur lors du chargement de la configuration", e);
        }

        // Charger database.properties
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("database.properties")) {
            if (input != null) {
                properties.load(input);
                logger.info("Configuration base de données chargée");
            }
        } catch (IOException e) {
            logger.error("Erreur lors du chargement de la configuration base de données", e);
        }
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public Properties getAllProperties() {
        return new Properties(properties);
    }
}
