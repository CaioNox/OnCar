/**
 * Leituras do painel de instrumentos derivadas dos dados reais da API.
 * Nada aqui inventa numero: quando falta dado, a funcao devolve vazio.
 */
import { dataLocal } from "@/lib/sinais";
import type { Movimentacao, Painel, Produto } from "@/lib/tipos";

/** Mais recente primeiro, como a API entrega; garante a ordem mesmo assim. */
function doMaisRecente(movimentacoes: Movimentacao[]): Movimentacao[] {
  return [...movimentacoes].sort(
    (a, b) => b.dataHora.localeCompare(a.dataHora) || b.id - a.id,
  );
}

/**
 * Evolucao do capital em estoque ao longo das movimentacoes recentes, do mais
 * antigo ao atual. Parte do saldo de hoje e volta no tempo pelo saldo resultante
 * de cada lancamento; o custo usado e o medio atual, por isso e uma estimativa.
 * Sem acesso a custos devolve lista vazia.
 */
export function serieDoCapital(movimentacoes: Movimentacao[], produtos: Produto[]): number[] {
  if (!produtos.some((produto) => produto.precoCusto !== null)) return [];

  const custo = new Map(produtos.map((produto) => [produto.id, Number(produto.precoCusto ?? 0)]));
  const saldo = new Map(produtos.map((produto) => [produto.id, Number(produto.saldo)]));
  const capital = () => {
    let total = 0;
    for (const [id, quantidade] of saldo) total += quantidade * (custo.get(id) ?? 0);
    return total;
  };

  const ordenadas = doMaisRecente(movimentacoes);
  const pontos = [capital()];
  ordenadas.forEach((movimentacao, indice) => {
    const anterior = ordenadas
      .slice(indice + 1)
      .find((outra) => outra.produtoId === movimentacao.produtoId);
    const resultante = Number(movimentacao.saldoResultante);
    const quantidade = Number(movimentacao.quantidade);
    let saldoAntes = resultante;
    if (anterior) saldoAntes = Number(anterior.saldoResultante);
    else if (movimentacao.tipo === "ENTRADA") saldoAntes = resultante - quantidade;
    else if (movimentacao.tipo === "SAIDA") saldoAntes = resultante + quantidade;
    saldo.set(movimentacao.produtoId, saldoAntes);
    pontos.push(capital());
  });
  return pontos.reverse();
}

/** Quantidade de lancamentos por dia, dos ultimos `dias` dias ate hoje. */
export function lancamentosPorDia(
  movimentacoes: Movimentacao[],
  hoje: Date,
  dias = 7,
): { dia: string; total: number }[] {
  return Array.from({ length: dias }, (_, indice) => {
    const data = new Date(hoje);
    data.setDate(hoje.getDate() - (dias - 1 - indice));
    const dia = dataLocal(data);
    return {
      dia,
      total: movimentacoes.filter((movimentacao) => movimentacao.dataHora.startsWith(dia)).length,
    };
  });
}

export type Leitura = {
  rotulo: string;
  /** 0 a 100 */
  percentual: number;
  atual: number;
  total: number;
  unidade: string;
  /** Leituras de estoque acendem o carmesim quando caem demais. */
  alertaDeEstoque: boolean;
};

/**
 * Tacometros com o que a API ja mede. As metas financeiras (faturamento x meta,
 * OS concluidas) entram aqui quando os modulos de ordens de servico e relatorios existirem.
 */
export function leiturasDoEstoque(painel: Painel, movimentacoes: Movimentacao[]): Leitura[] {
  const ativos = painel.produtosAtivos;
  const zerados = painel.listaDeReposicao.filter((produto) => Number(produto.saldo) <= 0).length;
  const lancamentos = movimentacoes.filter((movimentacao) => movimentacao.tipo !== "ESTORNO");
  const estornados = lancamentos.filter((movimentacao) => movimentacao.estornada).length;
  const percentual = (parte: number, todo: number) => (todo > 0 ? Math.round((parte / todo) * 100) : 0);

  return [
    {
      rotulo: "Acima do mínimo",
      percentual: percentual(ativos - painel.itensAbaixoDoMinimo, ativos),
      atual: ativos - painel.itensAbaixoDoMinimo,
      total: ativos,
      unidade: "produtos",
      alertaDeEstoque: true,
    },
    {
      rotulo: "Sem ruptura",
      percentual: percentual(ativos - zerados, ativos),
      atual: ativos - zerados,
      total: ativos,
      unidade: "com saldo",
      alertaDeEstoque: true,
    },
    {
      rotulo: "Sem estorno",
      percentual: percentual(lancamentos.length - estornados, lancamentos.length),
      atual: lancamentos.length - estornados,
      total: lancamentos.length,
      unidade: "lançamentos",
      alertaDeEstoque: false,
    },
  ];
}

/** Saudacao pela hora local. */
export function saudacao(hora: number): string {
  if (hora >= 5 && hora < 12) return "Bom dia";
  if (hora >= 12 && hora < 18) return "Boa tarde";
  return "Boa noite";
}
