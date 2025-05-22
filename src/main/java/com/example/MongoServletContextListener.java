package com.example;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebListener
public class MongoServletContextListener implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(MongoServletContextListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.info("ServletContext initialized. Attempting to connect to MongoDB.");
        try {
            // Initialize MongoDB connection on startup
            MongoDBConnection.getDatabase(); 
            logger.info("MongoDB connection should be initialized if not already.");
        } catch (Exception e) {
            logger.error("Failed to initialize MongoDB connection on startup: ", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        logger.info("ServletContext is being destroyed. Closing MongoDB connection.");
        MongoDBConnection.closeConnection();
        logger.info("MongoDB connection closed via ServletContextListener.");
    }
}
