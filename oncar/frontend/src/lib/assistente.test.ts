// @vitest-environment node
import assert from "node:assert/strict";
import { describe, it, vi } from "vitest";

import { normalizar, produtoCasa, responder, SUGESTOES } from "@/lib/assistente";
import { movimentacao, painel, produto } from "@/lib/fixtures.test-utils";

/** Responde cada caminho da API com o JSON informado e registra o que foi pedido. */
function simularApi(rotas: Record<string, unknown>): string[] {
  const pedidos: string[] = [];
  vi.spyOn(globalThis, "fetch").mockImplementation(async (endereco) => {
    const url = String(endereco);
    pedidos.push(url);
    if (!(url in rotas)) throw new Error(`rota nao simulada: ${url}`);
    return new Response(JSON.stringify(rotas[url]), { status: 200 });
  });
  return pedidos;
}

describe("normalizar", () => {
  it("tira acentos, espacos das pontas e passa para minusculas", () => {
    assert.equal(normalizar("  Óleo DE Câmbio "), "oleo de cambio");
  });
});

describe("produtoCasa", () => {
  const filtro = produto({ sku: "FL-001", descricao: "Filtro de óleo", marca: "Tecfil", categoria: "Filtro" });

  it("todas as palavras precisam aparecer em SKU, descricao, marca ou categoria", () => {
    assert.equal(produtoCasa(filtro, ["filtro", "tecfil"]), true);
    assert.equal(produtoCasa(filtro, ["oleo"]), true, "compara sem acento");
    assert.equal(produtoCasa(filtro, ["fl-001"]), true);
    assert.equal(produtoCasa(filtro, ["filtro", "bosch"]), false);
  });

  it("produto sem marca nao quebra a comparacao", () => {
    assert.equal(produtoCasa(produto({ marca: null }), ["null"]), false);
  });
});

describe("responder", () => {
  it("procura o produto localmente, sem chamar a API, e ignora os inativos", async () => {
    const pedidos = simularApi({});
    const produtos = [
      produto({ id: 1, descricao: "Filtro de óleo", ativo: true }),
      produto({ id: 2, sku: "FL-002", descricao: "Filtro de óleo antigo", ativo: false }),
      produto({ id: 3, sku: "VL-1", descricao: "Vela de ignicao", marca: "NGK", categoria: "Peca" }),
    ];

    const resposta = await responder("Qual o saldo do filtro de óleo?", produtos);

    assert.equal(resposta.texto, "Achei este produto:");
    assert.deepEqual(
      resposta.itens?.map((item) => item.href),
      ["/produtos/1"],
    );
    assert.equal(pedidos.length, 0);
  });

  it("varios produtos encontrados citam o termo pesquisado", async () => {
    simularApi({});
    const produtos = [produto({ id: 1 }), produto({ id: 2, sku: "FL-002" })];

    const resposta = await responder("filtro", produtos);

    assert.equal(resposta.texto, 'Achei 2 produtos com "filtro":');
  });

  it("pergunta sobre reposicao consulta a lista e marca os itens em alerta", async () => {
    const pedidos = simularApi({
      "/api/produtos/reposicao": [produto({ id: 4, abaixoDoMinimo: true })],
    });

    const resposta = await responder("O que está abaixo do mínimo?", []);

    assert.deepEqual(pedidos, ["/api/produtos/reposicao"]);
    assert.equal(resposta.texto, "1 item está no mínimo ou abaixo dele:");
    assert.equal(resposta.itens?.[0].alerta, true);
  });

  it("sem itens para repor responde que o estoque esta saudavel", async () => {
    simularApi({ "/api/produtos/reposicao": [] });

    const resposta = await responder("tem algo faltando?", []);

    assert.match(resposta.texto, /Nenhum item precisa de reposição/);
  });

  it("perfil sem acesso a custos nao recebe o valor do estoque", async () => {
    simularApi({ "/api/painel": painel({ valorTotalEmEstoque: null }) });

    const resposta = await responder("Quanto vale o estoque?", []);

    assert.match(resposta.texto, /não enxerga custos/);
  });

  it("o valor do estoque vem do painel em reais", async () => {
    simularApi({ "/api/painel": painel({ valorTotalEmEstoque: "1500.00", produtosAtivos: 2 }) });

    const resposta = await responder("quanto vale o estoque", []);

    assert.match(resposta.texto.replace(/ /g, " "), /R\$ 1\.500,00/);
    assert.match(resposta.texto, /2 produtos ativos/);
  });

  it("ultimas saidas filtram so as saidas e mostram no maximo cinco", async () => {
    const movimentacoes = [
      movimentacao({ id: 1, tipo: "ENTRADA" }),
      ...Array.from({ length: 6 }, (_, indice) => movimentacao({ id: 10 + indice, tipo: "SAIDA" })),
    ];
    simularApi({ "/api/movimentacoes": movimentacoes });

    const resposta = await responder("Últimas saídas", []);

    assert.equal(resposta.texto, "As últimas saídas:");
    assert.deepEqual(
      resposta.itens?.map((item) => item.chave),
      ["movimentacao-10", "movimentacao-11", "movimentacao-12", "movimentacao-13", "movimentacao-14"],
    );
  });

  it("sem movimentacoes do tipo pedido responde que nao ha registros", async () => {
    simularApi({ "/api/movimentacoes": [movimentacao({ tipo: "SAIDA" })] });

    const resposta = await responder("o que entrou hoje?", []);

    assert.equal(resposta.texto, "Não há entradas registradas ainda.");
  });

  it("o numero de OS ou nota e procurado no documento das movimentacoes", async () => {
    simularApi({
      "/api/movimentacoes": [
        movimentacao({ id: 1, documento: "OS 4512" }),
        movimentacao({ id: 2, documento: "NF-e 99" }),
      ],
    });

    const encontrada = await responder("tem peça na OS 4512?", []);
    const nota = await responder("nf-e 77", []);

    assert.match(encontrada.texto, /1 lançamento com o documento 4512/);
    assert.equal(encontrada.itens?.length, 1);
    assert.equal(nota.texto, "Nenhuma movimentação recente cita o documento 77.");
  });

  it("pergunta desconhecida devolve as sugestoes", async () => {
    simularApi({});

    const resposta = await responder("bom dia", [produto()]);

    assert.deepEqual(resposta.sugestoes, SUGESTOES);
  });

  it("erro da API chega a quem perguntou", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("", { status: 401 }));

    await assert.rejects(responder("o que esta abaixo do minimo", []), /sessao expirou/);
  });
});
