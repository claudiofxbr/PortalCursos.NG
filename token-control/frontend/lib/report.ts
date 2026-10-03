import { formatDateTime } from "./format";
import type { Report } from "./types";

const cell = (v: string) => v.replace(/\|/g, "\\|").replace(/\n/g, " ");

/** Relatório em Markdown (para o botão "Baixar (.md)"), gerado a partir das mesmas seções da tela. */
export function toMarkdown(r: Report): string {
  const out: string[] = [`# ${r.title}`, "", `_Gerado em ${formatDateTime(r.generatedAt, r.timezone)} (${r.timezone})_`, ""];
  for (const s of r.sections) {
    out.push(`## ${s.title}`, "");
    for (const p of s.paragraphs) out.push(p, "");
    if (s.bullets.length) {
      for (const b of s.bullets) out.push(`- ${b}`);
      out.push("");
    }
    if (s.table) {
      out.push(`**${s.table.caption}**`, "");
      out.push(`| ${s.table.headers.map(cell).join(" | ")} |`);
      out.push(`| ${s.table.headers.map(() => "---").join(" | ")} |`);
      for (const row of s.table.rows) out.push(`| ${row.map(cell).join(" | ")} |`);
      out.push("");
    }
  }
  return out.join("\n").trimEnd() + "\n";
}

/** relatorio-controle-de-tokens-AAAA-MM-DD.md (data no fuso configurado). */
export function reportFileName(r: Report): string {
  const date = new Intl.DateTimeFormat("en-CA", { timeZone: r.timezone, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(r.generatedAt));
  return `relatorio-controle-de-tokens-${date}.md`;
}

export function downloadMarkdown(r: Report): void {
  const blob = new Blob([toMarkdown(r)], { type: "text/markdown;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = reportFileName(r);
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
