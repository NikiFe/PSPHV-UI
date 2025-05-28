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

// NEW: Constitutional proposals section and table
const constitutionalProposalsSection = document.getElementById('constitutional-proposals-section');
const constitutionalProposalsTable = document.getElementById('constitutional-proposals-table');


// President Actions
const presidentActions = document.getElementById('president-actions');
const addProposalButton = document.getElementById('add-proposal');
const newProposalTitle = document.getElementById('new-proposal-title');
const newProposalParty = document.getElementById('new-proposal-party');
const newProposalPriority = document.getElementById('new-proposal-priority');
const newProposalAssociationType = document.getElementById('new-proposal-association-type');
const newAssociatedProposal = document.getElementById('new-associated-proposal');
// NEW: New dropdowns for proposal category and vote requirement
const newProposalCategory = document.getElementById('new-proposal-category');
const newProposalVoteRequirement = document.getElementById('new-proposal-vote-requirement');


const imposeFineButton = document.getElementById('impose-fine');
const fineUsername = document.getElementById('fine-username');
const fineAmount = document.getElementById('fine-amount');
const callBreakButton = document.getElementById('call-break');
const endSessionButton = document.getElementById('end-session');
const endVotingButton = document.getElementById('end-voting');
const endPriorityVotingButton = document.getElementById('end-priority-voting');
// NEW: End Constitutional Voting button
const endConstitutionalVotingButton = document.getElementById('end-constitutional-voting');


const alertContainer = document.getElementById('alert');
const alertMessage = document.getElementById('alert-message');
const breakOverlay = document.getElementById('break-overlay');
const endBreakButton = document.getElementById('end-break');

// NEW: Queue Elements
const parliamentaryQueueTableBody = document.getElementById('parliamentary-queue-table-body');
const requestSpeakButton          = document.getElementById('request-speak-button');
const queueActionsHeader          = document.getElementById('queue-actions-header');
const queueWindow                 = document.getElementById('parliamentary-queue-section');
const queueWindowHandle           = document.getElementById('queue-drag-handle');
const queueCollapseButton         = document.getElementById('queue-collapse');


// ======================
// State Variables
// ======================
let currentUser = null; // { username: '', role: '', id: '' }
let ws = null; // WebSocket connection
let csrfToken = null; // Variable to store the CSRF token

// ======================
// Utility Functions
// ======================


function makeDraggable(handle, element) {
    let startX = 0, startY = 0, origX = 0, origY = 0, dragging = false;

    handle.addEventListener('mousedown', startDrag);
    handle.addEventListener('touchstart', startDrag, { passive: false });

    function startDrag(e) {
        e.preventDefault();
        dragging = true;
        startX = (e.touches ? e.touches[0].clientX : e.clientX);
        startY = (e.touches ? e.touches[0].clientY : e.clientY);
        const rect = element.getBoundingClientRect();
        origX = rect.left;
        origY = rect.top;
        document.addEventListener('mousemove', onDrag);
        document.addEventListener('touchmove', onDrag, { passive: false });
        document.addEventListener('mouseup', endDrag);
        document.addEventListener('touchend', endDrag);
    }
    function onDrag(e) {
        if (!dragging) return;
        e.preventDefault();
        const x = (e.touches ? e.touches[0].clientX : e.clientX);
        const y = (e.touches ? e.touches[0].clientY : e.clientY);
        element.style.left = `${origX + (x - startX)}px`;
        element.style.top  = `${origY + (y - startY)}px`;
    }
    function endDrag() {
        dragging = false;
        document.removeEventListener('mousemove', onDrag);
        document.removeEventListener('touchmove', onDrag);
        document.removeEventListener('mouseup', endDrag);
        document.removeEventListener('touchend', endDrag);
    }
}

function getHeadersWithCsrf(additionalHeaders = {}) {
    const headers = {
        'Content-Type': 'application/json',
        ...additionalHeaders
    };
    if (csrfToken) {
        headers['X-CSRF-TOKEN'] = csrfToken;
    }
    return headers;
}

function showAlert(message, type = 'success') {
    // Use specific alert elements from index.html
    const container = document.getElementById('alert'); 
    const messageElement = document.getElementById('alert-message');

    console.log(`showAlert called. Message: "${message}", Type: ${type}`); // Log entry
    console.log("Target container:", container);
    console.log("Target message element:", messageElement);

    if (!container || !messageElement) {
        console.error("Alert container (#alert) or message element (#alert-message) not found in DOM!");
        return; 
    }

    messageElement.innerText = message;

    // Define classes for each type
    const baseClasses = ['block', 'px-4', 'py-3', 'rounded', 'shadow-lg', 'mb-4'];
    const successClasses = ['bg-green-100', 'border', 'border-green-400', 'text-green-700'];
    const errorClasses = ['bg-red-100', 'border', 'border-red-400', 'text-red-700'];
    const warningClasses = ['bg-yellow-100', 'border', 'border-yellow-400', 'text-yellow-700'];
    const allTypeClasses = [...successClasses, ...errorClasses, ...warningClasses];

    // Log current classes before modification
    console.log("Alert container classes BEFORE:", container.className);

    // Remove previous type classes and hidden
    container.classList.remove('hidden', ...allTypeClasses);
    
    // Add base classes and current type classes
    container.classList.add(...baseClasses);
    if (type === 'success') {
        container.classList.add(...successClasses);
    } else if (type === 'error') {
        container.classList.add(...errorClasses);
    } else if (type === 'warning') {
        container.classList.add(...warningClasses);
    }
    
    // Log current classes AFTER modification
    console.log("Alert container classes AFTER:", container.className);

    // Set timeout to hide again
    // Clear any existing timeout to prevent premature hiding if called rapidly
    if (container.dataset.hideTimeout) {
        clearTimeout(parseInt(container.dataset.hideTimeout));
    }
    const timeoutId = setTimeout(() => {
        container.classList.add('hidden');
        // Optionally remove type classes when hiding too
        container.classList.remove(...allTypeClasses, ...baseClasses);
        delete container.dataset.hideTimeout;
    }, 5000);
    container.dataset.hideTimeout = timeoutId.toString();
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
        const response = await fetch('/api/user-info', {
            method: 'GET',
            headers: getHeadersWithCsrf(), // Send existing token if any (harmless for GET)
            credentials: 'include'
        });
        if (response.ok) {
            const userInfo = await response.json();
            // Store CSRF token received from server (X-CSRF-TOKEN is the key we added)
            if (userInfo['X-CSRF-TOKEN']) {
                csrfToken = userInfo['X-CSRF-TOKEN'];
                // console.log("CSRF token set/refreshed from user-info");
            } else {
                 console.warn("CSRF token missing in /api/user-info response.");
                 // Don't null out existing csrfToken if response is missing it
            }
            
            // Remove token from user object before storing globally
            const user = { ...userInfo }; 
            delete user['X-CSRF-TOKEN']; 

            currentUser = user;
            return user;
        } else {
            console.error('Failed to fetch user info');
            currentUser = null; // Ensure currentUser is null on failure
            csrfToken = null;  // Clear CSRF token if user info fails (session likely invalid)
            return null;
        }
    } catch (error) {
        console.error('Error fetching user info:', error);
        currentUser = null; // Ensure currentUser is null on error
        csrfToken = null;  // Clear CSRF token on error
        return null;
    }
}

