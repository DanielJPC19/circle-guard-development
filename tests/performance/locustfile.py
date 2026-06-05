import datetime
import os
import random
import time
import uuid

from locust import HttpUser, between, task

AUTH_URL      = os.environ.get("AUTH_URL",      "http://localhost:8180")
GATEWAY_URL   = os.environ.get("GATEWAY_URL",   "http://localhost:8087")
PROMOTION_URL = os.environ.get("PROMOTION_URL", "http://localhost:8088")
FORM_URL      = os.environ.get("FORM_URL",      "http://localhost:8086")

MAX_RETRIES = 3
RETRY_BACKOFF = 0.5


class BaseUser(HttpUser):
    abstract = True

    def _do_login(self, username, password):
        for attempt in range(MAX_RETRIES):
            resp = self.client.post(
                f"{AUTH_URL}/api/v1/auth/login",
                json={"username": username, "password": password},
                name="POST /api/v1/auth/login [setup]",
            )
            if resp.status_code == 200:
                return resp
            if resp.status_code == 503 and attempt < MAX_RETRIES - 1:
                time.sleep(RETRY_BACKOFF * (attempt + 1))
                continue
            return resp
        return resp

    def refresh_token(self):
        if hasattr(self, 'username') and hasattr(self, 'password'):
            resp = self._do_login(self.username, self.password)
            if resp.status_code == 200:
                data = resp.json()
                self.token = data.get("token", "")
                self.headers = {"Authorization": f"Bearer {self.token}"}


class StudentUser(BaseUser):
    """Simulates a student doing daily campus check-in and health monitoring."""
    weight = 7
    wait_time = between(1, 3)
    host = AUTH_URL

    def on_start(self):
        self.username = "staff_guard"
        self.password = "password"
        self.anonymous_id = str(uuid.uuid4())
        self.token = ""
        self.headers = {}

        resp = self._do_login(self.username, self.password)
        if resp.status_code == 200:
            data = resp.json()
            self.token = data.get("token", "")
            self.anonymous_id = data.get("anonymousId", self.anonymous_id)
            self.headers = {"Authorization": f"Bearer {self.token}"}

    @task(3)
    def check_in(self):
        """Generate a QR token and validate it at the campus gate."""
        qr_resp = self._get_with_retry(
            f"{AUTH_URL}/api/v1/auth/qr/generate",
            headers=self.headers,
            name="GET /api/v1/auth/qr/generate",
        )
        if qr_resp.status_code == 200:
            qr_token = qr_resp.json().get("qrToken", "invalid")
            self.client.post(
                f"{GATEWAY_URL}/api/v1/gate/validate",
                json={"token": qr_token},
                name="POST /api/v1/gate/validate",
            )

    def _get_with_retry(self, url, **kwargs):
        for attempt in range(MAX_RETRIES):
            resp = self.client.get(url, **kwargs)
            if resp.status_code in (200, 404):
                return resp
            if resp.status_code in (503, 403, 401) and attempt < MAX_RETRIES - 1:
                time.sleep(RETRY_BACKOFF * (attempt + 1))
                if resp.status_code in (401, 403):
                    self.refresh_token()
                continue
            return resp
        return resp

    @task(1)
    def get_health_status(self):
        """Check personal health status."""
        self.client.get(
            f"{PROMOTION_URL}/api/v1/promotion/status/{self.anonymous_id}",
            name="GET /api/v1/promotion/status/{id}",
        )

    @task(1)
    def submit_survey(self):
        """Submit a daily health survey."""
        self.client.post(
            f"{FORM_URL}/api/v1/surveys",
            json={
                "anonymousId": str(uuid.uuid4()),
                "hasFever": random.choice([True, False]),
                "hasCough": random.choice([True, False]),
                "exposureDate": datetime.date.today().isoformat(),
            },
            name="POST /api/v1/surveys",
        )


class HealthOfficerUser(BaseUser):
    """Simulates a health officer monitoring and reporting cases."""
    weight = 3
    wait_time = between(2, 5)
    host = AUTH_URL

    def on_start(self):
        self.username = "health_user"
        self.password = "password"
        self.anonymous_id = str(uuid.uuid4())
        self.token = ""
        self.headers = {}

        resp = self._do_login(self.username, self.password)
        if resp.status_code == 200:
            data = resp.json()
            self.token = data.get("token", "")
            self.anonymous_id = data.get("anonymousId", self.anonymous_id)
            self.headers = {"Authorization": f"Bearer {self.token}"}

    @task(1)
    def report_case(self):
        """Report a confirmed health case."""
        self.client.post(
            f"{PROMOTION_URL}/api/v1/promotion/report",
            json={"anonymousId": self.anonymous_id, "status": "CONFIRMED"},
            headers=self.headers,
            name="POST /api/v1/promotion/report",
        )

    @task(2)
    def view_stats(self):
        """View aggregated health statistics."""
        self.client.get(
            f"{PROMOTION_URL}/api/v1/health-status/stats",
            name="GET /api/v1/health-status/stats",
        )