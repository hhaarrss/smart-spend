import axios from 'axios';

// Variable to store the JWT token in memory
let inMemoryToken = null;

/**
 * Sets the active in-memory access token.
 * Called by AuthContext on login, reload, or logout.
 * 
 * @param {string|null} token - JWT bearer token or null.
 */
export const setInMemoryToken = (token) => {
  inMemoryToken = token;
};

// Create the configured Axios client instance
const api = axios.create({
  baseURL: 'http://localhost:8000',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to automatically attach the JWT token
api.interceptors.request.use(
  (config) => {
    if (inMemoryToken) {
      config.headers.Authorization = `Bearer ${inMemoryToken}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export const authService = {
  /**
   * Registers a new user account.
   */
  register: async (fullName, email, password) => {
    const response = await api.post('/auth/register', {
      full_name: fullName,
      email,
      password,
    });
    return response.data;
  },

  /**
   * Authenticates user using standard OAuth2 URL-encoded request body.
   */
  login: async (email, password) => {
    const formData = new URLSearchParams();
    formData.append('username', email);
    formData.append('password', password);
    const response = await api.post('/auth/login', formData, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    });
    return response.data;
  },
  /**
   * Fetches current authenticated user profile.
   */
  getMe: async () => {
    const response = await api.get('/auth/me');
    return response.data;
  },
};

export const transactionService = {
  /**
   * Lists transactions with conditional filters.
   */
  listTransactions: async (filters = {}) => {
    const params = {};
    if (filters.category) params.category = filters.category;
    if (filters.start_date) params.start_date = filters.start_date;
    if (filters.end_date) params.end_date = filters.end_date;
    if (filters.type) params.type = filters.type;
    if (filters.review_status) params.review_status = filters.review_status;

    const response = await api.get('/transactions/', { params });
    return response.data;
  },

  /**
   * Manually logs a new transaction entry.
   */
  createTransaction: async (txData) => {
    // Schema matches TransactionCreate
    const response = await api.post('/transactions/', {
      amount: parseFloat(txData.amount),
      type: txData.type, // 'debit' or 'credit'
      category: txData.category,
      merchant: txData.merchant || null,
      bank: txData.bank || null,
      account_last4: txData.account_last4 || null,
      date: txData.date, // ISO Date string
      source: txData.source || 'manual',
    });
    return response.data;
  },

  /**
   * Re-categorizes a transaction and records user learning feedback.
   */
  recategorizeTransaction: async (transactionId, newCategory, subcategory = null, merchantRaw = null, displayName = null) => {
    const response = await api.patch(`/transactions/${transactionId}/recategorize`, {
      transaction_id: transactionId,
      merchant_raw: merchantRaw || 'Unknown',
      new_category: newCategory,
      subcategory: subcategory,
      display_name: displayName,
    });
    return response.data;
  },

  /**
   * Parses raw SMS content and ingests the transaction if unique.
   */
  ingestSMS: async (rawSMS, sender) => {
    const response = await api.post('/transactions/ingest-sms', {
      raw_sms: rawSMS,
      sender: sender,
    });
    return response.data; // Returns { success, transaction, message }
  },

  /**
   * Retrieves category totals for a given month (YYYY-MM).
   */
  getSummary: async (month) => {
    const response = await api.get('/transactions/summary', {
      params: { month },
    });
    return response.data; // Returns Dict[str, float]
  },
};

export const budgetService = {
  /**
   * Fetches all registered budgets for the current user.
   */
  getBudgets: async () => {
    const response = await api.get('/budget/');
    return response.data;
  },

  /**
   * Configures or updates a category-wide monthly budget limit.
   */
  setBudget: async (budgetData) => {
    const response = await api.post('/budget/', {
      category: budgetData.category,
      monthly_limit: parseFloat(budgetData.monthly_limit),
      alert_at_percent: parseFloat(budgetData.alert_at_percent || 80.0),
      is_family_limit: !!budgetData.is_family_limit,
    });
    return response.data;
  },
};

export const insightService = {
  /**
   * Fetches unified spending insights summary.
   */
  getSummary: async () => {
    const response = await api.get('/insights/summary');
    return response.data;
  },
};

export default api;
