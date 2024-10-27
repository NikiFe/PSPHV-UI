// app.js

// ======================
// DOM Elements
// ======================
const authContainer = document.getElementById('auth-container');
const loginTab = document.getElementById('login-tab');
const registerTab = document.getElementById('register-tab');
const loginForm = document.getElementById('login-form');
const registerForm = document.getElementById('register-form');

const mainContainer = document.getElementById('main-container');
const logoutButton = document.getElementById('logout');
const joinSeatButton = document.getElementById('join-seat');

const seatLayout = document.getElementById('seat-layout');
const proposalsTable = document.getElementById('proposals-table');

const presidentActions = document.getElementById('president-actions');
const addProposalButton = document.getElementById('add-proposal');
const newProposalTitle = document.getElementById('new-proposal-title');
const newProposalParty = document.getElementById('new-proposal-party');
const imposeFineButton = document.getElementById('impose-fine');
const fineUsername = document.getElementById('fine-username');
const fineAmount = document.getElementById('fine-amount');
const callBreakButton = document.getElementById('call-break');
const endSessionButton = document.getElementById('end-session');

const alertContainer = document.getElementById('alert');
const alertMessage = document.getElementById('alert-message');

// ======================
// State Variables
// ======================
let currentUser = null; // { username: '', role: '' }
let ws = null; // WebSocket connection

// ======================
// Utility Functions
// ======================

/**
 * Displays an alert message to the user.
 * @param {string} message - The message to display.
 * @param {string} type - The type of alert ('success', 'error', 'warning').
 */
function showAlert(message, type = 'success') {
    alertMessage.innerText = message;
    alertContainer.className = ''; // Reset classes

    // Set classes based on alert type
    if (type === 'success') {
        alertContainer.classList.add('block', 'px-4', 'py-3', 'rounded', 'shadow-lg', 'mb-4', 'bg-green-100', 'border', 'border-green-400', 'text-green-700');
    } else if (type === 'error') {
        alertContainer.classList.add('block', 'px-4', 'py-3', 'rounded', 'shadow-lg', 'mb-4', 'bg-red-100', 'border', 'border-red-400', 'text-red-700');
    } else if (type === 'warning') {
        alertContainer.classList.add('block', 'px-4', 'py-3', 'rounded', 'shadow-lg', 'mb-4', 'bg-yellow-100', 'border', 'border-yellow-400', 'text-yellow-700');
    }

    // Automatically hide after 5 seconds
    setTimeout(() => {
        alertContainer.classList.add('hidden');
    }, 5000);
}

/**
 * Switches between Login and Register tabs.
 * @param {string} activeTab - 'login' or 'register'.
 */
function switchTab(activeTab) {
    if (activeTab === 'login') {
        loginTab.classList.add('text-blue-400', 'border-blue-400', 'font-semibold');
        loginTab.classList.remove('text-gray-400', 'border-transparent');

        registerTab.classList.remove('text-blue-400', 'border-blue-400', 'font-semibold');
        registerTab.classList.add('text-gray-400', 'border-transparent');

        loginForm.classList.remove('hidden');
        registerForm.classList.add('hidden');
    } else {
        registerTab.classList.add('text-blue-400', 'border-blue-400', 'font-semibold');
        registerTab.classList.remove('text-gray-400', 'border-transparent');

        loginTab.classList.remove('text-blue-400', 'border-blue-400', 'font-semibold');
        loginTab.classList.add('text-gray-400', 'border-transparent');

        registerForm.classList.remove('hidden');
        loginForm.classList.add('hidden');
    }
}

/**
 * Fetches the authenticated user's information.
 * @returns {Promise<Object|null>} - User info or null.
 */
async function fetchUserInfo() {
    try {
        const response = await fetch('/api/user-info', {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' }
        });
        if (response.ok) {
            const data = await response.json();
            return data; // { username: '', role: '' }
        } else {
            return null;
        }
    } catch (error) {
        console.error('Error fetching user info:', error);
        return null;
    }
}

/**
 * Fetches all proposals from the backend.
 */
async function fetchProposals() {
    try {
        const response = await fetch('/api/proposals', {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' }
        });
        if (response.ok) {
            const proposals = await response.json();
            renderProposals(proposals);
        } else {
            console.warn('Failed to fetch proposals.');
        }
    } catch (error) {
        console.error('Error fetching proposals:', error);
    }
}

/**
 * Renders the proposals in the proposals table.
 * @param {Array} proposals - Array of proposal objects.
 */
