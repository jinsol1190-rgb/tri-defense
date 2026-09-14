// Read-only client for the existing Backend P0 API. No submission or chain writes.
export class ApiError extends Error {
  constructor(code, status = 0) {
    super(code);
    this.code = code;
    this.status = status;
  }
}
export const isHash = (value) =>
  typeof value === "string" && /^0x[0-9a-fA-F]{64}$/.test(value);
const decimal = (value) =>
  typeof value === "string" && /^(0|[1-9][0-9]*)$/.test(value);
const score = (value) =>
  Number.isInteger(value) && value >= 0 && value <= 10000;
export function validMeta(value) {
  return (
    value &&
    decimal(value.chainId) &&
    /^0x[0-9a-fA-F]{40}$/.test(value.registryAddress) &&
    typeof value.proofMode === "string" &&
    typeof value.transactionMode === "string"
  );
}
export function validate(value, kind) {
  let ok = validMeta(value);
  if (kind === "events")
    ok &&=
      Array.isArray(value.events) &&
      typeof value.nextCursor === "string" &&
      decimal(value.throughBlock) &&
      value.events.every(
        (event) =>
          decimal(event.chainId) &&
          event.chainId === value.chainId &&
          decimal(event.blockNumber) &&
          decimal(event.logIndex) &&
          isHash(event.blockHash) &&
          isHash(event.txHash) &&
          isHash(event.args?.threatId) &&
          (event.event === "ThreatRegistered"
            ? isHash(event.args.voiceprintHash) &&
              score(event.args.riskScore) &&
              decimal(event.args.registeredAt)
            : event.event === "BlacklistPromoted" &&
              isHash(event.args.phoneKey)),
      );
  if (kind === "threat")
    ok &&=
      isHash(value.threatId) &&
      isHash(value.voiceprintHash) &&
      score(value.riskScore) &&
      decimal(value.registeredAt) &&
      /^0x(?:[0-9a-fA-F]{2})*$/.test(value.zkProof);
  if (kind === "submission")
    ok &&=
      typeof value.submissionId === "string" &&
      ["SUBMITTED", "REGISTERED", "FAILED"].includes(value.status) &&
      isHash(value.threatId) &&
      (value.txHash === null || isHash(value.txHash)) &&
      (value.errorCode === null || typeof value.errorCode === "string");
  if (!ok) throw new ApiError("INVALID_BACKEND_RESPONSE");
  return value;
}
export function createApi(fetcher = fetch) {
  async function read(path, kind, signal) {
    const controller = new AbortController();
    const abort = () => controller.abort();
    signal?.addEventListener("abort", abort, { once: true });
    if (signal?.aborted) controller.abort();
    const timeout = setTimeout(abort, 10000);
    try {
      const response = await fetcher(path, {
        signal: controller.signal,
        cache: "no-store",
      });
      let body;
      try {
        body = await response.json();
      } catch {
        throw new ApiError("INVALID_BACKEND_RESPONSE", response.status);
      }
      if (!response.ok)
        throw new ApiError(
          body.errorCode || "BACKEND_UNAVAILABLE",
          response.status,
        );
      return validate(body, kind);
    } catch (error) {
      if (signal?.aborted) throw error;
      if (error instanceof ApiError) throw error;
      throw new ApiError(
        controller.signal.aborted ? "REQUEST_TIMEOUT" : "BACKEND_UNAVAILABLE",
      );
    } finally {
      clearTimeout(timeout);
      signal?.removeEventListener("abort", abort);
    }
  }
  return {
    events: (cursor, signal) =>
      read(
        "/v1/registry/events" +
          (cursor ? "?cursor=" + encodeURIComponent(cursor) : ""),
        "events",
        signal,
      ),
    threat: async (id, signal) => {
      const value = await read(
        "/v1/registry/threats/" + encodeURIComponent(id),
        "threat",
        signal,
      );
      if (value.threatId.toLowerCase() !== id.toLowerCase())
        throw new ApiError("RESPONSE_ID_MISMATCH");
      return value;
    },
    submission: async (id, signal) => {
      const value = await read(
        "/v1/threat-submissions/" + encodeURIComponent(id),
        "submission",
        signal,
      );
      if (value.submissionId !== id) throw new ApiError("RESPONSE_ID_MISMATCH");
      return value;
    },
  };
}
export function mergeEvents(previous, incoming) {
  const entries = new Map(previous.map((event) => [eventKey(event), event]));
  incoming.forEach((event) => entries.set(eventKey(event), event));
  return [...entries.values()].sort((a, b) => {
    const block = BigInt(a.blockNumber) - BigInt(b.blockNumber);
    const order = block || BigInt(a.logIndex) - BigInt(b.logIndex);
    return order < 0n ? 1 : order > 0n ? -1 : 0;
  });
}
export const eventKey = (event) =>
  `${event.chainId}:${event.txHash}:${event.logIndex}`;
export const short = (value) =>
  value ? `${value.slice(0, 10)}…${value.slice(-6)}` : "—";
export const risk = (value) => `${(value / 100).toFixed(2)} / 100`;
