// @vitest-environment node
import assert from "node:assert/strict";
import { describe, it } from "vitest";

import { movimentacao, painel, produto } from "@/lib/fixtures.test-utils";
import { lancamentosPorDia, leiturasDoEstoque, saudacao, serieDoCapital } from "@/lib/painel";

describe("saudacao", () => {
  const casos: Array<[number, string]> = [
    [0, "Boa noite"],
    [4, "Boa noite"],
    [5, "Bom dia"],
    [11, "Bom dia"],
    [12, "Boa tarde"],
    [17, "Boa tarde"],
    [18, "Boa noite"],
    [23, "Boa noite"],
  ];
  for (const [hora, esperada] of casos) {
    it(`${hora}h -> ${esperada}`, () => assert.equal(saudacao(hora), esperada));
  }
});

describe("lancamentosPorDia", () => {
  it("devolve os ultimos sete dias ate hoje, do mais antigo ao atual, atravessando o mes", () => {
    const dias = lancamentosPorDia(
      [
        movimentacao({ id: 1, dataHora: "2026-12-02T08:00:00" }),
        movimentacao({ id: 2, dataHora: "2026-12-02T17:00:00" }),
        movimentacao({ id: 3, dataHora: "2026-11-27T10:00:00" }),
        movimentacao({ id: 4, dataHora: "2026-11-25T10:00:00" }), // fora da janela
      ],
      new Date(2026, 11, 2, 12),
    );

    assert.deepEqual(
      dias.map((dia) => dia.dia),
      ["2026-11-26", "2026-11-27", "2026-11-28", "2026-11-29", "2026-11-30", "2026-12-01", "2026-12-02"],
    );
    assert.deepEqual(
      dias.map((dia) => dia.total),
      [0, 1, 0, 0, 0, 0, 2],
    );
  });

  it("aceita outra janela de dias", () => {
    assert.equal(lancamentosPorDia([], new Date(2026, 0, 1), 3).length, 3);
  });
});

describe("leiturasDoEstoque", () => {
  it("calcula os percentuais sobre os produtos ativos e os lancamentos sem contar os estornos", () => {
    const leituras = leiturasDoEstoque(
      painel({
        produtosAtivos: 8,
        itensAbaixoDoMinimo: 2,
        listaDeReposicao: [produto({ saldo: "0.000" }), produto({ saldo: "1.000" })],
      }),
      [
        movimentacao({ id: 1 }),
        movimentacao({ id: 2, estornada: true }),
        movimentacao({ id: 3 }),
        movimentacao({ id: 4, tipo: "ESTORNO" }),
      ],
    );

    const [acimaDoMinimo, semRuptura, semEstorno] = leituras;
    assert.deepEqual([acimaDoMinimo.atual, acimaDoMinimo.total, acimaDoMinimo.percentual], [6, 8, 75]);
    assert.deepEqual([semRuptura.atual, semRuptura.percentual], [7, 88]);
    assert.deepEqual([semEstorno.atual, semEstorno.total, semEstorno.percentual], [2, 3, 67]);
    assert.deepEqual(
      leituras.map((leitura) => leitura.alertaDeEstoque),
      [true, true, false],
    );
  });

  it("sem produtos nem lancamentos os percentuais sao zero, e nao NaN", () => {
    const leituras = leiturasDoEstoque(painel(), []);

    assert.deepEqual(
      leituras.map((leitura) => leitura.percentual),
      [0, 0, 0],
    );
  });
});

describe("serieDoCapital", () => {
  const peca = produto({ id: 1, saldo: "10.000", precoCusto: "2.0000" });

  it("sem acesso a custos a serie e vazia", () => {
    assert.deepEqual(serieDoCapital([movimentacao()], [produto({ precoCusto: null })]), []);
  });

  it("sem movimentacoes ha um ponto so, o capital de hoje", () => {
    assert.deepEqual(serieDoCapital([], [peca]), [20]);
  });

  it("volta no tempo pelo saldo resultante, do mais antigo ao atual, em qualquer ordem de entrada", () => {
    const antiga = movimentacao({
      id: 1,
      tipo: "ENTRADA",
      quantidade: "3",
      saldoResultante: "7",
      dataHora: "2026-11-10T08:00:00",
    });
    const recente = movimentacao({
      id: 2,
      tipo: "ENTRADA",
      quantidade: "3",
      saldoResultante: "10",
      dataHora: "2026-11-11T08:00:00",
    });

    // antes da antiga: 7 - 3 = 4 unidades; depois: 7; hoje: 10 (a 2 cada)
    assert.deepEqual(serieDoCapital([antiga, recente], [peca]), [8, 14, 20]);
    assert.deepEqual(serieDoCapital([recente, antiga], [peca]), [8, 14, 20]);
  });

  it("antes de uma saida sem lancamento anterior o saldo era maior", () => {
    const saida = movimentacao({ tipo: "SAIDA", quantidade: "5", saldoResultante: "10" });

    assert.deepEqual(serieDoCapital([saida], [peca]), [30, 20]);
  });

  it("mesmo horario desempata pelo id", () => {
    const primeira = movimentacao({ id: 1, tipo: "SAIDA", quantidade: "1", saldoResultante: "11" });
    const segunda = movimentacao({ id: 2, tipo: "SAIDA", quantidade: "1", saldoResultante: "10" });

    assert.deepEqual(serieDoCapital([segunda, primeira], [peca]), [24, 22, 20]);
  });
});
