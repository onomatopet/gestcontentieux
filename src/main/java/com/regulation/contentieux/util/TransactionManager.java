package com.regulation.contentieux.util;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.exception.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Callable;

/**
 * Gestionnaire de transactions pour garantir l'atomicité des opérations
 * Pattern Singleton pour gérer les transactions de manière centralisée
 */
public class TransactionManager {

    private static final Logger logger = LoggerFactory.getLogger(TransactionManager.class);
    private static TransactionManager instance;

    // Thread-local pour stocker la connexion courante par thread
    private static final ThreadLocal<Connection> currentConnection = new ThreadLocal<>();
    private static final ThreadLocal<Integer> transactionDepth = new ThreadLocal<>();

    private TransactionManager() {
        // Constructeur privé pour singleton
    }

    public static synchronized TransactionManager getInstance() {
        if (instance == null) {
            instance = new TransactionManager();
        }
        return instance;
    }

    /**
     * Exécute une opération dans une transaction
     * Gère automatiquement commit/rollback
     *
     * @param operation L'opération à exécuter
     * @return Le résultat de l'opération
     * @throws TransactionException si une erreur survient
     */
    public <T> T executeInTransaction(Callable<T> operation) {
        boolean isNewTransaction = false;
        Connection conn = null;

        try {
            // Vérifier si on est déjà dans une transaction
            conn = currentConnection.get();
            if (conn == null) {
                // Nouvelle transaction
                isNewTransaction = true;
                conn = DatabaseConfig.getSQLiteConnection();
                conn.setAutoCommit(false);
                currentConnection.set(conn);
                transactionDepth.set(1);
                logger.debug("🔄 Nouvelle transaction démarrée");
            } else {
                // Transaction imbriquée
                Integer depth = transactionDepth.get();
                transactionDepth.set(depth + 1);
                logger.debug("🔄 Transaction imbriquée niveau: {}", depth + 1);
            }

            // Exécuter l'opération
            T result = operation.call();

            // Commit si c'est la transaction principale
            if (isNewTransaction) {
                conn.commit();
                logger.debug("✅ Transaction committée avec succès");
            }

            return result;

        } catch (Exception e) {
            // Rollback en cas d'erreur
            if (conn != null && isNewTransaction) {
                try {
                    conn.rollback();
                    logger.error("❌ Transaction annulée suite à une erreur", e);
                } catch (SQLException ex) {
                    logger.error("Erreur lors du rollback", ex);
                }
            }

            throw new TransactionException("Erreur lors de l'exécution de la transaction", e);

        } finally {
            // Nettoyer si c'est la transaction principale
            if (isNewTransaction && conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Erreur lors de la fermeture de la connexion", e);
                }
                currentConnection.remove();
                transactionDepth.remove();
                logger.debug("🔒 Transaction fermée");
            } else if (!isNewTransaction) {
                // Décrémenter le niveau de transaction imbriquée
                Integer depth = transactionDepth.get();
                if (depth != null && depth > 1) {
                    transactionDepth.set(depth - 1);
                }
            }
        }
    }

    /**
     * Exécute une opération dans une transaction sans valeur de retour
     */
    public void executeInTransaction(Runnable operation) {
        executeInTransaction(() -> {
            operation.run();
            return null;
        });
    }

    /**
     * Obtient la connexion courante de la transaction
     * Utile pour les DAO qui ont besoin de la connexion transactionnelle
     */
    public static Connection getCurrentConnection() {
        return currentConnection.get();
    }

    /**
     * Vérifie si on est actuellement dans une transaction
     */
    public static boolean isInTransaction() {
        return currentConnection.get() != null;
    }

    /**
     * Obtient le niveau de profondeur de la transaction courante
     */
    public static int getTransactionDepth() {
        Integer depth = transactionDepth.get();
        return depth != null ? depth : 0;
    }
}