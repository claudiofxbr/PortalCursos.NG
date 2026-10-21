import { describe, test, expect } from 'vitest';
import {
  applyProcedure, buildChargePayload, catalogErrorMessage, clearProcedure, EMPTY_CHARGE, groupItems, optionLabel,
  sourceLabel, validateCharge, type CatalogItem, type ChargeForm,
} from '@/app/finance/chargeCatalog';

const item = (over: Partial<CatalogItem> = {}): CatalogItem => ({
  priceId: 7,
  code: 'NADA_CONSTA',
  name: 'Certidão de nada consta',
  group: 'Conclusão e Expedição de Documentos',
  paymentCategory: 'INTERNAL_PROCESS',
  processType: 'CERTIFICATE',
  value: 15.5,
  valueSource: 'GLOBAL',
  valueDefined: true,
  defaultDescription: 'Certidão de nada consta',
  ...over,
});

const form = (over: Partial<ChargeForm> = {}): ChargeForm => ({ ...EMPTY_CHARGE, studentId: '1', dueDate: '2026-12-01', ...over });

describe('estado inicial do formulário', () => {
  test('usa só valores que existem no backend (antes: MENSALIDADE e REGULAR)', () => {
    expect(EMPTY_CHARGE.category).toBe('TUITION');
    expect(EMPTY_CHARGE.secretaryProcessType).toBe('OTHER');
  });
});

describe('applyProcedure / clearProcedure', () => {
  test('preenche valor com duas casas, categoria, tipo de processo e descrição', () => {
    const result = applyProcedure(form(), item());
    expect(result.amount).toBe('15.50');
    expect(result.category).toBe('INTERNAL_PROCESS');
    expect(result.secretaryProcessType).toBe('CERTIFICATE');
    expect(result.description).toBe('Certidão de nada consta');
  });

  test('aceita valor zero (procedimento gratuito)', () => {
    expect(applyProcedure(form(), item({ value: 0 })).amount).toBe('0.00');
  });

  test('não sobrescreve a descrição que o usuário escreveu', () => {
    expect(applyProcedure(form({ description: 'Pedido urgente' }), item()).description).toBe('Pedido urgente');
  });

  test('ao trocar de procedimento troca a descrição que era automática', () => {
    const first = item();
    const second = item({ priceId: 8, name: 'Rematrícula', defaultDescription: 'Rematrícula' });
    const afterFirst = applyProcedure(form(), first);
    expect(applyProcedure(afterFirst, second, first).description).toBe('Rematrícula');
  });

  test('sem tipo de processo (cobrança recorrente) cai em OTHER', () => {
    expect(applyProcedure(form(), item({ processType: null, paymentCategory: 'TUITION' })).secretaryProcessType).toBe('OTHER');
  });

  test('valor não definido deixa o campo vazio em vez de inventar um número', () => {
    expect(applyProcedure(form(), item({ value: null, valueDefined: false })).amount).toBe('');
  });

  test('voltar ao modo manual limpa o valor e só a descrição automática', () => {
    const chosen = applyProcedure(form(), item());
    expect(clearProcedure(chosen, item())).toMatchObject({ amount: '', description: '' });
    const edited = { ...chosen, description: 'Texto do usuário' };
    expect(clearProcedure(edited, item()).description).toBe('Texto do usuário');
  });
});

describe('apresentação', () => {
  test('agrupa por categoria mantendo a ordem de chegada', () => {
    const groups = groupItems([item({ priceId: 1, group: 'A' }), item({ priceId: 2, group: 'B' }), item({ priceId: 3, group: 'A' })]);
    expect(groups.map(g => [g.group, g.items.map(i => i.priceId)])).toEqual([['A', [1, 3]], ['B', [2]]]);
  });

  test('rótulos explicam a origem e a falta do valor', () => {
    expect(sourceLabel(item({ valueSource: 'COURSE' }))).toBe('Valor do curso do aluno');
    expect(sourceLabel(item())).toBe('Valor da tabela (todos os cursos)');
    expect(sourceLabel(item({ valueDefined: false, value: null }))).toBe('Valor não definido na tabela');
    expect(optionLabel(item({ valueDefined: false, value: null }))).toMatch(/valor não definido/);
    expect(optionLabel(item())).toBe('Certidão de nada consta');
  });

  test('mensagens de erro do catálogo por status', () => {
    expect(catalogErrorMessage(403)).toMatch(/perfil/);
    expect(catalogErrorMessage(404)).toMatch(/Aluno não encontrado/);
    expect(catalogErrorMessage()).toMatch(/Não foi possível carregar/);
  });
});

describe('validateCharge', () => {
  test('exige aluno e vencimento', () => {
    expect(validateCharge(form({ studentId: '' }), null, false)).toMatch(/aluno/);
    expect(validateCharge(form({ dueDate: '' }), null, false)).toMatch(/vencimento/);
  });

  test('procedimento sem valor definido não pode ser lançado', () => {
    expect(validateCharge(form(), item({ valueDefined: false, value: null }), false)).toMatch(/sem valor|não tem valor/);
  });

  test('procedimento com valor passa sem exigir o campo de valor', () => {
    expect(validateCharge(form({ amount: '' }), item(), false)).toBeNull();
  });

  test('modo manual aceita zero e recusa negativo ou vazio', () => {
    expect(validateCharge(form({ amount: '0' }), null, false)).toBeNull();
    expect(validateCharge(form({ amount: '-1' }), null, false)).toMatch(/valor válido/);
    expect(validateCharge(form({ amount: '' }), null, false)).toMatch(/valor válido/);
  });

  test('falha ao carregar a lista bloqueia o envio em vez de cair no campo digitável', () => {
    expect(validateCharge(form({ amount: '10' }), null, true)).toMatch(/não carregou/);
  });
});

describe('buildChargePayload', () => {
  test('com procedimento envia o chargePriceId e nunca o valor', () => {
    const payload = buildChargePayload(applyProcedure(form(), item()), item(), 'GRADUATION');
    expect(payload).toEqual({
      studentId: 1, dueDate: '2026-12-01', description: 'Certidão de nada consta', academicLevel: 'GRADUATION', chargePriceId: 7,
    });
    expect(payload).not.toHaveProperty('amount');
  });

  test('manual envia valor e categoria; o tipo de processo só em processo interno', () => {
    const tuition = buildChargePayload(form({ amount: '250.5', category: 'TUITION' }), null, 'POSTGRADUATE');
    expect(tuition).toMatchObject({ amount: 250.5, category: 'TUITION', academicLevel: 'POSTGRADUATE' });
    expect(tuition).not.toHaveProperty('secretaryProcessType');
    expect(tuition).not.toHaveProperty('chargePriceId');
    const internal = buildChargePayload(form({ amount: '30', category: 'INTERNAL_PROCESS', secretaryProcessType: 'TRANSCRIPT' }), null, 'GRADUATION');
    expect(internal).toMatchObject({ secretaryProcessType: 'TRANSCRIPT' });
  });
});
