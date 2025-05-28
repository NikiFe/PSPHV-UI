# API Documentation - Parliamentary Hearing System

This document provides details for the backend API of the Parliamentary Hearing System.

---
## Part 1: User & Session Management API
---

This section details endpoints related to user registration, login, logout, and user information retrieval.

### 1. Register New User

*   **Endpoint:** `POST /api/register`
*   **Description:** Allows a new user to register with the system.
*   **Request Body:** JSON
    *   `username` (String, Mandatory): The desired username. Must be unique.
    *   `password` (String, Mandatory): The user's password.
    *   `role` (String, Optional, Default: "MEMBER"): The user's role. Can be "MEMBER" or "PRESIDENT". Other roles might be rejected or ignored.
*   **Responses:**
    *   `201 Created`: Registration successful.
        ```json
        {
          "message": "Registration successful. Please log in."
        }
        ```
    *   `400 Bad Request`: Invalid input (e.g., empty username/password/role). Note: Server uses `response.sendError()`, so actual format might be Jetty's default HTML error page.
    *   `409 Conflict`: Username already exists.
    *   `500 Internal Server Error`: An unexpected error occurred.
*   **Example Request:**
    ```json
    {
      "username": "newuser",
      "password": "password123",
      "role": "MEMBER"
    }
    ```

### 2. User Login

*   **Endpoint:** `POST /api/login`
*   **Description:** Logs in an existing user.
*   **Request Body:** JSON
    *   `username` (String, Mandatory): The user's username.
    *   `password` (String, Mandatory): The user's password.
*   **Responses:**
    *   `200 OK`: Login successful. Session created. CSRF token provided.
        ```json
        {
          "message": "Login successful.",
          "csrfToken": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
        }
        ```
    *   `400 Bad Request`: Username or password empty, or malformed JSON.
    *   `401 Unauthorized`: Invalid username or password.
    *   `500 Internal Server Error`: An unexpected error occurred.
*   **Example Request:**
    ```json
    {
      "username": "testuser",
      "password": "password123"
    }
    ```
*   **Note:** On successful login, a session cookie is set (HttpOnly, Secure if configured). The `csrfToken` from the response body must be stored by the client and sent as an `X-CSRF-TOKEN` header in subsequent state-changing requests.

### 3. User Logout

*   **Endpoint:** `POST /api/logout`
*   **Description:** Logs out the currently authenticated user and invalidates their session. Requires `X-CSRF-TOKEN` header.
*   **Request Body:** None.
*   **Responses:**
    *   `200 OK`: Logout successful.
        ```json
        {
          "message": "Logged out successfully."
        }
        ```
    *   `400 Bad Request`: No active session.
    *   `403 Forbidden`: CSRF check fails.
    *   `500 Internal Server Error`: An unexpected error occurred.
*   **Example Request:** `POST /api/logout` (with `X-CSRF-TOKEN` header)

### 4. Get Current User Information

*   **Endpoint:** `GET /api/user-info`
*   **Description:** Retrieves information for the currently authenticated user.
*   **Request Body:** None.
*   **Responses:**
    *   `200 OK`: Successfully retrieved user information.
        ```json
        {
          "id": "mongodb_object_id_string",
          "username": "testuser",
          "role": "MEMBER",
          "present": false,
          "seatStatus": "NEUTRAL",
          "fines": 0,
          "partyAffiliation": "Some Party",
          "electoralStrength": 1,
          "X-CSRF-TOKEN": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
        }
        ```
    *   `401 Unauthorized`: User not authenticated.
    *   `404 Not Found`: Authenticated user not found in database.
*   **Example Request:** `GET /api/user-info`

### 5. Get List of Users

*   **Endpoint:** `GET /api/users`
*   **Description:** Retrieves a list of all users, or only users currently marked as "present".
*   **Query Parameters:**
    *   `present` (Boolean, Optional, e.g., `true`): If `true`, returns only users with `present: true`.
*   **Responses:**
    *   `200 OK`: Successfully retrieved list of users. (Array of user objects, password excluded).
    *   `500 Internal Server Error`: An unexpected error occurred.
