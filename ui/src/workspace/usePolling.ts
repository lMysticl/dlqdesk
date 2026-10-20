import { useCallback, useEffect, useState } from "react";

export function usePolling<T>(request: (signal: AbortSignal) => Promise<T>, interval = 1500) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<Error | null>(null);
  const [revision, setRevision] = useState(0);
  const reload = useCallback(() => setRevision((value) => value + 1), []);

  useEffect(() => {
    let disposed = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const controller = new AbortController();
    async function poll() {
      try {
        const result = await request(controller.signal);
        if (!disposed) {
          setData(result);
          setError(null);
        }
      } catch (failure) {
        if (!disposed) setError(failure instanceof Error ? failure : new Error("Connection lost"));
      } finally {
        if (!disposed) timer = setTimeout(() => void poll(), interval);
      }
    }
    void poll();
    return () => {
      disposed = true;
      clearTimeout(timer);
      controller.abort();
    };
  }, [request, revision, interval]);

  return { data, error, reload };
}
