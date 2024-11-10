// admin.js

// ======================
// DOM Elements
// ======================
const authContainer = document.getElementById('auth-container');
const adminContainer = document.getElementById('admin-container');

const loginForm = document.getElementById('login-form');
const registerForm = document.getElementById('register-form');
const loginTab = document.getElementById('login-tab');
const registerTab = document.getElementById('register-tab');

const authAlertContainer = document.getElementById('auth-alert');
const authAlertMessage = document.getElementById('auth-alert-message');

const adminAlertContainer = document.getElementById('admin-alert');
const adminAlertMessage = document.getElementById('admin-alert-message');

const usersTableBody = document.querySelector('#users-table tbody');
const submitUserUpdatesButton = document.getElementById('submit-user-updates');

// ======================
// Utility Functions
// ======================

/**
 * Displays an alert message.
 * @param {HTMLElement} container - The alert container element.
 * @param {HTMLElement} messageElement - The alert message element.
 * @param {string} message - The message to display.
 * @param {string} type - The type of alert ('success', 'error', 'warning').
 */
function showAlert(container, messageElement, message, type = 'success') {
    if (!container || !messageElement) return; // Safety check

    messageElement.innerText = message;
    container.className = ''; // Reset classes

    // Set classes based on alert type
    if (type === 'success') {
        container.classList.add('block', 'px-4', 'py-3', 'rounded', 'shadow-lg', 'mb-4', 'bg-green-100', 'border', 'border-green-400', 'text-green-700');
    } else if (type === 'error') {
        container.classList.add('block', 'px-4', 'py-3', 'rounded', 'shadow-lg', 'mb-4', 'bg-red-100', 'border', 'border-red-400', 'text-red-700');
    } else if (type === 'warning') {
        container.classList.add('block', 'px-4', 'py-3', 'rounded', 'shadow-lg', 'mb-4', 'bg-yellow-100', 'border', 'border-yellow-400', 'text-yellow-700');
    }

    // Automatically hide after 5 seconds
    setTimeout(() => {
        container.classList.add('hidden');
    }, 5000);
}

/**
 * Switches between Login and Register tabs.
 * @param {string} tab - The tab to switch to ('login' or 'register').
 */
function switchTab(tab) {
    if (tab === 'login') {
        loginForm.classList.remove('hidden');
        registerForm.classList.add('hidden');
        loginTab.classList.remove('text-gray-400', 'border-transparent', 'hover:border-gray-600');
        loginTab.classList.add('text-blue-400', 'border-blue-400');
        registerTab.classList.remove('text-blue-400', 'border-blue-400');
        registerTab.classList.add('text-gray-400', 'border-transparent', 'hover:border-gray-600');
    } else if (tab === 'register') {
        registerForm.classList.remove('hidden');
        loginForm.classList.add('hidden');
        registerTab.classList.remove('text-gray-400', 'border-transparent', 'hover:border-gray-600');
        registerTab.classList.add('text-blue-400', 'border-blue-400');
        loginTab.classList.remove('text-blue-400', 'border-blue-400');
        loginTab.classList.add('text-gray-400', 'border-transparent', 'hover:border-gray-600');
    }
}

/**
 * Fetches all users from the backend.
 */
async function fetchAllUsers() {
    try {
        const response = await fetch('/api/users', {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' }
        });
        if (response.ok) {
            const users = await response.json();
            renderUsersTable(users);
        } else {
            console.warn('Failed to fetch users.');
            showAlert(adminAlertContainer, adminAlertMessage, 'Failed to fetch users.', 'error');
        }
    } catch (error) {
        console.error('Error fetching users:', error);
        showAlert(adminAlertContainer, adminAlertMessage, 'An error occurred while fetching users.', 'error');
    }
}

/**
 * Renders the users in the users table.
 * @param {Array} users - Array of user objects.
 */
function renderUsersTable(users) {
    usersTableBody.innerHTML = ''; // Clear existing rows
    users.forEach(user => {
        const row = document.createElement('tr');
        row.dataset.userid = user.id;

        const cellUsername = document.createElement('td');
        cellUsername.classList.add('px-4', 'py-2', 'border');
        cellUsername.textContent = user.username;

        const cellElectoralStrength = document.createElement('td');
        cellElectoralStrength.classList.add('px-4', 'py-2', 'border');
        const electoralStrengthInput = document.createElement('input');
        electoralStrengthInput.type = 'number';
        electoralStrengthInput.classList.add('electoral-strength', 'w-full', 'px-2', 'py-1', 'rounded', 'bg-gray-800', 'text-white');
        electoralStrengthInput.value = user.electoralStrength || 1;
        cellElectoralStrength.appendChild(electoralStrengthInput);

        const cellPartyAffiliation = document.createElement('td');
        cellPartyAffiliation.classList.add('px-4', 'py-2', 'border');
        const partyAffiliationInput = document.createElement('input');
        partyAffiliationInput.type = 'text';
        partyAffiliationInput.classList.add('party-affiliation', 'w-full', 'px-2', 'py-1', 'rounded', 'bg-gray-800', 'text-white');
        partyAffiliationInput.value = user.partyAffiliation || '';
        cellPartyAffiliation.appendChild(partyAffiliationInput);

        const cellRole = document.createElement('td');
        cellRole.classList.add('px-4', 'py-2', 'border');
        const roleSelect = document.createElement('select');
        roleSelect.classList.add('user-role', 'w-full', 'px-2', 'py-1', 'rounded', 'bg-gray-800', 'text-white');
        ['MEMBER', 'PRESIDENT', 'OTHER_ROLE'].forEach(roleOption => {
            const option = document.createElement('option');
            option.value = roleOption;
            option.textContent = roleOption;
            if (user.role === roleOption) {
                option.selected = true;
            }
            roleSelect.appendChild(option);
        });
        cellRole.appendChild(roleSelect);

        row.appendChild(cellUsername);
        row.appendChild(cellElectoralStrength);
        row.appendChild(cellPartyAffiliation);
        row.appendChild(cellRole);

        usersTableBody.appendChild(row);
    });
}

