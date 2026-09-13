"""Single-process local relay with durable idempotency; SQLite is not a threat registry."""
import json
import sqlite3
import threading
import uuid
from .errors import ApiError
from .schema import submission


class SubmissionService:
    def __init__(self, registry, database):
        self.registry = registry
        self.lock = threading.RLock()
        self.db = sqlite3.connect(database, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.executescript("""
            CREATE TABLE IF NOT EXISTS config (id INTEGER PRIMARY KEY, domain TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS submissions (
                id TEXT PRIMARY KEY, key TEXT UNIQUE NOT NULL, nonce TEXT UNIQUE NOT NULL,
                payload TEXT NOT NULL, threat_id TEXT NOT NULL, tx_hash TEXT,
                status TEXT NOT NULL, error TEXT);
        """)
        domain = json.dumps(registry.domain, sort_keys=True)
        existing = self.db.execute("SELECT domain FROM config WHERE id=1").fetchone()
        if existing and existing[0] != domain:
            self.db.close()
            raise ValueError("Database belongs to another chain/Registry/sender; use a separate database")
        with self.db:
            self.db.execute("INSERT OR IGNORE INTO config VALUES (1, ?)", (domain,))

    def close(self):
        self.db.close()

    def view(self, row):
        return {"submissionId": row["id"], "status": row["status"], "txHash": row["tx_hash"],
                "threatId": row["threat_id"], "errorCode": row["error"], **self.registry.meta}

    def submit(self, body):
        payload = submission(body)
        serialized = json.dumps(payload, sort_keys=True, separators=(",", ":"))
        with self.lock:
            self.registry.ensure_chain()
            row = self.db.execute("SELECT * FROM submissions WHERE key=?", (payload["idempotencyKey"],)).fetchone()
            if row:
                if row["payload"] != serialized:
                    raise ApiError(409, "IDEMPOTENCY_CONFLICT")
                return self.status(row["id"])
            if self.db.execute("SELECT 1 FROM submissions WHERE nonce=?", (payload["nonce"],)).fetchone():
                raise ApiError(409, "NONCE_CONFLICT")
            threat_id = self.registry.preflight(payload)
            identifier = str(uuid.uuid4())
            # Persist BEFORE sending. A crash or ambiguous RPC response must not cause another send.
            with self.db:
                self.db.execute("INSERT INTO submissions VALUES (?, ?, ?, ?, ?, NULL, 'SUBMITTED', 'BROADCAST_UNCERTAIN')",
                                (identifier, payload["idempotencyKey"], payload["nonce"], serialized, threat_id))
            try:
                tx_hash = self.registry.send(payload)
            except Exception as exc:
                raise ApiError(503, "BROADCAST_UNCERTAIN", submissionId=identifier) from exc
            with self.db:
                self.db.execute("UPDATE submissions SET tx_hash=?, error=NULL WHERE id=?", (tx_hash, identifier))
            return self.view(self.db.execute("SELECT * FROM submissions WHERE id=?", (identifier,)).fetchone())

    def status(self, identifier):
        with self.lock:
            self.registry.ensure_chain()
            row = self.db.execute("SELECT * FROM submissions WHERE id=?", (identifier,)).fetchone()
            if row is None:
                raise ApiError(404, "SUBMISSION_NOT_FOUND")
            if row["tx_hash"]:
                status, error = self.registry.receipt_status(row["tx_hash"], row["threat_id"], json.loads(row["payload"]))
                with self.db:
                    self.db.execute("UPDATE submissions SET status=?, error=? WHERE id=?", (status, error, identifier))
                row = self.db.execute("SELECT * FROM submissions WHERE id=?", (identifier,)).fetchone()
            return self.view(row)
