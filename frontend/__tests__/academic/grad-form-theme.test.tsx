import React from 'react';
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }));

vi.mock('@/app/services/api', () => ({
  __esModule: true,
  default: { get: getMock, post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
  BASE_URL: 'http://localhost:8080',
}));

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock('@/app/components/AuditStamp', () => ({ AuditStamp: () => null }));

import GraduationPage from '@/app/academic/page';

/**
 * Regressão do achado do teste de 20/10/2026: no tema claro (padrão) o formulário "Novo Aluno" usava branco
 * fixo (rótulos a 60%, campos a 3% de branco), ficando quase invisível (contraste ~1,2:1). Agora usa variáveis
 * de tema (--field-*), definidas para os três temas, com contraste mínimo de 4,5:1 (WCAG AA).
 */

const css = readFileSync(join(__dirname, '..', '..', 'app', 'globals.css'), 'utf8');

function themeVars(theme: string): Record<string, string> {
  const block = css.match(new RegExp(`\\.${theme}\\s*\\{([^}]*)\\}`))?.[1] ?? '';
  const vars: Record<string, string> = {};
  for (const m of block.matchAll(/--([a-z-]+):\s*([^;]+);/g)) vars[m[1]] = m[2].trim();
  return vars;
}

type Rgb = [number, number, number];

function parseColor(value: string, over: Rgb = [255, 255, 255]): Rgb {
  const hex = value.match(/^#([0-9a-f]{6})$/i);
  if (hex) return [0, 2, 4].map((i) => parseInt(hex[1].slice(i, i + 2), 16)) as Rgb;
  const rgba = value.match(/^rgba?\(([^)]+)\)$/);
  if (!rgba) throw new Error(`cor não suportada: ${value}`);
  const [r, g, b, a = '1'] = rgba[1].split(',').map((p) => p.trim());
  const alpha = Number(a);
  return [Number(r), Number(g), Number(b)].map((c, i) => Math.round(c * alpha + over[i] * (1 - alpha))) as Rgb;
}

function luminance([r, g, b]: Rgb): number {
  const f = (c: number) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
}

function contrast(a: Rgb, b: Rgb): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const FIELD_VARS = ['field-bg', 'field-border', 'field-text', 'field-label', 'field-surface', 'field-divider'];

describe('variáveis de tema do formulário (globals.css)', () => {
  test.each(['light-theme', 'dark-theme', 'blue-theme'])('%s define todas as variáveis --field-*', (theme) => {
    const vars = themeVars(theme);
    for (const name of FIELD_VARS) expect(vars[name], `${theme} sem --${name}`).toBeTruthy();
  });

  test.each(['light-theme', 'dark-theme', 'blue-theme'])('%s: texto e rótulo dos campos têm contraste mínimo de 4,5:1', (theme) => {
    const vars = themeVars(theme);
    const page = parseColor(vars['bg-color']);
    const card = parseColor(vars['card-bg']);
    // O campo é translúcido em alguns temas: compõe sobre o fundo da página.
    const field = parseColor(vars['field-bg'], page);
    const surface = parseColor(vars['field-surface'], page);

    expect(contrast(parseColor(vars['field-text'], field), field)).toBeGreaterThanOrEqual(4.5);
    expect(contrast(parseColor(vars['field-label'], surface), surface)).toBeGreaterThanOrEqual(4.5);
    expect(card).toBeTruthy();
  });

  test('tema claro: o campo é distinguível do fundo da página (borda visível)', () => {
    const vars = themeVars('light-theme');
    const page = parseColor(vars['bg-color']);
    expect(contrast(parseColor(vars['field-border']), page)).toBeGreaterThanOrEqual(1.3);
  });
});

describe('GraduationPage: formulário "Novo Aluno"', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getMock.mockResolvedValue({ data: [] });
  });

  test('rótulos, campos e botões usam variáveis de tema, sem branco fixo', async () => {
    const { container } = render(<GraduationPage />);
    await userEvent.click(await screen.findByRole('button', { name: /Novo Aluno/ }));

    const form = container.querySelector('form') as HTMLFormElement;
    expect(form).not.toBeNull();

    // Só os rótulos do próprio formulário (o seletor de foto 3x4 é um componente compartilhado, com estilo próprio).
    const labels = Array.from(form.querySelectorAll('label')).filter((l) => l.getAttribute('style'));
    expect(labels.length).toBeGreaterThanOrEqual(10);
    for (const label of labels) {
      const style = label.getAttribute('style') as string;
      expect(style).toContain('var(--field-label)');
      expect(style).not.toMatch(/rgba\(255,\s*255,\s*255/);
    }

    const fields = Array.from(form.querySelectorAll('input:not([type=file]), select'));
    expect(fields.length).toBeGreaterThanOrEqual(8);
    for (const field of fields) {
      const style = field.getAttribute('style') ?? '';
      if (!style.includes('width: 100%')) continue; // ignora o campo oculto do seletor de foto
      expect(style).toContain('var(--field-bg)');
      expect(style).toContain('var(--field-text)');
      expect(style).toContain('var(--field-border)');
      expect(style).not.toMatch(/rgba\(255,\s*255,\s*255/);
      expect(style).not.toMatch(/color:\s*#fff/i);
    }

    const closeButton = screen.getByRole('button', { name: 'Fechar' });
    expect(closeButton.getAttribute('style')).toContain('var(--field-text)');
    expect(closeButton.getAttribute('style')).not.toMatch(/color:\s*#fff/i);
  });

  test('o botão de alternar do cabeçalho também deixa de ser branco fixo quando o formulário está aberto', async () => {
    render(<GraduationPage />);
    await userEvent.click(await screen.findByRole('button', { name: /Novo Aluno/ }));

    const toggle = screen.getByRole('button', { name: /Cancelar Cadastro/ });
    const style = toggle.getAttribute('style') ?? '';
    expect(style).toContain('var(--field-bg)');
    expect(style).toContain('var(--field-text)');
    expect(style).not.toMatch(/rgba\(255,\s*255,\s*255/);
  });

  test('o formulário continua funcionando: os campos obrigatórios aceitam digitação', async () => {
    render(<GraduationPage />);
    await userEvent.click(await screen.findByRole('button', { name: /Novo Aluno/ }));

    const course = screen.getByPlaceholderText('Ex: Ciência da Computação');
    await userEvent.type(course, 'Engenharia');
    expect(course).toHaveValue('Engenharia');
    expect(screen.getByRole('button', { name: 'Finalizar Matrícula' })).toBeEnabled();
  });
});
