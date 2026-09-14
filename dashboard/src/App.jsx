import { useState } from "react";
import { createApi, eventKey, isHash, risk, short } from "./api";
import { mockApi } from "./mock";
import { useEvents, useLookup } from "./hooks";
const backendApi = createApi();

function Provenance({ data, mock }) {
  return (
    <div className="badges">
      <span className={mock ? "badge amber" : "badge"}>
        {mock ? "MOCK · Sample fixtures" : "REAL · Backend response"}
      </span>
      <span className="badge amber">
        {data?.proofMode || "Proof mode unknown"}
      </span>
      {data && <span className="badge neutral">{data.transactionMode}</span>}
    </div>
  );
}
function Fields({ entries }) {
  return (
    <dl>
      {entries.map(([name, value]) => (
        <div key={name}>
          <dt>{name}</dt>
          <dd>{value ?? "—"}</dd>
        </div>
      ))}
    </dl>
  );
}
function Detail({ api, id, mock }) {
  const { data, error, loading } = useLookup(api, "threat", id, true);
  return (
    <section className="panel" aria-labelledby="detail-title">
      <div className="panel-heading">
        <h2 id="detail-title">Threat detail</h2>
        <span className="number">02</span>
      </div>
      {!id && (
        <p className="empty">Select a registration or look up a Threat ID.</p>
      )}
      {loading && <p role="status">Reading Registry…</p>}
      {error && (
        <p role="alert" className="error">
          {error}. Record unavailable.
        </p>
      )}
      {data && (
        <>
          <Provenance data={data} mock={mock} />
          <Fields
            entries={[
              ["Threat ID", data.threatId],
              ["VoiceprintHash", data.voiceprintHash],
              [
                "RiskScore",
                `${risk(data.riskScore)} (${data.riskScore} / 10000)`,
              ],
              ["Registered at · Unix seconds", data.registeredAt],
              ["Chain ID", data.chainId],
              ["Registry address", data.registryAddress],
            ]}
          />
          <details>
            <summary>
              Stored proof · {(data.zkProof.length - 2) / 2} bytes
            </summary>
            <code className="proof">{data.zkProof}</code>
          </details>
          <p className="footnote">
            RiskScore is a model score, not a verified probability of crime.
            MOCK_PROOF provides no cryptographic assurance.
          </p>
        </>
      )}
    </section>
  );
}
function Submission({ api, mock }) {
  const [input, setInput] = useState(""),
    [id, setId] = useState("");
  const { data, error, loading } = useLookup(api, "submission", id, true);
  return (
    <section className="panel" aria-labelledby="submission-title">
      <div className="panel-heading">
        <h2 id="submission-title">Submission status</h2>
        <span className="number">03</span>
      </div>
      <p className="subtext">
        Track an existing submission. No transactions are created here.
      </p>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          setId(input.trim());
        }}
      >
        <label htmlFor="submission-id">Submission ID</label>
        <div className="input-row">
          <input
            id="submission-id"
            value={input}
            maxLength={128}
            onChange={(event) => setInput(event.target.value)}
            required
            placeholder={mock ? "sample-registered" : "Submission UUID"}
          />
          <button type="submit">Track</button>
        </div>
      </form>
      {mock && (
        <p className="footnote">
          SAMPLE IDs: sample-registered · sample-pending · sample-failed
        </p>
      )}
      {loading && <p role="status">Checking receipt…</p>}
      {error && (
        <p role="alert" className="error">
          {error}. Status unavailable.
        </p>
      )}
      {data && (
        <>
          <div className={`state ${data.status.toLowerCase()}`}>
            {data.status}
          </div>
          <Provenance data={data} mock={mock} />
          <Fields
            entries={[
              ["Submission ID", data.submissionId],
              ["Threat ID · assigned before confirmation", data.threatId],
              ["Transaction hash", data.txHash],
              ["Error code", data.errorCode],
            ]}
          />
          <p className="footnote">
            {data.status === "REGISTERED"
              ? "Backend reports a receipt-backed registration. This does not imply a real zkML proof."
              : "A submission or transaction hash does not mean the threat is registered."}
          </p>
        </>
      )}
    </section>
  );
}
function Workspace({ api, mock }) {
  const feed = useEvents(api);
  return <WorkspaceView key={feed.epoch} api={api} mock={mock} feed={feed} />;
}
function WorkspaceView({ api, mock, feed }) {
  const [selected, select] = useState(""),
    [input, setInput] = useState(""),
    [invalid, setInvalid] = useState("");
  const registrations = [
    ...new Map(
      feed.events
        .filter((event) => event.event === "ThreatRegistered")
        .map((event) => [event.args.threatId, event]),
    ).values(),
  ];
  return (
    <>
      <div className="connection" role="status">
        <div className="badges">
          <span className={`dot ${feed.error ? "offline" : ""}`} />
          <strong>
            {mock
              ? "MOCK dataset"
              : feed.error
                ? "Backend unavailable · displayed events may be stale"
                : feed.meta
                  ? "REAL backend connection"
                  : "Connecting to Backend API…"}
          </strong>
        </div>
        <span>
          {feed.updated
            ? `Last read ${feed.updated.toLocaleTimeString()} · poll 3s`
            : "Waiting for first read"}
        </span>
      </div>
      {feed.meta && (
        <div className="source-line">
          <Provenance data={feed.meta} mock={mock} />
          <span>
            Chain {feed.meta.chainId} · through block {feed.meta.throughBlock}
          </span>
        </div>
      )}
      {mock && (
        <div className="notice">
          SAMPLE fixtures only. These records, hashes, proof bytes and statuses
          are not evidence of AI inference or a live transaction.
        </div>
      )}
      {feed.error && (
        <div role="alert" className="notice error">
          {feed.error}.{" "}
          {feed.events.length
            ? "Previous events are retained for inspection and marked stale."
            : "No current event data."}{" "}
          Retrying automatically; no Mock fallback.
        </div>
      )}
      <div className="workspace">
        <div className="main-column">
          <section className="panel" aria-labelledby="registry-title">
            <div className="panel-heading">
              <div>
                <span className="eyebrow">THREATREGISTRY</span>
                <h2 id="registry-title">
                  Registry view{" "}
                  <span className="count">{registrations.length}</span>
                </h2>
              </div>
              <span className="number">01</span>
            </div>
            <p className="subtext">
              Registrations discovered from loaded events. This is a session
              view, not a complete chain total.
            </p>
            <form
              onSubmit={(event) => {
                event.preventDefault();
                if (!isHash(input.trim())) {
                  setInvalid("Enter a 0x-prefixed, 32-byte Threat ID.");
                  return;
                }
                setInvalid("");
                select(input.trim().toLowerCase());
              }}
            >
              <label htmlFor="threat-id">Look up Threat ID</label>
              <div className="input-row">
                <input
                  id="threat-id"
                  value={input}
                  onChange={(event) => setInput(event.target.value)}
                  placeholder="0x…"
                  required
                />
                <button type="submit">Inspect</button>
              </div>
              {invalid && (
                <p role="alert" className="error">
                  {invalid}
                </p>
              )}
            </form>
            {!registrations.length ? (
              <p className="empty">
                {feed.meta
                  ? "No registrations in the loaded block range."
                  : "Registry data has not loaded."}
              </p>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Threat ID</th>
                      <th>RiskScore</th>
                      <th>Block</th>
                      <th>
                        <span className="sr-only">Details</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {registrations.map((event) => (
                      <tr
                        key={eventKey(event)}
                        className={
                          selected === event.args.threatId ? "selected" : ""
                        }
                      >
                        <td>
                          <code title={event.args.threatId}>
                            {short(event.args.threatId)}
                          </code>
                        </td>
                        <td>{risk(event.args.riskScore)}</td>
                        <td>{event.blockNumber}</td>
                        <td>
                          <button
                            className="text-button"
                            aria-label={`Inspect threat ${event.args.threatId}`}
                            onClick={() => select(event.args.threatId)}
                          >
                            Inspect ↗
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
          <section className="panel" aria-labelledby="events-title">
            <div className="panel-heading">
              <div>
                <span className="eyebrow">CURSOR-BASED POLLING</span>
                <h2 id="events-title">
                  Event stream{" "}
                  <span className="count">{feed.events.length}</span>
                </h2>
              </div>
              <span className="badge neutral">Read only</span>
            </div>
            {!feed.events.length && <p className="empty">No events loaded.</p>}
            <ol className="events">
              {feed.events.map((event) => (
                <li key={eventKey(event)}>
                  <div className="event-heading">
                    <strong>{event.event}</strong>
                    <span>
                      Block {event.blockNumber} · log {event.logIndex}
                    </span>
                  </div>
                  <button
                    className="text-button"
                    onClick={() => select(event.args.threatId)}
                  >
                    Threat {short(event.args.threatId)} ↗
                  </button>
                  <details>
                    <summary>Event identity and payload</summary>
                    <Fields
                      entries={[
                        ["Chain ID", event.chainId],
                        ["Transaction hash", event.txHash],
                        ["Block hash", event.blockHash],
                        ...Object.entries(event.args),
                      ]}
                    />
                  </details>
                </li>
              ))}
            </ol>
          </section>
        </div>
        <aside>
          <Detail api={api} id={selected} mock={mock} />
          <Submission api={api} mock={mock} />
        </aside>
      </div>
    </>
  );
}
export default function App({ realApi = backendApi, fixtureApi = mockApi }) {
  const [mode, setMode] = useState("backend");
  return (
    <>
      <header>
        <a className="brand" href="#">
          <span className="brand-mark">
            T<span>III</span>
          </span>
          <span>
            TRI-DEFENSE<small>REGISTRY OBSERVATORY</small>
          </span>
        </a>
        <span className="header-note">P0 · Local development</span>
      </header>
      <main>
        <div className="hero">
          <div>
            <p className="eyebrow">SHARED THREAT LEDGER</p>
            <h1>
              Inspect the record.
              <br />
              <span>Trace the evidence.</span>
            </h1>
            <p>
              Registry entries, chain events and submission states in one
              read-only view.
            </p>
          </div>
          <fieldset className="mode-switch">
            <legend>Data source</legend>
            <button
              aria-pressed={mode === "backend"}
              onClick={() => setMode("backend")}
            >
              Backend API
            </button>
            <button
              aria-pressed={mode === "mock"}
              onClick={() => setMode("mock")}
            >
              Mock fixtures
            </button>
          </fieldset>
        </div>
        <Workspace
          key={mode}
          api={mode === "backend" ? realApi : fixtureApi}
          mock={mode === "mock"}
        />
      </main>
      <footer>
        Tri-Defense / Dashboard P0{" "}
        <span>
          REAL describes the API connection. Proof assurance is shown
          separately.
        </span>
      </footer>
    </>
  );
}
