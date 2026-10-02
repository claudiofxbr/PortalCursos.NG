export interface PlanConfig {
  planName: string;
  resetDayOfWeek: number; // 1=segunda … 7=domingo
  resetTime: string; // HH:mm
  timezone: string;
  weeklyLimitTokens: number;
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