*   **Example Request:** `GET /api/users?present=true`

### 6. Get Specific User by ID

*   **Endpoint:** `GET /api/users/{id}`
*   **Description:** Retrieves information for a specific user by ID. Access is restricted.
*   **Path Parameters:**
    *   `id` (String, Mandatory): The MongoDB ObjectId of the user.
*   **Responses:**
    *   `200 OK`: User information retrieved. (User object, password excluded).
    *   `400 Bad Request`: Invalid user ID format.
    *   `401 Unauthorized`: Requesting user not authenticated.
    *   `403 Forbidden`: Not authorized to view (not self, not President).
    *   `404 Not Found`: User ID not found.
    *   `500 Internal Server Error`.
*   **Example Request:** `GET /api/users/605c724f311d6b001f9b0f0a`

### 7. Update User Seat Status / Presence

*   **Endpoint:** `POST /api/users/update-status`
*   **Description:** Allows a user to update their own seat status, or President to update any user. Marks user `present: true`. Requires `X-CSRF-TOKEN`.
*   **Request Body:** JSON
    *   `id` (String, Mandatory): MongoDB ObjectId of the user.
    *   `seatStatus` (String, Mandatory): Valid values: "NEUTRAL", "REQUESTING_TO_SPEAK", "SPEAKING", "OBJECTING".
*   **Responses:**
    *   `200 OK`: Status updated. Returns updated user document.
    *   `400 Bad Request`: Invalid input (missing fields, invalid `seatStatus`, invalid ID).
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not authorized (e.g., modifying other if not President, or canceling own objection).
    *   `404 Not Found`: Target user ID not found.
    *   `500 Internal Server Error`.
*   **Example Request (User self-update):**
    ```json
    {
      "id": "current_user_mongodb_object_id_string",
      "seatStatus": "REQUESTING_TO_SPEAK"
    }
    ```

### 8. Update Multiple Users (President Only)

*   **Endpoint:** `POST /api/users/update`
*   **Description:** President updates multiple users' info (electoral strength, party, role). Requires `X-CSRF-TOKEN`.
*   **Request Body:** JSON Array of user update objects: `{ "id": "...", "electoralStrength": ..., "partyAffiliation": "...", "role": "..." }`.
*   **Responses:**
    *   `200 OK`: `{"message": "User updates processed successfully."}`
    *   `400 Bad Request`: Malformed JSON, invalid ID, or invalid role.
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `500 Internal Server Error`.
*   **Example Request:**
    ```json
    [
      { "id": "id1", "electoralStrength": 2 }, { "id": "id2", "role": "PRESIDENT" }
    ]
    ```

---
## Part 2: Proposals & Voting API
---

This section details endpoints related to creating, viewing, updating, deleting proposals, and managing voting processes.

### 1. Create New Proposal (President Only)

*   **Endpoint:** `POST /api/proposals`
*   **Description:** Allows the President to create a new proposal directly. Requires `X-CSRF-TOKEN`.
*   **Request Body:** JSON
    *   `title` (String, Mandatory): Proposal title.
    *   `party` (String, Optional, Default: "President").
    *   `priority` (Boolean, Optional, Default: `false`).
    *   `constitutional` (Boolean, Optional, Default: `false`).
    *   `voteRequirement` (String, Optional, Default: "Rel"). E.g., "Rel", "2/3", "3/5", "1/2", "2/3+", "3/5+", "1/2+".
    *   `type` (String, Optional, Default: "normal"). E.g., "normal", "additive", "countering".
    *   `assProposal` (String, Optional, Default: ""): Visual ID of associated proposal if type is not "normal".
*   **Responses:**
    *   `200 OK`: `{"message": "New proposal added successfully."}` (Ideally `201 Created`).
    *   `400 Bad Request`: Invalid input.
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `500 Internal Server Error`.
*   **Example Request:**
    ```json
    {
      "title": "New Environmental Law", "party": "Green Party", "priority": false
    }
    ```

