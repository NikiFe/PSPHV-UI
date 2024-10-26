// app.js

// Elements
const authContainer = document.getElementById('auth-container');
const loginTab = document.getElementById('login-tab');
const registerTab = document.getElementById('register-tab');
const loginForm = document.getElementById('login-form');
const registerForm = document.getElementById('register-form');
const alertContainer = document.getElementById('alert');
const alertMessage = document.getElementById('alert-message');

const mainContainer = document.getElementById('main-container');
const logoutButton = document.getElementById('logout');
const joinSeatButton = document.getElementById('join-seat');

const seatLayout = document.getElementById('seat-layout');
const proposalsTable = document.getElementById('proposals-table');

const presidentActions = document.getElementById('president-actions');
const addProposalButton = document.getElementById('add-proposal');
const newProposalTitle = document.getElementById('new-proposal-title');
const newProposalParty = document.getElementById('new-proposal-party'); // New element for party
const imposeFineButton = document.getElementById('impose-fine');
const fineUsername = document.getElementById('fine-username');
const fineAmount = document.getElementById('fine-amount');
const callBreakButton = document.getElementById('call-break');
const endSessionButton = document.getElementById('end-session');

// WebSocket
let ws;

// Utility Functions

/**
 * Displays an alert message to the user.
 * @param {string} message - The message to display.
 * @param {string} type - The type of alert ('success', 'error', 'warning').
 */
function showAlert(message, type = 'success') {
    alertMessage.innerText = message;
    alertContainer.classList.remove('hidden');
    alertContainer.classList.remove('bg-red-100', 'bg-green-100', 'bg-yellow-100', 'text-red-800', 'text-green-800', 'text-yellow-800');

    if (type === 'success') {
        alertContainer.classList.add('bg-green-100', 'text-green-800');
    } else if (type === 'error') {
        alertContainer.classList.add('bg-red-100', 'text-red-800');
    } else if (type === 'warning') {
        alertContainer.classList.add('bg-yellow-100', 'text-yellow-800');
    }

    // Hide after 5 seconds
    setTimeout(() => {
        alertContainer.classList.add('hidden');
    }, 5000);
}

/**
 * Switches the view to the Login tab.
 */
loginTab.addEventListener('click', () => {
    // Remove active classes from both tabs
    loginTab.classList.add('text-blue-400', 'border-blue-400', 'font-semibold');
    loginTab.classList.remove('text-gray-400', 'border-transparent', 'font-semibold');

    registerTab.classList.remove('text-blue-400', 'border-blue-400', 'font-semibold');
    registerTab.classList.add('text-gray-400', 'border-transparent', 'font-semibold');

    loginForm.classList.remove('hidden');
    registerForm.classList.add('hidden');
});

/**
 * Switches the view to the Register tab.
 */
registerTab.addEventListener('click', () => {
    // Remove active classes from both tabs
    registerTab.classList.add('text-blue-400', 'border-blue-400', 'font-semibold');
    registerTab.classList.remove('text-gray-400', 'border-transparent', 'font-semibold');

    loginTab.classList.remove('text-blue-400', 'border-blue-400', 'font-semibold');
    loginTab.classList.add('text-gray-400', 'border-transparent', 'font-semibold');

    registerForm.classList.remove('hidden');
    loginForm.classList.add('hidden');
});

/**
 * Handles the Login form submission.
 */
loginForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const username = document.getElementById('login-username').value.trim();
    const password = document.getElementById('login-password').value.trim();

    fetch('/api/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
    })
    .then(async (response) => {
        if (response.ok) {
            showAlert('Login successful!', 'success');
            await initializeApp();
        } else {
            const errorText = await response.text();
            showAlert(errorText || 'Login failed.', 'error');
        }
    })
    .catch((error) => {
        console.error('Login error:', error);
        showAlert('An error occurred during login.', 'error');
    });
});

/**
 * Handles the Registration form submission.
 */
registerForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const username = document.getElementById('register-username').value.trim();
    const password = document.getElementById('register-password').value.trim();
    const role = "MEMBER"; // Default role

    if (!username || !password) {
        showAlert('Please fill in all fields.', 'warning');
        return;
    }

    fetch('/api/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, role })
    })
    .then(async (response) => {
        if (response.ok) {
            showAlert('Registration successful! Please log in.', 'success');
            registerForm.reset();
            loginTab.click();
        } else {
            const errorText = await response.text();
            showAlert(errorText || 'Registration failed.', 'error');
        }
    })
    .catch((error) => {
        console.error('Registration error:', error);
        showAlert('An error occurred during registration.', 'error');
    });
});

/**
 * Initializes the main application after successful login.
 */
async function initializeApp() {
    authContainer.classList.add('hidden');
    mainContainer.classList.remove('hidden');

    // Fetch User Info to determine role
    const userInfo = await fetchUserInfo();
    if (userInfo && userInfo.role === 'PRESIDENT') {
        presidentActions.classList.remove('hidden');
    } else {
        presidentActions.classList.add('hidden');
    }

    // Fetch Initial Data
    fetchProposals();
    fetchSeats();

    // Initialize WebSocket
    initializeWebSocket();
}

