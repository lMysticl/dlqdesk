export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function createClient(token: string, base = new URL("api/", document.baseURI)) {
  async function request<T>(
    route: string,
    method: string,
    body?: unknown,
    signal?: AbortSignal,
  ): Promise<T> {
    const controller = new AbortController();
    const abort = () => controller.abort(signal?.reason);
    signal?.addEventListener("abort", abort, { once: true });
    if (signal?.aborted) abort();
    const timer = setTimeout(
      () =>
        controller.abort(new DOMException("The server did not respond in time", "TimeoutError")),
      10000,
    );
    try {
      const response = await fetch(new URL(route, base), {
        method,
        headers: {
          Authorization: `Bearer ${token}`,
          ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        signal: controller.signal,
      });
      if (!response.ok) {
        const problem: unknown = await response.json().catch(() => null);
        const detail =
          problem &&
          typeof problem === "object" &&
          "detail" in problem &&
          typeof problem.detail === "string"
            ? problem.detail
            : `Request failed (${response.status})`;
        throw new ApiError(response.status, detail);
      }
      return (await response.json()) as T;
    } finally {
      clearTimeout(timer);
      signal?.removeEventListener("abort", abort);
    }
  }
  return {
    get: <T>(route: string, signal?: AbortSignal) => request<T>(route, "GET", undefined, signal),
    post: <T>(route: string, body?: unknown, signal?: AbortSignal) =>
      request<T>(route, "POST", body, signal),
  };
}

export type Client = ReturnType<typeof createClient>;
