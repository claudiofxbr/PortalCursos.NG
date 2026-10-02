const nf = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 });

/** 1.234 -> "1,2 mil"; 3.400.000 -> "3,4 mi". */
export function formatTokens(n: number): string {
  const abs = Math.abs(n);
  if (abs >= 1_000_000_000) return `${nf.format(n / 1_000_000_000)} bi`;
  if (abs >= 1_000_000) return `${nf.format(n / 1_000_000)} mi`;
  if (abs >= 10_000) return `${nf.format(n / 1_000)} mil`;
  return new Intl.NumberFormat("pt-BR").format(Math.round(n));
}

export function formatPct(p: number): string {
  return `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 }).format(p)}%`;
}

/** 273600 -> "3d 4h"; 5400 -> "1h 30min"; 90 -> "1min". */
export function formatDuration(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const d = Math.floor(s / 86_400);
  const h = Math.floor((s % 86_400) / 3_600);
  const m = Math.floor((s % 3_600) / 60);
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}min`;
  return `${m}min`;
}

export function formatDateTime(iso: string, timeZone: string): string {
  return new Intl.DateTimeFormat("pt-BR", {
    weekday: "short",
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    timeZone,
  }).format(new Date(iso));
}

/** "2026-09-28" -> "28/09" sem passar por Date (evita deslocar o dia por fuso). */
export function formatDay(isoDate: string): string {
  const [, m, d] = isoDate.split("-");
  return `${d}/${m}`;
}

export const WEEKDAYS = ["", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"];

/** Cor de severidade da barra principal (também sinalizada por texto — não depende só de cor). */
export function severity(usedPct: number): "ok" | "warn" | "danger" {
  if (usedPct >= 90) return "danger";
  if (usedPct >= 70) return "warn";
  return "ok";
}

/** 1536 -> "1,5 KB"; 52_428_800 -> "50 MB". */
export function formatBytes(n: number): string {
  const units = ["B", "KB", "MB", "GB", "TB"];
  let v = Math.max(0, n);
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: i === 0 ? 0 : 1 }).format(v)} ${units[i]}`;
}
