import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { usePolling } from "../workspace/usePolling";

describe("polling ownership", () => {
  it("aborts the previous read and rejects its stale result", async () => {
    let resolveOld!: (value: string) => void;
    let oldSignal: AbortSignal | undefined;
    const old = vi.fn((signal: AbortSignal) => {
      oldSignal = signal;
      return new Promise<string>((resolve) => {
        resolveOld = resolve;
      });
    });
    const current = vi.fn(async () => "new selection");
    const { result, rerender, unmount } = renderHook(({ request }) => usePolling(request, 60000), {
      initialProps: { request: old as (signal: AbortSignal) => Promise<string> },
    });
    rerender({ request: current });
    await waitFor(() => expect(result.current.data).toBe("new selection"));
    expect(oldSignal?.aborted).toBe(true);
    await act(async () => resolveOld("obsolete selection"));
    expect(result.current.data).toBe("new selection");
    unmount();
  });

  it("shows a connection failure and recovers on an explicit refresh", async () => {
    const request = vi
      .fn<() => Promise<string>>()
      .mockRejectedValueOnce(new Error("Connection lost"))
      .mockResolvedValue("recovered");
    const { result, unmount } = renderHook(() => usePolling(request, 60000));
    await waitFor(() => expect(result.current.error?.message).toBe("Connection lost"));
    act(() => result.current.reload());
    await waitFor(() => expect(result.current.data).toBe("recovered"));
    expect(result.current.error).toBeNull();
    unmount();
  });
});
