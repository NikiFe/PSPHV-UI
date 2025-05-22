package com.example;

import java.util.stream.Collectors;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId; // Import ObjectId
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.UUID; // For CSRF token generation

import static com.mongodb.client.model.Filters.eq;

public class ParliamentServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(ParliamentServlet.class);

    private final MongoCollection<Document> usersCollection;
    private final MongoCollection<Document> proposalsCollection;
    private final MongoCollection<Document> votesCollection;
    private final MongoCollection<Document> votingLogsCollection;
    private final MongoCollection<Document> fineReasonsCollection;
    private final MongoCollection<Document> systemParametersCollection;
    private final MongoCollection<Document> proposalCountersCollection;
    private final MongoCollection<Document> pendingProposalsCollection;
    private final MongoCollection<Document> parliamentQueueCollection;

    private String discordWebhookUrl;

    public ParliamentServlet() {
        MongoDatabase database = MongoDBConnection.getDatabase();
        this.usersCollection = database.getCollection("users");
        this.proposalsCollection = database.getCollection("proposals");
        this.votesCollection = database.getCollection("votes");
        this.votingLogsCollection = database.getCollection("votingLogs");
        this.fineReasonsCollection = database.getCollection("fineReasons");
        this.systemParametersCollection = database.getCollection("systemParameters");
        this.proposalCountersCollection = database.getCollection("proposalCounters");
        this.pendingProposalsCollection = database.getCollection("pendingProposals");
        this.parliamentQueueCollection = database.getCollection("parliamentQueue");

        // Ensure break status is initialized if missing
        initializeBreakStatus();

        // Initialize meeting number if missing
        initializeMeetingNumber();

        // Initialize Discord webhook URL
        initializeDiscordWebhookUrl();
    }

    private void initializeDiscordWebhookUrl() {
        discordWebhookUrl = System.getenv("DISCORD_WEBHOOK_URL");
        if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) {
            logger.warn("Discord webhook URL not configured. Messages will not be sent to Discord.");
        }
    }

    private void initializeBreakStatus() {
        Document breakStatus = systemParametersCollection.find(Filters.eq("parameter", "breakStatus")).first();
        if (breakStatus == null) {
            systemParametersCollection.insertOne(new Document("parameter", "breakStatus").append("value", false));
        }
    }

    private void initializeMeetingNumber() {
        Document meetingDoc = systemParametersCollection.find(Filters.eq("parameter", "meetingNumber")).first();
        if (meetingDoc == null) {
            systemParametersCollection.insertOne(new Document("parameter", "meetingNumber").append("value", 1));
        }
    }

    private void setBreakStatus(boolean status) {
        systemParametersCollection.updateOne(
                Filters.eq("parameter", "breakStatus"),
                new Document("$set", new Document("value", status))
        );
    }

    private boolean isBreakActive() {
        Document breakStatus = systemParametersCollection.find(Filters.eq("parameter", "breakStatus")).first();
        return breakStatus != null && breakStatus.getBoolean("value", false);
    }

    private int getCurrentMeetingNumber() {
        Document meetingDoc = systemParametersCollection.find(Filters.eq("parameter", "meetingNumber")).first();
        return meetingDoc != null ? meetingDoc.getInteger("value", 1) : 1;
    }

    private void incrementMeetingNumber() {
        systemParametersCollection.updateOne(
                Filters.eq("parameter", "meetingNumber"),
                new Document("$inc", new Document("value", 1))
        );
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();

        switch (path) {
            case "/login":
                handleLogin(request, response);
                break;
            case "/logout":
                handleLogout(request, response);
                break;
            case "/join-seat":
                handleJoinSeat(request, response);
                break;
            case "/users/update-status":
                handleUpdateStatus(request, response);
                break;
            case "/proposals":
                handleNewProposal(request, response);
                break;
            case "/proposals/vote":
                handleSubmitVote(request, response);
                break;
            case "/proposals/end-voting":
                handleEndVoting(request, response);
                break;
            case "/proposals/end-voting-priority":
                handleEndPriorityVoting(request, response);
                break;
            case "/proposals/end-voting-constitutional":
                handleEndVotingConstitutional(request, response);
                break;
            case "/impose-fine":
                handleImposeFine(request, response);
                break;
            case "/break":
                handleCallBreak(request, response);
                break;
            case "/end-break":
                handleEndBreak(request, response);
                break;
            case "/end-session":
                handleEndSession(request, response);
                break;
            case "/register":
                handleRegister(request, response);
                break;
            case "/elections/results":
                handleElectionResults(request, response);
                break;
            case "/users/update":
                handleUpdateUsers(request, response);
                break;
            case "/proposals/submit":
                handlePlayerSubmitProposal(request, response);
                break;
            case "/queue/request-speak":
                handleRequestSpeak(request, response);
                break;
            default:
                if (path != null && path.startsWith("/proposals/pending/")) {
                    String[] parts = path.split("/");
                    if (parts.length == 5 && "pending".equals(parts[2])) { // /proposals/pending/{id}/action
                        String pendingProposalId = parts[3];
                        String action = parts[4];
                        if ("approve".equals(action)) {
                            handleApprovePendingProposal(request, response, pendingProposalId);
                        } else if ("reject".equals(action)) {
                            handleRejectPendingProposal(request, response, pendingProposalId);
                        } else {
                            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid action for pending proposal.");
                            logger.warn("Invalid action '{}' for pending proposal ID '{}'.", action, pendingProposalId);
                        }
                    } else {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");
                        logger.warn("Unknown POST endpoint structure for /proposals/pending/: {}", path);
                    }
                } else if (path != null && path.startsWith("/queue/set-active/")) {
                    String[] parts = path.split("/");
                    if (parts.length == 4) { // /queue/set-active/{itemId}
                        String itemId = parts[3];
                        handleQueueSetActive(request, response, itemId);
                    } else {
                         response.sendError(HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");
                         logger.warn("Unknown POST endpoint structure for /queue/set-active/: {}", path);
                    }
                } else if (path != null && path.startsWith("/queue/complete-active/")) {
                    String[] parts = path.split("/");
                    if (parts.length == 4) { // /queue/complete-active/{itemId}
                        String itemId = parts[3];
                        handleQueueCompleteActive(request, response, itemId);
                    } else {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");
                        logger.warn("Unknown POST endpoint structure for /queue/complete-active/: {}", path);
                    }
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");
                    logger.warn("Unknown POST endpoint: {}", path);
                }
                break;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();

        if (path != null) {
            if (path.equals("/proposals")) {
                handleGetProposals(request, response);
            } else if (path.startsWith("/proposals/")) {
                handleGetProposalById(request, response);
            } else if (path.equals("/users")) {
                handleGetUsers(request, response);
            } else if (path.startsWith("/users/")) {
                handleGetUserById(request, response);
            } else if (path.equals("/user-info")) {
                handleUserInfo(request, response);
            } else if (path.equals("/queue")) {
                handleGetQueue(request, response);
            } else if (path.equals("/system/break-status")) {
                handleGetBreakStatus(request, response);
            } else if (path.equals("/proposals/pending")) {
                handleGetPendingProposals(request, response);
            } else if (path.equals("/parliament-queue/view")) {
                handleGetParliamentQueue(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");
                logger.warn("Unknown GET endpoint: {}", path);
            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");
            logger.warn("Unknown GET endpoint: path is null");
        }
    }

    private void handleGetBreakStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        JSONObject breakStatus = new JSONObject();
        breakStatus.put("breakActive", isBreakActive());
        response.getWriter().write(breakStatus.toString());
    }

    // Handle proposal by ID
    private void handleGetProposalById(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String proposalId = request.getPathInfo().split("/")[2];

            Document proposal = proposalsCollection.find(eq("_id", new ObjectId(proposalId))).first();
            if (proposal != null) {
                JSONObject proposalJson = new JSONObject();
                // Convert '_id' to 'id'
                proposalJson.put("id", proposal.getObjectId("_id").toHexString());
                // Client-side code is responsible for HTML escaping this value if rendered in HTML to prevent XSS.
                proposalJson.put("title", proposal.getString("title")); 
                // Client-side code is responsible for HTML escaping this value if rendered in HTML to prevent XSS.
                proposalJson.put("party", proposal.getString("party")); 
                // Add other fields from 'proposal' document as needed, ensuring to respect original structure
                // For example, if proposal.toJson() included other fields, add them explicitly too.
                // This example assumes only id, title, and party were directly relevant for this specific transformation.
                // If other fields from proposal.toJson() are expected by client, they must be added here.
                // For instance:
                proposalJson.put("proposalNumber", proposal.getInteger("proposalNumber"));
                proposalJson.put("isPriority", proposal.getBoolean("isPriority"));
                proposalJson.put("isConstitutional", proposal.getBoolean("isConstitutional"));
                proposalJson.put("voteRequirement", proposal.getString("voteRequirement"));
                proposalJson.put("stupid", proposal.getBoolean("stupid"));
                proposalJson.put("associationType", proposal.getString("associationType"));
                proposalJson.put("referencedProposal", proposal.getString("referencedProposal"));
                proposalJson.put("proposalVisual", proposal.getString("proposalVisual"));
                proposalJson.put("meetingNumber", proposal.getInteger("meetingNumber"));
                proposalJson.put("passed", proposal.getBoolean("passed"));
                proposalJson.put("totalFor", proposal.getInteger("totalFor"));
                proposalJson.put("totalAgainst", proposal.getInteger("totalAgainst"));
                proposalJson.put("votingEnded", proposal.getBoolean("votingEnded"));


                response.setContentType("application/json");
                response.getWriter().write(proposalJson.toString());
                logger.info("Fetched proposal with id '{}'.", proposalId);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Proposal not found.");
                logger.warn("Proposal with id '{}' not found.", proposalId);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid proposal ID format: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid proposal ID format.");
        } catch (Exception e) {
            logger.error("Error during fetching proposal by id: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while fetching the proposal.");
        }
    }

    // Handle user registration
    private void handleRegister(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                sb.append(line);
            }
            JSONObject registerJson = new JSONObject(sb.toString());
            String username = registerJson.getString("username").trim();
            String password = registerJson.getString("password").trim();
            String role = registerJson.optString("role", "MEMBER").trim();

            if (username.isEmpty() || password.isEmpty() || role.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Username, password, and role cannot be empty.");
                logger.warn("Registration attempt with empty fields.");
                return;
            }

            Document existingUser = usersCollection.find(new Document("username", username)).first();
            if (existingUser != null) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "Username already exists.");
                logger.warn("Registration attempt with existing username '{}'.", username);
                return;
            }

            String hashedPassword = MongoDBConnection.hashPassword(password);

            Document newUser = new Document("username", username)
                    .append("password", hashedPassword)
                    .append("role", role.toUpperCase())
                    .append("present", false)
                    .append("seatStatus", "NEUTRAL")
                    .append("fines", 0)
                    .append("partyAffiliation", "")
                    .append("electoralStrength", 1);

            usersCollection.insertOne(newUser);
            logger.info("New user '{}' registered successfully with role '{}'.", username, role);

            response.setStatus(HttpServletResponse.SC_CREATED);
            JSONObject resp = new JSONObject();
            // Client-side code is responsible for HTML escaping this value if rendered in HTML to prevent XSS.
            resp.put("message", "Registration successful. Please log in.");
            response.setContentType("application/json");
            response.getWriter().write(resp.toString());

        } catch (org.json.JSONException je) {
            logger.warn("Malformed JSON in request to {}: {}", request.getRequestURI(), je.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON in request body.");
        } catch (Exception e) {
            logger.error("Error during registration at {}: ", request.getRequestURI(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred during registration.");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();

        if (path.startsWith("/proposals/")) {
            handleDeleteProposal(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");
        }
    }

    // Delete proposal by ID
    private void handleDeleteProposal(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
            String proposalIdString = request.getPathInfo().split("/")[2];

            try {
                ObjectId proposalId = new ObjectId(proposalIdString);
                Document query = new Document("_id", proposalId);
                proposalsCollection.deleteOne(query);

                // Broadcast the deletion event
                JSONObject deleteMsg = new JSONObject();
                deleteMsg.put("type", "proposalDelete");
                deleteMsg.put("proposalId", proposalIdString);
                SeatWebSocket.broadcast(deleteMsg);
                logger.info("Broadcasted proposalDelete for id '{}'.", proposalIdString);

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Proposal deleted successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President deleted proposal with id '{}'.", proposalIdString);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid proposal ID format: {}", proposalIdString);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid proposal ID format.");
            } catch (Exception e) {
                logger.error("Error deleting proposal: ", e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while deleting the proposal.");
            }
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can delete proposals.");
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();

        if (path.startsWith("/proposals/")) {
            handleUpdateProposal(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");
        }
    }

    /**
     * Updates an existing proposal. The President can update:
     * - Title
     * - Party
     * - "stupid" flag
     * (Any combination of the above fields if present in JSON).
     */
    private void handleUpdateProposal(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
            String proposalIdString = request.getPathInfo().split("/")[2]; // Renamed for clarity
            ObjectId proposalId = new ObjectId(proposalIdString);

            try {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject updateData = new JSONObject(sb.toString());
                Document setFields = new Document();

                if (updateData.has("title")) {
                    String title = updateData.getString("title");
                    if (title.trim().isEmpty()) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Proposal title cannot be empty.");
                        return;
                    }
                    setFields.put("title", title);
                }

                if (updateData.has("party")) {
                    String party = updateData.getString("party").trim();
                    if (party.isEmpty()) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Party cannot be empty.");
                        return;
                    }
                    setFields.put("party", party);
                }

                if (updateData.has("stupid")) {
                    boolean newStupidValue = updateData.getBoolean("stupid");
                    Document existingProposal = proposalsCollection.find(eq("_id", proposalId)).first();
                    if (existingProposal == null) {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND, "Proposal not found.");
                        return;
                    }
                    boolean oldStupidValue = existingProposal.getBoolean("stupid", false);
                    boolean alreadyEnded = existingProposal.getBoolean("votingEnded", false);
                    if (!oldStupidValue && newStupidValue) {
                        setFields.put("votingEnded", true);
                    } else if (oldStupidValue && !newStupidValue) {
                        if (alreadyEnded) {
                            setFields.put("votingEnded", false);
                        }
                    }
                    setFields.put("stupid", newStupidValue);
                }

                if (setFields.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "No fields to update.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    return;
                }

                Document query = new Document("_id", proposalId);
                Document updateDoc = new Document("$set", setFields);
                proposalsCollection.updateOne(query, updateDoc);

                // Fetch the updated proposal to broadcast its latest state
                Document updatedProposalDoc = proposalsCollection.find(eq("_id", proposalId)).first();
                if (updatedProposalDoc != null) {
                    JSONObject updatedProposalJson = new JSONObject(updatedProposalDoc.toJson());
                    updatedProposalJson.put("id", updatedProposalDoc.getObjectId("_id").toHexString());
                    updatedProposalJson.remove("_id");

                    JSONObject proposalUpdateMsg = new JSONObject();
                    proposalUpdateMsg.put("type", "proposalUpdate");
                    proposalUpdateMsg.put("proposal", updatedProposalJson);
                    SeatWebSocket.broadcast(proposalUpdateMsg);
                    logger.info("Broadcasted proposalUpdate for id '{}' after president update.", proposalIdString);
                }

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Proposal updated successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President updated proposal (id '{}') with data: {}", proposalIdString, setFields.toJson());

            } catch (IllegalArgumentException e) {
                logger.error("Invalid proposal ID format: {}", proposalIdString);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid proposal ID format.");
            } catch (org.json.JSONException je) {
                logger.warn("Malformed JSON in request to {}: {}", request.getRequestURI(), je.getMessage());
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON in request body.");
            } catch (Exception e) {
                logger.error("Error updating proposal at {}: ", request.getRequestURI(), e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while updating the proposal.");
            }
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can update proposals.");
        }
    }

    // Handle user login
    private void handleLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                sb.append(line);
            }
            JSONObject loginJson = new JSONObject(sb.toString());
            String username = loginJson.getString("username").trim();
            String password = loginJson.getString("password").trim();

            if (username.isEmpty() || password.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Username and password cannot be empty.");
                logger.warn("Login attempt with empty username or password.");
                return;
            }

            Document query = new Document("username", username);
            Document userDoc = usersCollection.find(query).first();

            if (userDoc != null) {
                String hashedPassword = userDoc.getString("password");
                if (MongoDBConnection.verifyPassword(password, hashedPassword)) {
                    HttpSession session = request.getSession(true);
                    session.setAttribute("username", username);
                    session.setAttribute("role", userDoc.getString("role"));
                    session.setAttribute("userId", userDoc.getObjectId("_id").toHexString());
                    session.setAttribute("electoralStrength", userDoc.getInteger("electoralStrength", 1));

                    // Generate and store CSRF token
                    String csrfToken = UUID.randomUUID().toString();
                    session.setAttribute(CsrfFilter.CSRF_TOKEN_SESSION_ATTR_NAME, csrfToken);

                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    // Client-side code is responsible for HTML escaping this value if rendered in HTML to prevent XSS.
                    resp.put("message", "Login successful.");
                    resp.put(CsrfFilter.CSRF_TOKEN_SESSION_ATTR_NAME, csrfToken); // Send token to client
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    logger.info("User '{}' logged in successfully. CSRF token generated.", username);
                    return;
                }
            }

            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid username or password.");
            logger.warn("Failed login attempt for username '{}'.", username);
        } catch (org.json.JSONException je) {
            logger.warn("Malformed JSON in request to {}: {}", request.getRequestURI(), je.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON in request body.");
        } catch (Exception e) {
            logger.error("Error during login at {}: ", request.getRequestURI(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred during login.");
        }
    }

    private void handleUserInfo(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("username") != null) {
            String username = (String) session.getAttribute("username");
            Document userDoc = usersCollection.find(eq("username", username)).first();
            if (userDoc != null) {
                JSONObject userJson = new JSONObject();
                userJson.put("id", userDoc.getObjectId("_id").toHexString());
                // Client-side code is responsible for HTML escaping these values if rendered in HTML to prevent XSS.
                userJson.put("username", userDoc.getString("username"));
                userJson.put("role", userDoc.getString("role"));
                userJson.put("partyAffiliation", userDoc.getString("partyAffiliation"));
                
                // Add other non-sensitive fields as needed
                userJson.put("present", userDoc.getBoolean("present", false));
                userJson.put("seatStatus", userDoc.getString("seatStatus"));
                userJson.put("fines", userDoc.getInteger("fines", 0));
                userJson.put("electoralStrength", userDoc.getInteger("electoralStrength",1));
                // userJson.remove("password"); // Not needed as we are selectively adding

                // Retrieve CSRF token from session and add to response
                String sessionToken = (String) session.getAttribute(CsrfFilter.CSRF_TOKEN_SESSION_ATTR_NAME);
                if (sessionToken != null) {
                    userJson.put(CsrfFilter.CSRF_TOKEN_HEADER_NAME, sessionToken); // Use consistent key name
                } else {
                    // This case is unlikely if the user is properly logged in via handleLogin,
                    // but log a warning if it happens.
                    logger.warn("CSRF token not found in session for authenticated user '{}' during user-info request.", username);
                }

                response.setContentType("application/json");
                response.getWriter().write(userJson.toString());
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found.");
            }
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
        }
    }

    // Handle user logout
    private void handleLogout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                String username = (String) session.getAttribute("username");
                if (username != null) {
                    usersCollection.updateOne(Filters.eq("username", username), Updates.set("present", false));
                }
                // Remove CSRF token from session
                session.removeAttribute(CsrfFilter.CSRF_TOKEN_SESSION_ATTR_NAME);
                logger.info("CSRF token removed for user '{}' during logout.", username);

                session.invalidate();
                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Logged out successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No active session.");
            }
        } catch (Exception e) {
            logger.error("Error during logout: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred during logout.");
        }
    }

    // Handle user joining a seat
    private void handleJoinSeat(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("username") != null) {
                String username = (String) session.getAttribute("username");

                Document query = new Document("username", username);
                Document update = new Document("$set", new Document("present", true).append("seatStatus", "NEUTRAL"));
                usersCollection.updateOne(query, update);

                Document userDoc = usersCollection.find(query).first();
                JSONObject userJson = new JSONObject(userDoc.toJson());
                userJson.put("id", userDoc.getObjectId("_id").toHexString());
                userJson.remove("_id");

                JSONObject seatUpdate = new JSONObject();
                seatUpdate.put("type", "seatUpdate");
                seatUpdate.put("user", userJson);
                SeatWebSocket.broadcast(seatUpdate);

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Joined seat successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("User '{}' joined a seat.", username);
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
                logger.warn("Unauthenticated attempt to join a seat.");
            }
        } catch (Exception e) {
            logger.error("Error during joining seat: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while joining the seat.");
        }
    }

    // Update seat status
    private void handleUpdateStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("username") != null) {
                String requesterUsername = (String) session.getAttribute("username");
                String requesterRole = (String) session.getAttribute("role");

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject statusUpdateJson = new JSONObject(sb.toString()); // Renamed for clarity
                String userIdStr = statusUpdateJson.getString("id");
                String newStatus = statusUpdateJson.getString("seatStatus");

                // Validate newStatus
                if (!isValidSeatStatus(newStatus)) {
                    logger.warn("Invalid seat status '{}' received for user ID '{}'.", newStatus, userIdStr);
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid seat status value provided: " + newStatus);
                    return;
                }

                ObjectId userObjectId;
                try {
                    userObjectId = new ObjectId(userIdStr);
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid user ID format '{}' in handleUpdateStatus.", userIdStr);
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid user ID format.");
                    return;
                }

                Document query = new Document("_id", userObjectId);
                Document userDoc = usersCollection.find(query).first();

                if (userDoc != null) {
                    String targetUsername = userDoc.getString("username");
                    String currentSeatStatus = userDoc.getString("seatStatus");

                    if (requesterUsername.equals(targetUsername)
                            && "OBJECTING".equals(currentSeatStatus)
                            && !"PRESIDENT".equals(requesterRole)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "You cannot cancel your own objection.");
                        logger.warn("User '{}' attempted to cancel their own objection.", requesterUsername);
                        return;
                    }

                    if (!requesterUsername.equals(targetUsername) && !"PRESIDENT".equals(requesterRole)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "You can only update your own status.");
                        logger.warn("User '{}' attempted to update status of '{}' without permission.", requesterUsername, targetUsername);
                        return;
                    }

                    Document update = new Document("$set", new Document("seatStatus", newStatus).append("present", true));
                    usersCollection.updateOne(query, update);

                    // Handle queue updates for objections
                    if ("OBJECTING".equals(newStatus)) {
                        // Remove any pending speaker requests for this user
                        parliamentQueueCollection.deleteMany(Filters.and(
                            Filters.eq("userId", userObjectId),
                            Filters.eq("type", "SPEAKER_REQUEST"),
                            Filters.eq("status", "pending")
                        ));
                        logger.info("Removed pending SPEAKER_REQUEST for user '{}' due to new OBJECTION.", targetUsername);

                        // Upsert an objection item
                        Document objectionQuery = Filters.and(
                            Filters.eq("userId", userObjectId),
                            Filters.eq("type", "OBJECTION") 
                        );
                        Document objectionUpdate = new Document("$set", new Document("username", targetUsername) // XSS: Client responsible for escaping
                                                                    .append("timestamp", new Date())
                                                                    .append("priority", 1) // Highest priority for objections
                                                                    .append("status", "pending"))
                                                    .append("$setOnInsert", new Document("userId", userObjectId).append("type", "OBJECTION"));
                        UpdateOptions options = new UpdateOptions().upsert(true);
                        parliamentQueueCollection.updateOne(objectionQuery, objectionUpdate, options);
                        logger.info("Upserted OBJECTION for user '{}' into queue.", targetUsername);
                        broadcastQueueUpdate(); // Ensure queue is updated
                    } else if ("OBJECTING".equals(currentSeatStatus) && !"OBJECTING".equals(newStatus) && "PRESIDENT".equals(requesterRole)) {
                        // President changed status from OBJECTING to something else (e.g., NEUTRAL, SPEAKING)
                        // Note: If changing to SPEAKING because they were selected from queue, handleQueueSetActive will manage it.
                        // This handles direct cancellation by President.
                        Document objectionQuery = Filters.and(
                            Filters.eq("userId", userObjectId),
                            Filters.eq("type", "OBJECTION"),
                            Filters.eq("status", "pending") // Only affect pending objections
                        );
                        Document objectionUpdate = new Document("$set", new Document("status", "completed")
                                                                    .append("completedTimestamp", new Date()));
                        var result = parliamentQueueCollection.updateOne(objectionQuery, objectionUpdate);
                        if (result.getModifiedCount() > 0) {
                           logger.info("President '{}' changed status of user '{}' from OBJECTION. Objection queue item marked completed.", requesterUsername, targetUsername);
                           broadcastQueueUpdate();
                        }
                    }


                    Document updatedUserDoc = usersCollection.find(query).first();
                    JSONObject userJsonForResponse = new JSONObject(updatedUserDoc.toJson());
                    userJsonForResponse.put("id", updatedUserDoc.getObjectId("_id").toHexString());
                    userJsonForResponse.remove("_id");
                    userJsonForResponse.remove("password"); // Ensure password is not sent in HTTP response

                    // Prepare user JSON for WebSocket broadcast (can be the same or slightly different if needed)
                    JSONObject userJsonForBroadcast = new JSONObject(userJsonForResponse.toString()); // Create a copy for broadcast

                    // Broadcast the change via WebSocket
                    JSONObject seatUpdateMsg = new JSONObject();
                    seatUpdateMsg.put("type", "seatUpdate");
                    seatUpdateMsg.put("user", userJsonForBroadcast);
                    SeatWebSocket.broadcast(seatUpdateMsg);
                    logger.info("Broadcasted seatUpdate for user '{}' due to status change.", targetUsername);

                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("application/json");
                    response.getWriter().write(userJsonForResponse.toString()); // Return the updated user document
                    logger.info("User '{}' updated seat status of '{}' to '{}'. Returned updated user data.", requesterUsername, targetUsername, newStatus);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found.");
                    logger.warn("User with ID '{}' not found.", userIdStr);
                }
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
                logger.warn("Unauthenticated attempt to update seat status.");
            }
        } catch (org.json.JSONException je) {
            logger.warn("Malformed JSON in request to {}: {}", request.getRequestURI(), je.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON in request body.");
        } catch (Exception e) {
            logger.error("Error during updating seat status at {}: ", request.getRequestURI(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while updating seat status.");
        }
    }

    // Validate seat status
    private boolean isValidSeatStatus(String status) {
        return "REQUESTING_TO_SPEAK".equals(status) ||
                "SPEAKING".equals(status) ||
                "OBJECTING".equals(status) ||
                "NEUTRAL".equals(status);
    }

    // Add a new proposal (President only)
    private void handleNewProposal(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject proposalJson = new JSONObject(sb.toString());
                String title = proposalJson.getString("title");
                String party = proposalJson.optString("party", "President").trim();
                boolean priority = proposalJson.optBoolean("priority", false);
                // NEW: Read the constitutional flag and vote requirement
                boolean constitutional = proposalJson.optBoolean("constitutional", false);
                String voteRequirement = proposalJson.optString("voteRequirement", "Rel").trim();
                String type = proposalJson.optString("type", "normal").trim();
                String associatedProposal = proposalJson.optString("assProposal", "").trim();

                int proposalNumber;
                String proposalVisual;
                if (constitutional) {
                    proposalNumber = getNextConstitutionalProposalNumber();
                    proposalVisual = "C" + proposalNumber;
                } else if (priority) {
                    proposalNumber = getNextProposalNumber(true);
                    proposalVisual = "P" + proposalNumber;
                } else {
                    proposalNumber = getNextProposalNumber(false);
                    proposalVisual = String.valueOf(proposalNumber);
                }
                if (!type.equals("normal")) {
                    proposalVisual += (type.equals("additive") ? " → " : type.equals("countering") ? " x " : "") + associatedProposal;
                }

                if (title.trim().isEmpty()) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Proposal title cannot be empty.");
                    logger.warn("President attempted to add a proposal with empty title.");
                    return;
                }

                int meetingNumber = getCurrentMeetingNumber();

                Document proposalDoc = new Document("title", title)
                        .append("proposalNumber", proposalNumber)
                        .append("party", party)
                        .append("isPriority", priority)
                        // NEW: Add the constitutional flag and vote requirement field
                        .append("isConstitutional", constitutional)
                        .append("voteRequirement", voteRequirement)
                        .append("stupid", false)
                        .append("associationType", type)
                        .append("referencedProposal", associatedProposal)
                        .append("proposalVisual", proposalVisual)
                        .append("meetingNumber", meetingNumber)
                        .append("passed", false)
                        .append("totalFor", 0)
                        .append("totalAgainst", 0)
                        .append("votingEnded", false);
                proposalsCollection.insertOne(proposalDoc);

                Document insertedProposal = proposalsCollection.find(Filters.eq("proposalNumber", proposalNumber)).first();
                if (insertedProposal != null) {
                    JSONObject insertedProposalJson = new JSONObject(insertedProposal.toJson());
                    String id = insertedProposal.getObjectId("_id").toHexString();
                    insertedProposalJson.put("id", id);
                    insertedProposalJson.remove("_id");

                    JSONObject proposalUpdate = new JSONObject();
                    proposalUpdate.put("type", "proposalUpdate");
                    proposalUpdate.put("proposal", insertedProposalJson);
                    SeatWebSocket.broadcast(proposalUpdate);

                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    // Client-side code is responsible for HTML escaping this value if rendered in HTML to prevent XSS.
                    resp.put("message", "New proposal added successfully.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    logger.info("President added new proposal: '{}'", title);
                    repopulateProposalQueue(); 
                } else {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to retrieve inserted proposal.");
                }
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can add proposals.");
                logger.warn("Non-president attempted to add a new proposal.");
            }
        } catch (org.json.JSONException je) {
            logger.warn("Malformed JSON in request to {}: {}", request.getRequestURI(), je.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON in request body.");
        } catch (Exception e) {
            logger.error("Error during adding new proposal at {}: ", request.getRequestURI(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while adding the proposal.");
        }
    }

    private int getNextAtomicProposalNumber(String counterName) {
        Document query = new Document("_id", counterName);
        // Increment the sequence_value by 1. If the field doesn't exist, $inc creates it and sets it to 1.
        Document update = new Document("$inc", new Document("sequence_value", 1));
        
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                .upsert(true) // Create the document if it doesn't exist
                .returnDocument(ReturnDocument.AFTER); // Return the document after the update

        Document result = proposalCountersCollection.findOneAndUpdate(query, update, options);

        if (result == null) {
            // This should ideally not happen with upsert=true and ReturnDocument.AFTER
            // if Mongo itself has an issue or there's a misconfiguration.
            logger.error("CRITICAL: findOneAndUpdate with upsert=true returned null for counter '{}'. This indicates a potential issue with MongoDB or driver configuration.", counterName);
            // Fallback strategy: attempt a direct read. If it was just created, it should be there.
            Document fallbackResult = proposalCountersCollection.find(Filters.eq("_id", counterName)).first();
            if (fallbackResult != null && fallbackResult.getInteger("sequence_value") != null) {
                 logger.warn("Fallback read succeeded for counter '{}'.", counterName);
                 return fallbackResult.getInteger("sequence_value");
            }
            // If we reach here, something is seriously wrong.
            throw new RuntimeException("Failed to retrieve or initialize proposal counter for '" + counterName + "' even after fallback.");
        }
        return result.getInteger("sequence_value");
    }

    private int getNextConstitutionalProposalNumber() {
        return getNextAtomicProposalNumber("constitutionalProposal");
    }

    // Handle submitting a vote
    private void handleSubmitVote(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("username") != null) {
                String username = (String) session.getAttribute("username");
                String userId = (String) session.getAttribute("userId");
                int electoralStrength = (int) session.getAttribute("electoralStrength");

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject voteJson = new JSONObject(sb.toString());
                String proposalIdStr = voteJson.getString("proposalId");
                String voteChoice = voteJson.getString("voteChoice");

                if (!Arrays.asList("For", "Against", "Abstain").contains(voteChoice)) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid vote choice.");
                    logger.warn("User '{}' submitted an invalid vote choice '{}'.", username, voteChoice);
                    return;
                }

                ObjectId proposalObjectId;
                ObjectId userObjectIdFromString; 
                try {
                    proposalObjectId = new ObjectId(proposalIdStr); 
                    userObjectIdFromString = new ObjectId(userId); // userId is from session attribute
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid ObjectId format. Proposal ID: '{}', User ID from session: '{}'. Error: {}", proposalIdStr, userId, e.getMessage());
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid ID format provided.");
                    return;
                }

                Document proposal = proposalsCollection.find(eq("_id", proposalObjectId)).first();
                if (proposal == null || proposal.getBoolean("votingEnded", false)) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid proposal or voting has ended.");
                    return;
                }
                // If it's stupid => no voting
                if (proposal.getBoolean("stupid", false)) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "This proposal is marked as stupid; no voting allowed.");
                    return;
                }

                Document voteRecord = new Document("proposalId", proposalObjectId)
                        .append("userId", userObjectIdFromString)
                        .append("username", username)
                        .append("voteChoice", voteChoice)
                        .append("electoralStrength", electoralStrength)
                        .append("timestamp", new Date());

                votesCollection.updateOne(
                        Filters.and(eq("proposalId", proposalObjectId), eq("userId", userObjectIdFromString)),
                        new Document("$set", voteRecord),
                        new UpdateOptions().upsert(true)
                );

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Vote submitted successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("User '{}' voted on proposal '{}': '{}'", username, proposalIdStr, voteChoice);
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
                logger.warn("Unauthenticated attempt to submit a vote.");
            }
        } catch (org.json.JSONException je) {
            logger.warn("Malformed JSON in request to {}: {}", request.getRequestURI(), je.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON in request body.");
        } catch (Exception e) {
            logger.error("Error during submitting vote at {}: ", request.getRequestURI(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while submitting the vote.");
        }
    }

    /**
     * Ends voting for normal proposals (isPriority=false, stupid=false).
     * Then sends all ended proposals to Discord (priority first, then normal).
     */
    private void handleEndVoting(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                List<Document> proposals = proposalsCollection.find(
                        Filters.and(
                                Filters.eq("votingEnded", false),
                                Filters.eq("isPriority", false),
                                Filters.eq("isConstitutional", false),
                                Filters.eq("stupid", false)
                        )
                ).into(new ArrayList<>());

                if (proposals.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "No normal proposals to end. Possibly only priority, constitutional, or stupid proposals remain.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    return;
                }

                Map<String, Integer> adjustedMap = computeAdjustedElectoralStrengths();
                for (Document proposal : proposals) {
                    endProposalVoting(proposal, adjustedMap);
                }
                
                // Broadcast that proposal states have been updated
                JSONObject updateMsg = new JSONObject().put("type", "proposalsUpdated");
                SeatWebSocket.broadcast(updateMsg);
                logger.info("Broadcasted proposalsUpdated after ending normal voting.");

                sendVotingResultsToDiscord();
                incrementMeetingNumber();
                repopulateProposalQueue(); // Call to repopulate and broadcast queue

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Voting ended for normal proposals and votes counted successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President ended normal proposals' voting.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can end voting.");
            }
        } catch (Exception e) {
            logger.error("Error during ending voting: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while ending voting.");
        }
    }

    /**
     * Ends voting for priority proposals (isPriority=true, stupid=false).
     * No Discord message is sent here—only in handleEndVoting.
     */
    private void handleEndPriorityVoting(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                List<Document> proposals = proposalsCollection.find(
                        Filters.and(
                                Filters.eq("votingEnded", false),
                                Filters.eq("isPriority", true),
                                Filters.eq("stupid", false)
                        )
                ).into(new ArrayList<>());

                if (proposals.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "No priority proposals to end, or they might be marked stupid.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    return;
                }

                Map<String, Integer> adjustedMap = computeAdjustedElectoralStrengths();
                for (Document proposal : proposals) {
                    endProposalVoting(proposal, adjustedMap);
                }

                // Broadcast that proposal states have been updated
                JSONObject updateMsg = new JSONObject().put("type", "proposalsUpdated");
                SeatWebSocket.broadcast(updateMsg);
                logger.info("Broadcasted proposalsUpdated after ending priority voting.");
                repopulateProposalQueue(); // Call to repopulate and broadcast queue

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Priority voting ended. Votes counted, no Discord message yet.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President ended priority proposals' voting.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can end priority voting.");
            }
        } catch (Exception e) {
            logger.error("Error during ending priority voting: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while ending priority voting.");
        }
    }

    private void handleEndVotingConstitutional(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                List<Document> proposals = proposalsCollection.find(
                        Filters.and(
                                Filters.eq("votingEnded", false),
                                Filters.eq("isConstitutional", true),
                                Filters.eq("stupid", false)
                        )
                ).into(new ArrayList<>());
                if (proposals.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "No constitutional proposals to end, or they might be marked stupid.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    return;
                }

                Map<String, Integer> adjustedMap = computeAdjustedElectoralStrengths();
                for (Document proposal : proposals) {
                    endProposalVoting(proposal, adjustedMap);
                }

                // Broadcast that proposal states have been updated
                JSONObject updateMsg = new JSONObject().put("type", "proposalsUpdated");
                SeatWebSocket.broadcast(updateMsg);
                logger.info("Broadcasted proposalsUpdated after ending constitutional voting.");
                repopulateProposalQueue(); // Call to repopulate and broadcast queue
                
                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Constitutional voting ended. Votes counted.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President ended constitutional proposals' voting.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can end constitutional voting.");
            }
        } catch (Exception e) {
            logger.error("Error during ending constitutional voting: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while ending constitutional voting.");
        }
    }

    private void endProposalVoting(Document proposal, Map<String, Integer> adjustedMap) {
        ObjectId proposalId = proposal.getObjectId("_id");
        List<Document> votes = votesCollection.find(Filters.eq("proposalId", proposalId)).into(new ArrayList<>());
        int totalFor = 0;
        int totalAgainst = 0;
        int supportersCount = 0;
        JSONArray detailedVotes = new JSONArray();

        for (Document vote : votes) {
            String voteChoice = vote.getString("voteChoice");
            String voterId = vote.getObjectId("userId").toHexString();
            String username = vote.getString("username");
            int voterAdjustedStrength = adjustedMap.getOrDefault(voterId, 0);
            Date timestamp = vote.getDate("timestamp");
            JSONObject detailedVote = new JSONObject();
            detailedVote.put("userId", voterId);
            detailedVote.put("username", username);
            detailedVote.put("voteChoice", voteChoice);
            detailedVote.put("electoralStrength", voterAdjustedStrength);
            if (timestamp != null) {
                detailedVote.put("timestamp", timestamp.toInstant().toString());
            }
            detailedVotes.put(detailedVote);

            if ("For".equalsIgnoreCase(voteChoice)) {
                totalFor += voterAdjustedStrength;
                supportersCount++;
            } else if ("Against".equalsIgnoreCase(voteChoice)) {
                totalAgainst += voterAdjustedStrength;
            }
        }

        boolean passed = false;
        String voteRequirement = proposal.getString("voteRequirement");
        if (voteRequirement == null) {
            voteRequirement = "Rel";
        }
        if ("Rel".equals(voteRequirement)) {
            // Rel: More For than Against and at least 2 For votes
            passed = (supportersCount >= 2 && totalFor > totalAgainst);
        } else {
            double ratio = 0.0;
            boolean useTotal = voteRequirement.endsWith("+");
            if (voteRequirement.startsWith("2/3")) {
                ratio = 2.0 / 3.0;
            } else if (voteRequirement.startsWith("3/5")) {
                ratio = 3.0 / 5.0;
            } else if (voteRequirement.startsWith("1/2")) {
                ratio = 1.0 / 2.0;
            }
            double denominator = useTotal ? getTotalElectoralStrength() : getTotalPresentElectoralStrength(adjustedMap);
            passed = (totalFor > ratio * denominator);
        }

        proposalsCollection.updateOne(
                Filters.eq("_id", proposalId),
                new Document("$set", new Document("passed", passed)
                        .append("totalFor", totalFor)
                        .append("totalAgainst", totalAgainst)
                        .append("votingEnded", true))
        );

        Document votingLog = new Document("proposalId", proposalId)
                .append("proposalTitle", proposal.getString("title"))
                .append("meetingNumber", proposal.getInteger("meetingNumber", 1))
                .append("votes", detailedVotes.toList())
                .append("timestamp", new Date());
        votingLogsCollection.insertOne(votingLog);
        logger.info("Proposal '{}': For = {}, Against = {}, Passed = {}",
                proposal.getString("title"), totalFor, totalAgainst, passed);
    }

    private int getTotalElectoralStrength() {
        List<Document> allUsers = usersCollection.find().into(new ArrayList<>());
        int sum = 0;
        for (Document u : allUsers) {
            sum += u.getInteger("electoralStrength", 0);
        }
        return sum;
    }

    private int getTotalPresentElectoralStrength(Map<String, Integer> adjustedMap) {
        int sum = 0;
        for (Integer strength : adjustedMap.values()) {
            sum += strength;
        }
        return sum;
    }

    private Map<String, Integer> computeAdjustedElectoralStrengths() {
        List<Document> allUsers = usersCollection.find().into(new ArrayList<>());

        List<Document> nezUsers = allUsers.stream()
                .filter(u -> "NEZ".equalsIgnoreCase(u.getString("partyAffiliation")))
                .collect(Collectors.toList());
        List<Document> otherUsers = allUsers.stream()
                .filter(u -> !"NEZ".equalsIgnoreCase(u.getString("partyAffiliation")))
                .collect(Collectors.toList());

        Map<String, Integer> adjustedMap = new HashMap<>();

        // NEZ users just keep their strength if present
        for (Document nez : nezUsers) {
            if (nez.getBoolean("present", false)) {
                String userId = nez.getObjectId("_id").toHexString();
                int es = nez.getInteger("electoralStrength", 0);
                adjustedMap.put(userId, es);
            }
        }

        // group others by party
        Map<String, List<Document>> usersByParty = otherUsers.stream()
                .collect(Collectors.groupingBy(u -> {
                    String partyAff = u.getString("partyAffiliation");
                    return (partyAff != null && !partyAff.trim().isEmpty()) ? partyAff : "Independent";
                }));

        for (Map.Entry<String, List<Document>> entry : usersByParty.entrySet()) {
            String party = entry.getKey();
            List<Document> partyUsers = entry.getValue();

            List<Document> presentUsers = new ArrayList<>();
            int sumPresent = 0;
            int sumAbsent = 0;

            for (Document user : partyUsers) {
                int es = user.getInteger("electoralStrength", 0);
                if (user.getBoolean("present", false)) {
                    presentUsers.add(user);
                    sumPresent += es;
                } else {
                    sumAbsent += es;
                }
            }

            if (sumPresent == 0) continue;

            for (Document pUser : presentUsers) {
                String userId = pUser.getObjectId("_id").toHexString();
                int original = pUser.getInteger("electoralStrength", 0);
                double adjustedDouble = original + ((double) original * sumAbsent / sumPresent);
                int adjusted = (int) Math.round(adjustedDouble);
                adjustedMap.put(userId, adjusted);
            }
        }

        return adjustedMap;
    }

    // Send Discord results with strikethrough for stupid proposals
    private void sendVotingResultsToDiscord() {
        try {
            if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) {
                logger.warn("Discord webhook URL is not configured. Skipping sending results to Discord.");
                return;
            }

            int meetingNumber = getCurrentMeetingNumber();

            // Fetch proposals from the DB which have ended voting in this meeting
            List<Document> endedProposals = proposalsCollection.find(
                    Filters.and(
                            Filters.eq("meetingNumber", meetingNumber),
                            Filters.eq("votingEnded", true)
                    )
            ).into(new ArrayList<>());

            // Separate them into priority vs. normal
            List<Document> priorityProposals = new ArrayList<>();
            List<Document> normalProposals = new ArrayList<>();
            for (Document p : endedProposals) {
                if (p.getBoolean("isPriority", false)) {
                    priorityProposals.add(p);
                } else {
                    normalProposals.add(p);
                }
            }

            // Fetch present users
            List<Document> presentUsers = usersCollection.find(eq("present", true)).into(new ArrayList<>());
            // Fetch the chairman/president
            Document chairman = usersCollection.find(eq("role", "PRESIDENT")).first();
            String chairmanName = (chairman != null) ? escapeDiscordMarkdown(chairman.getString("username")) : "N/A";

            // -----------------------
            // Build the main message
            // -----------------------
            StringBuilder msg = new StringBuilder();

            // "Jednání: **75**"
            msg.append("Jednání: **").append(meetingNumber).append("**\n");
            // "Účast: **2**"
            msg.append("Účast: **").append(presentUsers.size()).append("**\n");
            // "Předseda: **GeorgeH7**"
            msg.append("Předseda: **").append(chairmanName).append("**\n\n");

            // "Docházka:"
            msg.append("Docházka:\n");
            for (Document user : presentUsers) {
                String username = escapeDiscordMarkdown(user.getString("username"));
                String party = escapeDiscordMarkdown(user.getString("partyAffiliation"));
                int es = user.getInteger("electoralStrength", 0);

                // e.g. "**32** - MNSB - **PanNuggetek**"
                msg.append("**").append(es).append("** - ")
                        .append((party != null && !party.isEmpty()) ? party : "/")
                        .append(" - **").append(username).append("**\n");
            }
            msg.append("\n");

            // ---------------------------------------
            // Priority proposals first (no "PRIORITY" label in text)
            // ---------------------------------------
            for (Document p : priorityProposals) {
                boolean passed = p.getBoolean("passed", false);
                // int totalFor = p.getInteger("totalFor", 0); // Not directly used in message
                // int totalAgainst = p.getInteger("totalAgainst", 0); // Not directly used in message

                String resultEmoji = passed ? "✅" : "❌";
                String proposalVisual = escapeDiscordMarkdown(p.getString("proposalVisual"));
                String party = escapeDiscordMarkdown(p.getString("party"));
                String title = escapeDiscordMarkdown(p.getString("title"));
                boolean isStupid = p.getBoolean("stupid", false);

                // If "stupid", apply strikethrough AFTER escaping
                if (isStupid) {
                    title = "~~" + title + "~~";
                }

                // e.g.: "✅ **P1 VSP:** Title..."
                msg.append(resultEmoji).append(" **")
                        .append(proposalVisual).append(" ") // Already escaped
                        .append((party != null && !party.isEmpty()) ? party : "/") // Already escaped
                        .append(":** ")
                        .append(title) // Already escaped and potentially strikethroughed
                        .append("\n");
            }

            // ---------------------------------------
            // Normal proposals next
            // ---------------------------------------
            for (Document p : normalProposals) {
                boolean passed = p.getBoolean("passed", false);
                // int totalFor = p.getInteger("totalFor", 0); // Not directly used in message
                // int totalAgainst = p.getInteger("totalAgainst", 0); // Not directly used in message

                String resultEmoji = passed ? "✅" : "❌";
                String proposalVisual = escapeDiscordMarkdown(p.getString("proposalVisual"));
                String party = escapeDiscordMarkdown(p.getString("party"));
                String title = escapeDiscordMarkdown(p.getString("title"));
                boolean isStupid = p.getBoolean("stupid", false);

                // If "stupid", apply strikethrough AFTER escaping
                if (isStupid) {
                    title = "~~" + title + "~~";
                }

                // e.g.: "✅ **01 VSP:** Title..."
                msg.append(resultEmoji).append(" **")
                        .append(proposalVisual).append(" ") // Already escaped
                        .append((party != null && !party.isEmpty()) ? party : "/") // Already escaped
                        .append(":** ")
                        .append(title) // Already escaped and potentially strikethroughed
                        .append("\n");
            }

            // ---------------------------------------------------
            // Fines / Kázeňská opatření (optional, example logic)
            // ---------------------------------------------------
            msg.append("\nKázeňská opatření:\n");

            // If you have logic to fetch the fines for this meeting, do it here:
            List<Document> meetingFines = fineReasonsCollection.find(eq("meetingNumber", meetingNumber)).into(new ArrayList<>());

            if (meetingFines.isEmpty()) {
                // If no fines, print "--"
                msg.append("--\n");
            } else {
                // Example: "50 b.ch. - PanNuggetek (Reason: disruption)"
                for (Document fineDoc : meetingFines) {
                    String finedUser = escapeDiscordMarkdown(fineDoc.getString("username"));
                    int amount = fineDoc.getInteger("amount", 0);
                    String reason = escapeDiscordMarkdown(fineDoc.getString("reason"));
                    msg.append(amount).append(" b.ch. - ").append(finedUser)
                            .append(" (").append(reason).append(")\n");
                }
            }

            // -----------------------------------
            // Send final message to Discord
            // -----------------------------------
            sendDiscordWebhook(msg.toString());
            logger.info("Voting results sent to Discord using updated formatting.");

        } catch (Exception e) {
            logger.error("Error during sending voting results to Discord: ", e);
        }
    }

    private void sendDiscordWebhook(String content) {
        try {
            if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) {
                logger.warn("Discord webhook URL is not configured. Skipping sending results to Discord.");
                return;
            }

            List<String> chunks = splitContentIntoChunks(content);
            for (String chunk : chunks) {
                sendDiscordWebhookChunk(chunk);
            }
        } catch (Exception e) {
            logger.error("Error sending Discord webhook: ", e);
        }
    }

    private void sendDiscordWebhookChunk(String content) {
        try {
            URL url = new URL(discordWebhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            JSONObject payload = new JSONObject();
            payload.put("content", content);

            String jsonPayload = payload.toString();

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 204) {
                logger.error("Failed to send Discord webhook. Response code: " + responseCode);
            }

        } catch (Exception e) {
            logger.error("Error sending Discord webhook: ", e);
        }
    }

    private List<String> splitContentIntoChunks(String content) {
        List<String> chunks = new ArrayList<>();
        String[] lines = content.split("\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String line : lines) {
            if (currentChunk.length() + line.length() + 1 <= 2000) {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n");
                }
                currentChunk.append(line);
            } else {
                if (line.length() > 2000) {
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString());
                        currentChunk = new StringBuilder();
                    }
                    List<String> splitLines = splitLineIntoChunks(line, 2000);
                    chunks.addAll(splitLines);
                } else {
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString());
                        currentChunk = new StringBuilder();
                    }
                    currentChunk.append(line);
                }
            }
        }
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
        return chunks;
    }

    private List<String> splitLineIntoChunks(String line, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        if (line.length() <= maxChunkSize) {
            chunks.add(line);
        } else {
            String[] words = line.split(" ");
            StringBuilder currentChunk = new StringBuilder();
            for (String word : words) {
                if (currentChunk.length() + word.length() + 1 <= maxChunkSize) {
                    if (currentChunk.length() > 0) {
                        currentChunk.append(" ");
                    }
                    currentChunk.append(word);
                } else {
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString());
                        currentChunk = new StringBuilder();
                    }
                    if (word.length() > maxChunkSize) {
                        int index = 0;
                        while (index < word.length()) {
                            int endIndex = Math.min(index + maxChunkSize, word.length());
                            chunks.add(word.substring(index, endIndex));
                            index = endIndex;
                        }
                    } else {
                        currentChunk.append(word);
                    }
                }
            }
            if (currentChunk.length() > 0) {
                chunks.add(currentChunk.toString());
            }
        }
        return chunks;
    }

    // Handle entering election results (President only)
    private void handleElectionResults(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONArray electionResults = new JSONArray(sb.toString());

                for (int i = 0; i < electionResults.length(); i++) {
                    JSONObject result = electionResults.getJSONObject(i);
                    String username = result.getString("username");
                    int electoralStrength = result.getInt("electoralStrength");

                    usersCollection.updateOne(
                            eq("username", username),
                            new Document("$set", new Document("electoralStrength", electoralStrength))
                    );
                }

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Election results processed successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President updated electoral strengths from election results.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can submit election results.");
                logger.warn("Unauthorized attempt to submit election results.");
            }
        } catch (org.json.JSONException je) {
            logger.warn("Malformed JSON in request to {}: {}", request.getRequestURI(), je.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON in request body.");
        } catch (Exception e) {
            logger.error("Error during processing election results at {}: ", request.getRequestURI(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while processing election results.");
        }
    }

    // Handle updating user information (President only)
    private void handleUpdateUsers(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONArray userUpdates = new JSONArray(sb.toString());

                for (int i = 0; i < userUpdates.length(); i++) {
                    JSONObject userUpdate = userUpdates.getJSONObject(i);
                    String userIdStr = userUpdate.getString("id");
                    int electoralStrength = userUpdate.optInt("electoralStrength", 1);
                    String partyAffiliation = userUpdate.optString("partyAffiliation", "");
                    String role = userUpdate.optString("role", "MEMBER");

                    ObjectId userObjectId;
                    try {
                        userObjectId = new ObjectId(userIdStr);
                    } catch (IllegalArgumentException e) {
                        logger.warn("Invalid user ID format '{}' in handleUpdateUsers. Skipping update for this user.", userIdStr);
                        continue; // Skip to the next user in the loop
                    }

                    if (!Arrays.asList("MEMBER", "PRESIDENT", "OTHER_ROLE").contains(role)) {
                        logger.warn("Invalid role '{}' for user ID '{}'. Skipping update.", role, userIdStr);
                        continue;
                    }

                    Document updateFields = new Document()
                            .append("electoralStrength", electoralStrength)
                            .append("partyAffiliation", partyAffiliation)
                            .append("role", role);

                    usersCollection.updateOne(
                            eq("_id", userObjectId),
                            new Document("$set", updateFields)
                    );
                }

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "User updates processed successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President updated user information.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can update user information.");
                logger.warn("Unauthorized attempt to update user information.");
            }
        } catch (org.json.JSONException je) {
            logger.warn("Malformed JSON in request to {}: {}", request.getRequestURI(), je.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON in request body.");
        } catch (Exception e) {
            logger.error("Error during updating user information at {}: ", request.getRequestURI(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while updating user information.");
        }
    }

    // Handle imposing a fine on a user (President only)
    private void handleImposeFine(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject fineJson = new JSONObject(sb.toString());
                String usernameToFine = fineJson.getString("username").trim();
                int amount = fineJson.getInt("amount");

                if (!fineJson.has("reason")) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Fine reason is required.");
                    logger.warn("Fine reason missing for username '{}'.", usernameToFine);
                    return;
                }
                String reason = fineJson.getString("reason").trim();

                if (usernameToFine.isEmpty() || amount <= 0 || reason.isEmpty()) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid username, amount, or reason.");
                    logger.warn("President provided invalid data for imposing fine.");
                    return;
                }

                Document query = new Document("username", usernameToFine);
                Document userDoc = usersCollection.find(query).first();

                if (userDoc != null) {
                    usersCollection.updateOne(query, Updates.inc("fines", amount));

                    String fineId = "FINE-" + System.currentTimeMillis();
                    int meetingNumber = getCurrentMeetingNumber();

                    Document fineReasonDoc = new Document("fineId", fineId)
                            .append("username", usernameToFine)
                            .append("amount", amount)
                            .append("reason", reason)
                            .append("timestamp", new Date())
                            .append("issuedBy", (String) session.getAttribute("username"))
                            .append("status", "active")
                            .append("meetingNumber", meetingNumber);
                    fineReasonsCollection.insertOne(fineReasonDoc);

                    JSONObject fineImposed = new JSONObject();
                    fineImposed.put("type", "fineImposed");
                    fineImposed.put("username", usernameToFine);
                    fineImposed.put("amount", amount);
                    fineImposed.put("reason", reason);
                    SeatWebSocket.broadcast(fineImposed);

                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "Fine imposed successfully.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    logger.info("President imposed a fine of {} on '{}'. Reason: {}", amount, usernameToFine, reason);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "User to fine not found.");
                    logger.warn("President attempted to impose a fine on non-existent user '{}'.", usernameToFine);
                }
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can impose fines.");
                logger.warn("Unauthorized attempt to impose a fine.");
            }
        } catch (org.json.JSONException je) {
            logger.warn("Malformed JSON in request to {}: {}", request.getRequestURI(), je.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON in request body.");
        } catch (Exception e) {
            logger.error("Error during imposing fine at {}: ", request.getRequestURI(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while imposing the fine.");
        }
    }

    // Handle calling a break (President only)
    private void handleCallBreak(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                setBreakStatus(true);

                JSONObject breakNotification = new JSONObject();
                breakNotification.put("type", "break");
                SeatWebSocket.broadcast(breakNotification);

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Break has been called.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President called a break.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can call a break.");
                logger.warn("Non-president attempted to call a break.");
            }
        } catch (Exception e) {
            logger.error("Error during calling a break: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while calling a break.");
        }
    }

    // Handle ending a break
    private void handleEndBreak(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                setBreakStatus(false);

                JSONObject endBreakNotification = new JSONObject();
                endBreakNotification.put("type", "endBreak");
                SeatWebSocket.broadcast(endBreakNotification);

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Break has ended.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President ended the break.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can end the break.");
                logger.warn("Non-president attempted to end the break.");
            }
        } catch (Exception e) {
            logger.error("Error during ending the break: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while ending the break.");
        }
    }

    // Handle ending the session
    private void handleEndSession(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                Document update = new Document("$set", new Document("seatStatus", "NEUTRAL").append("present", false));
                usersCollection.updateMany(new Document(), update);

                JSONObject endSessionNotification = new JSONObject();
                endSessionNotification.put("type", "endSession");
                SeatWebSocket.broadcast(endSessionNotification);

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Session has been ended.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President ended the session.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can end the session.");
                logger.warn("Non-president attempted to end the session.");
            }
        } catch (Exception e) {
            logger.error("Error during ending session: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while ending the session.");
        }
    }

    // Fetch all users (updated to handle present parameter)
    private void handleGetUsers(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String presentParam = request.getParameter("present");
            List<Document> users;
            if ("true".equalsIgnoreCase(presentParam)) {
                users = usersCollection.find(eq("present", true)).into(new ArrayList<>());
            } else {
                users = usersCollection.find().into(new ArrayList<>());
            }

            JSONArray usersArray = new JSONArray();
            for (Document doc : users) {
                JSONObject userJson = new JSONObject();
                userJson.put("id", doc.getObjectId("_id").toHexString());
                // Client-side code is responsible for HTML escaping these values if rendered in HTML to prevent XSS.
                userJson.put("username", doc.getString("username"));
                userJson.put("role", doc.getString("role"));
                userJson.put("partyAffiliation", doc.getString("partyAffiliation"));
                
                // Add other non-sensitive fields as needed
                userJson.put("present", doc.getBoolean("present", false));
                userJson.put("seatStatus", doc.getString("seatStatus"));
                userJson.put("fines", doc.getInteger("fines", 0));
                userJson.put("electoralStrength", doc.getInteger("electoralStrength",1));
                // userJson.remove("password"); // Not needed as we are selectively adding

                String seatStatus = doc.getString("seatStatus");
                userJson.put("seatStatus", seatStatus != null ? seatStatus : "NEUTRAL");

                usersArray.put(userJson);
            }

            response.setContentType("application/json");
            response.getWriter().write(usersArray.toString());
            logger.info("Fetched users. Present only: {}", "true".equalsIgnoreCase(presentParam));
        } catch (Exception e) {
            logger.error("Error during fetching users: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while fetching users.");
        }
    }

    private void handleGetUserById(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            String sessionUserId = null;
            String sessionUserRole = null;
            String sessionUsername = null; // For logging

            if (session != null) {
                sessionUserId = (String) session.getAttribute("userId");
                sessionUserRole = (String) session.getAttribute("role");
                sessionUsername = (String) session.getAttribute("username"); // For logging
            }

            if (sessionUserId == null) { // Also implies sessionUserRole would be null
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
                logger.warn("Unauthenticated attempt to get user by ID.");
                return;
            }

            String targetUserIdFromPath = request.getPathInfo().split("/")[2];

            boolean allowed = false;
            if (targetUserIdFromPath.equals(sessionUserId)) {
                allowed = true; // User is requesting their own information
            } else if ("PRESIDENT".equals(sessionUserRole)) {
                allowed = true; // President can view any user
            }

            if (!allowed) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied to user information.");
                logger.warn("User '{}' (Role: '{}') denied access to user ID '{}'.", sessionUsername, sessionUserRole, targetUserIdFromPath);
                return;
            }

            Document userDoc = usersCollection.find(eq("_id", new ObjectId(targetUserIdFromPath))).first();

            if (userDoc != null) {
                JSONObject userJson = new JSONObject();
                userJson.put("id", userDoc.getObjectId("_id").toHexString());
                // Client-side code is responsible for HTML escaping these values if rendered in HTML to prevent XSS.
                userJson.put("username", userDoc.getString("username"));
                userJson.put("role", userDoc.getString("role"));
                userJson.put("partyAffiliation", userDoc.getString("partyAffiliation"));

                // Add other non-sensitive fields as needed
                userJson.put("present", userDoc.getBoolean("present", false));
                userJson.put("seatStatus", userDoc.getString("seatStatus"));
                userJson.put("fines", userDoc.getInteger("fines", 0));
                userJson.put("electoralStrength", userDoc.getInteger("electoralStrength",1));
                // userJson.remove("password"); // Not needed as we are selectively adding
                
                String seatStatus = userDoc.getString("seatStatus");
                userJson.put("seatStatus", seatStatus != null ? seatStatus : "NEUTRAL");

                response.setContentType("application/json");
                response.getWriter().write(userJson.toString());
                logger.info("User '{}' (Role: '{}') fetched user with id '{}'.", sessionUsername, sessionUserRole, targetUserIdFromPath);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found.");
                logger.warn("User with id '{}' not found (requested by User '{}', Role: '{}').", targetUserIdFromPath, sessionUsername, sessionUserRole);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid user ID format: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid user ID format.");
        } catch (Exception e) {
            logger.error("Error during fetching user by id: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while fetching the user.");
        }
    }

    // Fetch all proposals
    private void handleGetProposals(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<Document> proposals = proposalsCollection.find().into(new ArrayList<>());

            HttpSession session = request.getSession(false);
            String userId = null;
            if (session != null && session.getAttribute("userId") != null) {
                userId = (String) session.getAttribute("userId");
            }

            JSONArray proposalsArray = new JSONArray();
            for (Document doc : proposals) {
                JSONObject proposalJson = new JSONObject();
                proposalJson.put("id", doc.getObjectId("_id").toHexString());
                // Client-side code is responsible for HTML escaping these values if rendered in HTML to prevent XSS.
                proposalJson.put("title", doc.getString("title"));
                proposalJson.put("party", doc.getString("party"));
                // Add other fields from 'doc' as needed, respecting original structure
                proposalJson.put("proposalNumber", doc.getInteger("proposalNumber"));
                proposalJson.put("isPriority", doc.getBoolean("isPriority"));
                proposalJson.put("isConstitutional", doc.getBoolean("isConstitutional"));
                proposalJson.put("voteRequirement", doc.getString("voteRequirement"));
                proposalJson.put("stupid", doc.getBoolean("stupid"));
                proposalJson.put("associationType", doc.getString("associationType"));
                proposalJson.put("referencedProposal", doc.getString("referencedProposal"));
                proposalJson.put("proposalVisual", doc.getString("proposalVisual"));
                proposalJson.put("meetingNumber", doc.getInteger("meetingNumber"));


                if (doc.getBoolean("votingEnded", false)) {
                    proposalJson.put("passed", doc.getBoolean("passed", false));
                    proposalJson.put("totalFor", doc.getInteger("totalFor", 0));
                    proposalJson.put("totalAgainst", doc.getInteger("totalAgainst", 0));
                }

                if (userId != null) {
                    try {
                        ObjectId userObjectId = new ObjectId(userId); // userId is from session
                        Document vote = votesCollection.find(Filters.and(
                                eq("proposalId", doc.getObjectId("_id")), // doc.getObjectId already returns ObjectId
                                eq("userId", userObjectId)
                        )).first();
                        if (vote != null) {
                            // Client-side code is responsible for HTML escaping this value if rendered in HTML to prevent XSS.
                            proposalJson.put("userVote", vote.getString("voteChoice"));
                        } else {
                            proposalJson.put("userVote", "Abstain"); // Default if no vote found
                        }
                    } catch (IllegalArgumentException e) {
                        logger.warn("Invalid userId '{}' found in session during handleGetProposals. Skipping user vote for this proposal.", userId);
                        // No user-supplied string here, just a default
                        proposalJson.put("userVote", "Abstain"); // Or some other indicator of an issue
                    }
                } else {
                     // No user-supplied string here, just a default
                    proposalJson.put("userVote", "Abstain"); // Default for non-authenticated users
                }

                proposalsArray.put(proposalJson);
            }

            response.setContentType("application/json");
            response.getWriter().write(proposalsArray.toString());
            logger.info("Fetched all proposals.");
        } catch (Exception e) {
            logger.error("Error during fetching proposals: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while fetching proposals.");
        }
    }

    // Get the speaking queue
    private void handleGetQueue(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<Document> queueUsers = usersCollection.find(new Document("seatStatus",
                            new Document("$in", List.of("REQUESTING_TO_SPEAK", "OBJECTING"))))
                    .into(new ArrayList<>());

            JSONArray queueArray = new JSONArray();
            for (Document doc : queueUsers) {
                JSONObject queueItem = new JSONObject();
                queueItem.put("username", doc.getString("username"));
                queueItem.put("status", doc.getString("seatStatus"));
                queueArray.put(queueItem);
            }

            response.setContentType("application/json");
            response.getWriter().write(queueArray.toString());
            logger.info("Fetched speaking queue.");
        } catch (Exception e) {
            logger.error("Error during fetching queue: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while fetching the queue.");
        }
    }

    // Helper to get next proposal number
    private int getNextProposalNumber(Boolean priority) {
        String counterName = priority ? "priorityProposal" : "normalProposal";
        return getNextAtomicProposalNumber(counterName);
    }

    // Handles player-submitted proposals
    private void handlePlayerSubmitProposal(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
            logger.warn("Unauthenticated attempt to submit a proposal.");
            return;
        }

        String sessionUserId = (String) session.getAttribute("userId");
        String sessionUsername = (String) session.getAttribute("username");

        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                sb.append(line);
            }
            JSONObject proposalJson = new JSONObject(sb.toString());

            String title = proposalJson.optString("title", "").trim();
            String description = proposalJson.optString("description", "").trim(); // Already optional

            final int MAX_TITLE_LENGTH = 200;
            final int MAX_DESC_LENGTH = 2000;

            if (title.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Proposal title cannot be empty.");
                logger.warn("User '{}' attempted to submit a proposal with an empty title.", sessionUsername);
                return;
            }
            if (title.length() > MAX_TITLE_LENGTH) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Title exceeds maximum length of " + MAX_TITLE_LENGTH + " characters.");
                logger.warn("User '{}' submitted proposal with title exceeding max length. Title length: {}", sessionUsername, title.length());
                return;
            }
            if (description.length() > MAX_DESC_LENGTH) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Description exceeds maximum length of " + MAX_DESC_LENGTH + " characters.");
                logger.warn("User '{}' submitted proposal with description exceeding max length. Description length: {}", sessionUsername, description.length());
                return;
            }
            
            // Validate sessionUserId as ObjectId
            ObjectId userObjectId;
            try {
                userObjectId = new ObjectId(sessionUserId);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid sessionUserId '{}' for user '{}'.", sessionUserId, sessionUsername, e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid user session data.");
                return;
            }

            // Check for recent duplicates
            long thirtySecondsAgo = System.currentTimeMillis() - 30000;
            Document recentDuplicateQuery = new Document("submittedByUserId", userObjectId)
                .append("title", title)
                // Not checking description for stricter match, title and user within timeframe is enough
                .append("status", "pending")
                .append("submissionTimestamp", new Document("$gte", new Date(thirtySecondsAgo)));
            
            if (pendingProposalsCollection.find(recentDuplicateQuery).first() != null) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "Duplicate submission detected. Please wait a moment before resubmitting.");
                logger.warn("User '{}' attempted to submit a duplicate proposal for title '{}' within 30 seconds.", sessionUsername, title);
                return;
            }

            Document pendingProposalDoc = new Document()
                .append("submittedByUserId", userObjectId)
                .append("submittedByUsername", sessionUsername) // XSS: Client responsible for escaping if rendered
                .append("title", title) // XSS: Client responsible for escaping if rendered
                .append("description", description) // XSS: Client responsible for escaping if rendered
                .append("submissionTimestamp", new Date())
                .append("status", "pending");

            pendingProposalsCollection.insertOne(pendingProposalDoc);
            // To get the ID, we need to fetch it. MongoDB insertOne does not return the full doc by default with all drivers/versions.
            // However, the `pendingProposalDoc` will have the _id field populated *after* the insert.
            ObjectId insertedId = pendingProposalDoc.getObjectId("_id");


            response.setStatus(HttpServletResponse.SC_CREATED);
            JSONObject resp = new JSONObject();
            resp.put("message", "Proposal submitted successfully and is pending review.");
            resp.put("pendingProposalId", insertedId.toHexString());
            response.setContentType("application/json");
            response.getWriter().write(resp.toString());
            logger.info("User '{}' submitted proposal '{}' (ID: {}) for review.", sessionUsername, title, insertedId.toHexString());

            // WebSocket Broadcast
            JSONObject wsMessage = new JSONObject();
            wsMessage.put("type", "pendingProposalNew");
            JSONObject proposalData = new JSONObject();
            proposalData.put("id", insertedId.toHexString());
            // Client-side code is responsible for HTML escaping these values if rendered in HTML to prevent XSS.
            proposalData.put("title", title);
            proposalData.put("description", description);
            proposalData.put("submittedByUsername", sessionUsername);
            proposalData.put("submissionTimestamp", pendingProposalDoc.getDate("submissionTimestamp").toInstant().toString());
            proposalData.put("status", "pending");
            wsMessage.put("proposal", proposalData);
            SeatWebSocket.broadcast(wsMessage); 
            logger.info("Broadcasted new pending proposal notification for ID '{}'.", insertedId.toHexString());

        } catch (org.json.JSONException je) {
            logger.warn("Malformed JSON in request to {}: {}", request.getRequestURI(), je.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON in request body.");
        } catch (Exception e) {
            logger.error("Error during player proposal submission by user '{}' at {}: ", sessionUsername, request.getRequestURI(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while submitting the proposal.");
        }
    }


    private String escapeDiscordMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // Characters to escape: \, *, _, ~, `, ||, >, #, -, +, .
        // Order can matter if replacements create new sequences.
        // Generally, escaping the escape character itself first is safest if it's also a special char.
        // For Discord, we just need to prefix special chars with a backslash.
        return text
            .replace("\\", "\\\\") // Replace \ with \\ (becomes \ in output)
            .replace("*", "\\*")   // Replace * with \*
            .replace("_", "\\_")   // Replace _ with \_
            .replace("~", "\\~")   // Replace ~ with \~
            .replace("`", "\\`")   // Replace ` with \`
            .replace("||", "\\|\\|") // Replace || with \||
            .replace(">", "\\>")   // Replace > with \>
            .replace("#", "\\#")   // Replace # with \#
            .replace("-", "\\-")   // Replace - with \- (especially at start of lines)
            .replace("+", "\\+")   // Replace + with \+ (especially at start of lines)
            .replace(".", "\\.");    // Replace . with \. (especially after numbers for lists)
    }

    private void handleApprovePendingProposal(HttpServletRequest request, HttpServletResponse response, String pendingProposalIdStr) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !"PRESIDENT".equals(session.getAttribute("role"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied. Only the President can approve proposals.");
            logger.warn("Non-president user '{}' attempted to approve pending proposal ID '{}'.", session != null ? session.getAttribute("username") : "unauthenticated", pendingProposalIdStr);
            return;
        }
        String presidentUsername = (String) session.getAttribute("username");

        ObjectId pendingProposalObjectId;
        try {
            pendingProposalObjectId = new ObjectId(pendingProposalIdStr);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid pending proposal ID format '{}' in handleApprovePendingProposal by President '{}'.", pendingProposalIdStr, presidentUsername);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid pending proposal ID format.");
            return;
        }

        try {
            // Atomically check status and retrieve
            Document pendingProposalDoc = pendingProposalsCollection.find(
                Filters.and(
                    Filters.eq("_id", pendingProposalObjectId),
                    Filters.eq("status", "pending")
                )
            ).first();

            if (pendingProposalDoc == null) {
                // This means either not found OR status was not "pending"
                // Check if it exists at all to give a more specific error
                Document checkExists = pendingProposalsCollection.find(eq("_id", pendingProposalObjectId)).first();
                if (checkExists == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Pending proposal not found.");
                    logger.warn("Pending proposal ID '{}' not found for approval by President '{}'.", pendingProposalIdStr, presidentUsername);
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Proposal is not pending and cannot be approved. Current status: " + checkExists.getString("status"));
                    logger.warn("President '{}' attempted to approve proposal ID '{}' which is not pending (status: {}).", presidentUsername, pendingProposalIdStr, checkExists.getString("status"));
                }
                return;
            }

            // For simplicity, submitted proposals become normal, non-priority, non-constitutional
            boolean isPriority = false; 
            boolean isConstitutional = false; 
            String type = "normal"; 
            String associatedProposal = ""; 

            int proposalNumber;
            String proposalVisual;
            if (isConstitutional) {
                proposalNumber = getNextConstitutionalProposalNumber();
                proposalVisual = "C" + proposalNumber;
            } else if (isPriority) {
                proposalNumber = getNextProposalNumber(true); // True for priority
                proposalVisual = "P" + proposalNumber;
            } else {
                proposalNumber = getNextProposalNumber(false); // False for normal
                proposalVisual = String.valueOf(proposalNumber);
            }

            int meetingNumber = getCurrentMeetingNumber();
            String party = "Submitted by " + pendingProposalDoc.getString("submittedByUsername"); 

            Document mainProposalDoc = new Document("title", pendingProposalDoc.getString("title"))
                .append("description", pendingProposalDoc.getString("description")) 
                .append("proposalNumber", proposalNumber)
                .append("party", party) // XSS: Client responsible for escaping party if rendered
                .append("isPriority", isPriority)
                .append("isConstitutional", isConstitutional)
                .append("voteRequirement", "Rel") 
                .append("stupid", false)
                .append("associationType", type)
                .append("referencedProposal", associatedProposal)
                .append("proposalVisual", proposalVisual) // XSS: Client responsible for escaping if rendered
                .append("meetingNumber", meetingNumber)
                .append("passed", false)
                .append("totalFor", 0)
                .append("totalAgainst", 0)
                .append("votingEnded", false)
                .append("submittedByUsername", pendingProposalDoc.getString("submittedByUsername")) // XSS: Client responsible for escaping if rendered
                .append("pendingProposalId", pendingProposalDoc.getObjectId("_id")); 
            
            proposalsCollection.insertOne(mainProposalDoc);
            ObjectId mainProposalId = mainProposalDoc.getObjectId("_id");

            // Update the original pending proposal IF main proposal insertion was successful
            // If this update fails, we log it, but the main proposal is already in.
            Document updatePending = new Document("$set", new Document("status", "approved")
                                                        .append("approvedTimestamp", new Date())
                                                        .append("mainProposalId", mainProposalId));
            try {
                pendingProposalsCollection.updateOne(eq("_id", pendingProposalObjectId), updatePending);
            } catch (Exception e_update) {
                logger.error("CRITICAL: Failed to update status of pending proposal ID '{}' to 'approved' after creating main proposal ID '{}'. Manual cleanup might be needed. Error: {}", pendingProposalIdStr, mainProposalId.toHexString(), e_update.getMessage());
                // Continue with the response as the main proposal was created.
            }
            
            // Prepare response with main proposal details
            Document insertedProposal = proposalsCollection.find(Filters.eq("_id", mainProposalId)).first();
            JSONObject responseJson = new JSONObject();
            if (insertedProposal != null) {
                 // Selectively build the JSON response for the main proposal
                responseJson.put("id", insertedProposal.getObjectId("_id").toHexString());
                // Client-side code is responsible for HTML escaping these values if rendered in HTML to prevent XSS.
                responseJson.put("title", insertedProposal.getString("title"));
                responseJson.put("description", insertedProposal.getString("description"));
                responseJson.put("proposalNumber", insertedProposal.getInteger("proposalNumber"));
                responseJson.put("party", insertedProposal.getString("party"));
                responseJson.put("isPriority", insertedProposal.getBoolean("isPriority"));
                responseJson.put("isConstitutional", insertedProposal.getBoolean("isConstitutional"));
                responseJson.put("voteRequirement", insertedProposal.getString("voteRequirement"));
                responseJson.put("stupid", insertedProposal.getBoolean("stupid"));
                responseJson.put("associationType", insertedProposal.getString("associationType"));
                responseJson.put("referencedProposal", insertedProposal.getString("referencedProposal"));
                responseJson.put("proposalVisual", insertedProposal.getString("proposalVisual"));
                responseJson.put("meetingNumber", insertedProposal.getInteger("meetingNumber"));
                responseJson.put("passed", insertedProposal.getBoolean("passed", false));
                responseJson.put("totalFor", insertedProposal.getInteger("totalFor", 0));
                responseJson.put("totalAgainst", insertedProposal.getInteger("totalAgainst", 0));
                responseJson.put("votingEnded", insertedProposal.getBoolean("votingEnded", false));
                responseJson.put("submittedByUsername", insertedProposal.getString("submittedByUsername"));
                responseJson.put("pendingProposalId", insertedProposal.getObjectId("pendingProposalId").toHexString());
            } else {
                 // Fallback if somehow the inserted proposal is not found immediately
                responseJson.put("id", mainProposalId.toHexString());
                responseJson.put("title", pendingProposalDoc.getString("title"));
                responseJson.put("message", "Main proposal created, but full details fetch failed.");
            }


            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.getWriter().write(responseJson.toString());
            logger.info("President '{}' approved pending proposal ID '{}'. New main proposal ID '{}' created.", presidentUsername, pendingProposalIdStr, mainProposalId.toHexString());

            // WebSocket Broadcast for the new main proposal
            if (insertedProposal != null) {
                JSONObject proposalUpdateMsg = new JSONObject();
                proposalUpdateMsg.put("type", "proposalUpdate"); // Using "proposalUpdate" as it's a new proposal being added to the main list
                proposalUpdateMsg.put("proposal", responseJson); // Use the already constructed JSON
                SeatWebSocket.broadcast(proposalUpdateMsg);
                logger.info("Broadcasted new main proposal (from pending) id '{}'.", mainProposalId.toHexString());
            }
            
            // WebSocket Broadcast for the updated pending proposal status
            JSONObject pendingStatusUpdateMsg = new JSONObject();
            pendingStatusUpdateMsg.put("type", "pendingProposalStatusUpdate");
            JSONObject pendingData = new JSONObject();
            pendingData.put("id", pendingProposalIdStr);
            pendingData.put("status", "approved");
            pendingData.put("mainProposalId", mainProposalId.toHexString());
            pendingStatusUpdateMsg.put("proposal", pendingData);
            SeatWebSocket.broadcast(pendingStatusUpdateMsg);
            logger.info("Broadcasted pending proposal status update for ID '{}' to 'approved'.", pendingProposalIdStr);

            repopulateProposalQueue(); // Call to repopulate and broadcast queue

        } catch (Exception e) {
            logger.error("Error during approving pending proposal ID '{}' by President '{}': ", pendingProposalIdStr, presidentUsername, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while approving the proposal.");
        }
    }

    // Handles fetching pending proposals for President
    private void handleGetPendingProposals(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !"PRESIDENT".equals(session.getAttribute("role"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied. Only the President can view pending proposals.");
            logger.warn("Non-president user '{}' attempted to access pending proposals.", session != null ? session.getAttribute("username") : "unauthenticated");
            return;
        }

        try {
            List<Document> pendingDocs = pendingProposalsCollection.find(Filters.eq("status", "pending"))
                                                                  .sort(Sorts.ascending("submissionTimestamp"))
                                                                  .into(new ArrayList<>());
            JSONArray proposalsArray = new JSONArray();
            for (Document doc : pendingDocs) {
                JSONObject proposalJson = new JSONObject();
                proposalJson.put("id", doc.getObjectId("_id").toHexString());
                // Client-side code is responsible for HTML escaping these values if rendered in HTML to prevent XSS.
                proposalJson.put("title", doc.getString("title"));
                proposalJson.put("description", doc.getString("description"));
                proposalJson.put("submittedByUsername", doc.getString("submittedByUsername"));
                proposalJson.put("submittedByUserId", doc.getObjectId("submittedByUserId").toHexString());
                proposalJson.put("submissionTimestamp", doc.getDate("submissionTimestamp").toInstant().toString());
                proposalJson.put("status", doc.getString("status"));
                proposalsArray.put(proposalJson);
            }

            response.setContentType("application/json");
            response.getWriter().write(proposalsArray.toString());
            logger.info("President '{}' fetched {} pending proposals.", session.getAttribute("username"), pendingDocs.size());

        } catch (Exception e) {
            logger.error("Error during fetching pending proposals for President '{}': ", session.getAttribute("username"), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while fetching pending proposals.");
        }
    }

    private void broadcastQueueUpdate() {
        try {
            List<Document> queueItems = parliamentQueueCollection.find(
                Filters.or(Filters.eq("status", "pending"), Filters.eq("status", "active"))
            ).sort(Sorts.orderBy(Sorts.ascending("priority"), Sorts.ascending("timestamp"))).into(new ArrayList<>());

            JSONArray queueJsonArray = new JSONArray();
            for (Document item : queueItems) {
                JSONObject queueItemJson = new JSONObject();
                // Convert '_id' to 'id' and other fields from the document
                queueItemJson.put("id", item.getObjectId("_id").toHexString());
                queueItemJson.put("type", item.getString("type"));
                queueItemJson.put("status", item.getString("status"));
                queueItemJson.put("priority", item.getInteger("priority"));
                queueItemJson.put("timestamp", item.getDate("timestamp").toInstant().toString());

                // Add type-specific fields
                if ("PROPOSAL_DISCUSSION".equals(item.getString("type"))) {
                    queueItemJson.put("proposalId", item.getObjectId("proposalId").toHexString());
                    queueItemJson.put("proposalTitle", item.getString("proposalTitle"));
                    queueItemJson.put("proposalVisual", item.getString("proposalVisual"));
                } else if ("SPEAKER_REQUEST".equals(item.getString("type"))) {
                    queueItemJson.put("userId", item.getObjectId("userId").toHexString());
                    queueItemJson.put("username", item.getString("username"));
                    queueItemJson.put("requestType", item.getString("requestType")); // e.g., "REQUESTING_TO_SPEAK", "OBJECTING"
                }
                // Add other fields as necessary, ensuring they are handled if they might not exist.
                // Example: item.get("someField", defaultValue) or check with item.containsKey("someField")

                queueJsonArray.put(queueItemJson);
            }

            JSONObject message = new JSONObject();
            message.put("type", "queueUpdate");
            message.put("queue", queueJsonArray);
            SeatWebSocket.broadcast(message);
            logger.info("Broadcasted queue update with {} items.", queueItems.size());
        } catch (Exception e) {
            logger.error("Error broadcasting queue update: ", e);
        }
    }

    private void repopulateProposalQueue() {
        try {
            // Clear existing 'pending' proposals from the queue to avoid duplicates
            parliamentQueueCollection.deleteMany(
                Filters.and(Filters.eq("type", "PROPOSAL_DISCUSSION"), Filters.eq("status", "pending"))
            );

            List<Document> activeProposals = proposalsCollection.find(
                Filters.and(
                    Filters.eq("votingEnded", false),
                    Filters.eq("stupid", false) // Only add non-stupid, non-ended proposals
                )
            ).into(new ArrayList<>());

            List<Document> queueEntries = new ArrayList<>();
            for (Document proposal : activeProposals) {
                int priorityValue; // Renamed for clarity
                if (proposal.getBoolean("isConstitutional", false)) {
                    priorityValue = 20; // Highest priority for active discussion items
                } else if (proposal.getBoolean("isPriority", false)) {
                    priorityValue = 25; // Next highest
                } else {
                    priorityValue = 30; // Standard proposals
                }
                
                // Use current time for timestamp to ensure fresh items are ordered correctly if priorities are the same
                // Or, if proposals have a 'lastActivityTimestamp' or 'submissionTimestamp', that could be used.
                // For now, using new Date() for new queue entries.
                Date itemTimestamp = new Date(); 

                Document queueItem = new Document("type", "PROPOSAL_DISCUSSION")
                    .append("proposalId", proposal.getObjectId("_id"))
                    .append("proposalTitle", proposal.getString("title")) // XSS: Client responsible for escaping
                    .append("proposalVisual", proposal.getString("proposalVisual")) // XSS: Client responsible for escaping
                    .append("timestamp", itemTimestamp) 
                    .append("priority", priorityValue)
                    .append("status", "pending"); // All repopulated proposals start as pending in queue
                queueEntries.add(queueItem);
            }

            if (!queueEntries.isEmpty()) {
                parliamentQueueCollection.insertMany(queueEntries);
            }
            logger.info("Repopulated proposal discussion items in the queue with {} entries.", queueEntries.size());
            broadcastQueueUpdate(); // Broadcast after repopulating
        } catch (Exception e) {
            logger.error("Error repopulating proposal queue: ", e);
        }
    }

    private void handleRejectPendingProposal(HttpServletRequest request, HttpServletResponse response, String pendingProposalIdStr) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !"PRESIDENT".equals(session.getAttribute("role"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied. Only the President can reject proposals.");
            logger.warn("Non-president user '{}' attempted to reject pending proposal ID '{}'.", session != null ? session.getAttribute("username") : "unauthenticated", pendingProposalIdStr);
            return;
        }
        String presidentUsername = (String) session.getAttribute("username");

        ObjectId pendingProposalObjectId;
        try {
            pendingProposalObjectId = new ObjectId(pendingProposalIdStr);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid pending proposal ID format '{}' in handleRejectPendingProposal by President '{}'.", pendingProposalIdStr, presidentUsername);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid pending proposal ID format.");
            return;
        }

        try {
            // Atomically check status and retrieve
            Document pendingProposalDoc = pendingProposalsCollection.find(
                Filters.and(
                    Filters.eq("_id", pendingProposalObjectId),
                    Filters.eq("status", "pending")
                )
            ).first();

            if (pendingProposalDoc == null) {
                // This means either not found OR status was not "pending"
                Document checkExists = pendingProposalsCollection.find(eq("_id", pendingProposalObjectId)).first();
                if (checkExists == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Pending proposal not found.");
                    logger.warn("Pending proposal ID '{}' not found for rejection by President '{}'.", pendingProposalIdStr, presidentUsername);
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Proposal is not pending and cannot be rejected. Current status: " + checkExists.getString("status"));
                    logger.warn("President '{}' attempted to reject proposal ID '{}' which is not pending (status: {}).", presidentUsername, pendingProposalIdStr, checkExists.getString("status"));
                }
                return;
            }

            // Update the pending proposal status to "rejected"
            Document updatePending = new Document("$set", new Document("status", "rejected")
                                                        .append("rejectedTimestamp", new Date()));
            pendingProposalsCollection.updateOne(eq("_id", pendingProposalObjectId), updatePending);

            response.setStatus(HttpServletResponse.SC_OK);
            JSONObject resp = new JSONObject();
            resp.put("message", "Pending proposal rejected successfully.");
            resp.put("pendingProposalId", pendingProposalIdStr);
            resp.put("status", "rejected");
            response.setContentType("application/json");
            response.getWriter().write(resp.toString());
            logger.info("President '{}' rejected pending proposal ID '{}'.", presidentUsername, pendingProposalIdStr);
            
            // WebSocket Broadcast for the updated pending proposal status
            JSONObject pendingStatusUpdateMsg = new JSONObject();
            pendingStatusUpdateMsg.put("type", "pendingProposalStatusUpdate");
            JSONObject pendingData = new JSONObject();
            pendingData.put("id", pendingProposalIdStr);
            pendingData.put("status", "rejected");
            pendingStatusUpdateMsg.put("proposal", pendingData);
            SeatWebSocket.broadcast(pendingStatusUpdateMsg);
            logger.info("Broadcasted pending proposal status update for ID '{}' to 'rejected'.", pendingProposalIdStr);

        } catch (Exception e) {
            logger.error("Error during rejecting pending proposal ID '{}' by President '{}': ", pendingProposalIdStr, presidentUsername, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while rejecting the proposal.");
        }
    }

    private void handleGetParliamentQueue(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        // Allow all authenticated users to view the queue
        if (session == null || session.getAttribute("userId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
            logger.warn("Unauthenticated attempt to view parliament queue.");
            return;
        }

        try {
            List<Document> queueItems = parliamentQueueCollection.find(
                Filters.or(Filters.eq("status", "pending"), Filters.eq("status", "active"))
            ).sort(Sorts.orderBy(Sorts.ascending("priority"), Sorts.ascending("timestamp"))).into(new ArrayList<>());

            JSONArray queueJsonArray = new JSONArray();
            for (Document item : queueItems) {
                JSONObject queueItemJson = new JSONObject();
                // Convert '_id' to 'id' and other fields from the document
                queueItemJson.put("id", item.getObjectId("_id").toHexString());
                queueItemJson.put("type", item.getString("type"));
                queueItemJson.put("status", item.getString("status"));
                queueItemJson.put("priority", item.getInteger("priority"));
                
                Date timestamp = item.getDate("timestamp");
                if (timestamp != null) {
                    queueItemJson.put("timestamp", timestamp.toInstant().toString());
                } else {
                    // Handle case where timestamp might be null, though it shouldn't be based on insertion logic
                    queueItemJson.put("timestamp", ""); 
                }


                // Add type-specific fields, ensuring to check for nulls if fields are optional
                if ("PROPOSAL_DISCUSSION".equals(item.getString("type"))) {
                    if (item.getObjectId("proposalId") != null) {
                        queueItemJson.put("proposalId", item.getObjectId("proposalId").toHexString());
                    }
                    queueItemJson.put("proposalTitle", item.getString("proposalTitle")); // XSS: Client responsible for escaping
                    queueItemJson.put("proposalVisual", item.getString("proposalVisual")); // XSS: Client responsible for escaping
                } else if ("SPEAKER_REQUEST".equals(item.getString("type"))) {
                     if (item.getObjectId("userId") != null) {
                        queueItemJson.put("userId", item.getObjectId("userId").toHexString());
                    }
                    queueItemJson.put("username", item.getString("username")); // XSS: Client responsible for escaping
                    queueItemJson.put("requestType", item.getString("requestType"));
                }
                queueJsonArray.put(queueItemJson);
            }

            response.setContentType("application/json");
            response.getWriter().write(queueJsonArray.toString());
            logger.info("User '{}' fetched parliament queue with {} items.", session.getAttribute("username"), queueItems.size());

        } catch (Exception e) {
            logger.error("Error fetching parliament queue for user '{}': ", session.getAttribute("username"), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while fetching the parliament queue.");
        }
    }

    private void handleRequestSpeak(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
            logger.warn("Unauthenticated attempt to request to speak.");
            return;
        }

        String sessionUserId = (String) session.getAttribute("userId");
        String sessionUsername = (String) session.getAttribute("username");
        ObjectId userObjectId;
        try {
            userObjectId = new ObjectId(sessionUserId);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid sessionUserId '{}' for user '{}' during request to speak.", sessionUserId, sessionUsername, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid user session data.");
            return;
        }

        try {
            // Check if user already has an active ("pending") "SPEAKER_REQUEST" or "OBJECTION"
            Document existingRequest = parliamentQueueCollection.find(
                Filters.and(
                    Filters.eq("userId", userObjectId),
                    Filters.eq("status", "pending"),
                    Filters.or(
                        Filters.eq("type", "SPEAKER_REQUEST"),
                        Filters.eq("type", "OBJECTION")
                    )
                )
            ).first();

            if (existingRequest != null) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "You are already in the queue or have an active objection.");
                logger.warn("User '{}' (ID: {}) attempted to request to speak but is already in queue/objecting (Item ID: {}).", 
                            sessionUsername, sessionUserId, existingRequest.getObjectId("_id").toHexString());
                return;
            }
            
            // Optional: Parse JSON request for proposalId
            String proposalIdStr = null;
            ObjectId proposalObjectId = null;
            try {
                StringBuilder sb = new StringBuilder();
                String line;
                // Check if request has a body. getReader() might throw if no body.
                if (request.getContentLengthLong() > 0) { 
                    while ((line = request.getReader().readLine()) != null) {
                        sb.append(line);
                    }
                    if (sb.length() > 0) {
                        JSONObject requestJson = new JSONObject(sb.toString());
                        proposalIdStr = requestJson.optString("proposalId", null);
                        if (proposalIdStr != null && !proposalIdStr.trim().isEmpty()) {
                            proposalObjectId = new ObjectId(proposalIdStr.trim());
                            // Optional: Validate if proposalId actually exists in proposalsCollection
                        }
                    }
                }
            } catch (IOException | org.json.JSONException e) {
                logger.warn("Error parsing proposalId from request body for user '{}': {}", sessionUsername, e.getMessage());
                // Not a fatal error, proceed without proposalId
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid proposalId format '{}' provided by user '{}'.", proposalIdStr, sessionUsername);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid proposal ID format.");
                return;
            }


            Document queueItem = new Document("type", "SPEAKER_REQUEST")
                .append("userId", userObjectId)
                .append("username", sessionUsername) // XSS: Client responsible for escaping
                .append("timestamp", new Date())
                .append("priority", 10) // Standard priority for speaker requests
                .append("status", "pending")
                .append("requestType", "REQUESTING_TO_SPEAK"); // Default request type

            if (proposalObjectId != null) {
                queueItem.append("proposalId", proposalObjectId);
            }

            parliamentQueueCollection.insertOne(queueItem);
            broadcastQueueUpdate();

            response.setStatus(HttpServletResponse.SC_OK);
            JSONObject respJson = new JSONObject();
            respJson.put("message", "Request to speak added to queue.");
            respJson.put("queueItemId", queueItem.getObjectId("_id").toHexString());
            response.setContentType("application/json");
            response.getWriter().write(respJson.toString());
            logger.info("User '{}' (ID: {}) added to speaking queue. Item ID: {}.", sessionUsername, sessionUserId, queueItem.getObjectId("_id").toHexString());

        } catch (Exception e) {
            logger.error("Error during request to speak by user '{}': ", sessionUsername, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while processing your request.");
        }
    }

    private void handleQueueSetActive(HttpServletRequest request, HttpServletResponse response, String itemIdStr) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !"PRESIDENT".equals(session.getAttribute("role"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied. Only the President can set queue items active.");
            logger.warn("Non-president user '{}' attempted to set queue item '{}' active.", session != null ? session.getAttribute("username") : "unauthenticated", itemIdStr);
            return;
        }
        String presidentUsername = (String) session.getAttribute("username");

        ObjectId itemObjectId;
        try {
            itemObjectId = new ObjectId(itemIdStr);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid item ID format '{}' in handleQueueSetActive by President '{}'.", itemIdStr, presidentUsername);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid item ID format.");
            return;
        }

        try {
            // Atomically update: set current active to pending, then new one to active
            // 1. Set all currently "active" items to "pending"
            parliamentQueueCollection.updateMany(
                Filters.eq("status", "active"),
                Updates.set("status", "pending")
            );

            // 2. Set the specified item to "active"
            Document updateQuery = new Document("_id", itemObjectId);
            Document updateOperation = new Document("$set", new Document("status", "active"));
            var updateResult = parliamentQueueCollection.updateOne(updateQuery, updateOperation);

            if (updateResult.getModifiedCount() == 0) {
                 // Check if the item actually exists, because updateOne won't fail if no doc matches _id
                Document checkItemExists = parliamentQueueCollection.find(eq("_id", itemObjectId)).first();
                if (checkItemExists == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Queue item not found.");
                    logger.warn("President '{}' tried to set non-existent queue item ID '{}' active.", presidentUsername, itemIdStr);
                    return;
                }
                // If item exists but wasn't modified, it might already be active or some other issue.
                // For now, we assume if it exists, it should have been set active or was already active.
                // If it was already active, the previous updateMany would make it pending, then this would make it active again.
                logger.info("Queue item ID '{}' was targeted to be set active by President '{}'. Modified count was 0, but item exists. Status: {}", itemIdStr, presidentUsername, checkItemExists.getString("status"));

            }
            
            Document activeItem = parliamentQueueCollection.find(eq("_id", itemObjectId)).first();
            if (activeItem == null) {
                 // Should not happen if updateResult.getModifiedCount() > 0 or if checkItemExists found it
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to retrieve active item after update.");
                logger.error("CRITICAL: Failed to retrieve queue item ID '{}' after attempting to set active by President '{}'.", itemIdStr, presidentUsername);
                return;
            }


            // If active item is a speaker request or objection, update user's seatStatus
            String itemType = activeItem.getString("type");
            if ("SPEAKER_REQUEST".equals(itemType) || "OBJECTION".equals(itemType)) {
                ObjectId userIdToUpdate = activeItem.getObjectId("userId");
                if (userIdToUpdate != null) {
                    Document userUpdateQuery = new Document("_id", userIdToUpdate);
                    Document userUpdateOperation = new Document("$set", new Document("seatStatus", "SPEAKING"));
                    usersCollection.updateOne(userUpdateQuery, userUpdateOperation);

                    // Broadcast individual user seat update
                    Document updatedUserDoc = usersCollection.find(userUpdateQuery).first();
                    if (updatedUserDoc != null) {
                        JSONObject userJsonForBroadcast = new JSONObject();
                        userJsonForBroadcast.put("id", updatedUserDoc.getObjectId("_id").toHexString());
                        userJsonForBroadcast.put("username", updatedUserDoc.getString("username"));
                        userJsonForBroadcast.put("role", updatedUserDoc.getString("role"));
                        userJsonForBroadcast.put("partyAffiliation", updatedUserDoc.getString("partyAffiliation"));
                        userJsonForBroadcast.put("present", updatedUserDoc.getBoolean("present", false));
                        userJsonForBroadcast.put("seatStatus", updatedUserDoc.getString("seatStatus")); // Should be SPEAKING
                        userJsonForBroadcast.put("fines", updatedUserDoc.getInteger("fines", 0));
                        userJsonForBroadcast.put("electoralStrength", updatedUserDoc.getInteger("electoralStrength",1));
                        
                        JSONObject seatUpdateMsg = new JSONObject();
                        seatUpdateMsg.put("type", "seatUpdate");
                        seatUpdateMsg.put("user", userJsonForBroadcast);
                        SeatWebSocket.broadcast(seatUpdateMsg);
                        logger.info("Broadcasted seatUpdate for user '{}' (set to SPEAKING) due to queue item '{}' activation.", updatedUserDoc.getString("username"), itemIdStr);
                    }
                }
            }

            broadcastQueueUpdate();
            response.setStatus(HttpServletResponse.SC_OK);
            JSONObject respJson = new JSONObject();
            respJson.put("message", "Queue item set to active.");
            respJson.put("activeItemId", itemIdStr);
            response.setContentType("application/json");
            response.getWriter().write(respJson.toString());
            logger.info("President '{}' set queue item ID '{}' to active.", presidentUsername, itemIdStr);

        } catch (Exception e) {
            logger.error("Error during setting queue item active (ID: '{}') by President '{}': ", itemIdStr, presidentUsername, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while setting the queue item active.");
        }
    }

    private void handleQueueCompleteActive(HttpServletRequest request, HttpServletResponse response, String itemIdStr) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !"PRESIDENT".equals(session.getAttribute("role"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied. Only the President can complete queue items.");
            logger.warn("Non-president user '{}' attempted to complete queue item '{}'.", session != null ? session.getAttribute("username") : "unauthenticated", itemIdStr);
            return;
        }
        String presidentUsername = (String) session.getAttribute("username");

        ObjectId itemObjectId;
        try {
            itemObjectId = new ObjectId(itemIdStr);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid item ID format '{}' in handleQueueCompleteActive by President '{}'.", itemIdStr, presidentUsername);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid item ID format.");
            return;
        }

        try {
            Document itemToComplete = parliamentQueueCollection.find(
                Filters.and(Filters.eq("_id", itemObjectId), Filters.eq("status", "active"))
            ).first();

            if (itemToComplete == null) {
                // Check if the item exists at all to provide a more specific error if it's not found vs. not active
                Document checkExists = parliamentQueueCollection.find(eq("_id", itemObjectId)).first();
                if (checkExists == null) {
                    logger.warn("President {} attempted to complete item ID '{}', but it was not found.", presidentUsername, itemIdStr);
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Queue item not found.");
                } else {
                    logger.warn("President {} attempted to complete item ID '{}', but it was not active. Current status: {}", presidentUsername, itemIdStr, checkExists.getString("status"));
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Queue item is not currently active.");
                }
                return;
            }

            // Update item status to "completed"
            Document updateOperation = new Document("$set", new Document("status", "completed").append("completedTimestamp", new Date()));
            parliamentQueueCollection.updateOne(eq("_id", itemObjectId), updateOperation);

            // If item was speaker request or objection, update user's seatStatus to NEUTRAL
            String itemType = itemToComplete.getString("type");
            if ("SPEAKER_REQUEST".equals(itemType) || "OBJECTION".equals(itemType)) {
                ObjectId userIdToUpdate = itemToComplete.getObjectId("userId");
                if (userIdToUpdate != null) {
                    Document userUpdateQuery = new Document("_id", userIdToUpdate);
                    Document userUpdateOperation = new Document("$set", new Document("seatStatus", "NEUTRAL"));
                    usersCollection.updateOne(userUpdateQuery, userUpdateOperation);
                    
                    // Broadcast individual user seat update
                    Document updatedUserDoc = usersCollection.find(userUpdateQuery).first();
                    if (updatedUserDoc != null) {
                        JSONObject userJsonForBroadcast = new JSONObject();
                        userJsonForBroadcast.put("id", updatedUserDoc.getObjectId("_id").toHexString());
                        userJsonForBroadcast.put("username", updatedUserDoc.getString("username"));
                        userJsonForBroadcast.put("role", updatedUserDoc.getString("role"));
                        userJsonForBroadcast.put("partyAffiliation", updatedUserDoc.getString("partyAffiliation"));
                        userJsonForBroadcast.put("present", updatedUserDoc.getBoolean("present", false));
                        userJsonForBroadcast.put("seatStatus", updatedUserDoc.getString("seatStatus")); // Should be NEUTRAL
                        userJsonForBroadcast.put("fines", updatedUserDoc.getInteger("fines", 0));
                        userJsonForBroadcast.put("electoralStrength", updatedUserDoc.getInteger("electoralStrength",1));
                        
                        JSONObject seatUpdateMsg = new JSONObject();
                        seatUpdateMsg.put("type", "seatUpdate");
                        seatUpdateMsg.put("user", userJsonForBroadcast);
                        SeatWebSocket.broadcast(seatUpdateMsg);
                        logger.info("Broadcasted seatUpdate for user '{}' (set to NEUTRAL) due to queue item '{}' completion.", updatedUserDoc.getString("username"), itemIdStr);
                    }
                }
            }

            broadcastQueueUpdate();
            response.setStatus(HttpServletResponse.SC_OK);
            JSONObject respJson = new JSONObject();
            respJson.put("message", "Queue item completed.");
            respJson.put("completedItemId", itemIdStr);
            response.setContentType("application/json");
            response.getWriter().write(respJson.toString());
            logger.info("President '{}' completed queue item ID '{}'.", presidentUsername, itemIdStr);

        } catch (Exception e) {
            logger.error("Error during completing queue item (ID: '{}') by President '{}': ", itemIdStr, presidentUsername, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while completing the queue item.");
        }
    }
}
