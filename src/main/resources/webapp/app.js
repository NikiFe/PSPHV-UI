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
const newProposalPriority = document.getElementById('new-proposal-priority');
const newProposalAssociationType = document.getElementById('new-proposal-association-type');
const newAssociatedProposal = document.getElementById('new-associated-proposal');

const imposeFineButton = document.getElementById('impose-fine');
const fineUsername = document.getElementById('fine-username');
const fineAmount = document.getElementById('fine-amount');
const callBreakButton = document.getElementById('call-break');
const endSessionButton = document.getElementById('end-session');

const alertContainer = document.getElementById('alert');
const alertMessage = document.getElementById('alert-message');
const breakOverlay = document.getElementById('break-overlay'); // Break overlay
const endBreakButton = document.getElementById('end-break'); // End break button

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
        const response = await fetch('/api/user-info');
        if (response.ok) {
            const user = await response.json();
            currentUser = user; // Set currentUser here
            return user;
        } else {
            console.error('Failed to fetch user info');
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
    proposalsTable.innerHTML = ''; // Clear existing proposals
    proposals.forEach(proposal => {
        const row = proposalsTable.insertRow();
        row.dataset.proposalId = proposal.id; // Use 'proposal.id'
        row.innerHTML = `
            <td class="py-2 px-4 border-b text-center">${proposal.proposalVisual}</td>
            <td class="py-2 px-4 border-b text-center proposal-title">${proposal.title}</td>
            <td class="py-2 px-4 border-b text-center proposal-party">${proposal.party}</td>
            <td class="py-2 px-4 border-b text-center actions-cell"></td>
        `;
        addProposalActions(proposal, row); // Pass the row directly
    });
}

/**
 * Fetches proposals and users periodically to keep the UI updated.
 */
function refreshData() {
    console.log("Refreshing data...");
    fetchProposals().catch(error => console.error("Error fetching proposals:", error));
    fetchUsers().catch(error => console.error("Error fetching users:", error));
    checkBreakStatus(); // Check break status as part of the regular polling cycle
}

/**
 * Starts polling every 1 second to update proposals and users.
 */
function startPolling() {
    setInterval(refreshData, 1000); // Poll data every 1 second
}

async function removeProposal(proposalId) {
    if (!confirm("Are you sure you want to remove this proposal?")) return;

    try {
        const response = await fetch(`/api/proposals/${proposalId}`, {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response.ok) {
            showAlert("Proposal removed successfully!", "success");
            fetchProposals(); // Refresh proposal list
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, "error");
        }
    } catch (error) {
        console.error("Error removing proposal:", error);
        showAlert("An error occurred while removing the proposal.", "error");
    }
}

function openEditProposalWindow(proposalId) {
    console.log("Editing proposal with id:", proposalId);

    const modal = document.createElement('div');
    modal.classList.add('modal');
    modal.innerHTML = `
        <div class="modal-content bg-gray-700 p-6 rounded">
            <h2 class="text-xl font-bold mb-4">Edit Proposal</h2>
            <label class="block mb-2">Title</label>
            <input type="text" id="edit-proposal-title" class="w-full mb-4 px-3 py-2 rounded bg-gray-800 text-white">

            <label class="block mb-2">Party</label>
            <input type="text" id="edit-proposal-party" class="w-full mb-4 px-3 py-2 rounded bg-gray-800 text-white">

            <button id="save-proposal" class="bg-green-500 hover:bg-green-600 text-white py-2 px-4 rounded">Save</button>
            <button id="close-modal" class="bg-gray-500 hover:bg-gray-600 text-white py-2 px-4 rounded">Cancel</button>
        </div>
    `;
    document.body.appendChild(modal);

    modal.querySelector('#close-modal').addEventListener('click', () => modal.remove());

    fetch(`/api/proposals/${proposalId}`)
        .then(response => response.json())
        .then(proposal => {
            document.getElementById('edit-proposal-title').value = proposal.title;
            document.getElementById('edit-proposal-party').value = proposal.party;
        })
        .catch(error => {
            console.error('Error fetching proposal data:', error);
            showAlert("Failed to load proposal data.", "error");
            modal.remove();
        });

    modal.querySelector('#save-proposal').addEventListener('click', async () => {
        const updatedTitle = document.getElementById('edit-proposal-title').value.trim();
        const updatedParty = document.getElementById('edit-proposal-party').value.trim();

        await updateProposal(proposalId, updatedTitle, updatedParty);
        modal.remove();
    });
}