/**
 * Fetches the authenticated user's information.
 * @returns {Object|null} - The user information or null if not found.
 */
async function fetchUserInfo() {
    try {
        const response = await fetch('/api/user-info', {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' }
        });
        if (response.ok) {
            const data = await response.json();
            return data;
        } else {
            console.warn('Failed to fetch user info.');
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
function fetchProposals() {
    fetch('/api/proposals', {
        method: 'GET',
        headers: { 'Content-Type': 'application/json' }
    })
    .then(async (response) => {
        if (response.ok) {
            const proposals = await response.json();
            proposalsTable.innerHTML = '';
            proposals.forEach(proposal => {
                addProposalToTable(proposal);
            });
        } else {
            console.warn('Failed to fetch proposals.');
        }
    })
    .catch((error) => {
        console.error('Error fetching proposals:', error);
    });
}

/**
 * Adds a proposal to the proposals table in the UI.
 * @param {Object} proposal - The proposal object.
 */
function addProposalToTable(proposal) {
    const row = proposalsTable.insertRow();
    row.insertCell(0).innerText = proposal.proposalNumber;
    row.insertCell(1).innerText = proposal.title;
    row.insertCell(2).innerText = proposal.party;
}

/**
 * Fetches all users who are currently present.
 */
function fetchSeats() {
    fetch('/api/users', {
        method: 'GET',
        headers: { 'Content-Type': 'application/json' }
    })
    .then(async (response) => {
        if (response.ok) {
            const users = await response.json();
            seatLayout.innerHTML = '';
            users.forEach(user => {
                if (user.present) {
                    addSeatToLayout(user);
                }
            });
        } else {
            console.warn('Failed to fetch users.');
        }
    })
    .catch((error) => {
        console.error('Error fetching users:', error);
    });
}

/**
 * Adds a seat to the seat layout in the UI.
 * @param {Object} user - The user object.
 */
function addSeatToLayout(user) {
    const seat = document.createElement('div');
    seat.classList.add('p-4', 'bg-gray-700', 'rounded-md', 'shadow', 'relative');
    seat.id = `seat-${user.id}`;

    // Dynamic Background Color Based on Seat Status
    switch(user.seatStatus) {
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

    seat.innerHTML = `
        <h3 class="text-lg font-semibold">${user.username}</h3>
        <p class="text-sm">Role: ${user.role}</p>
        <p class="text-sm">Party: ${user.partyAffiliation || 'N/A'}</p>
        <p class="text-sm">Fines: ${user.fines || 0}</p>
        <div class="absolute top-2 right-2 space-x-2">
            <button class="raise-hand bg-blue-500 hover:bg-blue-600 text-white py-1 px-2 rounded text-xs" data-user-id="${user.id}">
                Raise Hand
            </button>
            <button class="object bg-red-500 hover:bg-red-600 text-white py-1 px-2 rounded text-xs" data-user-id="${user.id}">
                Object
            </button>
        </div>
    `;

    seatLayout.appendChild(seat);

    // Attach Event Listeners to Buttons (Ensure they're attached only once)
    const raiseHandButton = seat.querySelector('.raise-hand');
    const objectButton = seat.querySelector('.object');

    raiseHandButton.addEventListener('click', () => {
        console.log(`Raise Hand clicked for user ID: ${user.id}`);
        updateSeatStatus(user.id, 'REQUESTING_TO_SPEAK');
    });

    objectButton.addEventListener('click', () => {
        console.log(`Object clicked for user ID: ${user.id}`);
        updateSeatStatus(user.id, 'OBJECTING');
    });
}

/**
 * Updates the seat status of a user.
 * @param {string} userId - The ID of the user.
 * @param {string} status - The new seat status.
 */
function updateSeatStatus(userId, status) {
    fetch('/api/users/update-status', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: userId, seatStatus: status })
    })
    .then(async (response) => {
        if (response.ok) {
            showAlert('Seat status updated successfully!', 'success');
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    })
    .catch((error) => {
        console.error('Error updating seat status:', error);
        showAlert('An error occurred while updating seat status.', 'error');
    });
}

/**
 * Handles the Logout button click.
 */
logoutButton.addEventListener('click', () => {
    fetch('/api/logout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    })
    .then(async (response) => {
        if (response.ok) {
            showAlert('Logged out successfully.', 'success');
            resetApp();
        } else {
            const errorText = await response.text();
            showAlert(errorText || 'Logout failed.', 'error');
        }
    })
    .catch((error) => {
        console.error('Logout error:', error);
        showAlert('An error occurred during logout.', 'error');
    });
});

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
    }
}

/**
 * Initializes the WebSocket connection for real-time updates.
 */
function initializeWebSocket() {
    ws = new WebSocket('ws://localhost:8080/ws/seat/');

    ws.onopen = () => {
        console.log('WebSocket connection established.');
    };

    ws.onmessage = (event) => {
        try {
            const message = JSON.parse(event.data);
            console.log('Received:', message);

            switch(message.type) {
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
    };
}

/**
 * Handles seat updates received via WebSocket.
 * @param {Object} user - The user object with updated seat information.
 */
function handleSeatUpdate(user) {
    const seatElement = document.getElementById(`seat-${user.id}`);
    if (user.present) {
        if (!seatElement) {
            addSeatToLayout(user);
        } else {
            // Update existing seat
            updateSeatElement(seatElement, user);
        }
    } else {
        // Remove seat if not present
        if (seatElement) {
            seatElement.remove();
        }
    }
}

/**
 * Updates the seat element's appearance based on seat status.
 * @param {HTMLElement} seatElement - The seat's HTML element.
 * @param {Object} user - The user object with updated information.
 */
function updateSeatElement(seatElement, user) {
    // Update background color based on seatStatus
    seatElement.classList.remove('bg-gray-700', 'bg-yellow-500', 'bg-red-500', 'bg-green-500');
    switch(user.seatStatus) {
        case 'REQUESTING_TO_SPEAK':
            seatElement.classList.add('bg-yellow-500');
            break;
        case 'OBJECTING':
            seatElement.classList.add('bg-red-500');
            break;
        case 'SPEAKING':
            seatElement.classList.add('bg-green-500');
            break;
        default:
            seatElement.classList.add('bg-gray-700');
    }

    // Update user details
    seatElement.querySelector('h3').innerText = user.username;
    seatElement.querySelector('p:nth-child(2)').innerText = `Role: ${user.role}`;
    seatElement.querySelector('p:nth-child(3)').innerText = `Party: ${user.partyAffiliation || 'N/A'}`;
    seatElement.querySelector('p:nth-child(4)').innerText = `Fines: ${user.fines || 0}`;
}

/**
 * Handles proposal updates received via WebSocket.
 * @param {Object} proposal - The new proposal object.
 */
function handleProposalUpdate(proposal) {
    addProposalToTable(proposal);
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

/**
 * Adds a new proposal via Presidential Action.
 */
addProposalButton.addEventListener('click', () => {
    const title = newProposalTitle.value.trim();
    const party = newProposalParty.value.trim(); // New party field

    if (!title || !party) {
        showAlert('Proposal title and proposing party cannot be empty.', 'warning');
        return;
    }

    fetch('/api/proposals', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title, party }) // Include party in the request
    })
    .then(async (response) => {
        if (response.ok) {
            showAlert('Proposal added successfully!', 'success');
            newProposalTitle.value = '';
            newProposalParty.value = '';
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    })
    .catch((error) => {
        console.error('Error adding proposal:', error);
        showAlert('An error occurred while adding the proposal.', 'error');
    });
});

/**
 * Imposes a fine on a specified user via Presidential Action.
 */
imposeFineButton.addEventListener('click', () => {
    const username = fineUsername.value.trim();
    const amount = parseInt(fineAmount.value.trim(), 10);

    if (!username || isNaN(amount) || amount <= 0) {
        showAlert('Please enter a valid username and fine amount.', 'warning');
        return;
    }

    fetch('/api/impose-fine', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, amount })
    })
    .then(async (response) => {
        if (response.ok) {
            showAlert('Fine imposed successfully!', 'success');
            fineUsername.value = '';
            fineAmount.value = '';
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    })
    .catch((error) => {
        console.error('Error imposing fine:', error);
        showAlert('An error occurred while imposing the fine.', 'error');
    });
});

