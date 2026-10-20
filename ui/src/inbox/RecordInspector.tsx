import { useCallback, useState } from "react";
import type { Client } from "../api/client";
import type { Selection, Source } from "../api/types";
import { referenceId } from "../api/types";
import { usePolling } from "../workspace/usePolling";
import { pretty } from "../workspace/format";

export function RecordInspector({
  client,
  partition,
  offset,
  selection,
  save,
}: {
  client: Client;
  partition: number;
  offset: string;
  selection: Selection | undefined;
  save: (value: Selection) => void;
}) {
  const request = useCallback(
    (signal: AbortSignal) => client.get<Source>(`inbox/${partition}/${offset}`, signal),
    [client, partition, offset],
  );
  const { data, error, reload } = usePolling(request, 60000);
  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">RECORD INSPECTOR</p>
          <h2>
            Partition {partition} · Offset {offset}
          </h2>
        </div>
        <span className="version-tag">READ ONLY SOURCE</span>
      </div>
      {error && (
        <div className="error-banner" role="alert">
          {error.message}
          <button onClick={reload} className="text-button">
            Retry
          </button>
        </div>
      )}
      {data ? (
        <Editor key={data.sourceHash} source={data} selection={selection} save={save} />
      ) : (
        <div className="empty-state">Loading record…</div>
      )}
    </section>
  );
}

function Editor({
  source,
  selection,
  save,
}: {
  source: Source;
  selection: Selection | undefined;
  save: (value: Selection) => void;
}) {
  const [tab, setTab] = useState("payload");
  const [text, setText] = useState(selection?.editedJson ?? source.payloadText ?? "");
  const [error, setError] = useState("");
  function add() {
    try {
      JSON.parse(text);
      save({
        ...source.reference,
        expectedHash: source.sourceHash,
        editedJson: text === source.payloadText ? null : text,
      });
      setError("");
    } catch {
      setError("Enter valid JSON before adding this record.");
    }
  }
  return (
    <>
      <div className="inspector-meta">
        <span>
          Key <code>{source.keyText ?? (source.keyBase64 ? "binary · see headers" : "null")}</code>
        </span>
        <span className="mono" title={source.sourceHash}>
          SHA256 {source.sourceHash.slice(0, 12)}…
        </span>
      </div>
      <div className="tabs" role="group" aria-label="Record detail">
        <button aria-pressed={tab === "payload"} onClick={() => setTab("payload")}>
          Payload
        </button>
        <button aria-pressed={tab === "headers"} onClick={() => setTab("headers")}>
          Headers ({source.headers.length})
        </button>
      </div>
      <div className="panel-body">
        {tab === "payload" ? (
          <>
            <label htmlFor="payload-json">Replay payload · JSON</label>
            <textarea
              id="payload-json"
              className="payload-area"
              spellCheck={false}
              value={text}
              onChange={(event) => setText(event.target.value)}
              disabled={source.payloadBase64 === null}
            />
            <p className="field-help">
              Editing changes the preview only. The original Kafka record remains unchanged.
            </p>
            {source.problem && (
              <p className="callout">
                <strong>Original record: </strong>
                {source.problem}
              </p>
            )}
            {source.payloadText === null && source.payloadBase64 !== null && (
              <details>
                <summary>Original bytes · base64</summary>
                <pre>{source.payloadBase64}</pre>
              </details>
            )}
            {error && (
              <p role="alert" className="error-banner">
                {error}
              </p>
            )}
            <button
              className="button primary"
              disabled={source.payloadBase64 === null}
              onClick={add}
            >
              {selection ? "Update selection" : "Add to replay selection"}
            </button>
            {selection && (
              <p className="field-help">
                Record {referenceId(source.reference)} is selected. Create a preview to freeze these
                bytes.
              </p>
            )}
            {selection && text !== (selection.editedJson ?? source.payloadText ?? "") && (
              <p className="callout" role="status">
                Unsaved edit: use Update selection to include these changes in the preview.
              </p>
            )}
          </>
        ) : (
          <>
            <h3>Original headers</h3>
            <table className="header-table">
              <tbody>
                {source.headers.map((header, index) => (
                  <tr key={index}>
                    <td>{header.name}</td>
                    <td className="mono">{header.text ?? header.base64 ?? "null"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <details>
              <summary>Key bytes · base64</summary>
              <pre>{source.keyBase64 ?? "null"}</pre>
            </details>
            <details>
              <summary>Original payload</summary>
              <pre>{pretty(source.payloadText)}</pre>
            </details>
          </>
        )}
      </div>
    </>
  );
}
