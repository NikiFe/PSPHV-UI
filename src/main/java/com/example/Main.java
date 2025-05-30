package com.example;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Imports for the filter
import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.eclipse.jetty.servlets.CrossOriginFilter; // Keep if needed, or remove if only for headers
import javax.servlet.DispatcherType;
import java.util.EnumSet;

// Import the CsrfFilter
import com.example.CsrfFilter;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // Define SecurityHeadersFilter class
    public static class SecurityHeadersFilter implements Filter {
        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            // No init parameters needed
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            if (response instanceof HttpServletResponse) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setHeader("X-Content-Type-Options", "nosniff");
                httpResponse.setHeader("X-Frame-Options", "SAMEORIGIN"); // Or DENY if you don't embed elsewhere

                // Determine WebSocket scheme
                String wsScheme = "ws://";
                if (request.isSecure() || "https".equalsIgnoreCase(request.getScheme())) {
                    wsScheme = "wss://";
                }
                String wsConnectSrc = wsScheme + request.getServerName() + ":" + request.getServerPort();

                // Define CSP directives
                // Start with a reasonably strict policy. Adjust if necessary.
                String cspDirectives = "default-src 'self'; " +
                                     "script-src 'self' https://cdn.tailwindcss.com 'sha256-osMYrv5MtkdHa0ijz1PNNThFOAQEpqOTVcRIWv0aaRA='; " +
                                     // TODO: 'unsafe-inline' for style-src is generally discouraged. Review if it can be removed or replaced with hashes/nonces for better security.
                                     "style-src 'self' https://fonts.googleapis.com 'unsafe-inline'; " + 
                                     "font-src 'self' https://fonts.gstatic.com; " +
                                     "connect-src 'self' " + wsConnectSrc + "; " + // For WebSocket
                                     "img-src 'self' data:; " + // Allow self and data: URLs for images (if any)
                                     "object-src 'none'; " +
                                     "frame-ancestors 'self';"; // Similar to X-Frame-Options

                httpResponse.setHeader("Content-Security-Policy", cspDirectives);
            }
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
            // No resources to release
        }
    }

    public static void main(String[] args) {
        // Define server port
        int port = 8080;
        Server server = new Server();

        // Create a ServerConnector to listen on all interfaces
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        // Optionally, set host to "0.0.0.0" to bind to all IPv4 addresses
        connector.setHost("0.0.0.0");
        server.addConnector(connector);

        // Create a ServletContextHandler with sessions enabled
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/"); // Set root context

        // Configure session cookie security
        if (context.getSessionHandler() != null && context.getSessionHandler().getSessionCookieConfig() != null) {
            context.getSessionHandler().getSessionCookieConfig().setHttpOnly(true);
            // Set Secure flag for session cookies.
            // IMPORTANT: This should only be enabled if the application is exclusively served over HTTPS.
            // For local HTTP development, setting this to true will prevent cookies from being sent.
            // Consider making this configurable via an environment variable for production deployments.
            boolean forceSecureCookies = Boolean.parseBoolean(System.getenv("FORCE_SECURE_COOKIES")); // Example: use env var
            if (forceSecureCookies) {
                 context.getSessionHandler().getSessionCookieConfig().setSecure(true);
                 logger.info("Session cookies will be marked as Secure.");
            } else {
                 logger.warn("Session cookies will NOT be marked as Secure. Ensure FORCE_SECURE_COOKIES is true in HTTPS environments.");
            }
        } else {
            logger.warn("SessionHandler or SessionCookieConfig is null, could not configure HttpOnly/Secure flags for session cookies.");
        }

        server.setHandler(context); // Assign the handler to the server

        try {
            // Add SeatWebSocketServlet to handle WebSocket connections at /ws/seat/*
            ServletHolder wsHolder = new ServletHolder("ws-handler", new SeatWebSocketServlet());
            context.addServlet(wsHolder, "/ws/seat/*"); // WebSocket endpoint at /ws/seat/*

            // Add ParliamentServlet to handle HTTP API requests at /api/*
            ServletHolder parliamentServletHolder = new ServletHolder(new ParliamentServlet());
            context.addServlet(parliamentServletHolder, "/api/*");

            // Add default servlet for serving static content (e.g., index.html)
            ServletHolder defaultServlet = new ServletHolder("default",
                    org.eclipse.jetty.servlet.DefaultServlet.class);
            defaultServlet.setInitParameter("resourceBase", "src/main/resources/webapp");
            defaultServlet.setInitParameter("dirAllowed", "false"); // Set dirAllowed to false
            context.addServlet(defaultServlet, "/");

            // Add the SecurityHeadersFilter (should be early in the chain)
            context.addFilter(SecurityHeadersFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));

            // Add the CsrfFilter for API paths
            // It's generally good to have security filters like CSRF before the main servlet handling logic.
            // Ensure it's mapped correctly to protect your API endpoints.
            context.addFilter(CsrfFilter.class, "/api/*", EnumSet.of(DispatcherType.REQUEST));

            // Start the Jetty server
            server.start();
            logger.info("Server started at http://{}:{}/", connector.getHost(), port);
            server.join(); // Keep the server running
        } catch (Throwable t) {
            logger.error("Error starting Jetty server: ", t);
            System.exit(1);
        } finally {
            server.destroy();
        }
    }
}
