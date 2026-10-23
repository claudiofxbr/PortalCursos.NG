/**
 * Regras puras do pedido de exceção ao valor da tabela (sem React). Quem pode pedir e quem decide é SEMPRE
 * decidido pelo servidor (GET /finance/charge-policy); aqui só se valida a digitação e se escolhe o que mostrar.
 */
import type { CatalogItem } from './chargeCatalog';

export const MIN_REASON_LENGTH = 10;

export interface ChargePolicy {
  /** Regra "valor sempre da tabela" VALENDO agora (ligada e com a tabela pronta). */
  enforceTablePrice: boolean;
  canRequestException: boolean;
  canDecideException: boolean;
  /** Todos os valores globais da tabela estão preenchidos. */
  tableReady: boolean;
  /** Quantos valores globais ainda faltam. */
  blankValues: number;
}

/** Enquanto a política não chega (ou falha), a tela não oferece nada além do que já oferecia. */
export const DEFAULT_POLICY: ChargePolicy = {
  enforceTablePrice: false,
  canRequestException: false,
  canDecideException: false,
  tableReady: false,
  blankValues: 0,
};

/** Aceita só o formato esperado; qualquer outra coisa vira o padrão desligado (nunca libera nada por engano). */
export function normalizePolicy(data: unknown): ChargePolicy {
  if (typeof data !== 'object' || data === null || Array.isArray(data)) return DEFAULT_POLICY;
  const raw = data as Record<string, unknown>;
  return {
    enforceTablePrice: raw.enforceTablePrice === true,
    canRequestException: raw.canRequestException === true,
    canDecideException: raw.canDecideException === true,
    tableReady: raw.tableReady === true,
    blankValues: typeof raw.blankValues === 'number' && raw.blankValues >= 0 ? raw.blankValues : 0,
  };
}

export type RuleTone = 'waiting' | 'active' | 'off';

/** Aviso da tela de valores: em que ponto está a ativação da regra "valor sempre da tabela". */
export function ruleBanner(policy: ChargePolicy): { tone: RuleTone; text: string } {
  if (!policy.tableReady) {
    const missing = policy.blankValues > 0
      ? `${policy.blankValues === 1 ? 'Falta 1 valor' : `Faltam ${policy.blankValues} valores`} (cobranças sem curso)`
      : 'A tabela ainda não tem os valores cadastrados';
    return {
      tone: 'waiting',
      text: `A regra "valor sempre da tabela" está em espera. ${missing}. Ao preencher todos, ela passa a valer sozinha; `
        + 'até lá, o valor ainda pode ser digitado ao lançar uma cobrança.',
    };
  }
  if (policy.enforceTablePrice) {
    return {
      tone: 'active',
      text: 'Regra ativa: o valor de toda cobrança vem desta tabela. Um valor diferente só por pedido da secretária do curso, '
        + 'aprovado por um administrador.',
    };
  }
  return { tone: 'off', text: 'Tabela completa, mas a regra "valor sempre da tabela" está desligada na configuração do servidor.' };
}

export type ExceptionStatus ='PENDING' | 'APPROVED' | 'REJECTED';

export interface ChargeException {
  id: number;
  studentId: number;
  studentName: string | null;
  academicLevel: 'GRADUATION' | 'POSTGRADUATE';
  chargePriceId: number;
  procedureName: string | null;
  tableAmount: number;
  requestedAmount: number;
  dueDate: string;
  description: string | null;
  reason: string;
  status: ExceptionStatus;
  requestedBy: string;
  requestedAt: string;
  decidedBy: string | null;
  decidedAt: string | null;
  decisionReason: string | null;
  paymentId: number | null;
}

export interface ExceptionForm {
  requestedAmount: string;
  reason: string;
}

export const EMPTY_EXCEPTION_FORM: ExceptionForm = { requestedAmount: '', reason: '' };

export function statusLabel(status: ExceptionStatus): string {
  switch (status) {
    case 'PENDING': return 'Aguardando aprovação';
    case 'APPROVED': return 'Aprovado';
    case 'REJECTED': return 'Rejeitado';
  }
}

export function formatMoney(value: number): string {
  return `R$ ${value.toFixed(2).replace('.', ',')}`;
}

/** Mensagem de validação do pedido, ou null se pode enviar. */
export function validateExceptionRequest(form: ExceptionForm, tableValue: number): string | null {
  const amount = parseFloat(form.requestedAmount);
  if (Number.isNaN(amount) || amount < 0) return 'Informe o valor pedido (zero ou maior).';
  if (amount === tableValue) return 'O valor pedido é igual ao da tabela; lance a cobrança normalmente.';
  if (form.reason.trim().length < MIN_REASON_LENGTH) {
    return `Informe a justificativa (mínimo de ${MIN_REASON_LENGTH} caracteres).`;
  }
  return null;
}

export function buildExceptionPayload(
  form: ExceptionForm,
  item: CatalogItem,
  studentId: string,
  academicLevel: 'GRADUATION' | 'POSTGRADUATE',
  dueDate: string,
  description: string,
): Record<string, unknown> {
  return {
    studentId: parseInt(studentId, 10),
    academicLevel,
    chargePriceId: item.priceId,
    dueDate,
    description: description.trim() === '' ? null : description,
    requestedAmount: parseFloat(form.requestedAmount),
    reason: form.reason.trim(),
  };
}

/** Texto único para falhas ao pedir ou decidir uma exceção. */
export function exceptionErrorMessage(status?: number, serverMessage?: unknown): string {
  if (typeof serverMessage === 'string' && serverMessage.trim() !== '') return serverMessage;
  if (status === 403) return 'Você não tem permissão para esta ação.';
  if (status === 404) return 'Pedido não encontrado.';
  return 'Não foi possível concluir a ação. Tente novamente em instantes.';
}
