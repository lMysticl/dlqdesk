export function Status({ value }: { value: string }) {
  return (
    <span className={`status status-${value.toLowerCase().replaceAll("_", "-")}`}>
      <span aria-hidden="true" />
      {value.replaceAll("_", " ")}
    </span>
  );
}
