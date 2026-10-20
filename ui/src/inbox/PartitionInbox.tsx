import { useCallback, useState } from "react";
import { RefreshCw, Search } from "lucide-react";
import type { Client } from "../api/client";
import type { Message, Page, Selection } from "../api/types";
import { referenceId } from "../api/types";
import { RecordInspector } from "./RecordInspector";
import { usePolling } from "../workspace/usePolling";
import { timestamp } from "../workspace/format";

export function PartitionInbox({
  client,
  partition,
  selections,
  toggle,
  save,
}: {
  client: Client;
  partition: number;
  selections: Map<string, Selection>;
  toggle: (value: Message) => void;
  save: (value: Selection) => void;
}) {
  const [cursor, setCursor] = useState<string>();
  const [offsetInput, setOffsetInput] = useState("");
  const [selected, setSelected] = useState<string>();
  const request = useCallback(
    (signal: AbortSignal) =>
      client.get<Page>(
        `inbox?partition=${partition}&limit=25${cursor === undefined ? "" : `&offset=${cursor}`}`,
        signal,
      ),
    [client, partition, cursor],
  );
  const { data, error, reload } = usePolling(request, 5000);
  const offset = selected ?? data?.messages[0]?.reference.offset;
  return (
    <div className="split-workspace">
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Records</h2>
            <p className="panel-subtitle">
              Partition {partition} · offsets {data?.beginningOffset ?? "…"}–
              {data?.endOffset ?? "…"} (end exclusive)
            </p>
          </div>
          <button className="icon-button" aria-label="Refresh inbox" onClick={reload}>
            <RefreshCw size={16} />
          </button>
        </div>
        <form
          className="offset-form"
          onSubmit={(event) => {
            event.preventDefault();
            setCursor(offsetInput || undefined);
          }}
        >
          <label htmlFor="offset">Jump to offset</label>
          <input
            id="offset"
            inputMode="numeric"
            pattern="0|[1-9][0-9]*"
            value={offsetInput}
            onChange={(event) => setOffsetInput(event.target.value)}
            placeholder="Earliest retained"
          />
          <button className="button" aria-label="Go to offset">
            <Search size={14} />
          </button>
        </form>
        {error && (
          <div className="error-banner" role="alert">
            {error.message}
          </div>
        )}
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Select</th>
                <th>Offset / key</th>
                <th>Payload</th>
                <th className="optional-column">Received</th>
              </tr>
            </thead>
            <tbody>
              {data?.messages.map((message) => {
                const id = referenceId(message.reference);
                const checked = selections.has(id);
                return (
                  <tr key={id} className={message.reference.offset === offset ? "selected" : ""}>
                    <td>
                      <input
                        type="checkbox"
                        aria-label={`Select record ${id}`}
                        checked={checked}
                        disabled={!checked && selections.size >= 100}
                        onChange={() => toggle(message)}
                      />
                    </td>
                    <td>
                      <button
                        className="row-link mono"
                        onClick={() => setSelected(message.reference.offset)}
                      >
                        {message.reference.offset}
                      </button>
                      <p className="message-key muted">{message.key ?? "null / binary key"}</p>
                    </td>
                    <td>
                      <span
                        className={`status ${message.validJson ? "status-completed" : "status-failed"}`}
                      >
                        <span />
                        {message.validJson ? "JSON" : "REPAIR"}
                      </span>
                      <p className="record-error" title={message.error ?? message.problem ?? ""}>
                        {message.error ?? message.problem ?? ""}
                      </p>
                    </td>
                    <td className="optional-column muted">{timestamp(message.timestamp)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {data && !data.messages.length && (
          <div className="empty-state">
            <h3>No records in this range</h3>
            <p>
              {error
                ? "Check broker connectivity."
                : "Choose another partition or add demo records."}
            </p>
          </div>
        )}
        <div className="table-footer">
          <button
            className="text-button"
            onClick={() => {
              setCursor(undefined);
              setSelected(undefined);
            }}
          >
            Earliest
          </button>
          <span>{data?.messages.length ?? 0} records</span>
          <button
            className="text-button"
            disabled={!data || BigInt(data.nextOffset) >= BigInt(data.endOffset)}
            onClick={() => {
              if (data) {
                setCursor(data.nextOffset);
                setSelected(undefined);
              }
            }}
          >
            Next page →
          </button>
        </div>
      </section>
      {offset !== undefined ? (
        <RecordInspector
          key={`${partition}:${offset}`}
          client={client}
          partition={partition}
          offset={offset}
          selection={selections.get(`${partition}:${offset}`)}
          save={save}
        />
      ) : (
        <div className="panel empty-state">
          <h3>Select a record to inspect</h3>
          <p>Payload, original headers and repair controls appear here.</p>
        </div>
      )}
    </div>
  );
}