function renderProposals(proposals) {
    proposalsTable.innerHTML = ''; // Clear existing
    proposals.forEach(proposal => {
        const row = proposalsTable.insertRow();
        const numberCell = row.insertCell(0);
        const titleCell = row.insertCell(1);
        const partyCell = row.insertCell(2);

        numberCell.innerText = proposal.proposalNumber;
        titleCell.innerText = proposal.title;
        partyCell.innerText = proposal.party;
    });
}

/**
 * Fetches all users who are currently present.
 */
async function fetchUsers() {
    try {
        const response = await fetch('/api/users', {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' }
        });
        if (response.ok) {
            const users = await response.json();
            renderSeats(users);
        } else {
            console.warn('Failed to fetch users.');
        }
    } catch (error) {
        console.error('Error fetching users:', error);
    }
}

/**
 * Renders the seat layout based on users.
 * @param {Array} users - Array of user objects.
 */
function renderSeats(users) {
    seatLayout.innerHTML = ''; // Clear existing seats
    users.forEach(user => {
        if (user.present) {
            addOrUpdateSeat(user);
        }
    });
}

/**
 * Adds or updates a seat in the seat layout.
 * @param {Object} user - User object.
 */
function addOrUpdateSeat(user) {
    let seat = document.getElementById(`seat-${user.id}`);

    if (!seat) {
        // Create new seat
        seat = document.createElement('div');
        seat.classList.add('p-4', 'rounded-md', 'shadow', 'relative', 'bg-gray-700'); // Added bg-gray-700 as default
        seat.id = `seat-${user.id}`;

        // Dynamic Background Color Based on Seat Status
        updateSeatBackground(seat, user.seatStatus);

        seat.innerHTML = `
            <h3 class="text-lg font-semibold">${user.username}</h3>
            <p class="text-sm">Role: ${user.role}</p>
            <p class="text-sm">Party: ${user.partyAffiliation || 'N/A'}</p>
            <p class="text-sm">Fines: ${user.fines || 0}</p>
            <div class="absolute top-2 right-2 space-x-2 user-actions">
                <button class="raise-hand bg-blue-500 hover:bg-blue-600 text-white py-1 px-2 rounded text-xs" data-user-id="${user.id}" data-seat-status="${user.seatStatus}">
                    ${user.seatStatus === 'REQUESTING_TO_SPEAK' || user.seatStatus === 'OBJECTING' ? 'Cancel' : 'Raise Hand'}
                </button>
                <button class="object bg-red-500 hover:bg-red-600 text-white py-1 px-2 rounded text-xs" data-user-id="${user.id}">
                    Object
                </button>
                <!-- President-specific buttons will be added dynamically -->
            </div>
        `;

        seatLayout.appendChild(seat);

        // Attach Event Listeners to Buttons
        const raiseHandButton = seat.querySelector('.raise-hand');
        const objectButton = seat.querySelector('.object');

        raiseHandButton.addEventListener('click', () => {
            const currentStatus = raiseHandButton.dataset.seatStatus;
            if (currentStatus === 'REQUESTING_TO_SPEAK' || currentStatus === 'OBJECTING') {
                // Cancel hand
                updateSeatStatus(user.id, 'NEUTRAL');
            } else {
                // Raise hand
                updateSeatStatus(user.id, 'REQUESTING_TO_SPEAK');
            }
        });

        objectButton.addEventListener('click', () => {
            // Object
            updateSeatStatus(user.id, 'OBJECTING');
        });

        // If the current user is the president, add controls to assign speaking
        if (currentUser && currentUser.role === 'PRESIDENT') {
            if (user.seatStatus === 'REQUESTING_TO_SPEAK' || user.seatStatus === 'OBJECTING') {
                addPresidentControls(seat, user);
            }
        }
    } else {
        // Update existing seat
        updateSeat(seat, user);
    }
}

/**
 * Updates the seat's background color based on seat status.
 * @param {HTMLElement} seat - The seat element.
 * @param {string} seatStatus - The current seat status.
 */
function updateSeatBackground(seat, seatStatus) {
    seat.classList.remove('bg-gray-700', 'bg-yellow-500', 'bg-red-500', 'bg-green-500');
    switch (seatStatus) {
        case 'REQUESTING_TO_SPEAK':
            seat.classList.add('bg-yellow-500');
            break;
        case 'OBJECTING':
            seat.classList.add('bg-red-500');
            break;
        case 'SPEAKING':
            seat.classList.add('bg-green-500');
            break;
        default:
            seat.classList.add('bg-gray-700');
    }
}

