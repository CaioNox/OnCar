// @vitest-environment node
// Sem DOM de proposito: no Node nao existe `document`, como na renderizacao do servidor.
import assert from "node:assert/strict";
import { afterEach, describe, it, vi } from "vitest";

import {
  api,
  ErroDaApi,
  formatarData,
  formatarDataHora,
  formatarMes,
  formatarMoeda,
  formatarNumero,
  formatarQuantidade,
  mesAtual,
} from "@/lib/api";

type Chamada = { url: string; init: RequestInit | undefined };

/** Substitui o fetch global; cada resposta da fila atende uma chamada, na ordem. */
function simularFetch(...respostas: Array<Response | Error>): Chamada[] {
  const chamadas: Chamada[] = [];
  vi.spyOn(globalThis, "fetch").mockImplementation(async (url, init) => {
    chamadas.push({ url: String(url), init });
    const resposta = respostas.shift();
    if (!resposta) throw new Error(`fetch inesperado para ${url}`);
    if (resposta instanceof Error) throw resposta;
    return resposta;
  });
  return chamadas;
}

const json = (corpo: unknown, status = 200) => new Response(JSON.stringify(corpo), { status });

/** No Node nao ha `document`; os testes de CSRF criam um com o cookie desejado. */
function comCookie(cookie: string) {
  Reflect.set(globalThis, "document", { cookie });
}

const cabecalhos = (chamada: Chamada) => new Headers(chamada.init?.headers);

// O espiao do fetch e desfeito pelo restoreMocks do vitest.config.mts; o document, aqui.
afterEach(() => {
  Reflect.deleteProperty(globalThis, "document");
});

describe("cliente da API", () => {
  it("o GET vai para /api, envia o cookie de sessao e nao usa cache", async () => {
    const chamadas = simularFetch(json([{ id: 1 }]));

    const resultado = await api.get<{ id: number }[]>("/produtos");

    assert.deepEqual(resultado, [{ id: 1 }]);
    assert.equal(chamadas[0].url, "/api/produtos");
    assert.equal(chamadas[0].init?.credentials, "include");
    assert.equal(chamadas[0].init?.cache, "no-store");
    assert.equal(cabecalhos(chamadas[0]).has("Content-Type"), false, "GET sem corpo nao declara JSON");
  });

  it("resposta 204 devolve undefined sem tentar ler JSON", async () => {
    simularFetch(new Response(null, { status: 204 }));

    assert.equal(await api.get("/qualquer"), undefined);
  });

  it("resposta 200 com corpo vazio devolve null", async () => {
    simularFetch(new Response("", { status: 200 }));

    assert.equal(await api.get("/qualquer"), null);
  });

  it("erro da API vira ErroDaApi com a mensagem e os detalhes enviados pelo servidor", async () => {
    simularFetch(json({ mensagem: "Saldo insuficiente.", detalhes: ["quantidade"] }, 400));

    await assert.rejects(api.get("/movimentacoes"), (erro: unknown) => {
      assert.ok(erro instanceof ErroDaApi);
      assert.equal(erro.status, 400);
      assert.equal(erro.message, "Saldo insuficiente.");
      assert.deepEqual(erro.detalhes, ["quantidade"]);
      assert.equal(erro.naoAutenticado, false);
      return true;
    });
  });

  it("401 sem corpo avisa que a sessao expirou", async () => {
    simularFetch(new Response("", { status: 401 }));

    await assert.rejects(api.get("/painel"), (erro: unknown) => {
      assert.ok(erro instanceof ErroDaApi);
      assert.equal(erro.naoAutenticado, true);
      assert.match(erro.message, /sessao expirou/);
      assert.deepEqual(erro.detalhes, []);
      return true;
    });
  });

  it("500 sem corpo da uma mensagem generica", async () => {
    simularFetch(new Response("", { status: 500 }));

    await assert.rejects(api.get("/painel"), /Nao foi possivel concluir a operacao/);
  });

  it("sem cookie de CSRF, a escrita busca a sessao antes e manda o corpo em JSON", async () => {
    const chamadas = simularFetch(json({}), json({ id: 7 }, 201));

    const criado = await api.post<{ id: number }>("/despesas", { valor: "10.00" });

    assert.deepEqual(criado, { id: 7 });
    assert.equal(chamadas.length, 2);
    assert.equal(chamadas[0].url, "/api/sessao");
    assert.equal(chamadas[1].url, "/api/despesas");
    assert.equal(chamadas[1].init?.method, "POST");
    assert.equal(chamadas[1].init?.body, JSON.stringify({ valor: "10.00" }));
    assert.equal(cabecalhos(chamadas[1]).get("Content-Type"), "application/json");
  });

  it("com cookie de CSRF, a escrita nao busca a sessao e devolve o token decodificado no cabecalho", async () => {
    comCookie("outro=1; XSRF-TOKEN=abc%3D%3Dx; tema=papel");
    const chamadas = simularFetch(new Response(null, { status: 204 }));

    await api.delete("/despesas/3");

    assert.equal(chamadas.length, 1);
    assert.equal(chamadas[0].init?.method, "DELETE");
    assert.equal(cabecalhos(chamadas[0]).get("X-XSRF-TOKEN"), "abc==x");
  });

  it("falha de rede ao preparar o CSRF nao impede a escrita", async () => {
    const chamadas = simularFetch(new TypeError("rede fora"), json({ ok: true }));

    assert.deepEqual(await api.put("/produtos/1", { descricao: "Nova" }), { ok: true });
    assert.equal(chamadas[1].init?.method, "PUT");
  });

  it("POST sem corpo nao envia body nem Content-Type", async () => {
    comCookie("XSRF-TOKEN=t");
    const chamadas = simularFetch(new Response(null, { status: 204 }));

    await api.post("/sessao/sair");

    assert.equal(chamadas[0].init?.body, undefined);
    assert.equal(cabecalhos(chamadas[0]).has("Content-Type"), false);
  });
});

