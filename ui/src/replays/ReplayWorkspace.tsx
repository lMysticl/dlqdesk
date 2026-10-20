import { useCallback } from "react";
import { ArrowLeft } from "lucide-react";
import type { Client } from "../api/client";
import type { Run } from "../api/types";
import { Status } from "../components/Status";
import { timestamp } from "../workspace/format";
import { usePolling } from "../workspace/usePolling";
import { ReplayDetail } from "./ReplayDetail";
import { ErrorBanner } from "../components/ErrorBanner";

export function ReplayWorkspace({
  client,
  selected,
  select,
  inbox,
}: {
  client: Client;
  selected: string | undefined;
  select: (id: string) => void;
  inbox: () => void;
}) {
  const request = useCallback(
    (signal: AbortSignal) => client.get<Run[]>("replays", signal),
    [client],
  );
  const { data, error, reload } = usePolling(request, 1500);
  const id = selected ?? data?.[0]?.id;
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">DELIVERY LEDGER</p>
          <h1>Replay plans</h1>
          <p className="muted">
            Frozen previews. Broker acknowledgements. An explicit record of uncertainty.
          </p>
        </div>
        <button className="button" onClick={inbox}>
          <ArrowLeft size={15} />
          Back to inbox
        </button>
      </div>
      <ErrorBanner message={error?.message} onRetry={reload} />
      <div className="replay-workspace">
        <section className="panel">
          <div className="panel-heading">
            <h2>Recent plans</h2>
            <span className="muted">{data?.length ?? 0}</span>
          </div>
          <div className="run-list">
            {data?.map((run) => (
              <button
                key={run.id}
                className="run-card"
                aria-pressed={id === run.id}
                onClick={() => select(run.id)}
              >
                <span className="run-card-top">
                  <code>{run.id.slice(0, 8)}</code>
                  <Status value={run.state} />
                </span>
                <span className="run-card-bottom">
                  {timestamp(run.createdAt)} · {run.total} records
                </span>
                <span className="run-card-bottom">
                  {run.sent} sent · {run.pending} pending
                  {run.unknown > 0 && ` · ${run.unknown} uncertain`}
                </span>
              </button>
            ))}
          </div>
          {data && !data.length && (
            <div className="empty-state">
              <h3>No replay plans yet</h3>
              <p>Select inbox records, then create a preview.</p>
            </div>
          )}
        </section>
        {id ? (
          <ReplayDetail key={id} client={client} id={id} />
        ) : (
          <div className="panel empty-state">
            <h3>Recovery starts with a preview</h3>
            <p>Your durable plans and their delivery results will appear here.</p>
          </div>
        )}
      </div>
    </>
  );
}
