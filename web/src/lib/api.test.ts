import { describe, expect, it, vi } from "vitest";
import { ApiError, createApi } from "./api";

function respond(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json", ...(init.headers ?? {}) },
    ...init,
  });
}

const order = { id: "abc", status: "AGUARDANDO_PAGAMENTO" };

describe("api", () => {
  it("envia a Idempotency-Key e reconhece quando o pedido foi devolvido de novo", async () => {
    const fetchMock = vi.fn().mockResolvedValue(respond(order, { headers: { "Idempotent-Replayed": "true" } }));
    const api = createApi("https://api.exemplo/", fetchMock);

    const placed = await api.placeOrder({ customerName: "Mercado", items: [] }, "web-123");

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe("https://api.exemplo/api/orders");
    expect((init as RequestInit).headers).toMatchObject({ "Idempotency-Key": "web-123" });
    expect(placed.replayed).toBe(true);
  });

  it("transforma Problem Details em erro legível", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      respond({ status: 422, detail: "A Idempotency-Key já foi usada com outra requisição" }, { status: 422 }),
    );
    const api = createApi("https://api.exemplo", fetchMock);

    await expect(api.placeOrder({ customerName: "X", items: [] }, "k")).rejects.toMatchObject({
      status: 422,
      message: "A Idempotency-Key já foi usada com outra requisição",
    });
  });

  it("explica quando a API está fora do ar", async () => {
    const api = createApi("https://api.exemplo", vi.fn().mockRejectedValue(new TypeError("fetch failed")));
    const error = await api.stats().catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(0);
  });
});
