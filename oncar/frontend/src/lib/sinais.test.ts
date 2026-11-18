// @vitest-environment node
import assert from "node:assert/strict";
import { describe, it } from "vitest";

import { movimentacao, painel, produto } from "@/lib/fixtures.test-utils";
import { dataLocal, fluxoEstimado, MOTIVOS, osComPecasHoje, sinaisDaOficina } from "@/lib/sinais";
import type { IndicadoresFinanceiros } from "@/lib/tipos";

const HOJE = "2026-11-18";

describe("dataLocal", () => {
  it("usa o relogio local com mes e dia de dois digitos", () => {
    assert.equal(dataLocal(new Date(2026, 0, 5, 23, 59)), "2026-01-05");
  });
});

describe("osComPecasHoje", () => {
  const saidaPorOs = (id: number, documento: string | null, campos = {}) =>
    movimentacao({ id, motivo: MOTIVOS.ordemDeServico, documento, dataHora: `${HOJE}T09:00:00`, ...campos });

  it("conta ordens de servico distintas, sem diferenciar caixa nem espacos do documento", () => {
    const total = osComPecasHoje(
      [saidaPorOs(1, "OS 10"), saidaPorOs(2, " os 10 "), saidaPorOs(3, "OS 11")],
      HOJE,
    );

    assert.equal(total, 2);
  });

  it("saida por OS sem documento conta como uma OS propria", () => {
    assert.equal(osComPecasHoje([saidaPorOs(1, null), saidaPorOs(2, "  ")], HOJE), 2);
  });

  it("ignora estornos, outros dias, venda de balcao e entradas", () => {
    const total = osComPecasHoje(
      [
        saidaPorOs(1, "OS 1", { estornada: true }),
        saidaPorOs(2, "OS 2", { dataHora: "2026-11-17T23:59:59" }),
        saidaPorOs(3, "OS 3", { motivo: MOTIVOS.vendaBalcao }),
        saidaPorOs(4, "OS 4", { tipo: "ENTRADA" }),
      ],
      HOJE,
    );

    assert.equal(total, 0);
  });
});

describe("fluxoEstimado", () => {
  const peca = produto({ id: 1, precoVenda: "50.00", precoCusto: "20.0000" });
  const lancamento = (motivo: string, quantidade: string, campos = {}) =>
    movimentacao({ produtoId: 1, motivo, quantidade, ...campos });

  it("sem acesso a custos nao ha estimativa", () => {
    assert.equal(fluxoEstimado([lancamento(MOTIVOS.vendaBalcao, "1")], [produto({ precoCusto: null })]), undefined);
  });

  it("vendas e OS entram pelo preco de venda; compras e perdas saem pelo custo", () => {
    const fluxo = fluxoEstimado(
      [
        lancamento(MOTIVOS.vendaBalcao, "2"), // +100
        lancamento(MOTIVOS.ordemDeServico, "1"), // +50
        lancamento(MOTIVOS.compra, "3"), // -60
        lancamento(MOTIVOS.perda, "1"), // -20
      ],
      [peca],
    );

    assert.equal(fluxo, 70);
  });

  it("devolucao ao fornecedor recupera o custo e devolucao de cliente devolve a venda", () => {
    const fluxo = fluxoEstimado(
      [lancamento(MOTIVOS.devolucaoFornecedor, "1"), lancamento(MOTIVOS.devolucaoCliente, "1")],
      [peca],
    );

    assert.equal(fluxo, 20 - 50);
  });

  it("ignora lancamentos estornados, de produto desconhecido e ajustes", () => {
    const fluxo = fluxoEstimado(
      [
        lancamento(MOTIVOS.vendaBalcao, "5", { estornada: true }),
        lancamento(MOTIVOS.vendaBalcao, "5", { produtoId: 99 }),
        lancamento("Ajuste de inventario", "5", { tipo: "AJUSTE" }),
      ],
      [peca],
    );

    assert.equal(fluxo, 0);
  });
});

describe("sinaisDaOficina", () => {
  const financeiro = { mesAtual: { resultado: "-350.25" } } as unknown as IndicadoresFinanceiros;

  it("o proprietario recebe o resultado apurado do mes, e nao a estimativa", () => {
    const sinais = sinaisDaOficina(
      painel({ itensAbaixoDoMinimo: 3, financeiro }),
      [movimentacao({ motivo: MOTIVOS.vendaBalcao })],
      [produto()],
      HOJE,
    );

    assert.deepEqual(sinais, { osAbertas: 0, estoqueCritico: 3, saldo: -350.25 });
  });

  it("sem o financeiro o saldo vem da estimativa pelas movimentacoes", () => {
    const sinais = sinaisDaOficina(painel(), [movimentacao({ motivo: MOTIVOS.vendaBalcao, quantidade: "2" })], [produto()], HOJE);

    assert.equal(sinais.saldo, 100);
  });

  it("sem painel carregado nao ha contagem de estoque critico", () => {
    const sinais = sinaisDaOficina(null, [], [produto({ precoCusto: null })], HOJE);

    assert.deepEqual(sinais, { osAbertas: 0, estoqueCritico: undefined, saldo: undefined });
  });
});
