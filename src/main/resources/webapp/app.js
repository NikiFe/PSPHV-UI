// app.js

// ======================
// DOM Elements
// ======================
const authContainer = document.getElementById('auth-container');
const loginTab = document.getElementById('login-tab');
const registerTab = document.getElementById('register-tab');
const loginForm = document.getElementById('login-form');
const registerForm = document.getElementById('register-form');
const mainContainer = document.getElementById('main-container'); // For index.html
const logoutButton = document.getElementById('logout');
const joinSeatButton = document.getElementById('join-seat');

// Normal Proposals Table
const proposalsTable = document.getElementById('proposals-table');
const normalStupidHeader = document.getElementById('normal-stupid-header');

// Priority Proposals Section & Table
const priorityProposalsSection = document.getElementById('priority-proposals-section');
const priorityProposalsTable = document.getElementById('priority-proposals-table');
const priorityStupidHeader = document.getElementById('priority-stupid-header');

// President Actions
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
const endVotingButton = document.getElementById('end-voting');
const endPriorityVotingButton = document.getElementById('end-priority-voting');

const alertContainer = document.getElementById('alert');
const alertMessage = document.getElementById('alert-message');
const breakOverlay = document.getElementById('break-overlay');
const endBreakButton = document.getElementById('end-break');

// ======================
// State Variables
// ======================
let currentUser = null; // { username: '', role: '', id: '' }
let ws = null; // WebSocket connection

// ======================
// Utility Functions
// ======================

function showAlert(message, type = 'success') {
    alertMessage.innerText = message;
    alertContainer.className = '';

    if (type === 'success') {
        alertContainer.classList.add(
            'block','px-4','py-3','rounded','shadow-lg','mb-4',
            'bg-green-100','border','border-green-400','text-green-700'
        );
    } else if (type === 'error') {
        alertContainer.classList.add(
            'block','px-4','py-3','rounded','shadow-lg','mb-4',
            'bg-red-100','border','border-red-400','text-red-700'
        );
    } else if (type === 'warning') {
        alertContainer.classList.add(
            'block','px-4','py-3','rounded','shadow-lg','mb-4',
            'bg-yellow-100','border','border-yellow-400','text-yellow-700'
        );
    }

    setTimeout(() => {
        alertContainer.classList.add('hidden');
    }, 5000);
}

function switchTab(activeTab) {
    if (activeTab === 'login') {
        loginTab.classList.add('text-blue-400','border-blue-400','font-semibold');
        loginTab.classList.remove('text-gray-400','border-transparent');

        registerTab.classList.remove('text-blue-400','border-blue-400','font-semibold');
        registerTab.classList.add('text-gray-400','border-transparent');

        loginForm.classList.remove('hidden');
        registerForm.classList.add('hidden');
    } else {
        registerTab.classList.add('text-blue-400','border-blue-400','font-semibold');
        registerTab.classList.remove('text-gray-400','border-transparent');

        loginTab.classList.remove('text-blue-400','border-blue-400','font-semibold');
        loginTab.classList.add('text-gray-400','border-transparent');

        registerForm.classList.remove('hidden');
        loginForm.classList.add('hidden');
    }
}

