import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { createClient } from "../api/client";
import { RecordInspector } from "../inbox/RecordInspector";
import type { Source } from "../api/types";

afterEach(() => vi.restoreAllMocks());
describe("record repair", () => {
  it("retains the original fingerprint and exact offset when selecting repaired JSON", async () => {
    const original: Source = {
      reference: { partition: 1, offset: "9007199254740993" },
      keyBase64: null,
      keyText: null,
      payloadBase64: "e2JhZA==",
      payloadText: "{bad",
      timestamp: 1,
      headers: [],
      sourceHash: "a".repeat(64),
      validJson: false,
      problem: "Invalid JSON",
    };
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify(original), { status: 200 }),
    );
    const save = vi.fn();
    render(
      <RecordInspector
        client={createClient("a".repeat(32), new URL("http://localhost/api/"))}
        partition={1}
        offset="9007199254740993"
        selection={undefined}
        save={save}
      />,
    );
    const input = await screen.findByLabelText("Replay payload · JSON");
    fireEvent.click(screen.getByRole("button", { name: "Add to replay selection" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Enter valid JSON");
    expect(save).not.toHaveBeenCalled();
    fireEvent.change(input, { target: { value: '{"repaired":true}' } });
    fireEvent.click(screen.getByRole("button", { name: "Add to replay selection" }));
    expect(save).toHaveBeenCalledWith({
      partition: 1,
      offset: "9007199254740993",
      expectedHash: original.sourceHash,
      editedJson: '{"repaired":true}',
    });
    expect(original.payloadText).toBe("{bad");
  });
});