/**
 * Calls a break for all users via Presidential Action.
 */
callBreakButton.addEventListener('click', () => {
    fetch('/api/break', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    })
    .then(async (response) => {
        if (response.ok) {
            showAlert('Break has been called.', 'success');
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    })
    .catch((error) => {
        console.error('Error calling break:', error);
        showAlert('An error occurred while calling a break.', 'error');
    });
});

/**
 * Ends the current session via Presidential Action.
 */
endSessionButton.addEventListener('click', () => {
    if (!confirm('Are you sure you want to end the session?')) return;

    fetch('/api/end-session', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    })
    .then(async (response) => {
        if (response.ok) {
            showAlert('Session has been ended.', 'success');
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    })
    .catch((error) => {
        console.error('Error ending session:', error);
        showAlert('An error occurred while ending the session.', 'error');
    });
});

/**
 * Handles the "Join Seat" button click.
 */
joinSeatButton.addEventListener('click', () => {
    console.log('Join Seat button clicked.');
    fetch('/api/join-seat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
    })
    .then(async (response) => {
        if (response.ok) {
            showAlert('Successfully joined a seat.', 'success');
            // Optionally, refresh seats or update UI as needed
            fetchSeats();
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    })
    .catch((error) => {
        console.error('Error joining seat:', error);
        showAlert('An error occurred while joining the seat.', 'error');
    });
});
