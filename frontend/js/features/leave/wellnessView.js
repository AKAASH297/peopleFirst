import { agentApi } from '../../api/agentApi.js';
import { Auth } from '../../core/auth.js';

export const WellnessView = {
  async render() {
    const user = Auth.getCurrentUser();
    return `
      <div class="view-header">
        <div>
          <h2 class="view-title">Wellness & Benefits Concierge</h2>
          <p class="view-subtitle">Curated health, rejuvenation, and corporate perks powered by Kura.</p>
        </div>
      </div>

      <div style="display: flex; flex-direction: column; gap: 2rem;">
        <!-- Amenities -->
        <div>
          <h3 style="font-size: 1.125rem; font-weight: 600; margin-bottom: 1rem;">🌿 On-Campus Wellness Amenities</h3>
          <div id="amenitiesGrid" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 1rem;">
            <div style="color:var(--text-muted);">Loading amenities...</div>
          </div>
        </div>

        <!-- Hospitals -->
        <div>
          <div class="flex justify-between items-center" style="margin-bottom: 1rem;">
            <h3 style="font-size: 1.125rem; font-weight: 600;">🏥 Partner Hospitals & OPD Discounts (${user.baseLocation})</h3>
            <span style="font-size: 0.75rem; color: var(--primary);">Insurance Claim Window: Submit within 90 days</span>
          </div>
          <div id="hospitalsGrid" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 1rem;">
            <div style="color:var(--text-muted);">Loading partner hospitals...</div>
          </div>
        </div>

        <!-- Resorts -->
        <div>
          <h3 style="font-size: 1.125rem; font-weight: 600; margin-bottom: 1rem;">🌴 Partner Resorts & Vacation Getaways</h3>
          <div id="resortsGrid" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 1rem;">
            <div style="color:var(--text-muted);">Loading vacation partners...</div>
          </div>
        </div>
      </div>
    `;
  },

  async attachEvents() {
    const user = Auth.getCurrentUser();

    // 1. Amenities
    try {
      const amenities = await agentApi.getAmenities();
      const grid = document.getElementById('amenitiesGrid');
      if (amenities && amenities.length) {
        grid.innerHTML = amenities.map(a => `
          <div class="card" style="padding: 1.25rem;">
            <div class="flex justify-between items-center" style="margin-bottom: 0.5rem;">
              <span style="font-weight: 600; font-size: 0.9375rem;">${a.name}</span>
              <span class="badge badge-approved" style="font-size: 0.6875rem;">${a.category}</span>
            </div>
            <div style="font-size: 0.8125rem; color: var(--text-muted); margin-bottom: 0.5rem;">
              📍 ${a.location} • ⏰ ${a.timing}
            </div>
            <div style="font-size: 0.8125rem; color: var(--text-sub); line-height: 1.4;">
              ${a.description}
            </div>
          </div>
        `).join('');
      }
    } catch (e) {
      console.error(e);
    }

    // 2. Hospitals
    try {
      const hospitals = await agentApi.getHospitals(user.baseLocation);
      const grid = document.getElementById('hospitalsGrid');
      if (hospitals && hospitals.length) {
        grid.innerHTML = hospitals.map(h => `
          <div class="card" style="padding: 1.25rem; border-left: 4px solid var(--secondary);">
            <div style="font-weight: 600; font-size: 0.9375rem; color: var(--text-main);">${h.name}</div>
            <div style="font-size: 0.75rem; color: var(--text-muted); margin: 0.25rem 0 0.75rem;">${h.address} • ${h.city}</div>
            <div style="font-size: 0.8125rem; display: flex; flex-direction: column; gap: 0.25rem; background: #f8fafc; padding: 0.5rem; border-radius: 6px;">
              <div style="color: #0369a1; font-weight: 500;">🩺 ${h.opdDiscount}</div>
              <div style="color: #0369a1; font-weight: 500;">🔬 ${h.labTestDiscount}</div>
            </div>
            <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.5rem;">
              📞 Contact: ${h.contactNumber}
            </div>
          </div>
        `).join('');
      }
    } catch (e) {
      console.error(e);
    }

    // 3. Resorts
    try {
      const resorts = await agentApi.getResorts();
      const grid = document.getElementById('resortsGrid');
      if (resorts && resorts.length) {
        grid.innerHTML = resorts.map(r => `
          <div class="card" style="padding: 1.25rem; border-top: 4px solid var(--primary);">
            <div class="flex justify-between items-center">
              <span style="font-weight: 600; font-size: 0.9375rem;">${r.name}</span>
              <span class="badge" style="background:#fef3c7; color:#92400e;">${r.type}</span>
            </div>
            <div style="font-size: 0.75rem; color: var(--text-muted); margin: 0.25rem 0 0.5rem;">📍 ${r.destination}</div>
            <div style="font-size: 0.875rem; font-weight: 600; color: var(--primary); margin: 0.5rem 0;">
              ${r.discount}
            </div>
            <div style="font-size: 0.75rem; background: #f1f5f9; padding: 4px 8px; border-radius: 4px; display: inline-block;">
              Promo Code: <code>${r.couponCode}</code>
            </div>
          </div>
        `).join('');
      }
    } catch (e) {
      console.error(e);
    }
  }
};
