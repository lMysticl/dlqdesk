export function Routing({ source, target }: { source: string; target: string }) {
  return (
    <ol className="routing" aria-label="Replay route">
      <li>
        <small>01 / SOURCE · READ ONLY</small>
        <strong>{source}</strong>
      </li>
      <li>
        <small>02 / REVIEW</small>
        <strong>Inspect → repair → freeze</strong>
      </li>
      <li>
        <small>03 / EXPLICIT PUBLISH</small>
        <strong>{target}</strong>
      </li>
    </ol>
  );
}