/**
 * Updates an existing seat's information.
 * @param {HTMLElement} seat - The seat element.
 * @param {Object} user - User object.
 */
function updateSeat(seat, user) {
    // Update background color
    updateSeatBackground(seat, user.seatStatus);

    // Update user details
    seat.querySelector('h3').innerText = user.username;
    seat.querySelector('p:nth-child(2)').innerText = `Role: ${user.role}`;
    seat.querySelector('p:nth-child(3)').innerText = `Party: ${user.partyAffiliation || 'N/A'}`;
    seat.querySelector('p:nth-child(4)').innerText = `Fines: ${user.fines || 0}`;

    // Update the Raise Hand button label and data-seat-status
    const raiseHandButton = seat.querySelector('.raise-hand');
    if (user.seatStatus === 'REQUESTING_TO_SPEAK' || user.seatStatus === 'OBJECTING') {
        raiseHandButton.textContent = 'Cancel';
    } else {
        raiseHandButton.textContent = 'Raise Hand';
    }
    raiseHandButton.dataset.seatStatus = user.seatStatus;

    // Remove existing president controls if any
    const existingControls = seat.querySelectorAll('.give-speaking');
    existingControls.forEach(control => control.remove());

    // Add president controls if necessary
    if (currentUser && currentUser.role === 'PRESIDENT') {
        if (user.seatStatus === 'REQUESTING_TO_SPEAK' || user.seatStatus === 'OBJECTING') {
            addPresidentControls(seat, user);
        }
    }
}

/**
 * Adds presidential controls ("Give Speaking" button) to a user's seat.
 * @param {HTMLElement} seat - The seat element.
 * @param {Object} user - User object.
 */
function addPresidentControls(seat, user) {
    const userActions = seat.querySelector('.user-actions');

    // Create "Give Speaking" Button
    const giveSpeakingButton = document.createElement('button');
    giveSpeakingButton.classList.add('give-speaking', 'bg-green-500', 'hover:bg-green-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
    giveSpeakingButton.textContent = 'Give Speaking';
    giveSpeakingButton.dataset.userId = user.id;

    // Append to user actions
    userActions.appendChild(giveSpeakingButton);

    // Attach Event Listener
    giveSpeakingButton.addEventListener('click', () => {
        updateSeatStatus(user.id, 'SPEAKING');
    });
}

/**
 * Removes a seat from the seat layout.
 * @param {string} userId - The user's ID.
 */
function removeSeat(userId) {
    const seat = document.getElementById(`seat-${userId}`);
    if (seat) {
        seat.remove();
    }
}

/**
 * Updates the seat status of a user by making a POST request.
 * @param {string} userId - The ID of the user.
 * @param {string} status - The new seat status.
 */
async function updateSeatStatus(userId, status) {
    try {
        // Determine if 'present' should remain true
        let present = true; // Assume the user remains present

        // If status indicates leaving, set present to false
        // In this case, 'NEUTRAL' does not imply leaving
        // Only 'LEAVING' or similar statuses should set present to false
        // Modify accordingly based on your application's logic

        const isLeaving = status === 'LEAVING'; // Adjust based on your application's logic
        if (isLeaving) {
            present = false;
        }

        const payload = { id: userId, seatStatus: status };
        if (isLeaving) {
            payload.present = present;
        }

        const response = await fetch('/api/users/update-status', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (response.ok) {
            let message = '';
            switch (status) {
                case 'REQUESTING_TO_SPEAK':
                    message = 'You have raised your hand to speak.';
                    break;
                case 'OBJECTING':
                    message = 'You are objecting.';
                    break;
                case 'SPEAKING':
                    message = 'You are now speaking.';
                    break;
                case 'NEUTRAL':
                    message = 'Your hand has been lowered.';
                    break;
                case 'LEAVING':
                    message = 'You have left the meeting.';
                    break;
                default:
                    message = 'Seat status updated successfully!';
            }
            showAlert(message, 'success');
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error updating seat status:', error);
        showAlert('An error occurred while updating seat status.', 'error');
    }
}

// ======================
// Event Listeners
// ======================

/**
 * Handles the Login form submission.
 * @param {Event} e - The form submit event.
 */
loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('login-username').value.trim();
    const password = document.getElementById('login-password').value.trim();

    try {
        const response = await fetch('/api/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        if (response.ok) {
            showAlert('Login successful!', 'success');
            await initializeApp();
        } else {
            const errorText = await response.text();
            showAlert(errorText || 'Login failed.', 'error');
        }
    } catch (error) {
        console.error('Login error:', error);
        showAlert('An error occurred during login.', 'error');
    }
});

/**
 * Handles the Registration form submission.
 * @param {Event} e - The form submit event.
 */
registerForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('register-username').value.trim();
    const password = document.getElementById('register-password').value.trim();

    if (!username || !password) {
        showAlert('Please fill in all fields.', 'warning');
        return;
    }

    try {
        const response = await fetch('/api/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        if (response.ok) {
            showAlert('Registration successful! Please log in.', 'success');
            registerForm.reset();
            switchTab('login');
        } else {
            const errorText = await response.text();
            showAlert(errorText || 'Registration failed.', 'error');
        }
    } catch (error) {
        console.error('Registration error:', error);
        showAlert('An error occurred during registration.', 'error');
    }
});

