import { describe, expect, it } from "vitest";
import { brl, percent, shortId, statusLabel } from "./format";

describe("format", () => {
  it("formata centavos em reais", () => {
    expect(brl(179_80)).toBe("R$ 179,80");
    expect(brl(1_234_567)).toBe("R$ 12.345,67");
  });

  it("arredonda porcentagens", () => {
    expect(percent(0.255)).toBe("26%");
    expect(percent(0)).toBe("0%");
  });

  it("encurta o id do pedido", () => {
    expect(shortId("3f2a9c1e-0000-4000-8000-000000000000")).toBe("3f2a9c1e");
  });

  it("tem rótulo para todo status", () => {
    expect(Object.keys(statusLabel)).toHaveLength(4);
  });
});
