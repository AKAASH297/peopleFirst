import { agentApi } from '../../api/agentApi.js';
import { Auth } from '../../core/auth.js';

export const WellnessView = {
  async render() {
    const user = Auth.getCurrentUser();
    return `
      <div class="view-header">
        <div>
          <h2 class="view-title">Wellness & Benefits Concierge</h2>
          <p class="view-subtitle">Autonomous health monitoring, on-campus amenities, and corporate perks powered by Kura AI.</p>
        </div>
      </div>

      <div style="display: flex; flex-direction: column; gap: 2rem;">
        <!-- Weekly Wellbeing Status Monitor -->
        <div id="weeklyStatusCard" class="card" style="padding: 1.5rem; border-left: 5px solid var(--primary); background: #f8fafc;">
          <div style="color: var(--text-muted); font-size: 0.875rem;">Loading weekly wellbeing status...</div>
        </div>

        <!-- 9 Core Amenities -->
        <div>
          <div class="flex justify-between items-center" style="margin-bottom: 1rem;">
            <div>
              <h3 style="font-size: 1.125rem; font-weight: 600;">🌿 On-Campus Wellness Amenities</h3>
              <p style="font-size: 0.8125rem; color: var(--text-muted);">Explore health, mindfulness, fitness, and relaxation facilities across buildings.</p>
            </div>
          </div>
          <div id="amenitiesGrid" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 1rem;">
            <div style="color:var(--text-muted);">Loading amenities...</div>
          </div>
        </div>

        <!-- Partner Hospitals -->
        <div>
          <div class="flex justify-between items-center" style="margin-bottom: 1rem;">
            <div>
              <h3 style="font-size: 1.125rem; font-weight: 600;">🏥 Partner Network Hospitals (${user.baseLocation})</h3>
              <p style="font-size: 0.8125rem; color: var(--text-muted);">Corporate OPD and diagnostic test discounts at tied healthcare institutions.</p>
            </div>
            <a href="https://insurance.peoplefirst.internal/claims" target="_blank" rel="noopener noreferrer" class="btn btn-outline btn-sm">
              📄 Insurance Claims Portal (90-Day Window) &rarr;
            </a>
          </div>
          <div id="hospitalsGrid" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 1rem;">
            <div style="color:var(--text-muted);">Loading partner hospitals...</div>
          </div>
        </div>

        <!-- Partner Resorts -->
        <div>
          <div class="flex justify-between items-center" style="margin-bottom: 1rem;">
            <div>
              <h3 style="font-size: 1.125rem; font-weight: 600;">🌴 Partner Resorts & Corporate Vacation Getaways</h3>
              <p style="font-size: 0.8125rem; color: var(--text-muted);">Exclusive corporate tariffs for in-city staycations and weekend retreats.</p>
            </div>
            <button id="emailVacationPerksBtn" class="btn btn-secondary btn-sm">
              📧 Email Me Vacation Perks
            </button>
          </div>
          <div id="resortsGrid" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 1rem;">
            <div style="color:var(--text-muted);">Loading vacation partners...</div>
          </div>
        </div>

        <!-- Corporate Volunteering CSR Initiatives -->
        <div class="card" style="padding: 1.5rem; border-top: 4px solid #10b981;">
          <div class="flex justify-between items-center" style="margin-bottom: 0.75rem;">
            <div class="flex items-center gap-2">
              <span style="font-size: 1.25rem;">🤝</span>
              <h3 style="font-size: 1.125rem; font-weight: 600; color: #065f46;">Corporate CSR Volunteering Chapters</h3>
            </div>
            <span class="badge" style="background:#d1fae5; color:#065f46;">Participate under Company Banner</span>
          </div>
          <p style="font-size: 0.875rem; color: var(--text-sub); margin-bottom: 1rem; line-height: 1.5;">
            Giving back fuels collective purpose and wellbeing! Join our active corporate volunteering chapters. Complete your initiative to be featured on the company intranet banner.
          </p>
          <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 0.75rem;">
            <div style="padding: 0.75rem; background: #f0fdf4; border-radius: 6px; border: 1px solid #bbf7d0;">
              <div style="font-weight: 600; color: #166534; font-size: 0.875rem;">🌱 Green Earth Afforestation</div>
              <div style="font-size: 0.75rem; color: #15803d; margin-top: 2px;">Tree plantation drives & urban greening</div>
            </div>
            <div style="padding: 0.75rem; background: #f0fdf4; border-radius: 6px; border: 1px solid #bbf7d0;">
              <div style="font-weight: 600; color: #166534; font-size: 0.875rem;">💻 Code & Tech Literacy for Youth</div>
              <div style="font-size: 0.75rem; color: #15803d; margin-top: 2px;">Weekend programming mentoring for underprivileged students</div>
            </div>
            <div style="padding: 0.75rem; background: #f0fdf4; border-radius: 6px; border: 1px solid #bbf7d0;">
              <div style="font-weight: 600; color: #166534; font-size: 0.875rem;">🍲 Community Food Bank Support</div>
              <div style="font-size: 0.75rem; color: #15803d; margin-top: 2px;">Meal distribution and community kitchen volunteering</div>
            </div>
            <div style="padding: 0.75rem; background: #f0fdf4; border-radius: 6px; border: 1px solid #bbf7d0;">
              <div style="font-weight: 600; color: #166534; font-size: 0.875rem;">🐾 Paws & Care Animal Rescue</div>
              <div style="font-size: 0.75rem; color: #15803d; margin-top: 2px;">Animal shelter days, foster support, and care drives</div>
            </div>
          </div>
          <div style="margin-top: 1rem; display: flex; justify-content: flex-end;">
            <a href="https://csr.peoplefirst.internal/enroll" target="_blank" rel="noopener noreferrer" class="btn btn-outline btn-sm" style="color: #065f46; border-color: #059669;">
              Enroll in CSR Chapter &rarr;
            </a>
          </div>
        </div>
      </div>
    `;
  },

  async attachEvents() {
    const user = Auth.getCurrentUser();

    // 0. Weekly Wellbeing Status Monitor
    try {
      const status = await agentApi.getWeeklyWellbeingStatus();
      const statusCard = document.getElementById('weeklyStatusCard');
      if (status && statusCard) {
        let statusBadge = '<span class="badge badge-approved">🟢 Healthy & Balanced</span>';
        if (status.status === 'RECHARGE_RECOMMENDED') {
          statusBadge = '<span class="badge" style="background:#fef3c7; color:#92400e;">🟡 Recharge Recommended</span>';
        } else if (status.status === 'ACTION_REQUIRED') {
          statusBadge = '<span class="badge" style="background:#e0f2fe; color:#0369a1;">🔵 Health Action Follow-up</span>';
        }

        statusCard.innerHTML = `
          <div class="flex justify-between items-center" style="margin-bottom: 0.75rem;">
            <div class="flex items-center gap-2">
              <span style="font-size: 1.5rem;">📊</span>
              <div>
                <h3 style="font-size: 1.0625rem; font-weight: 600; margin: 0;">Weekly Wellbeing & Benefits Monitor</h3>
                <div style="font-size: 0.75rem; color: var(--text-muted);">Continuous autonomous health & rest tracking for <strong>${status.employeeName}</strong> (${status.baseLocation})</div>
              </div>
            </div>
            <div>${statusBadge}</div>
          </div>
          <p style="font-size: 0.875rem; color: var(--text-main); margin-bottom: 1rem; line-height: 1.5;">
            ${status.summary}
          </p>
          <div style="display: flex; gap: 1rem; flex-wrap: wrap; font-size: 0.8125rem;">
            <div style="background: #fff; padding: 0.5rem 0.875rem; border-radius: 6px; border: 1px solid var(--border);">
              🗓️ Leaves Taken (Last 30 Days): <strong>${status.leavesTakenThisMonth}</strong> day(s)
            </div>
            <div style="background: #fff; padding: 0.5rem 0.875rem; border-radius: 6px; border: 1px solid var(--border);">
              🌴 Leaves Taken (Last 90 Days): <strong>${status.leavesTakenLastQuarter}</strong> day(s)
            </div>
          </div>
          ${status.recentSickLeave && status.opdClaimReminder ? `
            <div class="alert alert-info" style="margin-top: 1rem; padding: 0.75rem 1rem; font-size: 0.8125rem;">
              <span>🩺</span>
              <div>
                <strong>OPD & Hospitalization Reimbursement:</strong> ${status.opdClaimReminder}
                <a href="${status.insuranceClaimsPortalUrl}" target="_blank" rel="noopener noreferrer" style="color: #0284c7; text-decoration: underline; margin-left: 0.5rem;">Submit Claim &rarr;</a>
              </div>
            </div>
          ` : ''}
        `;
      }
    } catch (e) {
      console.error(e);
    }

    // 1. Amenities Catalog (9 items)
    try {
      const amenities = await agentApi.getAmenities();
      const grid = document.getElementById('amenitiesGrid');
      if (amenities && amenities.length) {
        const iconMap = {
          'Fitness': '🏋️',
          'Healthcare': '🩺',
          'Mental Wellbeing': '🧠',
          'Advisory': '⚖️',
          'Benefits': '🛡️',
          'Fitness & Mindfulness': '🧘',
          'Recreation & Dance': '💃',
          'Recreation': '🎱',
          'Relaxation': '🛋️'
        };

        grid.innerHTML = amenities.map(a => `
          <div class="card" style="padding: 1.25rem; display: flex; flex-direction: column; justify-content: space-between;">
            <div>
              <div class="flex justify-between items-center" style="margin-bottom: 0.5rem;">
                <div class="flex items-center gap-2">
                  <span style="font-size: 1.25rem;">${iconMap[a.category] || '🌿'}</span>
                  <span style="font-weight: 600; font-size: 0.9375rem;">${a.name}</span>
                </div>
                <span class="badge badge-approved" style="font-size: 0.6875rem;">${a.category}</span>
              </div>
              <div style="font-size: 0.75rem; color: var(--text-muted); margin-bottom: 0.5rem;">
                📍 ${a.location} • ⏰ ${a.timing}
              </div>
              <div style="font-size: 0.8125rem; color: var(--text-sub); line-height: 1.4;">
                ${a.description}
              </div>
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
            <div style="font-size: 0.75rem; color: var(--text-muted); margin: 0.25rem 0 0.75rem;">📍 ${h.address} • ${h.city}</div>
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
              Corporate Promo Code: <code>${r.couponCode}</code>
            </div>
          </div>
        `).join('');
      }
    } catch (e) {
      console.error(e);
    }

    // 4. Email vacation perks button
    document.getElementById('emailVacationPerksBtn')?.addEventListener('click', async () => {
      try {
        await agentApi.sendVacationEmail();
        alert(`📧 Corporate resort discounts and vacation perks dispatched to your email (${user.email})!`);
      } catch (e) {
        console.error(e);
        alert('Could not dispatch vacation perks email. Please try again.');
      }
    });
  }
};

