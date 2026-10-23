import { describe, test, expect } from 'vitest';
import {
  buildExceptionPayload, DEFAULT_POLICY, EMPTY_EXCEPTION_FORM, exceptionErrorMessage, formatMoney, normalizePolicy,
  ruleBanner, statusLabel, validateExceptionRequest,
} from '@/app/finance/chargeExceptions';
import { EMPTY_CHARGE, validateCharge, type CatalogItem } from '@/app/finance/chargeCatalog';

const item: CatalogItem = {
  priceId: 7, code: 'NADA_CONSTA', name: 'Certidão de nada consta', group: 'G', paymentCategory: 'INTERNAL_PROCESS',
  processType: 'CERTIFICATE', value: 100, valueSource: 'GLOBAL', valueDefined: true, defaultDescription: 'Certidão de nada consta',
};

describe('normalizePolicy', () => {
  test('aceita o formato esperado', () => {
    expect(normalizePolicy({ enforceTablePrice: true, canRequestException: true, canDecideException: false }))
      .toEqual({ enforceTablePrice: true, canRequestException: true, canDecideException: false, tableReady: false, blankValues: 0 });
  });

  test('qualquer outra coisa vira o padrão desligado', () => {
    for (const bad of [null, undefined, [], 'x', 42, { enforceTablePrice: 'true' }]) {
      expect(normalizePolicy(bad)).toEqual(DEFAULT_POLICY);
    }
  });

  test('só o valor booleano true liga cada permissão', () => {
    expect(normalizePolicy({ canDecideException: 1, canRequestException: 'sim' })).toEqual(DEFAULT_POLICY);
  });
});

describe('validateExceptionRequest', () => {
  test('exige valor numérico não negativo', () => {
    expect(validateExceptionRequest({ requestedAmount: '', reason: 'Bolsa parcial concedida' }, 100)).toMatch(/valor pedido/);
    expect(validateExceptionRequest({ requestedAmount: '-5', reason: 'Bolsa parcial concedida' }, 100)).toMatch(/valor pedido/);
  });

  test('aceita zero (isenção)', () => {
    expect(validateExceptionRequest({ requestedAmount: '0', reason: 'Isenção concedida pelo conselho' }, 100)).toBeNull();
  });

  test('recusa valor igual ao da tabela', () => {
    expect(validateExceptionRequest({ requestedAmount: '100.00', reason: 'Bolsa parcial concedida' }, 100)).toMatch(/igual ao da tabela/);
  });

  test('exige justificativa com o tamanho mínimo, ignorando espaços', () => {
    expect(validateExceptionRequest({ requestedAmount: '60', reason: '   curto   ' }, 100)).toMatch(/justificativa/);
    expect(validateExceptionRequest({ requestedAmount: '60', reason: 'Bolsa parcial concedida' }, 100)).toBeNull();
  });
});

describe('buildExceptionPayload', () => {
  test('monta o corpo com o procedimento, o nível e a justificativa aparada', () => {
    const payload = buildExceptionPayload(
      { requestedAmount: '60.5', reason: '  Bolsa parcial concedida  ' }, item, '12', 'POSTGRADUATE', '2026-12-01', 'Observação');
    expect(payload).toEqual({
      studentId: 12, academicLevel: 'POSTGRADUATE', chargePriceId: 7, dueDate: '2026-12-01',
      description: 'Observação', requestedAmount: 60.5, reason: 'Bolsa parcial concedida',
    });
  });

  test('descrição vazia vai como nula', () => {
    expect(buildExceptionPayload({ ...EMPTY_EXCEPTION_FORM, requestedAmount: '1', reason: 'Motivo suficiente' }, item, '1', 'GRADUATION', '2026-12-01', '  ').description)
      .toBeNull();
  });
});

