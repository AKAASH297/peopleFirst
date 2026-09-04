// Central Application State
export const AppState = {
  token: localStorage.getItem('peoplefirst_token') || null,
  refreshToken: localStorage.getItem('peoplefirst_refresh_token') || null,
  currentUser: JSON.parse(localStorage.getItem('peoplefirst_user') || 'null'),
  currentView: 'dashboard',
  balances: [],
  leaves: [],
  policies: null,
  amenities: [],
  listeners: [],

  subscribe(listener) {
    this.listeners.push(listener);
  },

  notify() {
    this.listeners.forEach(cb => cb(this));
  },

  setUser(user, token, refreshToken) {
    this.currentUser = user;
    this.token = token;
    this.refreshToken = refreshToken;
    if (token) {
      localStorage.setItem('peoplefirst_token', token);
      localStorage.setItem('peoplefirst_user', JSON.stringify(user));
      if (refreshToken) localStorage.setItem('peoplefirst_refresh_token', refreshToken);
    } else {
      localStorage.removeItem('peoplefirst_token');
      localStorage.removeItem('peoplefirst_user');
      localStorage.removeItem('peoplefirst_refresh_token');
    }
    this.notify();
  },

  setCurrentView(view) {
    this.currentView = view;
    this.notify();
  }
};
