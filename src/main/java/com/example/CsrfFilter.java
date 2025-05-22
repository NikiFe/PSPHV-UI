package com.example;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CsrfFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(CsrfFilter.class);
    public static final String CSRF_TOKEN_SESSION_ATTR_NAME = "csrfToken";
    public static final String CSRF_TOKEN_HEADER_NAME = "X-CSRF-TOKEN";

    // Paths that do not require CSRF token validation (typically login/register)
    private final List<String> excludedPaths = Arrays.asList("/api/login", "/api/register");

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization code, if any
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String method = httpRequest.getMethod();
        String path = httpRequest.getRequestURI();

        // Only apply CSRF protection to state-changing methods
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            // Exempt specific paths (login, register)
            if (excludedPaths.contains(path)) {
                logger.debug("CSRF filter excluded path: {} {}", method, path);
                chain.doFilter(request, response);
                return;
            }

            HttpSession session = httpRequest.getSession(false); // Do not create a new session if one doesn't exist

            if (session == null) {
                logger.warn("CSRF check failed: No session for {} {} (Request likely not authenticated)", method, path);
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF Token Validation Failed: No active session.");
                return;
            }

            String sessionToken = (String) session.getAttribute(CSRF_TOKEN_SESSION_ATTR_NAME);
            String requestToken = httpRequest.getHeader(CSRF_TOKEN_HEADER_NAME);

            if (sessionToken == null) {
                logger.warn("CSRF check failed: No CSRF token in session for {} {}. User may need to re-authenticate.", method, path);
                // This might happen if the session is valid but the token wasn't generated or was cleared.
                // Could also indicate an attempt to access a protected resource without full login completion.
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF Token Validation Failed: Token missing in session.");
                return;
            }

            if (requestToken == null || !sessionToken.equals(requestToken)) {
                logger.warn("CSRF check failed: Invalid or missing token in request. Session token: '{}', Request token: '{}' for {} {}",
                        sessionToken.substring(0, Math.min(sessionToken.length(), 8)) + "...", // Log only a prefix
                        requestToken != null ? requestToken.substring(0, Math.min(requestToken.length(), 8)) + "..." : "null",
                        method, path);
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF Token Validation Failed: Invalid token.");
                return;
            }
            logger.debug("CSRF token validated for {} {}", method, path);
        } else {
             logger.debug("CSRF filter bypassed for non-state-changing method: {} {}", method, path);
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // Cleanup code, if any
    }
} 