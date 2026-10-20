import { Routing } from "../components/Routing";
import { useCallback, useState } from "react";
import { ArrowRight, RefreshCw } from "lucide-react";
import type { Client } from "../api/client";
import type { Info, Message, RunView, Selection } from "../api/types";
import { referenceId } from "../api/types";
import { PartitionInbox } from "./PartitionInbox";
import { usePolling } from "../workspace/usePolling";
import { useAction } from "../workspace/useAction";
import { ErrorBanner } from "../components/ErrorBanner";

export function InboxWorkspace({
  client,
  info,
  openReplay,
}: {
  client: Client;
  info: Info;
  openReplay: (id: string) => void;
}) {
  const request = useCallback(
    (signal: AbortSignal) => client.get<number[]>("partitions", signal),
    [client],
  );
  const { data: partitions, error, reload } = usePolling(request, 60000);
  const [partition, setPartition] = useState(0);
  const [selections, setSelections] = useState<Map<string, Selection>>(new Map());
  const [rate, setRate] = useState(10);
  const action = useAction();
  function save(selection: Selection) {
    setSelections((previous) => new Map(previous).set(referenceId(selection), selection));
  }
  function toggle(message: Message) {
    setSelections((previous) => {
      const next = new Map(previous);
      const id = referenceId(message.reference);
      if (next.has(id)) next.delete(id);
      else if (next.size < 100)
        next.set(id, { ...message.reference, expectedHash: message.sourceHash, editedJson: null });
      return next;
    });
  }
  async function preview() {
    const result = await action.run((signal) =>
      client.post<RunView>("previews", { rate, selections: [...selections.values()] }, signal),
    );
    if (result) {
      setSelections(new Map());
      openReplay(result.run.id);
    }
  }
  async function seed() {
    const result = await action.run((signal) =>
      client.post<{ added: number }>("demo/seed", undefined, signal),
    );
    if (result) reload();
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">KAFKA RECOVERY WORKSPACE</p>
          <h1>Dead-letter inbox</h1>
          <p className="muted">
            Recover with intent. Inspect a record, review the exact payload, then publish.
          </p>
        </div>
        <div className="heading-actions">
          {info.demo && (
            <button className="button" onClick={() => void seed()} disabled={action.pending}>
              Add 12 demo records
            </button>
          )}
          <button className="button" onClick={reload}>
            <RefreshCw size={15} />
            Refresh
          </button>
        </div>
      </div>
      {info.demo && (
        <div className="workspace-note">
          Demo fixture · each seed adds 12 records, including one malformed JSON payload.
        </div>
      )}
      <ErrorBanner message={error?.message} onRetry={reload} retryLabel="Retry connection" />
      <ErrorBanner message={action.error} />
      <div className="inbox-controls">
        <label htmlFor="partition">Partition</label>
        <select
          id="partition"
          value={partition}
          onChange={(event) => setPartition(Number(event.target.value))}
        >
          {(partitions?.length ? partitions : [0]).map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
        <span>Selection spans partitions · {selections.size} / 100</span>
      </div>
      <div className="workshop-grid">
        <PartitionInbox
          key={`${partition}:${partitions?.join(",")}`}
          client={client}
          partition={partition}
          selections={selections}
          toggle={toggle}
          save={save}
        />
        <section className="panel preview-form">
          <div>
            <p className="eyebrow">01 INSPECT · 02 PREVIEW · 03 REPLAY</p>
            <h2>{selections.size} records selected</h2>
            <p className="field-help">
              Preview freezes source fingerprints, keys, headers and output bytes. Nothing is
              published yet.
            </p>
          </div>
          <div className="preview-controls">
            <label htmlFor="replay-rate">Records / second</label>
            <input
              id="replay-rate"
              type="number"
              min={1}
              max={100}
              value={rate}
              onChange={(event) => setRate(Number(event.target.value))}
            />
            <button
              className="button primary"
              disabled={action.pending || selections.size === 0 || rate < 1 || rate > 100}
              onClick={() => void preview()}
            >
              {action.pending ? "Working…" : "Create preview"}
              <ArrowRight size={15} />
            </button>
            <button
              className="text-button"
              disabled={!selections.size || action.pending}
              onClick={() => setSelections(new Map())}
            >
              Clear selection
            </button>
          </div>
        </section>
      </div>
      <Routing source={info.sourceTopic} target={info.targetTopic} />
    </>
  );
}