async function fetchUserInfo() {
    try {
        const response = await fetch('/api/user-info');
        if (response.ok) {
            const user = await response.json();
            currentUser = user;
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

async function fetchProposals() {
    try {
        const response = await fetch('/api/proposals', {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' }
        });
        if (response.ok) {
            const allProposals = await response.json();
            const priorityProposals = allProposals.filter(p => p.isPriority === true);
            const normalProposals   = allProposals.filter(p => !p.isPriority);

            renderPriorityProposals(priorityProposals);
            renderProposals(normalProposals);
        } else {
            console.warn('Failed to fetch proposals.');
        }
    } catch (error) {
        console.error('Error fetching proposals:', error);
    }
}

/**
 * Toggles the "stupid" field of a proposal. This calls a PUT endpoint to update the proposal.
 */
async function toggleStupidProposal(proposalId, isStupid) {
    try {
        const response = await fetch(`/api/proposals/${proposalId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ stupid: isStupid })
        });

        if (response.ok) {
            showAlert(`Proposal marked as stupid = ${isStupid}`, 'success');
            await fetchProposals(); // Refresh to see changes
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error toggling stupid field:', error);
        showAlert('An error occurred while toggling the stupid flag.', 'error');
    }
}

/**
 * Renders the normal (non-priority) proposals.
 */
function renderProposals(proposals) {
    proposalsTable.innerHTML = '';
    proposals.forEach(proposal => {
        const row = proposalsTable.insertRow();
        row.dataset.proposalId = proposal.id;

        // 7 columns
        const cellNumber  = row.insertCell(0);
        const cellTitle   = row.insertCell(1);
        const cellParty   = row.insertCell(2);
        const cellVote    = row.insertCell(3);
        const cellStatus  = row.insertCell(4);
        const cellStupid  = row.insertCell(5);
        const cellActions = row.insertCell(6);

        cellNumber.classList.add('py-2','px-4','border-b','text-center');
        cellTitle.classList.add('py-2','px-4','border-b','text-center','break-words','whitespace-pre-wrap');
        cellParty.classList.add('py-2','px-4','border-b','text-center');
        cellVote.classList.add('py-2','px-4','border-b','text-center');
        cellStatus.classList.add('py-2','px-4','border-b','text-center');
        cellStupid.classList.add('py-2','px-4','border-b','text-center');
        cellActions.classList.add('py-2','px-4','border-b','text-center');

        cellNumber.textContent = proposal.proposalVisual || '';
        cellTitle.textContent = proposal.title || '';
        cellParty.textContent = proposal.party || '';

        if (proposal.stupid) {
            // No voting if stupid
            cellVote.innerHTML = '(Stupid, no voting)';
        } else {
            // Normal voting
            const voteChoices = ['For','Against','Abstain'];
            const userVote = proposal.userVote || 'Abstain';

            const voteForm = document.createElement('div');
            voteForm.classList.add('flex','justify-center','space-x-4');

            voteChoices.forEach(choice => {
                const label = document.createElement('label');
                label.classList.add('inline-flex','items-center','space-x-2');

                const radio = document.createElement('input');
                radio.type = 'radio';
                radio.name = `vote-${proposal.id}`;
                radio.value = choice;
                radio.checked = (userVote === choice);
                radio.disabled = proposal.votingEnded;
                radio.classList.add('form-radio','h-5','w-5','text-blue-600');

                radio.addEventListener('change', () => {
                    submitVote(proposal.id, choice);
                });

                const span = document.createElement('span');
                span.classList.add('text-sm');
                span.textContent = choice;

                label.appendChild(radio);
                label.appendChild(span);
                voteForm.appendChild(label);
            });

            cellVote.appendChild(voteForm);
        }

        if (proposal.votingEnded) {
            const statusText = proposal.passed ? 'Passed' : 'Failed';
            const statusDetail = `For: ${proposal.totalFor}, Against: ${proposal.totalAgainst}`;
            cellStatus.innerHTML = `<strong>${statusText}</strong><br>${statusDetail}`;
        } else {
            cellStatus.textContent = 'Voting in progress';
        }

        // Stupid? checkbox (only show for president)
        if (currentUser && currentUser.role === 'PRESIDENT') {
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = proposal.stupid === true;
            checkbox.classList.add('form-checkbox','h-5','w-5','text-blue-600');
            checkbox.addEventListener('change', () => {
                toggleStupidProposal(proposal.id, checkbox.checked);
            });
            cellStupid.appendChild(checkbox);
        } else {
            cellStupid.innerHTML = proposal.stupid ? 'Yes' : 'No';
        }

        // If PRESIDENT, show "edit"/"remove" actions
        if (currentUser && currentUser.role === 'PRESIDENT') {
            cellActions.innerHTML = `
                <button class="edit-proposal bg-blue-500 hover:bg-blue-600 text-white py-1 px-2 rounded text-xs"
                        data-id="${proposal.id}">
                    Edit
                </button>
                <button class="remove-proposal bg-red-500 hover:bg-red-600 text-white py-1 px-2 rounded text-xs"
                        data-id="${proposal.id}">
                    Remove
                </button>
            `;
            cellActions.querySelector('.edit-proposal')
                .addEventListener('click', () => openEditProposalWindow(proposal.id));
            cellActions.querySelector('.remove-proposal')
                .addEventListener('click', () => removeProposal(proposal.id));
        } else {
            cellActions.innerHTML = '—';
        }
    });
}

/**
 * Renders the priority proposals in their separate table.
 */
function renderPriorityProposals(proposals) {
    priorityProposalsTable.innerHTML = '';

    if (!proposals || proposals.length === 0) {
        priorityProposalsSection.classList.add('hidden');
        return;
    }
    priorityProposalsSection.classList.remove('hidden');

    proposals.forEach(proposal => {
        const row = priorityProposalsTable.insertRow();
        row.dataset.proposalId = proposal.id;

        const cellNumber  = row.insertCell(0);
        const cellTitle   = row.insertCell(1);
        const cellParty   = row.insertCell(2);
        const cellVote    = row.insertCell(3);
        const cellStatus  = row.insertCell(4);
        const cellStupid  = row.insertCell(5);
        const cellActions = row.insertCell(6);

        cellNumber.classList.add('py-2','px-4','border-b','text-center');
        cellTitle.classList.add('py-2','px-4','border-b','text-center','break-words','whitespace-pre-wrap');
        cellParty.classList.add('py-2','px-4','border-b','text-center');
        cellVote.classList.add('py-2','px-4','border-b','text-center');
        cellStatus.classList.add('py-2','px-4','border-b','text-center');
        cellStupid.classList.add('py-2','px-4','border-b','text-center');
        cellActions.classList.add('py-2','px-4','border-b','text-center');

        cellNumber.textContent = proposal.proposalVisual || '';
        cellTitle.textContent = proposal.title || '';
        cellParty.textContent = proposal.party || '';

        if (proposal.stupid) {
            cellVote.innerHTML = '(Stupid, no voting)';
        } else {
            const voteChoices = ['For','Against','Abstain'];
            const userVote = proposal.userVote || 'Abstain';

            const voteForm = document.createElement('div');
            voteForm.classList.add('flex','justify-center','space-x-4');

            voteChoices.forEach(choice => {
                const label = document.createElement('label');
                label.classList.add('inline-flex','items-center','space-x-2');

                const radio = document.createElement('input');
                radio.type = 'radio';
                radio.name = `vote-${proposal.id}`;
                radio.value = choice;
                radio.checked = (userVote === choice);
                radio.disabled = proposal.votingEnded;
                radio.classList.add('form-radio','h-5','w-5','text-blue-600');

                radio.addEventListener('change', () => {
                    submitVote(proposal.id, choice);
                });

                const span = document.createElement('span');
                span.classList.add('text-sm');
                span.textContent = choice;

                label.appendChild(radio);
                label.appendChild(span);
                voteForm.appendChild(label);
            });

            cellVote.appendChild(voteForm);
        }

        if (proposal.votingEnded) {
            const statusText = proposal.passed ? 'Passed' : 'Failed';
            const statusDetail = `For: ${proposal.totalFor}, Against: ${proposal.totalAgainst}`;
            cellStatus.innerHTML = `<strong>${statusText}</strong><br>${statusDetail}`;
        } else {
            cellStatus.textContent = 'Voting in progress (priority)';
        }

        // Stupid? (checkbox) if president
        if (currentUser && currentUser.role === 'PRESIDENT') {
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = proposal.stupid === true;
            checkbox.classList.add('form-checkbox','h-5','w-5','text-blue-600');
            checkbox.addEventListener('change', () => {
                toggleStupidProposal(proposal.id, checkbox.checked);
            });
            cellStupid.appendChild(checkbox);
        } else {
            cellStupid.innerHTML = proposal.stupid ? 'Yes' : 'No';
        }

        // Actions for President
        if (currentUser && currentUser.role === 'PRESIDENT') {
            cellActions.innerHTML = `
                <button class="edit-proposal bg-blue-500 hover:bg-blue-600 text-white py-1 px-2 rounded text-xs"
                        data-id="${proposal.id}">
                    Edit
                </button>
                <button class="remove-proposal bg-red-500 hover:bg-red-600 text-white py-1 px-2 rounded text-xs"
                        data-id="${proposal.id}">
                    Remove
                </button>
            `;
            cellActions.querySelector('.edit-proposal')
                .addEventListener('click', () => openEditProposalWindow(proposal.id));
            cellActions.querySelector('.remove-proposal')
                .addEventListener('click', () => removeProposal(proposal.id));
        } else {
            cellActions.innerHTML = '—';
        }
    });
}

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

function renderSeats(users) {
    const seatLayout = document.getElementById('seat-layout');
    seatLayout.innerHTML = '';
    users.forEach(user => {
        if (user.present) {
            addOrUpdateSeat(user);
        }
    });
}

function addOrUpdateSeat(user) {
    let seat = document.getElementById(`seat-${user.id}`);

    if (!seat) {
        seat = document.createElement('div');
        seat.classList.add('p-4','rounded-md','shadow','relative');
        seat.id = `seat-${user.id}`;

        seat.innerHTML = `
            <h3 class="text-lg font-semibold">${user.username}</h3>
            <p class="text-sm">Role: ${user.role}</p>
            <p class="text-sm">Party: ${user.partyAffiliation || 'N/A'}</p>
            <p class="text-sm">Voličská síla: ${user.electoralStrength || 0}</p>
            <div class="user-actions space-x-2"></div>
        `;
        document.getElementById('seat-layout').appendChild(seat);
    }

    updateSeatContent(seat, user);
    updateSeatBackground(seat, user.seatStatus);
}

function updateSeatContent(seat, user) {
    const userId = String(user.id);
    const currentUserId = currentUser ? String(currentUser.id) : null;

    seat.querySelector('h3').textContent = user.username;
    seat.querySelector('p:nth-of-type(1)').textContent = `Role: ${user.role}`;
    seat.querySelector('p:nth-of-type(2)').textContent = `Party: ${user.partyAffiliation || 'N/A'}`;
    seat.querySelector('p:nth-of-type(3)').textContent = `Voličská síla: ${user.electoralStrength || 0}`;

    const userActionsDiv = seat.querySelector('.user-actions');
    userActionsDiv.innerHTML = '';

    if (currentUserId && userId === currentUserId) {
        // Raise Hand
        const raiseHandBtn = document.createElement('button');
        raiseHandBtn.classList.add('bg-blue-500','hover:bg-blue-600','text-white','py-1','px-2','rounded','text-xs');
        raiseHandBtn.textContent = 'Raise Hand';
        raiseHandBtn.addEventListener('click', () => updateSeatStatus(user.id, 'REQUESTING_TO_SPEAK'));
        userActionsDiv.appendChild(raiseHandBtn);

        // Object
        const objectBtn = document.createElement('button');
        objectBtn.classList.add('bg-red-500','hover:bg-red-600','text-white','py-1','px-2','rounded','text-xs');
        objectBtn.textContent = 'Object';
        objectBtn.addEventListener('click', () => updateSeatStatus(user.id, 'OBJECTING'));
        userActionsDiv.appendChild(objectBtn);

        // Cancel
        if (user.seatStatus !== 'NEUTRAL') {
            const cancelBtn = document.createElement('button');
            cancelBtn.classList.add('bg-gray-500','hover:bg-gray-600','text-white','py-1','px-2','rounded','text-xs');
            cancelBtn.textContent = 'Cancel';
            cancelBtn.addEventListener('click', () => updateSeatStatus(user.id, 'NEUTRAL'));
            userActionsDiv.appendChild(cancelBtn);
        }

        // Call to Speak (President only)
        if (currentUser.role === 'PRESIDENT' && user.seatStatus !== 'SPEAKING') {
            const callToSpeakBtn = document.createElement('button');
            callToSpeakBtn.classList.add('bg-green-500','hover:bg-green-600','text-white','py-1','px-2','rounded','text-xs');
            callToSpeakBtn.textContent = 'Call to Speak';
            callToSpeakBtn.addEventListener('click', () => updateSeatStatus(user.id, 'SPEAKING'));
            userActionsDiv.appendChild(callToSpeakBtn);
        }
    } else if (currentUser && currentUser.role === 'PRESIDENT') {
        // President controlling other user
        if (user.seatStatus !== 'NEUTRAL' && user.seatStatus !== 'SPEAKING') {
            const callToSpeakBtn = document.createElement('button');
            callToSpeakBtn.classList.add('bg-green-500','hover:bg-green-600','text-white','py-1','px-2','rounded','text-xs');
            callToSpeakBtn.textContent = 'Call to Speak';
            callToSpeakBtn.addEventListener('click', () => updateSeatStatus(user.id, 'SPEAKING'));
            userActionsDiv.appendChild(callToSpeakBtn);
        }
        if (user.seatStatus !== 'NEUTRAL') {
            const cancelBtn = document.createElement('button');
            cancelBtn.classList.add('bg-gray-500','hover:bg-gray-600','text-white','py-1','px-2','rounded','text-xs');
            cancelBtn.textContent = 'Cancel';
            cancelBtn.addEventListener('click', () => updateSeatStatus(user.id, 'NEUTRAL'));
            userActionsDiv.appendChild(cancelBtn);
        }
    }
}

function updateSeatBackground(seat, seatStatus) {
    seat.classList.remove('bg-gray-700','bg-yellow-500','bg-red-500','bg-green-500');
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

function removeSeat(userId) {
    const seat = document.getElementById(`seat-${userId}`);
    if (seat) seat.remove();
}

async function updateSeatStatus(userId, status) {
    try {
        const payload = { id: userId, seatStatus: status };
        const response = await fetch('/api/users/update-status', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (response.ok) {
            const updatedUser = await fetchUserById(userId);
            if (updatedUser) {
                addOrUpdateSeat(updatedUser);
            }
            let msg = '';
            switch (status) {
                case 'REQUESTING_TO_SPEAK':
                    msg = 'You have raised your hand to speak.';
                    break;
                case 'OBJECTING':
                    msg = 'You are objecting.';
                    break;
                case 'NEUTRAL':
                    msg = 'Status has been cancelled.';
                    break;
                default:
                    msg = 'Seat status updated.';
            }
            showAlert(msg, 'success');
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
            return await response.json();
        } else {
            console.warn(`Failed to fetch user with ID ${userId}.`);
            return null;
        }
    } catch (error) {
        console.error(`Error fetching user with ID ${userId}:`, error);
        return null;
    }
}

async function submitVote(proposalId, voteChoice) {
    try {
        const response = await fetch('/api/proposals/vote', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ proposalId, voteChoice })
        });

        if (response.ok) {
            showAlert('Vote submitted successfully.', 'success');
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error submitting vote:', error);
        showAlert('An error occurred while submitting your vote.', 'error');
    }
}

// ======================
// Event Listeners
// ======================
if (loginForm) {
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
}

function toggleBreakOverlay(isBreak) {
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
            endBreakButton.classList.add('hidden');
            breakOverlay.classList.add('hidden');
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
    if (currentUser && currentUser.role !== 'PRESIDENT') {
        toggleBreakOverlay(true);
    }
}

function handleEndBreak() {
    toggleBreakOverlay(false);
}

if (registerForm) {
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
}

if (logoutButton) {
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
}

function controlAssProposal() {
    if (newProposalAssociationType.value.trim() === 'normal') {
        newAssociatedProposal.disabled = true;
    } else {
        newAssociatedProposal.disabled = false;
    }
}

/**
 * Adds a new proposal. No "stupid" field here; that is toggled from the list.
 */
addProposalButton.addEventListener('click', async () => {
    const title = newProposalTitle.value; // multiline
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
            newAssociatedProposal.disabled = true;
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
 * Imposes a fine on a specified user.
 */
imposeFineButton.addEventListener('click', async () => {
    const username = fineUsername.value.trim();
    const amount = parseInt(fineAmount.value.trim(), 10);
    const reason = document.getElementById('fine-reason').value.trim();

    if (!username || isNaN(amount) || amount <= 0 || !reason) {
        showAlert('Please enter a valid username, fine amount, and reason.', 'warning');
        return;
    }

    try {
        const response = await fetch('/api/impose-fine', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, amount, reason })
        });

        if (response.ok) {
            showAlert('Fine imposed successfully!', 'success');
            fineUsername.value = '';
            fineAmount.value = '';
            document.getElementById('fine-reason').value = '';
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
            endBreakButton.classList.remove('hidden');
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
 * Ends the current session.
 */
endSessionButton.addEventListener('click', async () => {
    if (!confirm('Are you sure you want to end the session?')) return;

    try {
        await fetch('/api/end-break', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

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

endVotingButton.addEventListener('click', async () => {
    if (!confirm('Are you sure you want to end the voting (normal proposals) and count the votes?')) return;

    try {
        const response = await fetch('/api/proposals/end-voting', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response.ok) {
            showAlert('Voting ended for normal proposals. Results sent to Discord.', 'success');
            await fetchProposals();
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error ending voting:', error);
        showAlert('An error occurred while ending voting.', 'error');
    }
});

endPriorityVotingButton.addEventListener('click', async () => {
    if (!confirm('Are you sure you want to end all priority proposal voting?')) return;

    try {
        const response = await fetch('/api/proposals/end-voting-priority', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response.ok) {
            showAlert('Priority voting ended successfully (votes counted). Discord message will be sent after normal vote ends.', 'success');
            await fetchProposals();
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error ending priority voting:', error);
        showAlert('An error occurred while ending priority voting.', 'error');
    }
});

joinSeatButton.addEventListener('click', async () => {
    try {
        const response = await fetch('/api/join-seat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response.ok) {
            showAlert('Successfully joined a seat.', 'success');
            await fetchUsers();
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
// WebSocket Handlers
// ======================
function handleSeatUpdate(user) {
    if (user.present) {
        addOrUpdateSeat(user);
    } else {
        removeSeat(user.id);
    }
}

function handleFineImposed(username, amount) {
    showAlert(`User ${username} has been fined ${amount} units.`, 'warning');
}

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
            endBreakButton.classList.remove('hidden');
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
function initializeWebSocket() {
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
        setTimeout(initializeWebSocket, 5000);
    };
}

// ======================
// Application Initialization
// ======================
function startPolling() {
  // 1) Poll users & break status
  setInterval(async () => {
    try {
      await fetchUsers();
      await checkBreakStatus();
    } catch (error) {
      console.error("Interval (users/break) error:", error);
      // This ensures the interval continues even if there's an error
    }
  }, 1500);

  // 2) Poll proposals
  setInterval(async () => {
    try {
      await fetchProposals();
    } catch (error) {
      console.error("Interval (proposals) error:", error);
    }
  }, 5000);
}

async function initializeApp() {
    authContainer.classList.add('hidden');
    mainContainer.classList.remove('hidden');

    currentUser = await fetchUserInfo();
    if (currentUser && currentUser.role === 'PRESIDENT') {
        presidentActions.classList.remove('hidden');
    } else {
        presidentActions.classList.add('hidden');
    }

    // Hide or show the "Stupid?" columns based on role
    if (currentUser && currentUser.role === 'PRESIDENT') {
        normalStupidHeader.classList.remove('hidden');
        priorityStupidHeader.classList.remove('hidden');
    } else {
        normalStupidHeader.classList.add('hidden');
        priorityStupidHeader.classList.add('hidden');
    }

    await checkBreakStatus();
    await fetchProposals();
    await fetchUsers();

    initializeWebSocket();
    startPolling();
}

function resetApp() {
    mainContainer.classList.add('hidden');
    authContainer.classList.remove('hidden');
    if (loginForm) loginForm.reset();
    if (registerForm) registerForm.reset();

    proposalsTable.innerHTML = '';
    priorityProposalsTable.innerHTML = '';
    document.getElementById('seat-layout').innerHTML = '';
    presidentActions.classList.add('hidden');

    if (ws) {
        ws.close();
        ws = null;
    }
}

async function checkAuthentication() {
    currentUser = await fetchUserInfo();
    if (currentUser) {
        authContainer.classList.add('hidden');
        mainContainer.classList.remove('hidden');
        if (currentUser.role === 'PRESIDENT') {
            presidentActions.classList.remove('hidden');
        } else {
            presidentActions.classList.add('hidden');
        }
        await fetchProposals();
        await fetchUsers();
        initializeWebSocket();
    } else {
        authContainer.classList.remove('hidden');
        mainContainer.classList.add('hidden');
    }
}

window.addEventListener('DOMContentLoaded', () => {
    if (loginTab && registerTab) {
        loginTab.addEventListener('click', () => switchTab('login'));
        registerTab.addEventListener('click', () => switchTab('register'));
    }
    checkAuthentication();
});

if (window.location.pathname.endsWith('/admin.html')) {
    initializeAdminDashboard();
}

async function initializeAdminDashboard() {
    currentUser = await fetchUserInfo();
    if (!currentUser || currentUser.role !== 'PRESIDENT') {
        alert('Access denied. Only the president can access this page.');
        window.location.href = '/';
        return;
    }
    // ... admin logic ...
}

// Removing or editing a proposal (president)
async function removeProposal(proposalId) {
    if (!confirm("Are you sure you want to remove this proposal?")) return;

    try {
        const response = await fetch(`/api/proposals/${proposalId}`, {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response.ok) {
            showAlert("Proposal removed successfully!", "success");
            fetchProposals();
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
    const modal = document.createElement('div');
    modal.classList.add('modal','fixed','inset-0','flex','items-center','justify-center','z-50','bg-black','bg-opacity-50');
    modal.innerHTML = `
        <div class="modal-content bg-gray-700 p-6 rounded">
            <h2 class="text-xl font-bold mb-4">Edit Proposal</h2>
            <label class="block mb-2">Title</label>
            <textarea id="edit-proposal-title"
                      rows="4"
                      class="w-full mb-4 px-3 py-2 rounded bg-gray-800 text-white"></textarea>

            <label class="block mb-2">Party</label>
            <input type="text" id="edit-proposal-party"
                   class="w-full mb-4 px-3 py-2 rounded bg-gray-800 text-white">

            <button id="save-proposal" class="bg-green-500 hover:bg-green-600 text-white py-2 px-4 rounded">Save</button>
            <button id="close-modal" class="bg-gray-500 hover:bg-gray-600 text-white py-2 px-4 rounded">Cancel</button>
        </div>
    `;
    document.body.appendChild(modal);

    modal.querySelector('#close-modal').addEventListener('click', () => modal.remove());

    fetch(`/api/proposals/${proposalId}`)
        .then(response => response.json())
        .then(proposal => {
            document.getElementById('edit-proposal-title').value = proposal.title || '';
            document.getElementById('edit-proposal-party').value = proposal.party || '';
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
        const response = await fetch(`/api/proposals/${proposalId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title, party })
        });

        if (response.ok) {
            showAlert("Proposal updated successfully!", "success");
            fetchProposals();
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, "error");
        }
    } catch (error) {
        console.error("Error updating proposal:", error);
        showAlert("An error occurred while updating the proposal.", "error");
    }
}
