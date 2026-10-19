/**
 * Regras puras do pagamento por cartão na tela financeira (sem React, fáceis de testar).
 * A cobrança em si é do PagBank (checkout hospedado); aqui só se decide o que mostrar e para onde ir.
 */

/** Botão de cartão fica oculto até a variável de build ser "true" (a tela atual não muda antes disso). */
export function cardPaymentsEnabled(): boolean {
  return process.env.NEXT_PUBLIC_CARD_PAYMENTS_ENABLED === 'true';
}

/** Só redireciona para https sem credenciais embutidas: o link vem da API e nunca é confiado às cegas. */
export function isSafePayUrl(value: unknown): value is string {
  if (typeof value !== 'string') return false;
  try {
    const u = new URL(value);
    return u.protocol === 'https:' && !u.username && !u.password;
  } catch {
    return false;
  }
}

export function cardCheckoutErrorMessage(status?: number, serverMessage?: unknown): string {
  if (status === 429) return 'Muitas tentativas de pagamento. Aguarde alguns minutos.';
  if (status === 403 || status === 400) {
    if (typeof serverMessage === 'string' && serverMessage) return serverMessage;
    return 'Não foi possível iniciar o pagamento por cartão para esta fatura.';
  }
  if (status === 404) return 'Fatura não encontrada.';
  return 'Pagamento por cartão indisponível no momento. Tente novamente em instantes.';
}

/** Lê ?payment=<id> da URL de retorno do PagBank; qualquer outra coisa é ignorada. */
export function paymentIdFromSearch(search: string): number | null {
  const raw = new URLSearchParams(search).get('payment');
  if (!raw || !/^\d{1,18}$/.test(raw)) return null;
  const id = Number(raw);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}
