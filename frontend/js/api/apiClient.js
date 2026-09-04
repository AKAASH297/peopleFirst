import { AppState } from '../core/state.js';

const BASE_URL = 'http://localhost:8080';

let isRefreshing = false;

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

    // Handle session expiration (401 Unauthorized or 403 Forbidden with stale session)
    const isAuthEndpoint = endpoint.includes('/api/auth/login') || endpoint.includes('/api/auth/refresh');
    if ((response.status === 401 || response.status === 403) && !isAuthEndpoint) {
      if (AppState.refreshToken && !isRefreshing) {
        isRefreshing = true;
        try {
          const refreshResp = await fetch(`${BASE_URL}/api/auth/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken: AppState.refreshToken })
          });

          if (refreshResp.ok) {
            const refreshData = await refreshResp.json();
            AppState.setUser(AppState.user, refreshData.accessToken, refreshData.refreshToken || AppState.refreshToken);
            isRefreshing = false;

            // Retry original request with new token
            headers['Authorization'] = `Bearer ${refreshData.accessToken}`;
            const retryResp = await fetch(url, { ...options, headers });
            const retryData = await retryResp.json().catch(() => null);

            if (!retryResp.ok) {
              const err = new Error(retryData && retryData.message ? retryData.message : `HTTP error ${retryResp.status}`);
              err.status = retryResp.status;
              err.data = retryData;
              throw err;
            }
            return retryData;
          } else {
            isRefreshing = false;
            AppState.setUser(null, null, null);
            window.location.reload();
            return null;
          }
        } catch (refreshErr) {
          isRefreshing = false;
          AppState.setUser(null, null, null);
          window.location.reload();
          return null;
        }
      } else if (!AppState.refreshToken) {
        AppState.setUser(null, null, null);
        window.location.reload();
        return null;
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
