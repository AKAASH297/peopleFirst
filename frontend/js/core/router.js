import { AppState } from './state.js';
import { Auth } from './auth.js';

export const Router = {
  navigate(viewName, params = {}) {
    AppState.currentView = viewName;
    AppState.viewParams = params;
    window.location.hash = viewName;
    AppState.notify();
  },

  getCurrentView() {
    const hash = window.location.hash.replace('#', '');
    if (hash) return hash;
    if (AppState.currentView && AppState.currentView !== 'dashboard') return AppState.currentView;
    if (Auth.isAdmin()) return 'adminLeaves';
    if (Auth.isManager()) return 'approvals';
    return 'dashboard';
  },

  init() {
    window.addEventListener('hashchange', () => {
      const hash = window.location.hash.replace('#', '');
      if (hash && hash !== AppState.currentView) {
        AppState.currentView = hash;
        AppState.notify();
      }
    });

    const initial = window.location.hash.replace('#', '');
    if (initial) {
      AppState.currentView = initial;
    } else {
      AppState.currentView = this.getCurrentView();
    }
  }
};
