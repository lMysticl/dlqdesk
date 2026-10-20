import { useCallback, useState } from "react";
import { Pause, Play } from "lucide-react";
import type { Client } from "../api/client";
import type { Entry, RunView } from "../api/types";
import { referenceId } from "../api/types";
import { Dialog } from "../components/Dialog";
import { Status } from "../components/Status";
import { timestamp, label } from "../workspace/format";
import { usePolling } from "../workspace/usePolling";
import { useAction } from "../workspace/useAction";
import { EntryDialog } from "./EntryDialog";

export function ReplayDetail({ client, id }: { client: Client; id: string }) {
  const request = useCallback(
    (signal: AbortSignal) => client.get<RunView>(`replays/${id}`, signal),
    [client, id],
  );
  const { data, error, reload } = usePolling(request, 1500);
  const action = useAction();
  const [confirm, setConfirm] = useState<"start" | "resume">();
  const [entry, setEntry] = useState<Entry>();
  async function command(name: string) {
    const result = await action.run((signal) =>
      client.post<RunView>(`replays/${id}/${name}`, undefined, signal),
    );
    if (result) {
      setConfirm(undefined);
      reload();
    }
  }
  if (!data)
    return (
      <div className="panel empty-state">
        {error ? (
          <p role="alert">
            {error.message}
            <button onClick={reload}>Retry</button>
          </p>
        ) : (
          "Loading replay…"
        )}
      </div>
    );
  const run = data.run;
  const resumable = ["PAUSED", "FAILED", "INTERRUPTED"].includes(run.state);
  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">IMMUTABLE PREVIEW</p>
          <h2>
            Replay <code>{id.slice(0, 8)}</code>
          </h2>
          <p className="panel-subtitle">
            {run.sourceTopic} → {run.targetTopic} · {run.rate} records/s
          </p>
        </div>
        <Status value={run.state} />
      </div>
      <div className="replay-summary">
        <div>
          <strong>{run.total}</strong>
          <span>Selected</span>
        </div>
        <div>
          <strong>{run.sent}</strong>
          <span>Broker acknowledged</span>
        </div>
        <div>
          <strong>{run.pending}</strong>
          <span>Pending</span>
        </div>
        <div>
          <strong>{run.unknown}</strong>
          <span>Uncertain</span>
        </div>
      </div>
      <div className="panel-body command-bar">
        {run.state === "DRAFT" && (
          <button
            className="button primary"
            onClick={() => setConfirm("start")}
            disabled={action.pending}
          >
            <Play size={14} />
            Start replay
          </button>
        )}
        {run.state === "RUNNING" && (
          <button
            className="button"
            onClick={() => void command("pause")}
            disabled={action.pending}
          >
            <Pause size={14} />
            Pause replay
          </button>
        )}
        {run.state === "PAUSE_REQUESTED" && (
          <p className="muted">Waiting for the in-flight acknowledgement…</p>
        )}
        {resumable && (
          <button
            className="button primary"
            onClick={() => setConfirm("resume")}
            disabled={action.pending || run.unknown > 0}
          >
            <Play size={14} />
            Resume pending
          </button>
        )}
        <p className="field-help">
          SENT means Kafka acknowledged the publish. It does not prove downstream processing.
        </p>
      </div>
      {(action.error || error) && (
        <p className="error-banner" role="alert">
          {action.error || error?.message}
        </p>
      )}
      {run.unknown > 0 && (
        <div className="callout">
          Delivery is uncertain for {run.unknown} record(s). Inspect the destination, then open each
          uncertain entry to record your decision. Automatic resend is blocked.
        </div>
      )}
      <div className="delivery-grid">
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Source</th>
                <th>Change</th>
                <th>Result</th>
                <th>Destination</th>
              </tr>
            </thead>
            <tbody>
              {data.entries.map((item) => (
                <tr key={item.index}>
                  <td>
                    <button className="row-link mono" onClick={() => setEntry(item)}>
                      {referenceId(item.source)}
                    </button>
                    <p className="muted message-key">{item.key ?? "null / binary key"}</p>
                  </td>
                  <td>
                    <span className="change-tag">{item.edited ? "EDITED JSON" : "ORIGINAL"}</span>
                    <p className="hash" title={item.payloadHash}>
                      {item.payloadHash.slice(0, 10)}…
                    </p>
                  </td>
                  <td>
                    <Status value={item.state} />
                  </td>
                  <td className="mono">
                    {item.targetPartition === null
                      ? "—"
                      : `${item.targetPartition}:${item.targetOffset}`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <aside aria-label="Audit trail">
          <div className="panel-heading">
            <h3>Audit trail</h3>
            <span className="panel-subtitle">Latest 30 events</span>
          </div>
          <ol className="audit-list">
            {data.audit.map((event, index) => (
              <li key={`${event.timestamp}:${index}`}>
                <span className="audit-dot" />
                <div>
                  <strong>{label(event.action)}</strong>
                  <p>{event.detail}</p>
                  <time>{timestamp(event.timestamp)}</time>
                </div>
              </li>
            ))}
          </ol>
        </aside>
      </div>
      {confirm && (
        <Dialog
          title={confirm === "start" ? "Publish this preview?" : "Resume pending records?"}
          onClose={() => {
            if (!action.pending) setConfirm(undefined);
          }}
        >
          <div className="panel-body">
            <p className="dialog-description">
              {confirm === "start"
                ? `${run.total} records will be published`
                : `${run.pending} pending records will be published`}{" "}
              to <strong>{run.targetTopic}</strong> at {run.rate} records/s. Source offsets and
              consumer groups stay unchanged.
            </p>
            <p className="field-help">
              Previously acknowledged and explicitly skipped entries are never resent by resume.
            </p>
            {action.error && (
              <p role="alert" className="error-banner">
                {action.error}
              </p>
            )}
            <div className="modal-actions">
              <button
                className="button"
                disabled={action.pending}
                onClick={() => setConfirm(undefined)}
              >
                Cancel
              </button>
              <button
                className="button primary"
                disabled={action.pending}
                onClick={() => void command(confirm)}
              >
                {action.pending ? "Accepting…" : "Confirm publish"}
              </button>
            </div>
          </div>
        </Dialog>
      )}
      {entry && (
        <EntryDialog
          client={client}
          id={id}
          entry={data.entries.find((value) => value.index === entry.index) ?? entry}
          onClose={() => setEntry(undefined)}
          onReviewed={reload}
        />
      )}
    </section>
  );
}
