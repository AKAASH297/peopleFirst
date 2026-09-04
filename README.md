# peopleFirst — Leave Management & Wellbeing Concierge Platform

An enterprise-grade leave management and employee wellbeing concierge application built for organizations prioritizing human-centric work culture. Powered by **Kura**, an autonomous AI concierge that couples strict organizational policy compliance with proactive wellness care.

---

## 1. Architecture & Modular Monolith Design

The platform follows a clean four-layer architecture with zero distributed system overhead:

```
Frontend (Web / Agent Portal)  ↔  AI Agent (Kura)  ↔  Spring Boot Backend  ↔  PostgreSQL / H2
```

### Modular Backend Structure
The backend is structured as a **modular monolith** organized strictly by business domain features:

```text
backend/src/main/java/com/peoplefirst/
├── auth/           # JWT security, token lifecycle, CurrentUserProvider
├── user/           # User hierarchy, roles (EMPLOYEE, CONTRACTOR, MANAGER, ADMIN), seed data
├── leave/          # Leave requests, balance tracking, lifecycle state machine
├── policy/         # Deterministic policy validation engine (combinations, cutoffs, notices)
├── approval/       # Approval hierarchies, manager scoping, self-approval prevention
├── ticket/         # Support tickets for late requests, errors, and post-date adjustments
├── wellbeing/      # Rule-based wellness triggers, amenities catalog, hospital/resort partners
├── agent/          # Kura AI Agent service, intent parsing, grounded execution
└── audit/          # Comprehensive immutable audit log ledger
```

### The Overriding Rule: Backend Identity Resolution
In strict adherence to security requirements, **identity is never trusted from the client or the AI agent**. Every action resolves caller identity via:

$$\text{JWT} \longrightarrow \text{Spring Security} \longrightarrow \text{SecurityContext} \longrightarrow \text{Database Lookup} \longrightarrow \text{Current User}$$

No controller or service accepts caller-supplied `userId` or `role` parameters for authorization decisions.

---

## 2. Tech Stack

- **Backend**: Java 17+, Spring Boot 3.2.5, Spring Security 6, Spring Data JPA / Hibernate, Maven.
- **Database**: PostgreSQL (production profile) with zero-config in-memory H2 (development profile).
- **API Documentation**: OpenAPI 3 / Swagger UI (`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0`).
- **AI Agent**: **Kura** (Latin *cura* = care, stewardship) — dual access via embedded web concierge drawer and standalone CLI client.
- **Frontend**: Vanilla ES6+ Single Page Application with clean modern CSS, feature-driven modules, responsive dashboard, and dedicated contractor chat portal.

---

## 3. Preloaded Demo Accounts

All demo accounts share the default password: **`password123`**

| Username | Full Name | Role | Channel Access | Base Location | Reporting Manager |
|---|---|---|---|---|---|
| `contractor1` | Kavita Nair | CONTRACTOR | **Agent Only** (Web blocked) | Bangalore | Vikram Malhotra (`manager1`) |
| `employee1` | Rohan Verma | EMPLOYEE | Web & Agent | Bangalore | Vikram Malhotra (`manager1`) |
| `employee2` | Ananya Gupta | EMPLOYEE | Web & Agent | Hyderabad | Priya Sen (`manager2`) |
| `manager1` | Vikram Malhotra | MANAGER | Web & Agent | Bangalore | Aditi Sharma (`admin1`) |
| `manager2` | Priya Sen | MANAGER | Web & Agent | Hyderabad | Aditi Sharma (`admin1`) |
| `admin1` | Aditi Sharma | ADMIN | Web & Agent | Bangalore | — |
| `admin2` | Arun Patel | ADMIN | Web & Agent | Bangalore | — |

---

## 4. Channel Access & Contractor Exclusivity

| Role | Webpage (`index.html`) | Agent (`contractor.html` / CLI) |
|---|---|---|
| **Employee** | Yes | Yes |
| **Contractor** | **No (HTTP 403)** | **Yes (Exclusive Channel)** |
| **Manager** | Yes | Yes |
| **Admin** | Yes | Yes |

> Contractors are barred from webpage login under any path. When contractors authenticate with `channel: "AGENT"`, they obtain full self-service capabilities (balance inquiry, leave applications for eligible types, policy reading, and cancellations) exclusively through **Kura**.

---

## 5. Leave Quotas, Rules & Policies

| Leave Type | Employee Quota | Contractor Quota | Combination Rule | Deadline & Documentation Constraints |
|---|---|---|---|---|
| **Casual Leave** | 12 days/yr | **0 (Ineligible)** | Only with WFH | Submit on or before Sunday 23:59:59 of current week |
| **Sick Leave** | 16 days/yr | 16 days/yr | None | Submit on or before 25th. **> 2 days requires medical document** |
| **Paid Leave** | 20 days/yr | 24 days/yr | None | Submit on or before 25th. **Advance notice: `startDate > appliedDate + 2`** |
| **Loss of Pay (LOP)** | 180 days/yr | 30 days/yr | None | Submit on or before 25th of month |
| **WFH** | 24 days/yr | **0 (Ineligible)** | Only with Casual | Submit on or before Sunday 23:59:59 of current week |
| **Maternity** | 182 days/yr | **0 (Ineligible)** | None | Standard notice |
| **Volunteering** | 2 days/yr | **0 (Ineligible)** | None | Triggers corporate CSR chapter enrollment |

