import { useEffect, useState } from "react";
import { mergeEvents } from "./api";
export const POLL_MS = 3000;
export function useEvents(api) {
  const [state, set] = useState({
    events: [],
    meta: null,
    error: "",
    updated: null,
    epoch: 0,
  });
  useEffect(() => {
    let alive = true,
      cursor,
      domain,
      timer;
    const controller = new AbortController();
    async function poll() {
      try {
        const page = await api.events(cursor, controller.signal);
        if (!alive) return;
        const nextDomain = `${page.chainId}:${page.registryAddress.toLowerCase()}`;
        if (domain && domain !== nextDomain) {
          cursor = undefined;
          domain = undefined;
          set((old) => ({
            events: [],
            meta: null,
            error: "CHAIN_CHANGED — rebuilding event view",
            updated: null,
            epoch: old.epoch + 1,
          }));
        } else {
          domain = nextDomain;
          cursor = page.nextCursor;
          set((old) => ({
            ...old,
            events: mergeEvents(old.events, page.events),
            meta: page,
            error: "",
            updated: new Date(),
          }));
        }
      } catch (error) {
        if (!alive) return;
        if (
          ["CURSOR_REORG", "CHAIN_CHANGED", "INVALID_CURSOR"].includes(
            error.code,
          )
        ) {
          cursor = undefined;
          domain = undefined;
          set((old) => ({
            events: [],
            meta: null,
            error: `${error.message} — rebuilding event view`,
            updated: null,
            epoch: old.epoch + 1,
          }));
        } else
          set((old) => ({
            ...old,
            error: error.message || "BACKEND_UNAVAILABLE",
          }));
      } finally {
        if (alive) timer = setTimeout(poll, POLL_MS);
      }
    }
    poll();
    return () => {
      alive = false;
      controller.abort();
      clearTimeout(timer);
    };
  }, [api]);
  return state;
}
export function useLookup(api, method, id, poll = false) {
  const [state, set] = useState({ data: null, error: "", loading: false });
  useEffect(() => {
    let alive = true,
      timer;
    const controller = new AbortController();
    set({ data: null, error: "", loading: Boolean(id) });
    if (!id) return;
    async function read() {
      try {
        const data = await api[method](id, controller.signal);
        if (alive) set({ data, error: "", loading: false });
      } catch (error) {
        if (alive) set({ data: null, error: error.message, loading: false });
      } finally {
        if (alive && poll) timer = setTimeout(read, POLL_MS);
      }
    }
    read();
    return () => {
      alive = false;
      clearTimeout(timer);
      controller.abort();
    };
  }, [api, method, id, poll]);
  return state;
}
