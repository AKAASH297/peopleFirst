import { AppState } from '../core/state.js';

const BASE_URL = 'http://localhost:8080';

export async function apiRequest(endpoint, options = {}) {
  const url = `${BASE_URL}${endpoint}`;
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  if (AppState.token) {
    headers['Authorization'] = `Bearer ${AppState.token}`;
  }

  const config = {
    ...options,
    headers
  };

  try {
    const response = await fetch(url, config);

    if (response.status === 401) {
      // If unauthorized on protected route, log out
      if (!endpoint.includes('/api/auth/login')) {
        AppState.setUser(null, null, null);
        window.location.reload();
      }
    }

    const data = await response.json().catch(() => null);

    if (!response.ok) {
      const errorMessage = data && data.message ? data.message : `HTTP error ${response.status}`;
      const err = new Error(errorMessage);
      err.status = response.status;
      err.data = data;
      throw err;
    }

    return data;
  } catch (error) {
    console.error(`API Error [${endpoint}]:`, error);
    throw error;
  }
}
