export function timestamp(value: string | number) {
  return new Date(value).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}
export function pretty(value: string | null) {
  if (value === null) return "Binary or null value";
  try {
    return JSON.stringify(JSON.parse(value) as unknown, null, 2);
  } catch {
    return value;
  }
}
export function label(value: string) {
  return value.replaceAll("_", " ").toLowerCase();
}