### 2. Get All Proposals

*   **Endpoint:** `GET /api/proposals`
*   **Description:** Retrieves all proposals. Includes user's vote if authenticated.
*   **Responses:**
    *   `200 OK`: Array of proposal objects. Each includes `id`, `title`, `proposalNumber`, `party`, `isPriority`, `isConstitutional`, `voteRequirement`, `stupid`, `associationType`, `referencedProposal`, `proposalVisual`, `meetingNumber`, and voting results if `votingEnded: true` (`passed`, `totalFor`, `totalAgainst`), plus `userVote` ("For", "Against", "Abstain") if user authenticated.
    *   `500 Internal Server Error`.
*   **Example Request:** `GET /api/proposals`

### 3. Get Specific Proposal by ID

*   **Endpoint:** `GET /api/proposals/{id}`
*   **Description:** Retrieves details for a specific proposal.
*   **Path Parameters:** `id` (String, Mandatory): Proposal's MongoDB ObjectId.
*   **Responses:**
    *   `200 OK`: Proposal details (similar to one item in GET /api/proposals).
    *   `400 Bad Request`: Invalid ID format.
    *   `404 Not Found`.
    *   `500 Internal Server Error`.
*   **Example Request:** `GET /api/proposals/605c724f311d6b001f9b0f0b`

### 4. Update Proposal (President Only)

*   **Endpoint:** `PUT /api/proposals/{id}`
*   **Description:** President updates fields of a proposal. Requires `X-CSRF-TOKEN`.
*   **Path Parameters:** `id` (String, Mandatory): Proposal's MongoDB ObjectId.
*   **Request Body:** JSON. Optional fields: `title` (String), `party` (String), `stupid` (Boolean).
*   **Responses:**
    *   `200 OK`: `{"message": "Proposal updated successfully."}` or `{"message": "No fields to update."}`.
    *   `400 Bad Request`: Invalid input or ID format.
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `404 Not Found`.
    *   `500 Internal Server Error`.
*   **Example Request:**
    ```json
    { "title": "Updated Title", "stupid": true }
    ```

### 5. Delete Proposal (President Only)

*   **Endpoint:** `DELETE /api/proposals/{id}`
*   **Description:** President deletes a proposal. Requires `X-CSRF-TOKEN`.
*   **Path Parameters:** `id` (String, Mandatory): Proposal's MongoDB ObjectId.
*   **Responses:**
    *   `200 OK`: `{"message": "Proposal deleted successfully."}`.
    *   `400 Bad Request`: Invalid ID format.
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `500 Internal Server Error`.
*   **Example Request:** `DELETE /api/proposals/605c724f311d6b001f9b0f0b` (with `X-CSRF-TOKEN`)

### 6. Submit Vote on a Proposal

*   **Endpoint:** `POST /api/proposals/vote`
*   **Description:** Authenticated user submits/changes vote. Requires `X-CSRF-TOKEN`.
*   **Request Body:** JSON
    *   `proposalId` (String, Mandatory): Proposal's MongoDB ObjectId.
    *   `voteChoice` (String, Mandatory): "For", "Against", "Abstain".
*   **Responses:**
    *   `200 OK`: `{"message": "Vote submitted successfully."}`.
    *   `400 Bad Request`: Invalid input (ID, choice), proposal not found, voting ended/stupid.
    *   `401 Unauthorized`.
    *   `500 Internal Server Error`.
*   **Example Request:**
    ```json
    { "proposalId": "605c724f311d6b001f9b0f0b", "voteChoice": "For" }
    ```

### 7. End Voting (President Only)

*   **Endpoints:** Requires `X-CSRF-TOKEN`.
    *   `POST /api/proposals/end-voting`: Ends normal proposals. Sends Discord message, increments meeting number.
    *   `POST /api/proposals/end-voting-priority`: Ends priority proposals. No Discord message.
    *   `POST /api/proposals/end-voting-constitutional`: Ends constitutional proposals. No Discord message.
