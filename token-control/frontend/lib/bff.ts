/** Regras do BFF: só repassa o que o dashboard e o collector realmente usam. */
export type Rule = { method: string; path: string };

const RULES: Rule[] = [
  { method: "GET", path: "summary" },
  { method: "GET", path: "history" },
  { method: "GET", path: "config" },
  { method: "GET", path: "db" },
  { method: "GET", path: "month" },
  { method: "PUT", path: "config" },
  { method: "POST", path: "usage" },
];

export function isAllowed(method: string, segments: string[]): boolean {
  if (segments.length !== 1) return false;
  return RULES.some((r) => r.method === method && r.path === segments[0]);
}

/** POST /usage traz a própria chave (collector); o resto usa a chave do servidor, após o login do dashboard. */
export function usesClientKey(method: string, segments: string[]): boolean {
  return method === "POST" && segments[0] === "usage";
}
