import type { DbStatus, HistoryEntry, MonthSummary, PlanConfig, Summary, Tower } from "./types";

const BASE = process.env.NEXT_PUBLIC_BASE_PATH || "";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}/api/tokens/${path}`, { cache: "no-store", ...init });
  if (!res.ok) {
    let message = `Erro ${res.status}`;
    try {
      const body = await res.json();
      if (body?.error) message = String(body.error);
    } catch {
      /* corpo não-JSON */
    }
    throw new Error(message);
  }
  return res.json() as Promise<T>;
}

export const getSummary = () => request<Summary>("summary");
export const getDbStatus = () => request<DbStatus>("db");
export const getMonth = () => request<MonthSummary>("month");
export const getTower = () => request<Tower>("tower");
export const getHistory = (cycles = 8) => request<HistoryEntry[]>(`history?cycles=${cycles}`);
export const saveConfig = (config: PlanConfig) =>
  request<PlanConfig>("config", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(config),
  });
