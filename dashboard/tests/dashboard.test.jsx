import {
  act,
  fireEvent,
  render,
  renderHook,
  screen,
  within,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "../src/App";
import { ApiError, createApi, mergeEvents, validate } from "../src/api";
import {
  mockApi,
  sampleEvents,
  sampleId,
  sampleMeta,
  sampleRecord,
} from "../src/mock";
import { POLL_MS, useEvents, useLookup } from "../src/hooks";
const page = {
  ...sampleMeta,
  events: sampleEvents,
  nextCursor: "next+/=",
  throughBlock: "5",
};
afterEach(() => vi.useRealTimers());

describe("read-only API contract", () => {
  it("uses existing routes and encodes cursors and IDs", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue({ ok: true, json: async () => page });
    const api = createApi(fetcher);
    await api.events("next+/=");
    expect(fetcher.mock.calls[0][0]).toBe(
      "/v1/registry/events?cursor=next%2B%2F%3D",
    );
    fetcher.mockResolvedValue({ ok: true, json: async () => sampleRecord });
    await api.threat(sampleId);
    expect(fetcher.mock.calls[1][0]).toBe("/v1/registry/threats/" + sampleId);
    fetcher.mockResolvedValue({
      ok: true,
      json: async () => mockApi.submission("sample-pending"),
    });
    await api.submission("sample-pending");
    expect(fetcher.mock.calls[2][0]).toBe(
      "/v1/threat-submissions/sample-pending",
    );
    expect(
      fetcher.mock.calls.every(
        ([, options]) => !options.method || options.method === "GET",
      ),
    ).toBe(true);
  });
  it("rejects responses for a different record", async () => {
    const api = createApi(async () => ({
      ok: true,
      json: async () => sampleRecord,
    }));
    await expect(api.threat("0x" + "e".repeat(64))).rejects.toMatchObject({
      code: "RESPONSE_ID_MISMATCH",
    });
  });
  it("rejects unknown mock IDs including prototype names", async () => {
    await expect(mockApi.submission("toString")).rejects.toMatchObject({
      code: "SUBMISSION_NOT_FOUND",
    });
  });
  it("preserves Backend error codes and never supplies fallback data", async () => {
    const api = createApi(async () => ({
      ok: false,
      status: 409,
      json: async () => ({ errorCode: "CURSOR_REORG" }),
    }));
    await expect(api.events()).rejects.toMatchObject({
      code: "CURSOR_REORG",
      status: 409,
    });
  });
  it("rejects malformed data and unsupported event types", () => {
    expect(() => validate({ ...page, proofMode: undefined }, "events")).toThrow(
      "INVALID_BACKEND_RESPONSE",
    );
    expect(() =>
      validate(
        { ...page, events: [{ ...sampleEvents[0], event: "Invented" }] },
        "events",
      ),
    ).toThrow();
    expect(() =>
      validate({ ...sampleRecord, riskScore: 10001 }, "threat"),
    ).toThrow();
  });
  it("reports transport failures and invalid JSON", async () => {
    await expect(
      createApi(async () => {
        throw new TypeError();
      }).events(),
    ).rejects.toMatchObject({ code: "BACKEND_UNAVAILABLE" });
    await expect(
      createApi(async () => ({
        ok: true,
        json: async () => {
          throw new Error();
        },
      })).events(),
    ).rejects.toMatchObject({ code: "INVALID_BACKEND_RESPONSE" });
  });
  it("aborts timed-out requests", async () => {
    vi.useFakeTimers();
    const fetcher = vi.fn(
      (_, { signal }) =>
        new Promise((resolve, reject) =>
          signal.addEventListener("abort", () => reject(new Error("aborted"))),
        ),
    );
    const promise = createApi(fetcher).events();
    const assertion = expect(promise).rejects.toMatchObject({
      code: "REQUEST_TIMEOUT",
    });
    await vi.advanceTimersByTimeAsync(10000);
    await assertion;
  });
  it("deduplicates exact event identities without dropping same-block logs", () => {
    const second = { ...sampleEvents[0], logIndex: "1" };
    expect(mergeEvents(sampleEvents, [...sampleEvents, second])).toHaveLength(
      2,
    );
    const huge = { ...second, blockNumber: "90071992547409930000" };
    expect(mergeEvents(sampleEvents, [huge])[0]).toEqual(huge);
  });
});

