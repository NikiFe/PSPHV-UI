package com.example;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoDBConnection {
    private static final Logger logger = LoggerFactory.getLogger(MongoDBConnection.class);

    private static final String CONNECTION_STRING = "mongodb://localhost:27017"; // Update as needed
    private static final String DATABASE_NAME = "parliament";

    private static MongoClient mongoClient = null;
    private static MongoDatabase database = null;

    // Initialize the MongoDB connection
    public static synchronized MongoDatabase getDatabase() {
        if (mongoClient == null) {
            try {
                mongoClient = MongoClients.create(CONNECTION_STRING);
                database = mongoClient.getDatabase(DATABASE_NAME);
                logger.info("Connected to MongoDB at '{}', database '{}'.", CONNECTION_STRING, DATABASE_NAME);
            } catch (Exception e) {
                logger.error("Failed to connect to MongoDB: ", e);
                throw new RuntimeException("Failed to connect to MongoDB.", e);
            }
        }
        return database;
    }

    // Hash a plain-text password using BCrypt
    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    }

    // Verify a plain-text password against a hashed password
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }

    // Close the MongoDB connection
    public static synchronized void closeConnection() {
        if (mongoClient != null) {
            mongoClient.close();
            logger.info("MongoDB connection closed.");
        }
    }
}
