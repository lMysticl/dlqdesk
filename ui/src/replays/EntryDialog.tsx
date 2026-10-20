import { useCallback, useState } from "react";
import type { Client } from "../api/client";
import type { Entry, Frozen, RunView } from "../api/types";
import { referenceId } from "../api/types";
import { Dialog } from "../components/Dialog";
import { Status } from "../components/Status";
import { pretty } from "../workspace/format";
import { usePolling } from "../workspace/usePolling";
import { useAction } from "../workspace/useAction";
import { ErrorBanner } from "../components/ErrorBanner";

export function EntryDialog({
  client,
  id,
  entry,
  onClose,
  onReviewed,
}: {
  client: Client;
  id: string;
  entry: Entry;
  onClose: () => void;
  onReviewed: () => void;
}) {
  const request = useCallback(
    (signal: AbortSignal) => client.get<Frozen>(`replays/${id}/entries/${entry.index}`, signal),
    [client, id, entry.index],
  );
  const { data, error } = usePolling(request, 60000);
  const action = useAction();
  const [reason, setReason] = useState("");
  async function review() {
    const result = await action.run((signal) =>
      client.post<RunView>(`replays/${id}/entries/${entry.index}/review`, { reason }, signal),
    );
    if (result) {
      onReviewed();
      onClose();
    }
  }
  return (
    <Dialog title={`Frozen record ${referenceId(entry.source)}`} onClose={onClose}>
      <div className="panel-body">
        <Status value={entry.state} />
        <p className="field-help mono">
          Source SHA256 {entry.sourceHash}
          <br />
          Output SHA256 {entry.payloadHash}
        </p>
        <ErrorBanner message={error?.message} />
        {data && (
          <>
            <h3>Output payload</h3>
            <pre>{pretty(data.outputPayload)}</pre>
            {entry.edited && (
              <details>
                <summary>Original payload</summary>
                <pre>{pretty(data.originalPayload)}</pre>
              </details>
            )}
            <details>
              <summary>Preserved headers ({data.headers.length})</summary>
              <table className="header-table">
                <tbody>
                  {data.headers.map((header, index) => (
                    <tr key={index}>
                      <td>{header.name}</td>
                      <td>{header.text ?? header.base64 ?? "null"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </details>
          </>
        )}
        {entry.state === "UNKNOWN" && (
          <>
            <p className="callout">
              {entry.error} Check the target using this replay ID and source coordinate. Marking
              reviewed skips this entry; it does not send it again.
            </p>
            <label htmlFor="review-reason">What did you verify?</label>
            <textarea
              id="review-reason"
              value={reason}
              maxLength={500}
              onChange={(event) => setReason(event.target.value)}
              placeholder="Record the destination evidence and your decision…"
            />
            <ErrorBanner message={action.error} />
            <button
              className="button primary"
              disabled={action.pending || !reason.trim()}
              onClick={() => void review()}
            >
              Record review and skip entry
            </button>
          </>
        )}
      </div>
    </Dialog>
  );
}
