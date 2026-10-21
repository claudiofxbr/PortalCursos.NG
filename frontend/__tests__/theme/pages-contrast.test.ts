import { describe, test, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Regressão da auditoria de contraste de 20/10/2026. Várias páginas foram desenhadas para o tema escuro
 * (texto em branco translúcido, dourado e cores de status) e ficavam ilegíveis no tema claro, que é o padrão.
 * Agora usam variáveis de tema: --fg-*, --accent-text, --*-text e --dim-*, com contraste mínimo de 4,5:1 no
 * tema claro e os mesmos valores de antes nos temas escuros.
 */

const root = join(__dirname, '..', '..');
const css = readFileSync(join(root, 'app', 'globals.css'), 'utf8');

function themeVars(theme: string): Record<string, string> {
  const block = css.match(new RegExp(`\\.${theme}\\s*\\{([^}]*)\\}`))?.[1] ?? '';
  const vars: Record<string, string> = {};
  for (const m of block.matchAll(/--([a-z0-9-]+):\s*([^;]+);/g)) vars[m[1]] = m[2].trim();
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

const THEMES = ['light-theme', 'dark-theme', 'blue-theme'];
const COLOR_VARS = ['fg-1', 'fg-2', 'fg-3', 'fg-4', 'accent-text', 'success-text', 'warning-text', 'danger-text'];
const DIM_VARS = ['dim-2', 'dim-3', 'dim-4', 'dim-5'];

describe('variáveis de tema para texto (globals.css)', () => {
  test.each(THEMES)('%s define todas as variáveis de texto e de esmaecimento', (theme) => {
    const vars = themeVars(theme);
    for (const name of [...COLOR_VARS, ...DIM_VARS]) expect(vars[name], `${theme} sem --${name}`).toBeTruthy();
  });

  test.each(COLOR_VARS)('tema claro: --%s tem contraste >= 4,5:1 sobre a página, o cartão e fundos tingidos', (name) => {
    const vars = themeVars('light-theme');
    const text = parseColor(vars[name]);
    const backgrounds: Record<string, Rgb> = {
      pagina: parseColor(vars['bg-color']),
      cartao: parseColor(vars['card-bg']),
      tintaVerde: parseColor('#d9f0e3'),
      tintaAmbar: parseColor('#f6ecd0'),
      tintaDourada: parseColor('#efeadb'),
    };
    // fg-4 (texto mais apagado) vale em página e cartão; nos fundos tingidos o mínimo é 4,2:1 (texto auxiliar).
    for (const [nome, bg] of Object.entries(backgrounds)) {
      const minimo = name === 'fg-4' && nome.startsWith('tinta') ? 4.2 : 4.5;
      expect(contrast(text, bg), `--${name} sobre ${nome}`).toBeGreaterThanOrEqual(minimo);
    }
  });

  test('tema claro: texto esmaecido por opacidade continua legível (>= 4,5:1) com o valor definido', () => {
    const vars = themeVars('light-theme');
    const page = parseColor(vars['bg-color']);
    const text = parseColor(vars['text-primary']);
    for (const name of DIM_VARS) {
      const opacity = Number(vars[name]);
      expect(opacity).toBeGreaterThanOrEqual(0.7);
      const blended = text.map((c, i) => Math.round(c * opacity + page[i] * (1 - opacity))) as Rgb;
      expect(contrast(blended, page), `--${name}`).toBeGreaterThanOrEqual(4.5);
    }
  });

  test.each(['dark-theme', 'blue-theme'])('%s mantém os valores que as páginas usavam antes (sem mudança visual)', (theme) => {
    const vars = themeVars(theme);
    expect(vars['fg-1']).toBe('#ffffff');
    expect(vars['fg-4']).toBe('rgba(255, 255, 255, 0.4)');
    expect(vars['success-text']).toBe('#2ecc71');
    expect(vars['danger-text']).toBe('#e74c3c');
    expect(vars['warning-text']).toBe('#f0a500');
    expect([vars['dim-2'], vars['dim-3'], vars['dim-4'], vars['dim-5']]).toEqual(['0.2', '0.3', '0.4', '0.5']);
  });
});

/** Trecho da página que fica sobre o fundo do tema (antes do primeiro modal, que tem fundo escuro próprio). */
function pageBody(file: string): string {
  const source = readFileSync(join(root, file), 'utf8');
  const overlay = source.search(/position:\s*['"]fixed['"]/);
  return overlay === -1 ? source : source.slice(0, overlay);
}

const PAGES = [
  'app/academic/page.tsx',
  'app/academic/enroll/page.tsx',
  'app/academic/postgrad/page.tsx',
  'app/courses/page.tsx',
  'app/roadmap/page.tsx',
  'app/page.tsx',
  'app/minha-conta/page.tsx',
];

describe('páginas: nada de texto branco fixo nem cores de status fixas sobre o fundo do tema', () => {
  test.each(PAGES)('%s usa variáveis de tema para o texto', (file) => {
    const body = pageBody(file);
    expect(body, 'texto em branco translúcido fixo').not.toMatch(/color:\s*['"]rgba\(255,\s*255,\s*255/);
    expect(body, 'texto dourado fixo (var(--secondary-color))').not.toMatch(/color:\s*['"]var\(--secondary-color\)['"]/);
    expect(body, 'cor de status fixa no texto').not.toMatch(/color:\s*['"](#2ecc71|#e74c3c|#f0a500|#f1c40f)['"]/);
  });

  test.each(PAGES)('%s não usa bordas e fundos brancos translúcidos fixos (invisíveis no tema claro)', (file) => {
    const body = pageBody(file).replace(/\/\*[\s\S]*?\*\//g, '');
    const fixos = body.match(/(?:border\w*|background\w*):\s*['"][^'"]*rgba\(255,\s*255,\s*255,\s*0\.\d+\)[^'"]*['"]/g) ?? [];
    // Tolerado: listras zebradas muito sutis (0,01 a 0,02), que não carregam informação.
    const relevantes = fixos.filter((t) => !/0\.0[12]\)/.test(t));
    expect(relevantes).toEqual([]);
  });

  test('listas de alunos mostram o status com a cor de TEXTO legível (STATUS_TEXT), não com a cor de preenchimento', () => {
    for (const file of ['app/academic/page.tsx', 'app/academic/enroll/page.tsx', 'app/academic/postgrad/page.tsx']) {
      const body = pageBody(file);
      expect(body).toContain('STATUS_TEXT');
      expect(body).not.toMatch(/color:\s*STATUS_COLORS\[/);
    }
  });

  test('usuários: o cabeçalho e a lista usam variáveis de tema', () => {
    const body = pageBody('app/users/page.tsx').slice(0, 14000);
    expect(body).toContain("color: 'var(--fg-1)'");
    expect(body).toContain("color: 'var(--accent-text)'");
  });
});
