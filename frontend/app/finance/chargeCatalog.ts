/**
 * Regras do formulário "Nova cobrança" ligadas ao catálogo de procedimentos (GET /api/finance/charge-catalog).
 * O servidor é quem decide o valor; aqui só se escolhe o procedimento, se mostra o que vale e se monta o envio.
 */

export type ValueSource = 'COURSE' | 'GLOBAL';

export interface CatalogItem {
  priceId: number;
  code: string;
  name: string;
  group: string;
  paymentCategory: string;
  processType: string | null;
  value: number | null;
  valueSource: ValueSource;
  valueDefined: boolean;
  defaultDescription: string;
}

export interface ChargeForm {
  studentId: string;
  amount: string;
  dueDate: string;
  category: string;
  secretaryProcessType: string;
  description: string;
}

/** Valores que existem no backend (EPaymentCategory / ESecretaryProcessType); o antigo 'MENSALIDADE'/'REGULAR' não existia. */
export const EMPTY_CHARGE: ChargeForm = {
  studentId: '',
  amount: '',
  dueDate: '',
  category: 'TUITION',
  secretaryProcessType: 'OTHER',
  description: '',
};

/** Preenche valor, categoria, tipo de processo e descrição a partir do procedimento escolhido. */
export function applyProcedure(form: ChargeForm, item: CatalogItem, previous?: CatalogItem | null): ChargeForm {
  const descriptionIsAuto = form.description.trim() === '' || form.description === previous?.defaultDescription;
  return {
    ...form,
    amount: item.value === null ? '' : item.value.toFixed(2),
    category: item.paymentCategory,
    secretaryProcessType: item.processType ?? 'OTHER',
    description: descriptionIsAuto ? item.defaultDescription : form.description,
  };
}

/** Volta ao modo manual: limpa o valor vindo da tabela e a descrição que era automática. */
export function clearProcedure(form: ChargeForm, previous: CatalogItem | null): ChargeForm {
  const descriptionWasAuto = previous !== null && form.description === previous.defaultDescription;
  return { ...form, amount: '', description: descriptionWasAuto ? '' : form.description };
}

export function groupItems(items: CatalogItem[]): { group: string; items: CatalogItem[] }[] {
  const groups = new Map<string, CatalogItem[]>();
  for (const item of items) {
    const list = groups.get(item.group) ?? [];
    list.push(item);
    groups.set(item.group, list);
  }
  return Array.from(groups, ([group, list]) => ({ group, items: list }));
}

export function sourceLabel(item: CatalogItem): string {
  if (!item.valueDefined) return 'Valor não definido na tabela';
  return item.valueSource === 'COURSE' ? 'Valor do curso do aluno' : 'Valor da tabela (todos os cursos)';
}

export function optionLabel(item: CatalogItem): string {
  return item.valueDefined ? item.name : `${item.name} (valor não definido)`;
}

/** Mensagem única para os problemas ao carregar a lista de procedimentos. */
export function catalogErrorMessage(status?: number): string {
  if (status === 403) return 'Seu perfil não pode consultar os procedimentos cobráveis.';
  if (status === 404) return 'Aluno não encontrado para consultar os procedimentos.';
  return 'Não foi possível carregar os procedimentos. Tente novamente em instantes.';
}

/** Retorna a mensagem de erro de validação, ou null se o formulário pode ser enviado. */
export function validateCharge(form: ChargeForm, selected: CatalogItem | null, catalogFailed: boolean): string | null {
  if (!form.studentId) return 'Informe o aluno.';
  if (!form.dueDate) return 'Informe a data de vencimento.';
  if (selected) {
    if (!selected.valueDefined) return 'Este procedimento ainda não tem valor definido na tabela de valores.';
    return null;
  }
  if (catalogFailed) return 'A lista de procedimentos não carregou; recarregue antes de lançar a cobrança.';
  const amount = parseFloat(form.amount);
  if (Number.isNaN(amount) || amount < 0) return 'Informe um valor válido (zero ou maior).';
  return null;
}

/**
 * Corpo do POST /finance/charge. Com procedimento, o valor e a classificação ficam por conta do servidor
 * (não se envia amount). Sem procedimento, o tipo de processo só vai quando a categoria é processo interno.
 */
export function buildChargePayload(
  form: ChargeForm,
  selected: CatalogItem | null,
  academicLevel: 'GRADUATION' | 'POSTGRADUATE',
): Record<string, unknown> {
  const base = {
    studentId: parseInt(form.studentId, 10),
    dueDate: form.dueDate,
    description: form.description,
    academicLevel,
  };
  if (selected) {
    return { ...base, chargePriceId: selected.priceId };
  }
  return {
    ...base,
    amount: parseFloat(form.amount),
    category: form.category,
    ...(form.category === 'INTERNAL_PROCESS' ? { secretaryProcessType: form.secretaryProcessType } : {}),
  };
}
