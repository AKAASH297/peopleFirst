# Kura AI Agent — Standalone CLI Client

Kura is the dedicated leave management assistant and wellbeing concierge for the **peopleFirst** platform.

## Features
- **Contractor Dedicated Access**: Enables contractors to check leave balances, apply for eligible leave (Sick, Paid, LOP), view company policies, and cancel upcoming leaves via chat.
- **Stateless JWT Security**: Authenticates directly with the Spring Boot backend (`/api/auth/login`) with `channel="AGENT"` and includes bearer tokens in all requests.
- **Rule-Based Concierge**: Seamlessly receives and displays proactive wellbeing advice (doctor consultations, partner hospital discounts by location, office sick room availability, stress mitigation amenities, and vacation nudges).

## Quick Start

1. Ensure the backend is running on `http://localhost:8080`.
2. Run the agent script using standard Python 3:

```bash
python3 agent/agent_runner.py
```

3. Log in with any demo account:
   - `contractor1` / `password123` (Contractor — Agent Only)
   - `employee1` / `password123` (Permanent Employee)
   - `manager1` / `password123` (Manager)
   - `admin1` / `password123` (Administrator)