*Note: Contractors have zero combination rights and are eligible only for Sick, Paid, and LOP.*

---

## 6. Proactive Wellbeing Concierge (Rule-Based Triggers)

The `wellbeing` module evaluates deterministic, non-blocking suggestions after leave actions:

1. **Sick Leave Trigger**:
   - Prompts if a doctor was consulted.
   - Reminds employee to submit OPD/hospitalization bills within **90 days** for corporate insurance reimbursement.
   - Surfaces company-tied hospitals with discounts in the employee's `baseLocation` (e.g., Apollo, Manipal, Fortis).
2. **Half-Day Sick Leave Trigger**:
   - Inquires if the employee wishes to rest in the office sick room before heading home.
   - Supplies exact room location (e.g., *Building 2, 3rd Floor, Room 304*).
3. **Stress / Fatigue Expression Trigger**:
   - Detects stress indicators in chat queries.
   - Directs the employee to on-campus zero-gravity massage recliners, the 24/7 recreational lounge, or confidential psychologist counseling.
4. **Vacation Nudge Trigger**:
   - Evaluates quarterly activity; if no leave was taken in 90 days, generates a restorative nudge with partner resort discounts (up to 25% corporate savings).
5. **Volunteering Leave Trigger**:
   - Suggests active corporate CSR chapters (Afforestation, Youth Tech Literacy, Animal Rescue) and offers intranet banner recognition.

---

## 7. Approval Hierarchies & State Machine

```
[APPLY] ──> PENDING ──┬──> [APPROVE] ──> APPROVED ──> [CANCEL before start] ──> CANCELLED (Restores balance)
                      ├──> [REJECT]  ──> REJECTED (Restores pending balance)
                      └──> [SEND BACK] ──> RETURNED ──> [EDIT] ──> PENDING
```

- **Manager Scoping**: Managers can only view and act on direct reportees' leaves. Accessing other teams' leaves yields HTTP 403.
- **Admin Oversight**: Admins can approve leaves across the organization on behalf of managers.
- **Self-Approval Prohibition**: Neither Managers nor Admins can approve their own leaves.
- **Peer Admin Approval**: An Admin's leave must be approved by a *different* Admin.
- **Admin Direct-DB-Edit Utility**: Privileged operation (`PUT /api/admin/leaves/{id}/direct-edit`) with mandatory audit justification, recorded with `action = "ADMIN_DIRECT_EDIT"` and `adminDirectEdit = true`.

---

## 8. Setup & Running Instructions

### Prerequisites
- Java 17+
- Maven 3.8+
- Python 3.8+ (for CLI agent)
- Modern Web Browser

### 1. Start the Backend
Navigate to the `backend/` directory and run:

```bash
cd backend
mvn spring-boot:run
```

By default, the backend boots in the `dev` profile using the in-memory H2 database (PostgreSQL mode) with all seed users and balances automatically loaded.

#### PostgreSQL Configuration (Optional Production Mode)
To connect to an external PostgreSQL instance:
```bash
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5432
export POSTGRES_DB=peoplefirst
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=yourpassword

mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

### 2. Access Swagger / OpenAPI Documentation
Once started, visit:
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### 3. Launch the Frontend
The frontend is located in `frontend/`. Since it uses modern ES modules, serve it using any standard HTTP server:

```bash
cd frontend
python3 -m http.server 3000
```

- **Employee / Manager / Admin Portal**: [http://localhost:3000/index.html](http://localhost:3000/index.html)
- **Contractor Dedicated Agent Portal**: [http://localhost:3000/contractor.html](http://localhost:3000/contractor.html)

### 4. Run the Kura Standalone CLI Agent
To interact with Kura from the command line:

```bash
python3 agent/agent_runner.py
```

Log in as `contractor1` (or any user) and ask questions in plain English!

---

## 9. Assumptions Made (§5 & §9)

1. **Contractor Web Exclusion**: Contractors interact solely via the AI agent (CLI runner and `contractor.html`). Attempted login to `index.html` returns HTTP 403.
2. **Admin Approval Chain**: Admins cannot approve their own leaves. Manager leaves are approved by any Admin; Admin leaves are approved by a different Admin.
3. **Admin Visibility**: Disregarded the duplicated restriction line under Admin in the source document; Admins have organization-wide visibility across all departments.
4. **End-of-Week Cutoff**: Casual/WFH leaves for the current week must be submitted before Sunday 23:59:59. Late submissions must use Support Tickets.
5. **25th of Month Cutoff**: Sick/Paid/LOP leaves for the current month must be submitted on or before the 25th. Subsequent requests require a Support Ticket.
6. **Direct DB Edit**: Admin direct edit is a distinct audited operation (`ADMIN_DIRECT_EDIT`), separate from normal approval overrides.

---

## 10. Automated Test Execution

Run the complete test suite verifying all 15 acceptance criteria:

```bash
cd backend
mvn clean test
```

All 26 unit and Spring Boot integration tests will execute and pass.
