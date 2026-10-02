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