/**
 * Handles submitting user updates.
 */
async function handleSubmitUserUpdates() {
    const userUpdates = [];
    const rows = usersTableBody.querySelectorAll('tr');
    rows.forEach(row => {
        const userId = row.dataset.userid;
        const electoralStrengthInput = row.querySelector('.electoral-strength');
        const electoralStrength = parseInt(electoralStrengthInput.value.trim(), 10);

        const partyAffiliationInput = row.querySelector('.party-affiliation');
        const partyAffiliation = partyAffiliationInput.value.trim();

        const roleSelect = row.querySelector('.user-role');
        const role = roleSelect.value;

        userUpdates.push({
            id: userId,
            electoralStrength: isNaN(electoralStrength) ? 1 : electoralStrength,
            partyAffiliation: partyAffiliation,
            role: role
        });
    });

    if (userUpdates.length === 0) {
        showAlert(adminAlertContainer, adminAlertMessage, 'No user updates to submit.', 'warning');
        return;
    }

    // Send user updates to the server
    try {
        const response = await fetch('/api/users/update', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(userUpdates)
        });

        if (response.ok) {
            showAlert(adminAlertContainer, adminAlertMessage, 'User updates submitted successfully.', 'success');
            // Optionally, refetch users to reflect updates
            await fetchAllUsers();
        } else {
            const errorText = await response.text();
            showAlert(adminAlertContainer, adminAlertMessage, `Error: ${errorText}`, 'error');
        }
    } catch (error) {
        console.error('Error submitting user updates:', error);
        showAlert(adminAlertContainer, adminAlertMessage, 'An error occurred while submitting user updates.', 'error');
    }
}

/**
 * Handles the Login form submission.
 * @param {Event} e - The form submit event.
 */
async function handleLogin(e) {
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
            showAlert(authAlertContainer, authAlertMessage, 'Login successful!', 'success');
            await initializeAdminDashboard();
        } else {
            const errorText = await response.text();
            showAlert(authAlertContainer, authAlertMessage, errorText || 'Login failed.', 'error');
        }
    } catch (error) {
        console.error('Login error:', error);
        showAlert(authAlertContainer, authAlertMessage, 'An error occurred during login.', 'error');
    }
}

/**
 * Handles the Register form submission.
 * @param {Event} e - The form submit event.
 */
async function handleRegister(e) {
    e.preventDefault();
    const username = document.getElementById('register-username').value.trim();
    const password = document.getElementById('register-password').value.trim();

    if (!username || !password) {
        showAlert(authAlertContainer, authAlertMessage, 'Please fill in all fields.', 'warning');
        return;
    }

    try {
        const response = await fetch('/api/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        if (response.ok) {
            showAlert(authAlertContainer, authAlertMessage, 'Registration successful! Please log in.', 'success');
            registerForm.reset();
            switchTab('login');
        } else {
            const errorText = await response.text();
            showAlert(authAlertContainer, authAlertMessage, errorText || 'Registration failed.', 'error');
        }
    } catch (error) {
        console.error('Registration error:', error);
        showAlert(authAlertContainer, authAlertMessage, 'An error occurred during registration.', 'error');
    }
}

/**
 * Initializes the Admin Dashboard after successful login.
 */
async function initializeAdminDashboard() {
    // Hide the auth container and show the admin container
    authContainer.classList.add('hidden');
    adminContainer.classList.remove('hidden');

    // Fetch and display users
    await fetchAllUsers();
}

// ======================
// Initialization
// ======================

document.addEventListener('DOMContentLoaded', () => {
    // Attach event listener to Login form if it exists
    if (loginForm) {
        loginForm.addEventListener('submit', handleLogin);
    }

    // Attach event listener to Register form if it exists
    if (registerForm) {
        registerForm.addEventListener('submit', handleRegister);
    }

    // Attach event listeners to Tabs if they exist
    if (loginTab && registerTab) {
        loginTab.addEventListener('click', () => switchTab('login'));
        registerTab.addEventListener('click', () => switchTab('register'));
    }

    // Attach event listener to Submit User Updates button if it exists
    if (submitUserUpdatesButton) {
        submitUserUpdatesButton.addEventListener('click', handleSubmitUserUpdates);
    }
});