describe('apresentação', () => {
  test('rótulos de situação e dinheiro em reais', () => {
    expect(statusLabel('PENDING')).toBe('Aguardando aprovação');
    expect(statusLabel('APPROVED')).toBe('Aprovado');
    expect(statusLabel('REJECTED')).toBe('Rejeitado');
    expect(formatMoney(60)).toBe('R$ 60,00');
    expect(formatMoney(15.5)).toBe('R$ 15,50');
  });

  test('mensagem do servidor tem prioridade; senão por status', () => {
    expect(exceptionErrorMessage(400, 'Já existe um pedido pendente')).toBe('Já existe um pedido pendente');
    expect(exceptionErrorMessage(403)).toMatch(/permissão/);
    expect(exceptionErrorMessage(404)).toMatch(/não encontrado/);
    expect(exceptionErrorMessage()).toMatch(/Não foi possível/);
  });
});

describe('validateCharge com a regra "valor sempre da tabela" ligada', () => {
  const form = { ...EMPTY_CHARGE, studentId: '1', dueDate: '2026-12-01', amount: '10' };

  test('sem procedimento o lançamento é recusado, mesmo com valor digitado', () => {
    expect(validateCharge(form, null, false, true)).toMatch(/Escolha o procedimento/);
  });

  test('com procedimento passa', () => {
    expect(validateCharge(form, item, false, true)).toBeNull();
  });

  test('regra desligada mantém o modo manual como antes', () => {
    expect(validateCharge(form, null, false, false)).toBeNull();
    expect(validateCharge(form, null, false)).toBeNull();
  });
});

describe('normalizePolicy: prontidão da tabela', () => {
  test('lê tableReady e blankValues', () => {
    expect(normalizePolicy({ enforceTablePrice: true, canRequestException: false, canDecideException: true, tableReady: true, blankValues: 0 }))
      .toMatchObject({ tableReady: true, blankValues: 0 });
    expect(normalizePolicy({ tableReady: false, blankValues: 7 })).toMatchObject({ tableReady: false, blankValues: 7 });
  });

  test('blankValues inválido (texto, negativo) vira 0 e tableReady só com true', () => {
    expect(normalizePolicy({ blankValues: '5', tableReady: 'true' })).toMatchObject({ tableReady: false, blankValues: 0 });
    expect(normalizePolicy({ blankValues: -3 })).toMatchObject({ blankValues: 0 });
  });
});

describe('ruleBanner', () => {
  const policy = (over: Partial<typeof DEFAULT_POLICY>) => ({ ...DEFAULT_POLICY, ...over });

  test('tabela incompleta: regra em espera, com a quantidade que falta', () => {
    const b = ruleBanner(policy({ tableReady: false, blankValues: 24 }));
    expect(b.tone).toBe('waiting');
    expect(b.text).toMatch(/em espera/);
    expect(b.text).toMatch(/Faltam 24 valores/);
    expect(b.text).toMatch(/pode ser digitado/);
  });

  test('falta um só: singular', () => {
    expect(ruleBanner(policy({ tableReady: false, blankValues: 1 })).text).toMatch(/Falta 1 valor/);
  });

  test('sem nenhuma linha cadastrada: não diz "faltam 0"', () => {
    const b = ruleBanner(policy({ tableReady: false, blankValues: 0 }));
    expect(b.tone).toBe('waiting');
    expect(b.text).not.toMatch(/Faltam 0/);
    expect(b.text).toMatch(/ainda não tem os valores/);
  });

  test('tabela pronta e regra valendo: ativa, e cita a exceção aprovada por administrador', () => {
    const b = ruleBanner(policy({ tableReady: true, enforceTablePrice: true }));
    expect(b.tone).toBe('active');
    expect(b.text).toMatch(/Regra ativa/);
    expect(b.text).toMatch(/aprovado por um administrador/);
  });

  test('tabela pronta mas desligada na configuração: avisa', () => {
    const b = ruleBanner(policy({ tableReady: true, enforceTablePrice: false }));
    expect(b.tone).toBe('off');
    expect(b.text).toMatch(/desligada/);
  });
});
