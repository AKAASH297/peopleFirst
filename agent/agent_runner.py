#!/usr/bin/env python3
"""
Kura AI Agent CLI Runner - peopleFirst Platform
Autonomous leave management and wellbeing concierge for contractors, employees, managers, and admins.
Interacts with the peopleFirst Spring Boot backend via REST API using stateless JWT authentication.
"""

import sys
import json
import urllib.request
import urllib.error

DEFAULT_BACKEND_URL = "http://localhost:8080"

class KuraAgentClient:
    def __init__(self, base_url=DEFAULT_BACKEND_URL):
        self.base_url = base_url.rstrip("/")
        self.access_token = None
        self.user_profile = None

    def login(self, username, password, channel="AGENT"):
        url = f"{self.base_url}/api/auth/login"
        payload = json.dumps({
            "username": username,
            "password": password,
            "channel": channel
        }).encode("utf-8")

        req = urllib.request.Request(
            url,
            data=payload,
            headers={"Content-Type": "application/json"}
        )

        try:
            with urllib.request.urlopen(req) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                self.access_token = data.get("accessToken")
                self.user_profile = data.get("user")
                return True, data
        except urllib.error.HTTPError as e:
            error_body = e.read().decode("utf-8")
            try:
                err_json = json.loads(error_body)
                return False, err_json.get("message", error_body)
            except:
                return False, error_body
        except Exception as e:
            return False, str(e)

    def chat(self, message, conversation_id="kura-cli-session"):
        if not self.access_token:
            return False, "Not authenticated. Please log in first."

        url = f"{self.base_url}/api/agent/chat"
        payload = json.dumps({
            "message": message,
            "conversationId": conversation_id
        }).encode("utf-8")

        req = urllib.request.Request(
            url,
            data=payload,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.access_token}"
            }
        )

        try:
            with urllib.request.urlopen(req) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                return True, data
        except urllib.error.HTTPError as e:
            error_body = e.read().decode("utf-8")
            try:
                err_json = json.loads(error_body)
                return False, err_json.get("message", error_body)
            except:
                return False, error_body
        except Exception as e:
            return False, str(e)

    def get_policies(self):
        if not self.access_token:
            return False, "Not authenticated."

        url = f"{self.base_url}/api/policies"
        req = urllib.request.Request(
            url,
            headers={"Authorization": f"Bearer {self.access_token}"}
        )
        try:
            with urllib.request.urlopen(req) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                return True, data
        except Exception as e:
            return False, str(e)


def run_interactive_session():
    print("=" * 65)
    print("  peopleFirst - Kura AI Agent Terminal Concierge")
    print("  Hybrid Google Gemini GenAI & Grounded Policy Engine")
    print("=" * 65)

    backend_url = DEFAULT_BACKEND_URL
    client = KuraAgentClient(backend_url)

    print(f"Connecting to backend at: {backend_url}")
    print("\nPre-configured Demo Accounts:")
    print(" 1. contractor1 (Contractor - Agent Only)")
    print(" 2. employee1   (Permanent Employee)")
    print(" 3. manager1    (Engineering Manager)")
    print(" 4. admin1      (HR Administrator)")
    print(" Default password for all: password123\n")

    username = input("Enter Username [default: contractor1]: ").strip()
    if not username:
        username = "contractor1"

    password = input("Enter Password [default: password123]: ").strip()
    if not password:
        password = "password123"

    print(f"\nAuthenticating as '{username}' via AGENT channel...")
    success, result = client.login(username, password, channel="AGENT")

    if not success:
        print(f"❌ Login failed: {result}")
        sys.exit(1)

    user = client.user_profile
    role = "CONTRACTOR (Agent-Only Access)" if user.get("contractor") else user.get("role")
    print(f"✅ Authenticated successfully!")
    print(f"   Name: {user.get('fullName')}")
    print(f"   Role: {role}")
    print(f"   Department: {user.get('department')} | Base Location: {user.get('baseLocation')}")
    print("-" * 65)

    # Initial greeting
    _, greeting_resp = client.chat("hello")
    print(f"\nKura: {greeting_resp.get('reply')}\n")

    print("Type your message below (or 'exit' to quit). Examples:")
    print(" - 'How many sick leaves do I have left?'")
    print(" - 'Apply for sick leave tomorrow'")
    print(" - 'Can I combine casual leave with sick leave?'")
    print(" - 'I am feeling exhausted and stressed from sprint deadlines'")
    print(" - 'What partner hospitals and clinics do we have in my city?'")
    print("-" * 65)

    while True:
        try:
            msg = input(f"\n[{user.get('username')}] > ").strip()
            if not msg:
                continue
            if msg.lower() in ("exit", "quit", "q"):
                print("\nThank you for using Kura. Take care and stay well!")
                break

            ok, chat_resp = client.chat(msg)
            if ok:
                print(f"\nKura: {chat_resp.get('reply')}")

                # Surface wellbeing suggestions if present
                suggestions = chat_resp.get("wellbeingSuggestions")
                if suggestions:
                    print("\n" + "~" * 50)
                    for sug in suggestions:
                        print(f"  🌿 [{sug.get('title')}]")
                        print(f"  {sug.get('message')}")
                        if sug.get("actionUrl"):
                            print(f"  🔗 Action link: {sug.get('actionUrl')}")
                    print("~" * 50)

                # Show quick replies
                quick = chat_resp.get("quickReplies")
                if quick:
                    print(f"💡 Suggestions: { ' | '.join(quick) }")
            else:
                print(f"\n❌ Error from Kura: {chat_resp}")

        except (KeyboardInterrupt, EOFError):
            print("\nSession ended. Goodbye!")
            break


if __name__ == "__main__":
    run_interactive_session()
