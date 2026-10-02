import { describe, expect, it } from "vitest";
import { formatDay, formatDuration, formatPct, formatTokens, severity } from "@/lib/format";

describe("format", () => {
  it("formata tokens em mil/mi/bi", () => {
    expect(formatTokens(950)).toBe("950");
    expect(formatTokens(12_500)).toBe("12,5 mil");
    expect(formatTokens(3_400_000)).toBe("3,4 mi");
    expect(formatTokens(2_000_000_000)).toBe("2 bi");
  });
  it("formata duração", () => {
    expect(formatDuration(273_600)).toBe("3d 4h");
    expect(formatDuration(5_400)).toBe("1h 30min");
    expect(formatDuration(90)).toBe("1min");
    expect(formatDuration(-5)).toBe("0min");
  });
  it("formata percentual e dia sem deslocar fuso", () => {
    expect(formatPct(12.34)).toBe("12,3%");
    expect(formatDay("2026-09-28")).toBe("28/09");
  });
  it("classifica severidade", () => {
    expect(severity(10)).toBe("ok");
    expect(severity(70)).toBe("warn");
    expect(severity(95)).toBe("danger");
    expect(severity(130)).toBe("danger");
  });
});
