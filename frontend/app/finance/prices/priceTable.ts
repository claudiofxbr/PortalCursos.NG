/**
 * Regras puras da tabela de valores das cobranças (sem React). A decisão de quem pode o quê é SEMPRE do
 * servidor; aqui só se formata, valida a digitação e escolhe o que mostrar com base no que a API devolveu.
 */

export type PriceRole = 'ADMIN' | 'HEAD' | 'SECRETARIA';

export interface PendingProposal {
  id: number;
  value: number;
  proposedBy: string;
  proposedAt: string;
  reason?: string | null;
}

export interface PriceRow {
  id: number;
  code: string;
  category: string;
  name: string;
  academicScope: 'ALL' | 'GRADUATION' | 'POSTGRAD';
  courseId: string | null;
  currentValue: number | null;
  version: number;
  canEdit: boolean;
  canPropose: boolean;
  pending: PendingProposal | null;
}

export interface PriceTable {
  role: PriceRole;
  rows: PriceRow[];
}

const SCOPE_LABEL: Record<PriceRow['academicScope'], string> = {
  ALL: 'Graduação e Pós',
  GRADUATION: 'Graduação',
  POSTGRAD: 'Pós-graduação',
};

export const scopeLabel = (scope: PriceRow['academicScope']): string => SCOPE_LABEL[scope] ?? scope;

export const roleLabel = (role: PriceRole): string =>
  role === 'ADMIN' ? 'Administrador' : role === 'HEAD' ? 'Chefe de secretaria' : 'Secretaria (somente leitura)';

export function formatMoney(value: number | null | undefined): string {
  if (value === null || value === undefined) return '— a preencher';
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

/** "1.234,50", "1234,5" ou "1234.50" → 1234.5; vazio, negativo, 3+ casas ou acima do limite → null. */
export function parseMoney(text: string): number | null {
  const raw = text.trim().replace(/^R\$\s*/, '');
  if (!raw) return null;
  let normalized = raw;
  if (raw.includes(',')) {
    normalized = raw.replace(/\./g, '').replace(',', '.');
  }
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) return null;
  const value = Number(normalized);
  return Number.isFinite(value) && value <= 9999999.99 ? value : null;
}

/** Quem vê o atalho da tabela; o servidor ainda decide o acesso de verdade. */
export function mayOpenPriceTable(roles: unknown): boolean {
  if (!Array.isArray(roles)) return false;
  const names = roles.map((r) => (typeof r === 'string' ? r : (r as { name?: string })?.name));
  return names.some((n) => n === 'ROLE_ADMIN' || n === 'ROLE_ROOT_MASTER' || n === 'ROLE_SECRETARIA');
}

export function groupByCategory(rows: PriceRow[]): Array<{ category: string; rows: PriceRow[] }> {
  const groups = new Map<string, PriceRow[]>();
  for (const row of rows) {
    const list = groups.get(row.category) ?? [];
    list.push(row);
    groups.set(row.category, list);
  }
  return Array.from(groups, ([category, list]) => ({ category, rows: list }));
}

export function priceErrorMessage(status?: number, serverMessage?: unknown): string {
  if (status === 403) return 'Você não tem permissão para esta ação.';
  if (status === 404) return 'Cobrança não encontrada (ou fora do seu escopo).';
  if (typeof serverMessage === 'string' && serverMessage) return serverMessage;
  return 'Não foi possível concluir a operação. Tente novamente.';
}