*   **Responses (for all):**
    *   `200 OK`: Message indicating what was processed (e.g., `{"message": "Voting ended for normal proposals..."}`).
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `500 Internal Server Error`.
*   **Example Request:** `POST /api/proposals/end-voting` (with `X-CSRF-TOKEN`)

---
## Part 3: Player Submitted Proposals API
---

This section details endpoints for players to submit new proposals and for the President to manage these pending proposals.

### 1. Submit a Proposal (Authenticated Users)

*   **Endpoint:** `POST /api/proposals/submit`
*   **Description:** Authenticated user submits a proposal for President's review. Requires `X-CSRF-TOKEN`.
*   **Request Body:** JSON
    *   `title` (String, Mandatory, Max: 200 chars).
    *   `description` (String, Optional, Default: "", Max: 2000 chars).
*   **Responses:**
    *   `201 Created`: `{"message": "Pending proposal submitted successfully.", "pendingProposalId": "mongodb_id"}`.
    *   `400 Bad Request`: Invalid input (missing title, too long, malformed JSON).
    *   `401 Unauthorized`.
    *   `403 Forbidden`: CSRF validation failed.
    *   `409 Conflict`: Duplicate submission detected.
    *   `500 Internal Server Error`.
*   **Example Request:**
    ```json
    { "title": "New community garden", "description": "Plan and benefits..." }
    ```
*   **WebSocket:** `pendingProposalNew` broadcast.

### 2. View Pending Proposals (President Only)

*   **Endpoint:** `GET /api/proposals/pending`
*   **Description:** President retrieves list of "pending" proposals.
*   **Responses:**
    *   `200 OK`: Array of pending proposal objects. Each includes `id`, `title`, `description`, `submittedByUserId`, `submittedByUsername`, `submissionTimestamp`, `status`.
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `500 Internal Server Error`.
*   **Example Request:** `GET /api/proposals/pending`

### 3. Approve a Pending Proposal (President Only)

*   **Endpoint:** `POST /api/proposals/pending/{id}/approve`
*   **Description:** President approves a pending proposal, moving it to main proposals. Requires `X-CSRF-TOKEN`.
*   **Path Parameters:** `id` (String, Mandatory): Pending proposal's MongoDB ObjectId.
*   **Responses:**
    *   `200 OK`: Returns the newly created main proposal object.
    *   `400 Bad Request`: Invalid ID or proposal not "pending".
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `404 Not Found`: Pending proposal not found.
    *   `500 Internal Server Error`.
*   **Example Request:** `POST /api/proposals/pending/605c7abc311d6b001f9b0f0c/approve` (with `X-CSRF-TOKEN`)
*   **WebSockets:** `proposalUpdate` (for new main proposal), `pendingProposalStatusUpdate`.

### 4. Reject a Pending Proposal (President Only)

*   **Endpoint:** `POST /api/proposals/pending/{id}/reject`
*   **Description:** President rejects a pending proposal. Requires `X-CSRF-TOKEN`.
*   **Path Parameters:** `id` (String, Mandatory): Pending proposal's MongoDB ObjectId.
*   **Responses:**
    *   `200 OK`: `{"message": "Pending proposal rejected successfully.", "pendingProposalId": "id", "status": "rejected"}`.
    *   `400 Bad Request`: Invalid ID or proposal not "pending".
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `404 Not Found`: Pending proposal not found.
    *   `500 Internal Server Error`.
*   **Example Request:** `POST /api/proposals/pending/605c7abc311d6b001f9b0f0d/reject` (with `X-CSRF-TOKEN`)
*   **WebSocket:** `pendingProposalStatusUpdate`.

---
## Part 4: Queue Management API
---

This section details endpoints related to the parliamentary queue.

### 1. Request to Speak (Authenticated Users)

*   **Endpoint:** `POST /api/queue/request-speak`
*   **Description:** Authenticated user requests to speak. Adds to queue. Requires `X-CSRF-TOKEN`.
*   **Request Body:** JSON (Optional)
    *   `proposalId` (String, Optional): Specific proposal ID to speak about.
