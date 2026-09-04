import { AppState } from './state.js';

export const Auth = {
  isAuthenticated() {
    return !!AppState.token && !!AppState.currentUser;
  },

  getCurrentUser() {
    return AppState.currentUser;
  },

  getToken() {
    return AppState.token;
  },

  isContractor() {
    return AppState.currentUser && AppState.currentUser.contractor === true;
  },

  isManager() {
    return AppState.currentUser && (AppState.currentUser.role === 'MANAGER' || AppState.currentUser.role === 'ADMIN');
  },

  isAdmin() {
    return AppState.currentUser && AppState.currentUser.role === 'ADMIN';
  },

  logout() {
    AppState.setUser(null, null, null);
    window.location.reload();
  }
};