async function updateProposal(proposalId, title, party) {
    try {
        console.log("Updating proposal with id:", proposalId);

        const response = await fetch(`/api/proposals/${proposalId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title, party })
        });

        if (response.ok) {
            showAlert("Proposal updated successfully!", "success");
            fetchProposals(); // Refresh proposal list
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, "error");
        }
    } catch (error) {
        console.error("Error updating proposal:", error);
        showAlert("An error occurred while updating the proposal.", "error");
    }
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

function addOrUpdateSeat(user) {
    let seat = document.getElementById(`seat-${user.id}`);

    if (!seat) {
        // Create new seat element if it doesn't exist
        seat = document.createElement('div');
        seat.classList.add('p-4', 'rounded-md', 'shadow', 'relative');
        seat.id = `seat-${user.id}`;

        seat.innerHTML = `
            <h3 class="text-lg font-semibold">${user.username}</h3>
            <p class="text-sm">Role: ${user.role}</p>
            <p class="text-sm">Party: ${user.partyAffiliation || 'N/A'}</p>
            <p class="text-sm">Voličská síla: ${user.electoralStrength || 0}</p>
            <div class="user-actions space-x-2">
                <button class="raise-hand-btn bg-blue-500 hover:bg-blue-600 text-white py-1 px-2 rounded text-xs">
                    Raise Hand
                </button>
                <button class="object-btn bg-red-500 hover:bg-red-600 text-white py-1 px-2 rounded text-xs">
                    Object
                </button>
                <!-- Cancel button will be conditionally added -->
            </div>
        `;

        seatLayout.appendChild(seat);
    }

    // Update seat content
    updateSeatContent(seat, user);

    // Always apply background color based on the seat status
    updateSeatBackground(seat, user.seatStatus);

    // Remove the call to addPresidentControls
    // Previously, we had:
    // if (currentUser && currentUser.role === 'PRESIDENT') {
    //     addPresidentControls(seat, user);
    // }
    // Since we're using a general "Cancel" button, this is no longer needed
}




function attachSeatEventListeners(seat, user) {
    const raiseHandButton = seat.querySelector('.raise-hand');
    const objectButton = seat.querySelector('.object');

    raiseHandButton.addEventListener('click', () => {
        if (raiseHandButton.textContent === 'Cancel') {
            updateSeatStatus(user.id, 'NEUTRAL');
        } else {
            updateSeatStatus(user.id, 'REQUESTING_TO_SPEAK');
        }
    });

    objectButton.addEventListener('click', () => {
        updateSeatStatus(user.id, 'OBJECTING');
    });
}

function updateSeatContent(seat, user) {
    // Logging IDs
    console.log(`updateSeatContent - user.id: ${user.id}, currentUser.id: ${currentUser.id}`);

    const userId = String(user.id);
    const currentUserId = String(currentUser.id);

    // Update user details
    seat.querySelector('h3').textContent = user.username;
    seat.querySelector('p:nth-of-type(1)').textContent = `Role: ${user.role}`;
    seat.querySelector('p:nth-of-type(2)').textContent = `Party: ${user.partyAffiliation || 'N/A'}`;
    seat.querySelector('p:nth-of-type(3)').textContent = `Voličská síla: ${user.electoralStrength || 0}`;

    const userActionsDiv = seat.querySelector('.user-actions');

    // Remove existing buttons to prevent duplicates
    userActionsDiv.innerHTML = '';

    // Buttons for the current user (including the president)
    if (userId === currentUserId) {
        // Raise Hand Button
        const raiseHandButton = document.createElement('button');
        raiseHandButton.classList.add('raise-hand-btn', 'bg-blue-500', 'hover:bg-blue-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
        raiseHandButton.textContent = 'Raise Hand';
        raiseHandButton.addEventListener('click', () => {
            updateSeatStatus(user.id, 'REQUESTING_TO_SPEAK');
        });
        userActionsDiv.appendChild(raiseHandButton);

        // Object Button
        const objectButton = document.createElement('button');
        objectButton.classList.add('object-btn', 'bg-red-500', 'hover:bg-red-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
        objectButton.textContent = 'Object';
        objectButton.addEventListener('click', () => {
            updateSeatStatus(user.id, 'OBJECTING');
        });
        userActionsDiv.appendChild(objectButton);

        // Add Cancel Button if necessary
        if (user.seatStatus !== 'NEUTRAL') {
            const cancelButton = document.createElement('button');
            cancelButton.classList.add('cancel-btn', 'bg-gray-500', 'hover:bg-gray-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
            cancelButton.textContent = 'Cancel';
            cancelButton.addEventListener('click', () => {
                updateSeatStatus(user.id, 'NEUTRAL');
            });
            userActionsDiv.appendChild(cancelButton);
        }

        // Add "Call to Speak" Button if applicable (for the president)
        if (currentUser.role === 'PRESIDENT' && user.seatStatus !== 'SPEAKING') {
            const callToSpeakButton = document.createElement('button');
            callToSpeakButton.classList.add('call-to-speak-btn', 'bg-green-500', 'hover:bg-green-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
            callToSpeakButton.textContent = 'Call to Speak';
            callToSpeakButton.addEventListener('click', () => {
                updateSeatStatus(user.id, 'SPEAKING');
            });
            userActionsDiv.appendChild(callToSpeakButton);
        }
    }
    // Buttons for other users (visible to the president)
    else if (currentUser.role === 'PRESIDENT') {
        // Add "Call to Speak" button if user's status is not 'NEUTRAL' or 'SPEAKING'
        if (user.seatStatus !== 'NEUTRAL' && user.seatStatus !== 'SPEAKING') {
            const callToSpeakButton = document.createElement('button');
            callToSpeakButton.classList.add('call-to-speak-btn', 'bg-green-500', 'hover:bg-green-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
            callToSpeakButton.textContent = 'Call to Speak';
            callToSpeakButton.addEventListener('click', () => {
                updateSeatStatus(user.id, 'SPEAKING');
            });
            userActionsDiv.appendChild(callToSpeakButton);
        }

        // Add Cancel Button if necessary
        if (user.seatStatus !== 'NEUTRAL') {
            const cancelButton = document.createElement('button');
            cancelButton.classList.add('cancel-btn', 'bg-gray-500', 'hover:bg-gray-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
            cancelButton.textContent = 'Cancel';
            cancelButton.addEventListener('click', () => {
                updateSeatStatus(user.id, 'NEUTRAL');
            });
            userActionsDiv.appendChild(cancelButton);
        }
    }
}

/**
 * Updates the seat's background color based on seat status.
 * @param {HTMLElement} seat - The seat element.
 * @param {string} seatStatus - The current seat status.
 */
function updateSeatBackground(seat, seatStatus) {
    // Remove existing background color classes
    seat.classList.remove('bg-gray-700', 'bg-yellow-500', 'bg-red-500', 'bg-green-500');

    // Add the appropriate class based on seat status
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
        const payload = { id: userId, seatStatus: status };

        const response = await fetch('/api/users/update-status', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (response.ok) {
            // Fetch the updated user data
            const updatedUser = await fetchUserById(userId);
            if (updatedUser) {
                addOrUpdateSeat(updatedUser);
            }

            // Show appropriate message
            let message = '';
            switch (status) {
                case 'REQUESTING_TO_SPEAK':
                    message = 'You have raised your hand to speak.';
                    break;
                case 'OBJECTING':
                    message = 'You are objecting.';
                    break;
                case 'NEUTRAL':
                    message = 'Status has been cancelled.';
                    break;
                default:
                    message = 'Seat status updated.';
            }
            showAlert(message, 'success');
        } else if (response.status === 403) {
            // Forbidden action
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error updating seat status:', error);
        showAlert('An error occurred while updating seat status.', 'error');
    }
}


async function fetchUserById(userId) {
    try {
        const response = await fetch(`/api/users/${userId}`, {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' }
        });
        if (response.ok) {
            const user = await response.json();
            return user;
        } else {
            console.warn(`Failed to fetch user with ID ${userId}.`);
            return null;
        }
    } catch (error) {
        console.error(`Error fetching user with ID ${userId}:`, error);
        return null;
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

function toggleBreakOverlay(isBreak) {
    console.log("toggleBreakOverlay called with isBreak:", isBreak);
    if (isBreak && currentUser && currentUser.role !== 'PRESIDENT') {
        breakOverlay.classList.remove('hidden');
        mainContainer.classList.add('hidden');
    } else {
        breakOverlay.classList.add('hidden');
        mainContainer.classList.remove('hidden');
    }
}
endBreakButton.addEventListener('click', async () => {
    try {
        const response = await fetch('/api/end-break', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response.ok) {
            showAlert('Break has ended.', 'success');
            document.getElementById('end-break').classList.add('hidden'); // Hide End Break button
            document.getElementById('break-overlay').classList.add('hidden'); // Hide the break overlay
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error ending break:', error);
        showAlert('An error occurred while ending the break.', 'error');
    }
});
function handleBreak() {
    if (currentUser.role !== 'PRESIDENT') {
        toggleBreakOverlay(true);
    }
}

function handleEndBreak() {
    toggleBreakOverlay(false);
}

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

function controlAssProposal(){
    if (newProposalAssociationType.value.trim().localeCompare('normal') === 0){
        newAssociatedProposal.disabled = true;
    }
    else {
        newAssociatedProposal.disabled = false;
    }
}

/**
 * Adds a new proposal via Presidential Action.
 */
addProposalButton.addEventListener('click', async () => {
    const title = newProposalTitle.value.trim();
    const party = newProposalParty.value.trim();
    const priority = newProposalPriority.checked;
    const type = newProposalAssociationType.value.trim();
    const assProposal = newAssociatedProposal.value.trim();

    if (!title || !party) {
        showAlert('Proposal title and proposing party cannot be empty.', 'warning');
        return;
    }

    try {
        const response = await fetch('/api/proposals', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title, party, priority, type, assProposal })
        });

        if (response.ok) {
            showAlert('Proposal added successfully!', 'success');
            newProposalTitle.value = '';
            newProposalParty.value = '';
            newProposalPriority.checked = false;
            newProposalAssociationType.value = 'normal';
            newAssociatedProposal.value = '';

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
    const reason = document.getElementById('fine-reason').value.trim(); // Add reason field

    if (!username || isNaN(amount) || amount <= 0 || !reason) { // Validate reason as well
        showAlert('Please enter a valid username, fine amount, and reason.', 'warning');
        return;
    }

    try {
        const response = await fetch('/api/impose-fine', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, amount, reason }) // Include reason in payload
        });

        if (response.ok) {
            showAlert('Fine imposed successfully!', 'success');
            fineUsername.value = '';
            fineAmount.value = '';
            document.getElementById('fine-reason').value = ''; // Clear reason field
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
            document.getElementById('end-break').classList.remove('hidden'); // Show End Break button for president
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
        const response = await fetch('/api/end-break', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response.ok) {
            showAlert('Break has ended.', 'success');
            document.getElementById('end-break').classList.add('hidden'); // Hide End Break button
            document.getElementById('break-overlay').classList.add('hidden'); // Hide the break overlay
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
        const response1 = await fetch('/api/end-session', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response1.ok) {
            showAlert('Session has been ended.', 'success');
        } else {
            const errorText = await response1.text();
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
 * Adds action buttons to proposals and attaches event listeners.
 * @param {Object} proposal - The proposal object.
 * @param {HTMLElement} row - The table row element for the proposal.
 */
function addProposalActions(proposal, row) {
    const actionsCell = row.querySelector('.actions-cell');
    if (currentUser && currentUser.role === 'PRESIDENT') {
        actionsCell.innerHTML = `
            <button class="edit-proposal bg-blue-500 hover:bg-blue-600 text-white py-1 px-2 rounded text-xs" data-id="${proposal.id}">
                Edit
            </button>
            <button class="remove-proposal bg-red-500 hover:bg-red-600 text-white py-1 px-2 rounded text-xs" data-id="${proposal.id}">
                Remove
            </button>
        `;

        actionsCell.querySelector('.edit-proposal').addEventListener('click', () => openEditProposalWindow(proposal.id));
        actionsCell.querySelector('.remove-proposal').addEventListener('click', () => removeProposal(proposal.id));
    }
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
 * Handles session end notifications received via WebSocket.
 */
function handleEndSession() {
    alert('The session has been ended.');
    handleEndBreak();
    resetApp();
}

async function checkBreakStatus() {
    try {
        const response = await fetch('/api/system/break-status');
        const data = await response.json();

        toggleBreakOverlay(data.breakActive);

        if (data.breakActive && currentUser && currentUser.role === 'PRESIDENT') {
            endBreakButton.classList.remove('hidden'); // Show for president
        } else {
            endBreakButton.classList.add('hidden');
        }
    } catch (error) {
        console.error('Error fetching break status:', error);
    }
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
                case 'fineImposed':
                    handleFineImposed(message.username, message.amount);
                    break;
                case 'break':
                    handleBreak();
                    break;
                case 'endBreak':
                    handleEndBreak();
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

    currentUser = await fetchUserInfo();
    if (currentUser && currentUser.role === 'PRESIDENT') {
        presidentActions.classList.remove('hidden');
    } else {
        presidentActions.classList.add('hidden');
    }

    await checkBreakStatus();  // Check if break is active on initialization
    await fetchProposals();
    await fetchUsers();
    initializeWebSocket();
    startPolling();
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
    // Start polling for data immediately
    startPolling();

    // Attach event listeners for tab switching
    loginTab.addEventListener('click', () => switchTab('login'));
    registerTab.addEventListener('click', () => switchTab('register'));

    // Initial authentication check
    checkAuthentication();
});