/**
 * Renders the parliamentary queue in the UI.
 */
function renderQueue(queueItems = []) {
    /* safety checks ------------------------------------------------------- */
    if (!parliamentaryQueueTableBody) {
        console.error('Parliamentary queue table body not found!');
        return;
    }

    /* clear previous rows ------------------------------------------------- */
    parliamentaryQueueTableBody.replaceChildren();

    /* show placeholder when empty ---------------------------------------- */
    if (queueItems.length === 0) {
        const emptyRow = parliamentaryQueueTableBody.insertRow();
        emptyRow.classList.add('text-gray-400');
        const td = emptyRow.insertCell(0);
        td.colSpan = currentUser && currentUser.role === 'PRESIDENT' ? 6 : 5;
        td.classList.add('py-3', 'text-center');
        td.textContent = 'Queue is empty';
        return;
    }

    /* build rows ---------------------------------------------------------- */
    queueItems.forEach((item, index) => {
        const row = parliamentaryQueueTableBody.insertRow();
        row.dataset.queueItemId = item.id;

        const cellBase   = ['py-2', 'px-4', 'border-b', 'border-gray-600', 'text-center'];
        const wrapCells  = [...cellBase, 'break-words', 'whitespace-pre-wrap'];

        const [num, type, who, prio, stat, act] =
              Array.from({ length: 6 }, () => row.insertCell(-1));

        num .classList.add(...cellBase);
        type.classList.add(...cellBase);
        who .classList.add(...wrapCells);
        prio.classList.add(...cellBase);
        stat.classList.add(...cellBase);
        act .classList.add(...cellBase);

        num .textContent = index + 1;
        type.textContent = item.type.replace(/_/g, ' ');
        //  10‥20  → High   (speaker requests, constitutional)
        //  21‥25  → High   (priority proposals)
        //  26+    → Normal (normal proposals, or anything lower-priority)
        if (item.priority <= 7) {
            prio.textContent = 'Critical';
            prio.classList.add('text-red-400', 'font-bold');        // 🔴 optional styling
        } else if (item.priority <= 25) {
            prio.textContent = 'High';
        } else {
            prio.textContent = 'Normal';
        }

        stat.textContent = item.status;

        if (item.status === 'active')  row.classList.add('bg-green-800/40');
        if (item.status === 'pending') row.classList.add('bg-yellow-800/20');

        /* user / proposal column ----------------------------------------- */
        if (item.type === 'SPEAKER_REQUEST' || item.type === 'OBJECTION') {
            who.textContent = item.username || 'N/A';
        } else if (item.type === 'PROPOSAL_DISCUSSION') {
            who.textContent = item.proposalTitle || item.proposalVisual || 'N/A';
        } else {
            who.textContent = 'N/A';
        }

        /* president-only action buttons ---------------------------------- */
        act.replaceChildren();
        if (currentUser?.role === 'PRESIDENT') {
            queueActionsHeader.classList.remove('hidden');

            if (item.status === 'pending') {
                const btn = document.createElement('button');
                btn.textContent = 'Set Active';
                btn.classList.add('bg-green-500', 'hover:bg-green-600',
                                  'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
                btn.onclick = () => setQueueItemActive(item.id);
                act.appendChild(btn);
            } else if (item.status === 'active') {
                const btn = document.createElement('button');
                btn.textContent = 'Complete';
                btn.classList.add('bg-red-500', 'hover:bg-red-600',
                                  'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
                btn.onclick = () => completeActiveQueueItem(item.id);
                act.appendChild(btn);
            }
        } else {
            queueActionsHeader.classList.add('hidden');
            act.textContent = '—';
        }
    });
}

/**
 * Handles requesting to speak.
 */
async function requestToSpeak() {
    try {
        const response = await fetch('/api/queue/request-speak', {
            method: 'POST',
            headers: getHeadersWithCsrf(),
            body: JSON.stringify({})
        });


        if (response.ok) {
            showAlert('Request to speak added to queue.', 'success');
            await fetchQueue(); // Refresh queue
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error requesting to speak:', error);
        showAlert('An error occurred while requesting to speak.', 'error');
    }
}

async function fetchQueue(retry = false) {
    try {
        const res = await fetch('/api/parliament-queue/view', {
            method: 'GET',
            headers: getHeadersWithCsrf(),   // adds X-CSRF-TOKEN when we have one
            credentials: 'include'           // sends the session cookie
        });

        if (!res.ok) throw new Error(res.statusText);
        const q = await res.json();
        renderQueue(q);
    } catch (err) {
        console.error('Queue fetch failed:', err);
        if (!retry) setTimeout(() => fetchQueue(true), 1000);
    }
}


/**
 * President sets a queue item as active.
 */
async function setQueueItemActive(itemId) {
    try {
        const response = await fetch(`/api/queue/set-active/${itemId}`, {
            method: 'POST',
            headers: getHeadersWithCsrf()
        });

        if (response.ok) {
            showAlert('Queue item set to active.', 'success');
            await fetchQueue(); // Refresh queue
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error setting queue item active:', error);
        showAlert('An error occurred while setting the queue item active.', 'error');
    }
}

/**
 * President completes the active queue item.
 */
async function completeActiveQueueItem(itemId) {
    try {
        const response = await fetch(`/api/queue/complete-active/${itemId}`, {
            method: 'POST',
            headers: getHeadersWithCsrf()
        });

        if (response.ok) {
            showAlert('Active queue item completed.', 'success');
            await fetchQueue(); // Refresh queue
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error completing active queue item:', error);
        showAlert('An error occurred while completing the active queue item.', 'error');
    }
}

function renderConstitutionalProposals(proposals) {
    const tableBody = document.getElementById('constitutional-proposals-table-body');
    const section = document.getElementById('constitutional-proposals-section');

    if (!tableBody) {
        console.error("Constitutional proposals table body not found!");
        if (section) section.classList.add('hidden');
        return;
    }
    tableBody.replaceChildren(); // Clear existing rows

    if (!proposals || proposals.length === 0) {
        if (section) section.classList.add('hidden');
        return;
    }
    if (section) section.classList.remove('hidden');

    proposals.forEach(proposal => {
        const row = tableBody.insertRow();
        row.dataset.proposalId = proposal.id;

        // Apply Tailwind classes for consistent styling (Copied from Priority)
        const cellClasses = ['py-2', 'px-4', 'border-b', 'border-gray-600', 'text-center'];
        const titleCellClasses = [...cellClasses, 'break-words', 'whitespace-pre-wrap'];

        const cellNumber  = row.insertCell(0);
        const cellTitle   = row.insertCell(1);
        const cellParty   = row.insertCell(2);
        const cellVote    = row.insertCell(3);
        const cellStatus  = row.insertCell(4);
        const cellVoteReq = row.insertCell(5);
        const cellStupid  = row.insertCell(6);
        const cellActions = row.insertCell(7);

        // Apply consistent classes
        cellNumber.classList.add(...cellClasses);
        cellTitle.classList.add(...titleCellClasses);
        cellParty.classList.add(...cellClasses);
        cellVote.classList.add(...cellClasses);
        cellStatus.classList.add(...cellClasses);
        cellVoteReq.classList.add(...cellClasses);
        cellStupid.classList.add(...cellClasses);
        cellActions.classList.add(...cellClasses);

        cellNumber.textContent = proposal.proposalVisual || '';
        cellTitle.textContent = proposal.title || '';
        cellParty.textContent = proposal.party || '';
        cellVoteReq.textContent = proposal.voteRequirement || 'Rel';

        cellVote.replaceChildren();
        if (proposal.stupid) {
            cellVote.textContent = '(Stupid, no voting)';
        } else {
            const voteChoices = ['For', 'Against', 'Abstain'];
            const userVote = proposal.userVote || 'Abstain';
            const voteForm = document.createElement('div');
            voteForm.classList.add('flex', 'justify-center', 'space-x-4');
            voteChoices.forEach(choice => {
                const label = document.createElement('label');
                label.classList.add('inline-flex', 'items-center', 'space-x-2');
                const radio = document.createElement('input');
                radio.type = 'radio';
                radio.name = `vote-constitutional-${proposal.id}`; // Unique name
                radio.value = choice;
                radio.checked = (userVote === choice);
                radio.disabled = proposal.votingEnded;
                // Match Priority styling for radio
                radio.classList.add('form-radio', 'h-4', 'w-4', 'sm:h-5', 'sm:w-5', 'text-blue-500', 'bg-gray-700', 'border-gray-500', 'focus:ring-blue-500'); 
                radio.addEventListener('change', () => submitVote(proposal.id, choice));
                const span = document.createElement('span');
                span.classList.add('text-sm');
                span.textContent = choice;
                label.appendChild(radio);
                label.appendChild(span);
                voteForm.appendChild(label);
            });
            cellVote.appendChild(voteForm);
        }

        cellStatus.replaceChildren();
        if (proposal.votingEnded) {
            const statusText = proposal.passed ? 'Passed' : 'Failed';
            const statusDetail = `For: ${proposal.totalFor}, Against: ${proposal.totalAgainst}`;
            const strongElement = document.createElement('strong');
            strongElement.textContent = statusText;
            strongElement.classList.add(proposal.passed ? 'text-green-400' : 'text-red-400'); // Match Priority
            cellStatus.appendChild(strongElement);
            cellStatus.appendChild(document.createElement('br'));
            cellStatus.appendChild(document.createTextNode(statusDetail));
        } else {
            cellStatus.textContent = 'Voting in progress (constitutional)';
        }

        cellStupid.replaceChildren();
        if (currentUser && currentUser.role === 'PRESIDENT') {
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = proposal.stupid === true;
            // Apply consistent styling matching other tables
            checkbox.classList.add('form-checkbox', 'h-5', 'w-5', 'text-blue-600', 'rounded', 'bg-gray-700', 'border-gray-500', 'focus:ring-blue-500');
            checkbox.addEventListener('change', () => toggleStupidProposal(proposal.id, checkbox.checked));
            cellStupid.appendChild(checkbox);
        } else {
            cellStupid.textContent = proposal.stupid ? 'Yes' : 'No';
        }

        cellActions.replaceChildren();
        if (currentUser && currentUser.role === 'PRESIDENT') {
            const editButton = document.createElement('button');
            editButton.classList.add('edit-proposal', 'bg-blue-500', 'hover:bg-blue-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
            editButton.dataset.id = proposal.id;
            editButton.textContent = 'Edit';
            editButton.addEventListener('click', () => openEditProposalWindow(proposal.id));

            const removeButton = document.createElement('button');
            removeButton.classList.add('remove-proposal', 'bg-red-500', 'hover:bg-red-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs', 'ml-1');
            removeButton.dataset.id = proposal.id;
            removeButton.textContent = 'Remove';
            removeButton.addEventListener('click', () => removeProposal(proposal.id));

            cellActions.appendChild(editButton);
            cellActions.appendChild(removeButton);
        } else {
            cellActions.textContent = '—';
        }
    });
}

async function fetchProposals() {
    try {
        const response = await fetch('/api/proposals', {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' }
        });
        if (response.ok) {
            const allProposals = await response.json();
            // Split proposals into three groups:
            const constitutionalProposals = allProposals.filter(p => p.isConstitutional === true);
            const priorityProposals = allProposals.filter(p => p.isPriority === true);
            const normalProposals = allProposals.filter(p => !p.isPriority && !p.isConstitutional);
            renderConstitutionalProposals(constitutionalProposals);
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
            headers: getHeadersWithCsrf(),
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
    if (proposalsTable) proposalsTable.replaceChildren(); // Clear with replaceChildren
    else return; // proposalsTable is not found

    proposals.forEach(proposal => {
        const row = proposalsTable.insertRow();
        row.dataset.proposalId = proposal.id;

        // Apply Tailwind classes for consistent styling (Copied from Priority)
        const cellClasses = ['py-2', 'px-4', 'border-b', 'border-gray-600', 'text-center'];
        const titleCellClasses = [...cellClasses, 'break-words', 'whitespace-pre-wrap'];

        const cellNumber  = row.insertCell(0);
        const cellTitle   = row.insertCell(1);
        const cellParty   = row.insertCell(2);
        const cellVote    = row.insertCell(3);
        const cellStatus  = row.insertCell(4);
        const cellVoteReq = row.insertCell(5);
        const cellStupid  = row.insertCell(6);
        const cellActions = row.insertCell(7);

        // Apply consistent classes
        cellNumber.classList.add(...cellClasses);
        cellTitle.classList.add(...titleCellClasses);
        cellParty.classList.add(...cellClasses);
        cellVote.classList.add(...cellClasses);
        cellStatus.classList.add(...cellClasses);
        cellVoteReq.classList.add(...cellClasses);
        cellStupid.classList.add(...cellClasses);
        cellActions.classList.add(...cellClasses);

        cellNumber.textContent = proposal.proposalVisual || '';
        cellTitle.textContent = proposal.title || '';
        cellParty.textContent = proposal.party || '';
        cellVoteReq.textContent = proposal.voteRequirement || 'Rel';

        cellVote.replaceChildren(); // Clear cell
        if (proposal.stupid) {
            cellVote.textContent = '(Stupid, no voting)';
        } else {
            const voteChoices = ['For', 'Against', 'Abstain'];
            const userVote = proposal.userVote || 'Abstain';
            const voteForm = document.createElement('div');
            voteForm.classList.add('flex', 'justify-center', 'space-x-4');
            voteChoices.forEach(choice => {
                const label = document.createElement('label');
                label.classList.add('inline-flex', 'items-center', 'space-x-1', 'sm:space-x-2', 'cursor-pointer'); // Match Priority

                const radio = document.createElement('input');
                radio.type = 'radio';
                radio.name = `vote-${proposal.id}`;
                radio.value = choice;
                radio.checked = (userVote === choice);
                radio.disabled = proposal.votingEnded;
                 // Match Priority styling for radio
                radio.classList.add('form-radio', 'h-4', 'w-4', 'sm:h-5', 'sm:w-5', 'text-blue-500', 'bg-gray-700', 'border-gray-500', 'focus:ring-blue-500');
                radio.addEventListener('change', () => submitVote(proposal.id, choice));
                
                const span = document.createElement('span');
                span.classList.add('text-xs', 'sm:text-sm'); // Match Priority
                span.textContent = choice;

                label.appendChild(radio);
                label.appendChild(span);
                voteForm.appendChild(label);
            });
            cellVote.appendChild(voteForm);
        }

        cellStatus.replaceChildren(); // Clear cell
        if (proposal.votingEnded) {
            const statusText = proposal.passed ? 'Passed' : 'Failed';
            const statusDetail = `For: ${proposal.totalFor}, Against: ${proposal.totalAgainst}`;
            const strongElement = document.createElement('strong');
            strongElement.textContent = statusText;
            strongElement.classList.add(proposal.passed ? 'text-green-400' : 'text-red-400'); // Match Priority
            cellStatus.appendChild(strongElement);
            cellStatus.appendChild(document.createElement('br'));
            cellStatus.appendChild(document.createTextNode(statusDetail));
        } else {
            cellStatus.textContent = 'Voting in progress';
        }

        cellStupid.replaceChildren(); // Clear cell
        if (currentUser && currentUser.role === 'PRESIDENT') {
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = proposal.stupid === true;
            // Apply consistent styling matching other tables
            checkbox.classList.add('form-checkbox', 'h-5', 'w-5', 'text-blue-600', 'rounded', 'bg-gray-700', 'border-gray-500', 'focus:ring-blue-500');
            checkbox.addEventListener('change', () => toggleStupidProposal(proposal.id, checkbox.checked));
            cellStupid.appendChild(checkbox);
        } else {
            cellStupid.textContent = proposal.stupid ? 'Yes' : 'No';
        }

        cellActions.replaceChildren(); // Clear cell
        if (currentUser && currentUser.role === 'PRESIDENT') {
            const editButton = document.createElement('button');
            editButton.classList.add('edit-proposal', 'bg-blue-500', 'hover:bg-blue-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
            editButton.dataset.id = proposal.id;
            editButton.textContent = 'Edit';
            editButton.addEventListener('click', () => openEditProposalWindow(proposal.id));

            const removeButton = document.createElement('button');
            removeButton.classList.add('remove-proposal', 'bg-red-500', 'hover:bg-red-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs', 'ml-1');
            removeButton.dataset.id = proposal.id;
            removeButton.textContent = 'Remove';
            removeButton.addEventListener('click', () => removeProposal(proposal.id));

            cellActions.appendChild(editButton);
            cellActions.appendChild(removeButton);
        } else {
            cellActions.textContent = '—';
        }
    });
}

/**
 * Renders the priority proposals in their separate table.
 */
function renderPriorityProposals(proposals) {
    const section = document.getElementById('priority-proposals-section');

    if (!section) {
        console.error("renderPriorityProposals: CRITICAL - Priority proposals SECTION ('priority-proposals-section') not found in DOM!");
        return; // Can't do anything if the whole section is missing
    }

    // Attempt to find the tbody using its ID directly
    const tableBody = document.getElementById('priority-proposals-table-body');

    if (!tableBody) {
        console.error("renderPriorityProposals: Priority proposals TABLE BODY ('priority-proposals-table-body') not found in DOM.");
        // Log details about the section to see if its tbody is missing or the section is malformed
        if (section) { // Check section again, just in case it vanished between the two getElementById calls (highly unlikely)
            console.log("renderPriorityProposals: outerHTML of 'priority-proposals-section' (up to 500 chars):", section.outerHTML.substring(0, 500));
        }
        section.classList.add('hidden'); // Hide the section as we can't populate its body
        return;
    }

    // Sanity check: Ensure the found tableBody is a child of the section. 
    // This helps detect if getElementById picked up an orphaned element or one from an unexpected part of the DOM.
    if (!section.contains(tableBody)) {
         console.warn("renderPriorityProposals: 'priority-proposals-table-body' was found, but is NOT a child of 'priority-proposals-section'. This is unexpected. Section outerHTML:", section.outerHTML.substring(0,500));
         // Depending on desired robustness, one might choose to return here or try to use tableBody anyway.
         // For now, we'll proceed if found by ID, but the warning is important.
    }

    tableBody.replaceChildren(); // Clear existing rows

    if (!proposals || proposals.length === 0) {
        section.classList.add('hidden');
        // console.log("renderPriorityProposals: No priority proposals to display or proposals array empty.");
        return;
    }

    section.classList.remove('hidden'); // Show section if there are proposals

    proposals.forEach(proposal => {
        const row = tableBody.insertRow();
        row.dataset.proposalId = proposal.id;

        // Apply Tailwind classes for consistent styling
        const cellClasses = ['py-2', 'px-4', 'border-b', 'border-gray-600', 'text-center'];
        const titleCellClasses = [...cellClasses, 'break-words', 'whitespace-pre-wrap']; // For title

        const cellNumber  = row.insertCell(0);
        const cellTitle   = row.insertCell(1);
        const cellParty   = row.insertCell(2);
        const cellVote    = row.insertCell(3);
        const cellStatus  = row.insertCell(4);
        const cellVoteReq = row.insertCell(5);
        const cellStupid  = row.insertCell(6);
        const cellActions = row.insertCell(7);

        cellNumber.classList.add(...cellClasses);
        cellTitle.classList.add(...titleCellClasses);
        cellParty.classList.add(...cellClasses);
        cellVote.classList.add(...cellClasses);
        cellStatus.classList.add(...cellClasses);
        cellVoteReq.classList.add(...cellClasses);
        cellStupid.classList.add(...cellClasses);
        cellActions.classList.add(...cellClasses);

        cellNumber.textContent = proposal.proposalVisual || '';
        cellTitle.textContent = proposal.title || '';
        cellParty.textContent = proposal.party || '';
        cellVoteReq.textContent = proposal.voteRequirement || 'Rel';

        cellVote.replaceChildren(); // Clear previous content
        if (proposal.stupid) {
            cellVote.textContent = '(Stupid, no voting)';
        } else {
            const voteChoices = ['For', 'Against', 'Abstain'];
            const userVote = proposal.userVote || 'Abstain'; // Default to Abstain if no vote
            const voteForm = document.createElement('div');
            voteForm.classList.add('flex', 'justify-center', 'space-x-2', 'sm:space-x-4'); // Responsive spacing

            voteChoices.forEach(choice => {
                const label = document.createElement('label');
                label.classList.add('inline-flex', 'items-center', 'space-x-1', 'sm:space-x-2', 'cursor-pointer');

                const radio = document.createElement('input');
                radio.type = 'radio';
                radio.name = `vote-priority-${proposal.id}`;
                radio.value = choice;
                radio.checked = (userVote === choice);
                radio.disabled = proposal.votingEnded;
                radio.classList.add('form-radio', 'h-4', 'w-4', 'sm:h-5', 'sm:w-5', 'text-blue-500', 'bg-gray-700', 'border-gray-500', 'focus:ring-blue-500');
                radio.addEventListener('change', () => submitVote(proposal.id, choice));

                const span = document.createElement('span');
                span.classList.add('text-xs', 'sm:text-sm');
                span.textContent = choice;

                label.appendChild(radio);
                label.appendChild(span);
                voteForm.appendChild(label);
            });
            cellVote.appendChild(voteForm);
        }

        cellStatus.replaceChildren(); // Clear previous content
        if (proposal.votingEnded) {
            const statusText = proposal.passed ? 'Passed' : 'Failed';
            const statusDetail = `For: ${proposal.totalFor}, Against: ${proposal.totalAgainst}`;
            const strongElement = document.createElement('strong');
            strongElement.textContent = statusText;
            strongElement.classList.add(proposal.passed ? 'text-green-400' : 'text-red-400');
            cellStatus.appendChild(strongElement);
            cellStatus.appendChild(document.createElement('br'));
            cellStatus.appendChild(document.createTextNode(statusDetail));
        } else {
            cellStatus.textContent = 'Voting in progress (priority)';
        }

        cellStupid.replaceChildren(); // Clear previous content
        if (currentUser && currentUser.role === 'PRESIDENT') {
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.checked = proposal.stupid === true;
            // Apply consistent styling matching other tables
            checkbox.classList.add('form-checkbox', 'h-5', 'w-5', 'text-blue-600', 'rounded', 'bg-gray-700', 'border-gray-500', 'focus:ring-blue-500');
            checkbox.addEventListener('change', () => toggleStupidProposal(proposal.id, checkbox.checked));
            cellStupid.appendChild(checkbox);
        } else {
            cellStupid.textContent = proposal.stupid ? 'Yes' : 'No';
        }

        cellActions.replaceChildren(); // Clear previous content
        if (currentUser && currentUser.role === 'PRESIDENT') {
            const editButton = document.createElement('button');
            editButton.classList.add('edit-proposal', 'bg-blue-500', 'hover:bg-blue-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
            editButton.dataset.id = proposal.id;
            editButton.textContent = 'Edit';
            editButton.addEventListener('click', () => openEditProposalWindow(proposal.id));

            const removeButton = document.createElement('button');
            removeButton.classList.add('remove-proposal', 'bg-red-500', 'hover:bg-red-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs', 'ml-1');
            removeButton.dataset.id = proposal.id;
            removeButton.textContent = 'Remove';
            removeButton.addEventListener('click', () => removeProposal(proposal.id));

            cellActions.appendChild(editButton);
            cellActions.appendChild(removeButton);
        } else {
            cellActions.textContent = '—';
        }
    });
    // console.log(`renderPriorityProposals: Rendered ${proposals.length} priority proposals.`);
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
    const seatLayout = document.getElementById('seat-layout');

    if (!seat && seatLayout) {
        seat = document.createElement('div');
        seat.classList.add('p-4', 'rounded-md', 'shadow', 'relative');
        seat.id = `seat-${user.id}`;

        const userNameElement = document.createElement('h3');
        userNameElement.classList.add('text-lg', 'font-semibold');
        // userNameElement.textContent will be set in updateSeatContent

        const roleElement = document.createElement('p');
        roleElement.classList.add('text-sm');
        // roleElement.textContent will be set in updateSeatContent

        const partyElement = document.createElement('p');
        partyElement.classList.add('text-sm');
        // partyElement.textContent will be set in updateSeatContent

        const strengthElement = document.createElement('p');
        strengthElement.classList.add('text-sm');
        // strengthElement.textContent will be set in updateSeatContent

        const actionsDiv = document.createElement('div');
        actionsDiv.classList.add('user-actions', 'space-x-2');

        seat.appendChild(userNameElement);
        seat.appendChild(roleElement);
        seat.appendChild(partyElement);
        seat.appendChild(strengthElement);
        seat.appendChild(actionsDiv);

        seatLayout.appendChild(seat);
    } else if (!seatLayout) {
        console.error("Seat layout container not found!");
        return; // Cannot add or update seat if layout container is missing
    }

    if (seat) { // Ensure seat exists before updating content
        updateSeatContent(seat, user);
        updateSeatBackground(seat, user.seatStatus);
    } else {
        // This case should ideally not be reached if the above logic is correct
        // but as a fallback if seat is unexpectedly null (e.g. user.id is problematic)
        console.error(`Seat element could not be found or created for user ID: ${user.id}`);
    }
}

function updateSeatContent(seat, user) {
    const userId = String(user.id);
    const currentUserId = currentUser ? String(currentUser.id) : null;

    const h3 = seat.querySelector('h3');
    const p1 = seat.querySelector('p:nth-of-type(1)');
    const p2 = seat.querySelector('p:nth-of-type(2)');
    const p3 = seat.querySelector('p:nth-of-type(3)');

    if (h3) h3.textContent = user.username;
    if (p1) p1.textContent = `Role: ${user.role}`;
    if (p2) p2.textContent = `Party: ${user.partyAffiliation || 'N/A'}`;
    if (p3) p3.textContent = `Voličská síla: ${user.electoralStrength || 0}`;

    const userActionsDiv = seat.querySelector('.user-actions');
    
    if (userActionsDiv) { // Ensure this block correctly wraps all userActionsDiv manipulations
        userActionsDiv.replaceChildren(); // Clear previous buttons

        if (currentUserId && userId === currentUserId) {
            // Raise Hand
            const raiseHandBtn = document.createElement('button');
            raiseHandBtn.classList.add('bg-blue-500', 'hover:bg-blue-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
            raiseHandBtn.textContent = 'Raise Hand';
            raiseHandBtn.addEventListener('click', () => updateSeatStatus(user.id, 'REQUESTING_TO_SPEAK'));
            userActionsDiv.appendChild(raiseHandBtn);

            // Object
            const objectBtn = document.createElement('button');
            objectBtn.classList.add('bg-red-500', 'hover:bg-red-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
            objectBtn.textContent = 'Object';
            objectBtn.addEventListener('click', () => updateSeatStatus(user.id, 'OBJECTING'));
            userActionsDiv.appendChild(objectBtn);

            // Cancel
            if (user.seatStatus !== 'NEUTRAL') {
                const cancelBtn = document.createElement('button');
                cancelBtn.classList.add('bg-gray-500', 'hover:bg-gray-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
                cancelBtn.textContent = 'Cancel';
                cancelBtn.addEventListener('click', () => updateSeatStatus(user.id, 'NEUTRAL'));
                userActionsDiv.appendChild(cancelBtn);
            }

            // Call to Speak (President only)
            if (currentUser && currentUser.role === 'PRESIDENT' && user.seatStatus !== 'SPEAKING') {
                const callToSpeakBtn = document.createElement('button');
                callToSpeakBtn.classList.add('bg-green-500', 'hover:bg-green-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
                callToSpeakBtn.textContent = 'Call to Speak';
                callToSpeakBtn.addEventListener('click', () => updateSeatStatus(user.id, 'SPEAKING'));
                userActionsDiv.appendChild(callToSpeakBtn);
            }
        } else if (currentUser && currentUser.role === 'PRESIDENT') {
            // President controlling other user
            if (user.seatStatus !== 'NEUTRAL' && user.seatStatus !== 'SPEAKING') {
                const callToSpeakBtn = document.createElement('button');
                callToSpeakBtn.classList.add('bg-green-500', 'hover:bg-green-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
                callToSpeakBtn.textContent = 'Call to Speak';
                callToSpeakBtn.addEventListener('click', () => updateSeatStatus(user.id, 'SPEAKING'));
                userActionsDiv.appendChild(callToSpeakBtn);
            }
            if (user.seatStatus !== 'NEUTRAL') {
                const cancelBtn = document.createElement('button');
                cancelBtn.classList.add('bg-gray-500', 'hover:bg-gray-600', 'text-white', 'py-1', 'px-2', 'rounded', 'text-xs');
                cancelBtn.textContent = 'Cancel';
                cancelBtn.addEventListener('click', () => updateSeatStatus(user.id, 'NEUTRAL'));
                userActionsDiv.appendChild(cancelBtn);
            }
        }
    } // This is the closing brace for if (userActionsDiv)
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
            headers: getHeadersWithCsrf(),
            body: JSON.stringify(payload)
        });

        if (response.ok) {
            const updatedUser = await response.json(); // Get updated user from response
            if (updatedUser) {
                addOrUpdateSeat(updatedUser); // Update UI with returned user data for the current client
                // Client-side broadcast removed; server now handles broadcasting to all clients.
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
            headers: getHeadersWithCsrf(),
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
                const responseData = await response.json(); // Parse JSON response
                csrfToken = responseData.csrfToken; // Store CSRF token
                showAlert('Login successful!', 'success');
                await setupAuthenticatedSession(true); // Call the correct setup function
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
            headers: getHeadersWithCsrf()
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
                headers: getHeadersWithCsrf()
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
    const title = newProposalTitle.value;
    const party = newProposalParty.value.trim();
    // Read new category and vote requirement values
    const category = newProposalCategory.value; // "normal", "priority", or "constitutional"
    const voteRequirement = newProposalVoteRequirement.value;
    const type = newProposalAssociationType.value.trim();
    const assProposal = newAssociatedProposal.value.trim();

    if (!title || !party) {
        showAlert('Proposal title and proposing party cannot be empty.', 'warning');
        return;
    }

    try {
        const response = await fetch('/api/proposals', {
            method: 'POST',
            headers: getHeadersWithCsrf(),
            body: JSON.stringify({
                title,
                party,
                priority: (category === 'priority'),
                constitutional: (category === 'constitutional'),
                type,
                assProposal,
                voteRequirement
            })
        });
        if (response.ok) {
            showAlert('Proposal added successfully!', 'success');
            await fetchProposals(); // Add this line to refresh
            newProposalTitle.value = '';
            newProposalParty.value = '';
            newProposalCategory.value = 'normal';
            newProposalVoteRequirement.value = 'Rel';
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

endConstitutionalVotingButton.addEventListener('click', async () => {
    if (!confirm('Are you sure you want to end all constitutional proposal voting?')) return;
    try {
        const response = await fetch('/api/proposals/end-voting-constitutional', {
            method: 'POST',
            headers: getHeadersWithCsrf()
        });
        if (response.ok) {
            showAlert('Constitutional voting ended successfully. Votes counted.', 'success');
            await fetchProposals();
        } else {
            const errorText = await response.text();
            showAlert(`Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error ending constitutional voting:', error);
        showAlert('An error occurred while ending constitutional voting.', 'error');
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
            headers: getHeadersWithCsrf(),
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
            headers: getHeadersWithCsrf()
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
            headers: getHeadersWithCsrf()
        });

        const response1 = await fetch('/api/end-session', {
            method: 'POST',
            headers: getHeadersWithCsrf()
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
            headers: getHeadersWithCsrf()
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
            headers: getHeadersWithCsrf()
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
            headers: getHeadersWithCsrf()
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

function handleQueueUpdate(queue) {
    console.log('Queue update received, rendering queue.');
    renderQueue(queue);
}

function handleFineImposed(username, amount) {
    showAlert(`User ${username} has been fined ${amount} units.`, 'warning');
}

function handleEndSession() {
    showAlert('The session has been ended.', 'info');
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
            console.log('Received WebSocket message:', message);

            switch (message.type) {
                case 'seatUpdate':
                    handleSeatUpdate(message.user);
                    break;
                case 'proposalUpdate':
                    console.log('Proposal update received, fetching proposals.');
                    fetchProposals();
                    break;
                case 'proposalDelete':
                    console.log(`Proposal deletion received for ID: ${message.proposalId}, removing from UI.`);
                    removeProposalFromUI(message.proposalId);
                    break;
                case 'proposalsUpdated':
                    console.log('General proposal update received (e.g., voting ended), fetching proposals.');
                    fetchProposals();
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
                case 'queueUpdate':
                    handleQueueUpdate(message.queue);
                    break;
                default:
                    console.warn('Unknown WebSocket message type:', message.type);
            }
        } catch (error) {
            console.error('Error processing WebSocket message:', event.data, error);
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
async function setupAuthenticatedSession(isInitialLoad = false) {
    if (!currentUser) {
        currentUser = await fetchUserInfo();
    }

    if (currentUser) {
        if (authContainer) authContainer.classList.add('hidden');
        if (mainContainer) mainContainer.classList.remove('hidden');

        if (presidentActions) {
            if (currentUser.role === 'PRESIDENT') {
                presidentActions.classList.remove('hidden');
            } else {
                presidentActions.classList.add('hidden');
            }
        }

        // Hide or show the "Stupid?" columns based on role
        const constitutionalStupidHeader = document.getElementById('constitutional-stupid-header'); // Get the element
        if (currentUser.role === 'PRESIDENT') {
            if (normalStupidHeader) normalStupidHeader.classList.remove('hidden');
            if (priorityStupidHeader) priorityStupidHeader.classList.remove('hidden');
            if (constitutionalStupidHeader) constitutionalStupidHeader.classList.remove('hidden'); // Show if president
            if (queueActionsHeader) queueActionsHeader.classList.remove('hidden'); // Show queue actions header for president
        } else {
            if (normalStupidHeader) normalStupidHeader.classList.add('hidden');
            if (priorityStupidHeader) priorityStupidHeader.classList.add('hidden');
            if (constitutionalStupidHeader) constitutionalProposalsTable.classList.add('hidden'); // Hide if not president
            if (queueActionsHeader) queueActionsHeader.classList.add('hidden'); // Hide queue actions header for non-president
        }

        await checkBreakStatus();
        await fetchProposals();
        await fetchUsers();
        await fetchQueue(); // Fetch queue on session setup

        if (ws === null || ws.readyState === WebSocket.CLOSED) {
             initializeWebSocket();
        }

        // Polling is removed - rely on initial fetch and WebSockets
        // if (isInitialLoad) {
        //     startPolling();
        // }
    } else {
        // Not authenticated or failed to fetch user info
        if (authContainer) authContainer.classList.remove('hidden');
        if (mainContainer) mainContainer.classList.add('hidden');
        if (presidentActions) presidentActions.classList.add('hidden');
        resetApp(); // Ensure app is fully reset to auth state
    }
}

function resetApp() {
    if (mainContainer) mainContainer.classList.add('hidden');
    if (authContainer) authContainer.classList.remove('hidden');
    if (loginForm) loginForm.reset();
    if (registerForm) registerForm.reset();

    if (proposalsTable) proposalsTable.replaceChildren();
    // Correctly target the tbody for priority proposals
    const priorityProposalsTableBody = document.getElementById('priority-proposals-table-body');
    if (priorityProposalsTableBody) priorityProposalsTableBody.replaceChildren();

    const constitutionalProposalsTableBody = document.getElementById('constitutional-proposals-table-body');
    if (constitutionalProposalsTableBody) constitutionalProposalsTableBody.replaceChildren();

    const seatLayout = document.getElementById('seat-layout');
    if (seatLayout) seatLayout.replaceChildren();

    // NEW: Clear queue table on reset and hide section
    if (parliamentaryQueueTableBody) parliamentaryQueueTableBody.replaceChildren();


    if (presidentActions) presidentActions.classList.add('hidden');

    if (ws) {
        ws.onclose = null; // Prevent automatic reconnection on manual logout/reset
        ws.close(); 
        ws = null;
    }
    csrfToken = null; // Clear CSRF token on app reset/logout
    // Stop polling - No longer needed as polling functions are removed
}

async function checkAuthentication() {
    currentUser = await fetchUserInfo(); 
    // Only proceed to setup if fetchUserInfo succeeded
    if (currentUser) {
        await setupAuthenticatedSession(true); // Pass true for initial load setup
    } else {
        // Stay on auth screen if user info fetch failed (e.g., 401)
        resetApp(); // Ensure clean state showing login/register
    }
}

/* ----------  draggable queue window  ---------- */
(function makeQueueWindowDraggable () {
    /* drag-enable ---------------------------------- */
    if (queueWindow && queueWindowHandle) {
        makeDraggable(queueWindowHandle, queueWindow);
    }

    /* collapse / expand ---------------------------- */
    if (queueCollapseButton && queueWindow) {
        let collapsed = false;
        queueCollapseButton.addEventListener('click', () => {
            collapsed = !collapsed;
            queueWindow
                .querySelector('.overflow-x-auto')
                .classList.toggle('hidden', collapsed);

            queueCollapseButton.textContent = collapsed ? '+' : '−';
        });
    }
})();   //  ←—--- add this: closes the brace AND immediately invokes the function



window.addEventListener('DOMContentLoaded', () => {
    if (loginTab && registerTab) {
        loginTab.addEventListener('click', () => switchTab('login'));
        registerTab.addEventListener('click', () => switchTab('register'));
    }
    // Add event listener for the proposal association type dropdown
    if (newProposalAssociationType) {
        newProposalAssociationType.addEventListener('change', controlAssProposal);
    }

    // NEW: Add event listener for Request to Speak button
    if (requestSpeakButton) {
        requestSpeakButton.addEventListener('click', requestToSpeak);
    }

    checkAuthentication(); // Check auth on load
});

if (window.location.pathname.endsWith('/admin.html')) {
    initializeAdminDashboard();
}

async function initializeAdminDashboard() {
    currentUser = await fetchUserInfo();
    if (!currentUser || currentUser.role !== 'PRESIDENT') {
        showAlert('Access denied. Only the president can access this page.', 'error');
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
            headers: getHeadersWithCsrf()
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
    modal.classList.add('modal', 'fixed', 'inset-0', 'flex', 'items-center', 'justify-center', 'z-50', 'bg-black', 'bg-opacity-50');

    const modalContent = document.createElement('div');
    modalContent.classList.add('modal-content', 'bg-gray-700', 'p-6', 'rounded');

    const titleHeader = document.createElement('h2');
    titleHeader.classList.add('text-xl', 'font-bold', 'mb-4');
    titleHeader.textContent = 'Edit Proposal';

    const titleLabel = document.createElement('label');
    titleLabel.classList.add('block', 'mb-2');
    titleLabel.textContent = 'Title';

    const titleTextarea = document.createElement('textarea');
    titleTextarea.id = 'edit-proposal-title';
    titleTextarea.rows = 4;
    titleTextarea.classList.add('w-full', 'mb-4', 'px-3', 'py-2', 'rounded', 'bg-gray-800', 'text-white');

    const partyLabel = document.createElement('label');
    partyLabel.classList.add('block', 'mb-2');
    partyLabel.textContent = 'Party';

    const partyInput = document.createElement('input');
    partyInput.type = 'text';
    partyInput.id = 'edit-proposal-party';
    partyInput.classList.add('w-full', 'mb-4', 'px-3', 'py-2', 'rounded', 'bg-gray-800', 'text-white');

    const saveButton = document.createElement('button');
    saveButton.id = 'save-proposal';
    saveButton.classList.add('bg-green-500', 'hover:bg-green-600', 'text-white', 'py-2', 'px-4', 'rounded');
    saveButton.textContent = 'Save';

    const cancelButton = document.createElement('button');
    cancelButton.id = 'close-modal';
    cancelButton.classList.add('bg-gray-500', 'hover:bg-gray-600', 'text-white', 'py-2', 'px-4', 'rounded', 'ml-2');
    cancelButton.textContent = 'Cancel';

    modalContent.appendChild(titleHeader);
    modalContent.appendChild(titleLabel);
    modalContent.appendChild(titleTextarea);
    modalContent.appendChild(partyLabel);
    modalContent.appendChild(partyInput);
    modalContent.appendChild(saveButton);
    modalContent.appendChild(cancelButton);
    modal.appendChild(modalContent);
    document.body.appendChild(modal);

    cancelButton.addEventListener('click', () => modal.remove());

    fetch(`/api/proposals/${proposalId}`)
        .then(response => response.json())
        .then(proposal => {
            titleTextarea.value = proposal.title || '';
            partyInput.value = proposal.party || '';
        })
        .catch(error => {
            console.error('Error fetching proposal data:', error);
            showAlert("Failed to load proposal data.", "error");
            modal.remove();
        });

    saveButton.addEventListener('click', async () => {
        const updatedTitle = titleTextarea.value.trim();
        const updatedParty = partyInput.value.trim();

        await updateProposal(proposalId, updatedTitle, updatedParty);
        modal.remove();
    });
}

async function updateProposal(proposalId, title, party) {
    try {
        const response = await fetch(`/api/proposals/${proposalId}`, {
            method: 'PUT',
            headers: getHeadersWithCsrf(),
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

// New function to remove a proposal row from all tables
function removeProposalFromUI(proposalId) {
    const selectors = [
        `#proposals-table tr[data-proposal-id="${proposalId}"]`,
        `#priority-proposals-table-body tr[data-proposal-id="${proposalId}"]`,
        `#constitutional-proposals-table-body tr[data-proposal-id="${proposalId}"]`
    ];
    selectors.forEach(selector => {
        const row = document.querySelector(selector);
        if (row) {
            row.remove();
        }
    });
    // Potentially re-check if proposal sections should be hidden if they become empty
    checkProposalSectionsVisibility(); 
}

// Helper function to hide sections if their tables are empty
function checkProposalSectionsVisibility() {
    const sections = [
        { sectionId: 'proposals-section', tableBodyId: 'proposals-table' }, // Assuming normal proposals have a wrapper section
        { sectionId: 'priority-proposals-section', tableBodyId: 'priority-proposals-table-body' },
        { sectionId: 'constitutional-proposals-section', tableBodyId: 'constitutional-proposals-table-body' }
    ];
    sections.forEach(item => {
        const section = document.getElementById(item.sectionId);
        const tableBody = document.getElementById(item.tableBodyId);
        if (section && tableBody && tableBody.rows.length === 0) {
            section.classList.add('hidden');
        }
         // Note: Logic to re-show sections if they become non-empty is handled by render functions
    });
}