*   **Responses:**
    *   `200 OK`: `{"message": "Request to speak added to queue successfully."}`.
    *   `400 Bad Request`: Invalid `proposalId` or malformed JSON.
    *   `401 Unauthorized`.
    *   `403 Forbidden`: CSRF validation failed.
    *   `409 Conflict`: Already in queue or objecting.
    *   `500 Internal Server Error`.
*   **Example Request (General):** `POST /api/queue/request-speak` (Empty JSON body `{}` or no body).
*   **WebSocket:** `queueUpdate` broadcast.

### 2. View Parliamentary Queue

*   **Endpoint:** `GET /api/parliament-queue/view`
*   **Description:** Retrieves current queue (items "pending" or "active"), sorted by priority then timestamp.
*   **Responses:**
    *   `200 OK`: Array of queue item objects. Each item has `id`, `type` ("OBJECTION", "SPEAKER_REQUEST", "PROPOSAL_DISCUSSION"), `userId`/`username` (for user types), `proposalId`/`proposalTitle`/`proposalVisual` (for proposal type), `timestamp`, `priority`, `status`.
    *   `401 Unauthorized`.
    *   `500 Internal Server Error`.
*   **Example Request:** `GET /api/parliament-queue/view`

### 3. Set Queue Item as Active (President Only)

*   **Endpoint:** `POST /api/queue/set-active/{itemId}`
*   **Description:** President marks a queue item "active". Previous active item -> "pending". If user item, user `seatStatus` -> "SPEAKING". Requires `X-CSRF-TOKEN`.
*   **Path Parameters:** `itemId` (String, Mandatory): Queue item's MongoDB ObjectId.
*   **Responses:**
    *   `200 OK`: `{"message": "Queue item set to active."}`.
    *   `400 Bad Request`: Invalid `itemId`.
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `404 Not Found`: Queue item not found.
    *   `500 Internal Server Error`.
*   **Example Request:** `POST /api/queue/set-active/605c7def311d6b001f9b0f0e` (with `X-CSRF-TOKEN`)
*   **WebSockets:** `queueUpdate`, and `seatUpdate` if a user's status changed.

### 4. Complete/Clear Active Queue Item (President Only)

*   **Endpoint:** `POST /api/queue/complete-active/{itemId}`
*   **Description:** President marks an "active" item "completed". If user item, user `seatStatus` -> "NEUTRAL". Requires `X-CSRF-TOKEN`.
*   **Path Parameters:** `itemId` (String, Mandatory): Active queue item's MongoDB ObjectId.
*   **Responses:**
    *   `200 OK`: `{"message": "Active queue item completed."}`.
    *   `400 Bad Request`: Invalid `itemId` or item not "active".
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `404 Not Found`: Queue item not found or not active.
    *   `500 Internal Server Error`.
*   **Example Request:** `POST /api/queue/complete-active/605c7def311d6b001f9b0f0e` (with `X-CSRF-TOKEN`)
*   **WebSockets:** `queueUpdate`, and `seatUpdate` if a user's status changed.

---
## Part 5: System & Other Endpoints API
---

This section details miscellaneous system-level endpoints and provides an overview of WebSocket events.

### 1. Join Seat

*   **Endpoint:** `POST /api/join-seat`
*   **Description:** Authenticated user "joins seat" (`present: true`, `seatStatus: "NEUTRAL"`). Requires `X-CSRF-TOKEN`.
*   **Responses:**
    *   `200 OK`: `{"message": "Joined seat successfully."}`.
    *   `401 Unauthorized`.
    *   `403 Forbidden`: CSRF validation failed.
    *   `500 Internal Server Error`.
*   **WebSocket:** `seatUpdate` for the user.

### 2. Impose Fine (President Only)

*   **Endpoint:** `POST /api/impose-fine`
*   **Description:** President imposes fine. Requires `X-CSRF-TOKEN`.
*   **Request Body:** JSON: `username` (String, Mandatory), `amount` (Integer, Mandatory, >0), `reason` (String, Mandatory).
*   **Responses:**
    *   `200 OK`: `{"message": "Fine imposed successfully."}`.
    *   `400 Bad Request`: Invalid input.
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `404 Not Found`: User to fine not found.
    *   `500 Internal Server Error`.