/**
 * Handles the Logout button click.
 */
logoutButton.addEventListener('click', async () => {
    try {
        const response = await fetch('/api/logout', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response.ok) {
            showAlert('Logged out successfully.', 'success');
            resetApp();
        } else {
            const errorText = await response.text();
            showAlert(errorText || 'Logout failed.', 'error');
        }
    } catch (error) {
        console.error('Logout error:', error);
        showAlert('An error occurred during logout.', 'error');
    }
});

/**
 * Adds a new proposal via Presidential Action.
 */
addProposalButton.addEventListener('click', async () => {
    const title = newProposalTitle.value.trim();
    const party = newProposalParty.value.trim();

    if (!title || !party) {
        showAlert('Proposal title and proposing party cannot be empty.', 'warning');
        return;
    }

    try {
        const response = await fetch('/api/proposals', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title, party })
        });

        if (response.ok) {
            showAlert('Proposal added successfully!', 'success');
            newProposalTitle.value = '';
            newProposalParty.value = '';
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error adding proposal:', error);
        showAlert('An error occurred while adding the proposal.', 'error');
    }
});

/**
 * Imposes a fine on a specified user via Presidential Action.
 */
imposeFineButton.addEventListener('click', async () => {
    const username = fineUsername.value.trim();
    const amount = parseInt(fineAmount.value.trim(), 10);

    if (!username || isNaN(amount) || amount <= 0) {
        showAlert('Please enter a valid username and fine amount.', 'warning');
        return;
    }

    try {
        const response = await fetch('/api/impose-fine', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, amount })
        });

        if (response.ok) {
            showAlert('Fine imposed successfully!', 'success');
            fineUsername.value = '';
            fineAmount.value = '';
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error imposing fine:', error);
        showAlert('An error occurred while imposing the fine.', 'error');
    }
});

/**
 * Calls a break for all users via Presidential Action.
 */