/** O Intl separa "R$" do numero com espaco nao separavel; compara o texto visivel. */
const visivel = (texto: string) => texto.replace(/ /g, " ");

describe("formatacao", () => {
  it("valores ausentes viram traco", () => {
    assert.equal(formatarNumero(null), "-");
    assert.equal(formatarQuantidade(null), "-");
    assert.equal(formatarMoeda(null), "-");
  });

  it("numero com casas fixas e separadores brasileiros", () => {
    assert.equal(formatarNumero("1234.5"), "1.234,50");
    assert.equal(formatarNumero("2", 3), "2,000");
    assert.equal(formatarNumero(0), "0,00");
  });

  it("quantidade sem zeros sobrando e com no maximo tres casas", () => {
    assert.equal(formatarQuantidade("5.000"), "5");
    assert.equal(formatarQuantidade("2.500"), "2,5");
    assert.equal(formatarQuantidade("0.1234"), "0,123");
  });

  it("moeda em reais", () => {
    assert.equal(visivel(formatarMoeda("1234.5")), "R$ 1.234,50");
    assert.equal(visivel(formatarMoeda(0)), "R$ 0,00");
  });

  it("mes por extenso com inicial maiuscula, e curto com ano de dois digitos", () => {
    assert.equal(formatarMes("2026-10"), "Outubro de 2026");
    assert.equal(formatarMes("2026-03", "short"), "mar/26");
    assert.equal(formatarMes("2027-01", "short"), "jan/27");
  });

  it("data da API sem passar por fuso horario", () => {
    assert.equal(formatarData("2026-10-05"), "05/10/2026");
    assert.equal(formatarData("2026-01-01"), "01/01/2026", "meia-noite nao volta para o dia anterior");
  });

  it("data e hora local no padrao brasileiro", () => {
    const texto = formatarDataHora("2026-10-05T14:30:00");

    assert.match(texto, /05\/10\/2026/);
    assert.match(texto, /14:30/);
  });

  it("mes atual no formato AAAA-MM, com zero a esquerda", () => {
    assert.equal(mesAtual(new Date(2026, 0, 31)), "2026-01");
    assert.equal(mesAtual(new Date(2026, 11, 1)), "2026-12");
  });
});