describe("polling and stale-response safety", () => {
  it("resumes with cursor, retains stale data on outage, and clears after reorg", async () => {
    vi.useFakeTimers();
    const api = {
      events: vi
        .fn()
        .mockResolvedValueOnce(page)
        .mockRejectedValueOnce(new ApiError("BACKEND_UNAVAILABLE"))
        .mockRejectedValueOnce(new ApiError("CURSOR_REORG", 409))
        .mockResolvedValue({ ...page, events: [] }),
    };
    const { result, unmount } = renderHook(() => useEvents(api));
    await act(async () => {});
    expect(result.current.events).toHaveLength(1);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_MS);
    });
    expect(result.current.events).toHaveLength(1);
    expect(result.current.error).toBe("BACKEND_UNAVAILABLE");
    expect(api.events.mock.calls[1][0]).toBe(page.nextCursor);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_MS);
    });
    expect(result.current.events).toHaveLength(0);
    expect(result.current.epoch).toBe(1);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_MS);
    });
    expect(api.events.mock.calls[3][0]).toBeUndefined();
    unmount();
    const count = api.events.mock.calls.length;
    await vi.advanceTimersByTimeAsync(POLL_MS * 2);
    expect(api.events).toHaveBeenCalledTimes(count);
  });
  it("discards results for a previously selected threat", async () => {
    let complete;
    const api = {
      threat: vi
        .fn()
        .mockImplementationOnce(
          () =>
            new Promise((resolve) => {
              complete = resolve;
            }),
        )
        .mockResolvedValue({ ...sampleRecord, threatId: "new" }),
    };
    const { result, rerender } = renderHook(
      ({ id }) => useLookup(api, "threat", id),
      { initialProps: { id: "old" } },
    );
    rerender({ id: "new" });
    await act(async () => {});
    await act(async () => {
      complete(sampleRecord);
    });
    expect(result.current.data.threatId).toBe("new");
  });
  it("clears successful status after a later request fails", async () => {
    vi.useFakeTimers();
    const api = {
      submission: vi
        .fn()
        .mockResolvedValueOnce(await mockApi.submission("sample-registered"))
        .mockRejectedValue(new ApiError("BACKEND_UNAVAILABLE")),
    };
    const { result } = renderHook(() =>
      useLookup(api, "submission", "sample-registered", true),
    );
    await act(async () => {});
    expect(result.current.data.status).toBe("REGISTERED");
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_MS);
    });
    expect(result.current.data).toBeNull();
  });
});

describe("dashboard views and provenance", () => {
  it("does not turn an unavailable backend into a mock success", async () => {
    render(
      <App
        realApi={{
          events: async () => {
            throw new ApiError("BACKEND_UNAVAILABLE");
          },
        }}
      />,
    );
    expect(
      await screen.findByText(/Backend unavailable · displayed events/),
    ).toBeVisible();
    expect(screen.queryByText("MOCK dataset")).not.toBeInTheDocument();
  });
  it("shows real transport and mock proof as separate facts", async () => {
    render(<App realApi={mockApi} />);
    expect(await screen.findByText("REAL backend connection")).toBeVisible();
    expect(screen.getByText("MOCK_PROOF")).toBeVisible();
    expect(screen.getByText("REAL · Backend response")).toBeVisible();
  });
  it("supports explicit mock mode, registry selection, and full proof inspection", async () => {
    render(<App realApi={mockApi} />);
    fireEvent.click(screen.getByRole("button", { name: "Mock fixtures" }));
    expect(await screen.findByText("MOCK dataset")).toBeVisible();
    expect(screen.getByText(/SAMPLE fixtures only/)).toBeVisible();
    fireEvent.click(
      await screen.findByRole("button", { name: "Inspect threat " + sampleId }),
    );
    expect(
      await within(
        screen.getByRole("region", { name: "Threat detail" }),
      ).findByText(sampleRecord.voiceprintHash),
    ).toBeVisible();
    fireEvent.click(screen.getByText("Stored proof · 6 bytes"));
    expect(screen.getByText(sampleRecord.zkProof)).toBeVisible();
  });
  it("looks up pending submission without claiming registration", async () => {
    render(<App realApi={mockApi} />);
    fireEvent.click(screen.getByRole("button", { name: "Mock fixtures" }));
    fireEvent.change(screen.getByLabelText("Submission ID"), {
      target: { value: "sample-pending" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Track" }));
    expect(await screen.findByText("SUBMITTED")).toBeVisible();
    expect(screen.queryByText("REGISTERED")).not.toBeInTheDocument();
  });
  it("clears mock selections when returning to backend", async () => {
    render(<App realApi={mockApi} />);
    fireEvent.click(screen.getByRole("button", { name: "Mock fixtures" }));
    fireEvent.click(
      await screen.findByRole("button", { name: "Inspect threat " + sampleId }),
    );
    await within(
      screen.getByRole("region", { name: "Threat detail" }),
    ).findByText(sampleRecord.voiceprintHash);
    fireEvent.click(screen.getByRole("button", { name: "Backend API" }));
    expect(
      screen.getByText("Select a registration or look up a Threat ID."),
    ).toBeVisible();
  });
  it("rejects invalid IDs and displays missing threat errors", async () => {
    render(<App realApi={mockApi} />);
    fireEvent.change(screen.getByLabelText("Look up Threat ID"), {
      target: { value: "bad" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Inspect", exact: true }),
    );
    expect(screen.getByText(/Enter a 0x-prefixed/)).toBeVisible();
    fireEvent.change(screen.getByLabelText("Look up Threat ID"), {
      target: { value: "0x" + "e".repeat(64) },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Inspect", exact: true }),
    );
    expect(
      await screen.findByText(/THREAT_NOT_FOUND. Record unavailable/),
    ).toBeVisible();
  });
});