*   **WebSocket:** `fineImposed` with `username`, `amount`, `reason`.

### 3. Call a Break (President Only)

*   **Endpoint:** `POST /api/break`
*   **Description:** President calls a break (`breakActive: true`). Requires `X-CSRF-TOKEN`.
*   **Responses:**
    *   `200 OK`: `{"message": "Break has been called."}`.
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `500 Internal Server Error`.
*   **WebSocket:** `break` message (type: "break").

### 4. End a Break (President Only)

*   **Endpoint:** `POST /api/end-break`
*   **Description:** President ends break (`breakActive: false`). Requires `X-CSRF-TOKEN`.
*   **Responses:**
    *   `200 OK`: `{"message": "Break has ended."}`.
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `500 Internal Server Error`.
*   **WebSocket:** `endBreak` message (type: "endBreak").

### 5. End Current Session (President Only)

*   **Endpoint:** `POST /api/end-session`
*   **Description:** President ends session (all users `present: false`, `seatStatus: "NEUTRAL"`). Requires `X-CSRF-TOKEN`.
*   **Responses:**
    *   `200 OK`: `{"message": "Session has been ended."}`.
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `500 Internal Server Error`.
*   **WebSocket:** `endSession` message (type: "endSession").

### 6. Get System Break Status

*   **Endpoint:** `GET /api/system/break-status`
*   **Description:** Retrieves current break status.
*   **Responses:**
    *   `200 OK`: `{"breakActive": true | false}`.
    *   `500 Internal Server Error`.

### 7. Update Electoral Results (President Only)

*   **Endpoint:** `POST /api/elections/results`
*   **Description:** President updates users' electoral strengths. Requires `X-CSRF-TOKEN`.
*   **Request Body:** JSON Array: `[ { "username": "user1", "electoralStrength": 3 }, ... ]`.
*   **Responses:**
    *   `200 OK`: `{"message": "Election results processed successfully."}`.
    *   `400 Bad Request`: Malformed JSON.
    *   `401 Unauthorized`.
    *   `403 Forbidden`: Not President.
    *   `500 Internal Server Error`.

## WebSocket Events

WebSocket endpoint: `/ws/seat`. Messages are JSON with a `type` field.

*   **`seatUpdate`**: User status/presence change. Payload: `{ "type": "seatUpdate", "user": { ...user_object... } }`.
*   **`proposalUpdate`**: New/updated proposal. Payload: `{ "type": "proposalUpdate", "proposal": { ...proposal_object... } }`.
*   **`proposalDelete`**: Proposal deleted. Payload: `{ "type": "proposalDelete", "proposalId": "id_string" }`.
*   **`proposalsUpdated`**: Batch proposal update signal. Payload: `{ "type": "proposalsUpdated" }`.
*   **`fineImposed`**: Fine imposed. Payload: `{ "type": "fineImposed", "username": "string", "amount": int, "reason": "string" }`.
*   **`break`**: Break called. Payload: `{ "type": "break" }`.
*   **`endBreak`**: Break ended. Payload: `{ "type": "endBreak" }`.
*   **`endSession`**: Session ended. Payload: `{ "type": "endSession" }`.
*   **`pendingProposalNew`**: Player submitted proposal. Payload: `{ "type": "pendingProposalNew", "proposal": { ...pending_proposal_object... } }`.
*   **`pendingProposalStatusUpdate`**: Pending proposal approved/rejected. Payload: `{ "type": "pendingProposalStatusUpdate", "pendingProposalId": "id", "status": "approved"|"rejected", "mainProposalId": "id_if_approved_optional" }`.
*   **`queueUpdate`**: Parliamentary queue changed. Payload: `{ "type": "queueUpdate", "queue": [ ...array_of_queue_items... ] }`.

---
```
