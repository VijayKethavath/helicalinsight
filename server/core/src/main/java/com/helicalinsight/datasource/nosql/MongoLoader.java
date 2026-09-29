package com.helicalinsight.datasource.nosql;

import com.google.gson.JsonObject;
import com.helicalinsight.datasource.GsonUtility;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MongoLoader is a concrete implementation of {@link NoSQLLoader} for MongoDB connectivity.
 * <p>
 * It is registered as a Spring bean named "mongo" so that {@code NoSqlUtils.getNoSqlImplementation("mongo")}
 * can resolve it at runtime. This follows the exact same pattern used for other NoSQL sub-types in
 * Helical Insight: a bean name that matches the {@code subType} field supplied in the connection form.
 * </p>
 *
 * <p><b>Supported connection form fields:</b></p>
 * <ul>
 *   <li>{@code jdbcUrl}  – full MongoDB URI, e.g. {@code mongodb://host:27017/mydb}
 *       (preferred, takes precedence over individual fields)</li>
 *   <li>{@code url}      – alias for jdbcUrl</li>
 *   <li>{@code hostName} – MongoDB host (default: localhost)</li>
 *   <li>{@code port}     – MongoDB port (default: 27017)</li>
 *   <li>{@code databaseName} / {@code database} – MongoDB database name</li>
 *   <li>{@code userName} / {@code username}     – authentication username (optional)</li>
 *   <li>{@code password}                         – authentication password (optional)</li>
 * </ul>
 *
 * @author Assessment – Helical Insight
 * @see NoSQLLoader
 * @see com.helicalinsight.efw.utility.NoSqlUtils
 */
@Component("mongo")
public class MongoLoader extends NoSQLLoader {

    private static final Logger logger = LoggerFactory.getLogger(MongoLoader.class);

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 27017;

    /**
     * Loads the MongoDB data source into the middleware layer.
     * <p>
     * For MongoDB, "loading to middleware" means verifying that a connection
     * can be established. If the connection succeeds the method returns {@code true};
     * any exception causes it to return {@code false} and logs the error.
     * </p>
     *
     * @param formData JSON object containing connection parameters
     * @return {@code true} if the connection was successfully established
     */
    @Override
    public boolean loadToMiddleWare(JsonObject formData) {
        logger.info("MongoLoader.loadToMiddleWare: registering MongoDB data source");
        return testConnection(formData);
    }

    /**
     * Tests the MongoDB connection using parameters from the supplied {@link JsonObject}.
     *
     * @param formData JSON object containing connection parameters
     * @return {@code true} if a MongoDB connection could be established, {@code false} otherwise
     */
    @Override
    public boolean testConnection(JsonObject formData) {
        MongoClient mongoClient = null;
        try {
            mongoClient = buildMongoClient(formData);
            // Calling listDatabaseNames() forces an actual network round-trip,
            // which is the lightest-weight way to verify connectivity.
            mongoClient.listDatabaseNames().first();
            logger.info("MongoLoader: MongoDB connection test successful");
            return true;
        } catch (Exception e) {
            logger.error("MongoLoader: MongoDB connection test failed – {}", e.getMessage(), e);
            return false;
        } finally {
            if (mongoClient != null) {
                try {
                    mongoClient.close();
                } catch (Exception closeEx) {
                    logger.warn("MongoLoader: error closing MongoClient – {}", closeEx.getMessage());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link MongoClient} from the connection parameters in {@code formData}.
     * <p>
     * Resolution order for connection details:
     * <ol>
     *   <li>If {@code jdbcUrl} or {@code url} is present and non-blank, use it as a
     *       {@link MongoClientURI}.</li>
     *   <li>Otherwise, assemble a connection from {@code hostName}, {@code port},
     *       {@code databaseName}/{@code database}, {@code userName}/{@code username},
     *       and {@code password}.</li>
     * </ol>
     * </p>
     */
    private MongoClient buildMongoClient(JsonObject formData) {
        // 1. Try URI-based connection first
        String uri = resolveUri(formData);
        if (uri != null && !uri.trim().isEmpty()) {
            logger.debug("MongoLoader: connecting via URI");
            return new MongoClient(new MongoClientURI(uri));
        }

        // 2. Fall back to individual fields
        String host = optString(formData, "hostName");
        if (isBlank(host)) host = optString(formData, "host");
        if (isBlank(host)) host = DEFAULT_HOST;

        int port = DEFAULT_PORT;
        String portStr = optString(formData, "port");
        if (!isBlank(portStr)) {
            try {
                port = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException nfe) {
                logger.warn("MongoLoader: invalid port '{}', using default {}", portStr, DEFAULT_PORT);
            }
        }

        ServerAddress serverAddress = new ServerAddress(host, port);

        String database = resolveDatabase(formData);
        String userName = resolveUsername(formData);
        String password = optString(formData, "password");

        if (!isBlank(userName) && !isBlank(database)) {
            logger.debug("MongoLoader: connecting with credentials to {}:{}/{}", host, port, database);
            char[] passwordChars = isBlank(password) ? new char[0] : password.toCharArray();
            MongoCredential credential = MongoCredential.createCredential(userName, database, passwordChars);
            List<MongoCredential> credentials = new ArrayList<>();
            credentials.add(credential);
            return new MongoClient(serverAddress, credentials);
        }

        logger.debug("MongoLoader: connecting without credentials to {}:{}", host, port);
        return new MongoClient(serverAddress);
    }

    /** Returns the MongoDB URI string if one is supplied in {@code formData}. */
    private String resolveUri(JsonObject formData) {
        String uri = optString(formData, "jdbcUrl");
        if (isBlank(uri)) {
            uri = optString(formData, "url");
        }
        return uri;
    }

    /** Resolves the database name from multiple possible field names. */
    private String resolveDatabase(JsonObject formData) {
        String db = optString(formData, "databaseName");
        if (isBlank(db)) db = optString(formData, "database");
        if (isBlank(db)) db = optString(formData, "dbName");
        return db;
    }

    /** Resolves the username from multiple possible field names. */
    private String resolveUsername(JsonObject formData) {
        String user = optString(formData, "userName");
        if (isBlank(user)) user = optString(formData, "username");
        return user;
    }

    /** Null-safe string extraction from a JsonObject. */
    private String optString(JsonObject json, String key) {
        return GsonUtility.optString(json, key);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
