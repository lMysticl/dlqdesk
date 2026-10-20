import { useEffect, useRef, useState } from "react";

export function useAction() {
  const controller = useRef<AbortController | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => () => controller.current?.abort(), []);
  async function run<T>(operation: (signal: AbortSignal) => Promise<T>): Promise<T | undefined> {
    if (controller.current) return;
    const current = new AbortController();
    controller.current = current;
    setPending(true);
    setError("");
    try {
      const value = await operation(current.signal);
      if (!current.signal.aborted) return value;
    } catch (failure) {
      if (!current.signal.aborted)
        setError(
          failure instanceof Error
            ? failure.message
            : "The operation failed. Refresh to see its current state.",
        );
    } finally {
      if (!current.signal.aborted) {
        controller.current = null;
        setPending(false);
      }
    }
  }
  return { pending, error, run };
}