callBreakButton.addEventListener('click', async () => {
    try {
        const response = await fetch('/api/break', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response.ok) {
            showAlert('Break has been called.', 'success');
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error calling break:', error);
        showAlert('An error occurred while calling a break.', 'error');
    }
});

/**
 * Ends the current session via Presidential Action.
 */
endSessionButton.addEventListener('click', async () => {
    if (!confirm('Are you sure you want to end the session?')) return;

    try {
        const response = await fetch('/api/end-session', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response.ok) {
            showAlert('Session has been ended.', 'success');
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error ending session:', error);
        showAlert('An error occurred while ending the session.', 'error');
    }
});

/**
 * Handles the "Join Seat" button click.
 */
joinSeatButton.addEventListener('click', async () => {
    try {
        const response = await fetch('/api/join-seat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response.ok) {
            showAlert('Successfully joined a seat.', 'success');
            await fetchUsers(); // Refresh seats
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error joining seat:', error);
        showAlert('An error occurred while joining the seat.', 'error');
    }
});

// ======================
// WebSocket Message Handlers
// ======================

/**
 * Handles seat updates received via WebSocket.
 * @param {Object} user - The user object with updated seat information.
 */
function handleSeatUpdate(user) {
    if (user.present) {
        addOrUpdateSeat(user);
    } else {
        removeSeat(user.id);
    }
}

/**
 * Handles proposal updates received via WebSocket.
 * @param {Object} proposal - The new proposal object.
 */
function handleProposalUpdate(proposal) {
    // Assuming proposals are uniquely identified by proposalNumber
    // Update or add the proposal in the table
    const existingRow = Array.from(proposalsTable.rows).find(row => row.cells[0].innerText === proposal.proposalNumber);
    if (existingRow) {
        existingRow.cells[1].innerText = proposal.title;
        existingRow.cells[2].innerText = proposal.party;
    } else {
        const row = proposalsTable.insertRow();
        const numberCell = row.insertCell(0);
        const titleCell = row.insertCell(1);
        const partyCell = row.insertCell(2);

        numberCell.innerText = proposal.proposalNumber;
        titleCell.innerText = proposal.title;
        partyCell.innerText = proposal.party;
    }
    showAlert(`New proposal added: "${proposal.title}"`, 'success');
}

/**
 * Handles fine imposition notifications received via WebSocket.
 * @param {string} username - The username of the fined user.
 * @param {number} amount - The amount of the fine.
 */
function handleFineImposed(username, amount) {
    showAlert(`User ${username} has been fined ${amount} units.`, 'warning');
}

/**
 * Handles break notifications received via WebSocket.
 */
function handleBreak() {
    alert('A break has been called.');
}

/**
 * Handles session end notifications received via WebSocket.
 */
function handleEndSession() {
    alert('The session has been ended.');
    resetApp();
}

// ======================
// WebSocket Initialization
// ======================

/**
 * Initializes the WebSocket connection for real-time updates.
 */
function initializeWebSocket() {
    // Adjust WebSocket URL based on the current host and protocol
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/seat/`;
    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
        console.log('WebSocket connection established.');
    };

    ws.onmessage = (event) => {
        try {
            const message = JSON.parse(event.data);
            console.log('Received:', message);

            switch (message.type) {
                case 'seatUpdate':
                    handleSeatUpdate(message.user);
                    break;
                case 'proposalUpdate':
                    handleProposalUpdate(message.proposal);
                    break;
                case 'fineImposed':
                    handleFineImposed(message.username, message.amount);
                    break;
                case 'break':
                    handleBreak();
                    break;
                case 'endSession':
                    handleEndSession();
                    break;
                default:
                    console.warn('Unknown message type:', message.type);
            }
        } catch (error) {
            console.error('Error parsing WebSocket message:', error);
        }
    };

    ws.onerror = (error) => {
        console.error('WebSocket error:', error);
    };

    ws.onclose = () => {
        console.log('WebSocket connection closed.');
        // Optionally, attempt to reconnect after a delay
        setTimeout(initializeWebSocket, 5000); // Reconnect after 5 seconds
    };
}

// ======================
// Application Initialization
// ======================

/**
 * Initializes the main application after successful login.
 */
async function initializeApp() {
    authContainer.classList.add('hidden');
    mainContainer.classList.remove('hidden');

    // Fetch User Info to determine role
    currentUser = await fetchUserInfo();
    if (currentUser && currentUser.role === 'PRESIDENT') {
        presidentActions.classList.remove('hidden');
    } else {
        presidentActions.classList.add('hidden');
    }

    // Fetch Initial Data
    await fetchProposals();
    await fetchUsers();

    // Initialize WebSocket
    initializeWebSocket();
}

/**
 * Resets the application to the login state.
 */
function resetApp() {
    mainContainer.classList.add('hidden');
    authContainer.classList.remove('hidden');
    loginForm.reset();
    registerForm.reset();
    seatLayout.innerHTML = '';
    proposalsTable.innerHTML = '';
    presidentActions.classList.add('hidden');

    // Close WebSocket Connection
    if (ws) {
        ws.close();
        ws = null;
    }
}

/**
 * Initializes the application based on the user's authentication status.
 */
async function checkAuthentication() {
    currentUser = await fetchUserInfo();
    if (currentUser) {
        // User is logged in
        authContainer.classList.add('hidden');
        mainContainer.classList.remove('hidden');

        if (currentUser.role === 'PRESIDENT') {
            presidentActions.classList.remove('hidden');
        } else {
            presidentActions.classList.add('hidden');
        }

        // Fetch initial data
        await fetchProposals();
        await fetchUsers();

        // Initialize WebSocket
        initializeWebSocket();
    } else {
        // User is not logged in
        authContainer.classList.remove('hidden');
        mainContainer.classList.add('hidden');
    }
}

// ======================
// Event Listeners for Tab Switching and Page Load
// ======================

window.addEventListener('DOMContentLoaded', () => {
    // Attach event listeners for tab switching
    loginTab.addEventListener('click', () => switchTab('login'));
    registerTab.addEventListener('click', () => switchTab('register'));

    // Initial authentication check
    checkAuthentication();
});
