export interface PlanConfig {
  planName: string;
  resetDayOfWeek: number; // 1=segunda … 7=domingo
  resetTime: string; // HH:mm
  timezone: string;
  weeklyLimitTokens: number;
  monthlyLimitTokens: number | null; // null = referência estimada (semanal × dias/7)
  countCacheReads: boolean;
}

export interface Slice {
  name: string;
  tokens: number;
  messages: number;
  pct: number;
}

export interface Day {
  index: number;
  date: string; // yyyy-MM-dd
  tokens: number;
  cumulative: number;
}

export interface Summary {
  config: PlanConfig;
  cycle: { start: string; end: string; secondsRemaining: number; elapsedPct: number };
  limit: number;
  used: number;
  remaining: number;
  usedPct: number;
  totals: { input: number; output: number; cacheCreation: number; cacheRead: number; messages: number };
  projection: {
    projectedTotal: number;
    projectedPct: number;
    dailyAverage: number;
    willExceed: boolean;
    exhaustionAt: string | null;
  } | null;
  last5hTokens: number;
  daily: Day[];
  byProcess: Slice[];
  byModel: Slice[];
}

export interface HistoryEntry {
  start: string;
  end: string;
  used: number;
  limit: number;
  usedPct: number;
  current: boolean;
}

export interface NeonApiInfo {
  enabled: boolean;
  ok: boolean;
  error: string | null;
  project: {
    name: string | null; regionId: string | null; pgVersion: number | null; computeTimeSeconds: number | null;
    activeTimeSeconds: number | null; writtenDataBytes: number | null; dataTransferBytes: number | null;
    syntheticStorageBytes: number | null; consumptionPeriodStart: string | null; consumptionPeriodEnd: string | null;
  } | null;
  branches: { name: string | null; state: string | null; primary: boolean; logicalSizeBytes: number | null }[];
  endpoints: { id: string | null; state: string | null; minCu: number | null; maxCu: number | null;
    suspendTimeoutSeconds: number | null; poolerEnabled: boolean | null }[];
}

export interface DbStatus {
  connected: boolean;
  latencyMs: number;
  version: string | null;
  databaseSizeBytes: number;
  connections: { total: number; active: number; max: number };
  endpoint: { neon: boolean; pooled: boolean; endpointId: string | null; region: string | null };
  migrations: { status: "OK" | "FAILED" | "UNKNOWN"; latest: string | null; failed: number };
  tables: { name: string; rows: number; sizeBytes: number }[];
  neonApi: NeonApiInfo | null;
}

export interface MonthSummary {
  config: PlanConfig;
  period: { start: string; end: string; daysInMonth: number; dayOfMonth: number; secondsRemaining: number; elapsedPct: number };
  limit: number;
  limitEstimated: boolean;
  used: number;
  remaining: number;
  usedPct: number;
  totals: Summary["totals"];
  projection: Summary["projection"];
  daily: Day[];
  byProcess: Slice[];
  byModel: Slice[];
}

export type TowerStatus = "ok" | "active" | "queued" | "warn" | "danger";
export type TowerCategory = "work" | "queue" | "health" | "auto";

export interface TowerItem {
  id: string;
  category: TowerCategory;
  label: string;
  status: TowerStatus;
  detail: string;
}

export interface Tower {
  generatedAt: string;
  overall: "ok" | "warn" | "danger";
  counts: { ok: number; active: number; queued: number; warn: number; danger: number };
  items: TowerItem[];
}

export interface ReportTable {
  caption: string;
  headers: string[];
  rows: string[][];
}

export interface ReportSection {
  title: string;
  paragraphs: string[];
  bullets: string[];
  table: ReportTable | null;
}

export interface Report {
  generatedAt: string;
  title: string;
  timezone: string;
  sections: ReportSection[];
}
