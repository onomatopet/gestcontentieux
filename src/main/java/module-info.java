module com.regulation.contentieux {
    // JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.swing;
    requires javafx.media;

    // AtlantaFX
    requires atlantafx.base;

    // Base de données
    requires java.sql;
    requires com.zaxxer.hikari;

    // Logging
    requires org.slf4j;
    requires ch.qos.logback.classic;

    // JSON
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    // PDF et Excel - COMMENTÉ TEMPORAIREMENT
    // requires kernel;
    // requires layout;
    // requires html2pdf;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;

    // Contrôles étendus
    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;

    // Icônes
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.material2;
    requires org.kordamp.ikonli.feather;
    requires bcrypt;

    // Sécurité - COMMENTÉ TEMPORAIREMENT
    // requires com.favre.lib.bcrypt;

    // Exports pour FXML
    exports com.regulation.contentieux;
    exports com.regulation.contentieux.controller;
    exports com.regulation.contentieux.model;
    exports com.regulation.contentieux.model.enums;
    exports com.regulation.contentieux.service;
    exports com.regulation.contentieux.util;

    // Opens pour FXML et reflection
    opens com.regulation.contentieux to javafx.fxml;
    opens com.regulation.contentieux.controller to javafx.fxml;
    opens com.regulation.contentieux.model to javafx.fxml, com.fasterxml.jackson.databind;
    opens com.regulation.contentieux.model.enums to javafx.fxml, com.fasterxml.jackson.databind;
    opens com.regulation.contentieux.service to javafx.fxml;
    // SUPPRIMÉ: opens com.regulation.contentieux.view to javafx.fxml;
}