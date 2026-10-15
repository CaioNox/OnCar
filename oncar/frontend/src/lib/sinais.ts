/**
 * Sinais da oficina para o GarageWallpaper, derivados dos dados que a API ja expoe.
 *
 * A API ainda nao tem ordens de servico nem financeiro, entao
 * dois sinais sao aproximacoes documentadas; quando esses modulos existirem,
 * basta trocar o calculo aqui e o wallpaper continua igual.
 */
import type { Movimentacao, Painel, Produto } from "@/lib/tipos";

export type Sinais = {
  osAbertas?: number;
  estoqueCritico?: number;
  saldo?: number;
};

/** Descricoes dos motivos como a API devolve (espelham MotivoMovimentacao.java). */
export const MOTIVOS = {
  compra: "Compra de fornecedor",
  devolucaoCliente: "Devolucao de cliente",
  vendaBalcao: "Venda de balcao",
  ordemDeServico: "Aplicacao em ordem de servico",
  devolucaoFornecedor: "Devolucao ao fornecedor",
  perda: "Perda ou avaria",
} as const;

/** Data local no formato AAAA-MM-DD, o mesmo prefixo do dataHora da API. */
export function dataLocal(data: Date): string {
  const mes = String(data.getMonth() + 1).padStart(2, "0");
  const dia = String(data.getDate()).padStart(2, "0");
  return `${data.getFullYear()}-${mes}-${dia}`;
}

/**
 * Aproximacao de "OS abertas" enquanto o modulo de ordens de servico nao existe: ordens de servico
 * distintas que receberam pecas hoje (saidas por OS, agrupadas pelo documento).
 */
export function osComPecasHoje(movimentacoes: Movimentacao[], hoje: string): number {
  const documentos = new Set(
    movimentacoes
      .filter(
        (movimentacao) =>
          movimentacao.tipo === "SAIDA" &&
          movimentacao.motivo === MOTIVOS.ordemDeServico &&
          !movimentacao.estornada &&
          movimentacao.dataHora.startsWith(hoje),
      )
      .map((movimentacao) => movimentacao.documento?.trim().toUpperCase() || `#${movimentacao.id}`),
  );
  return documentos.size;
}

/**
 * Aproximacao do saldo enquanto o financeiro nao existe: fluxo das movimentacoes
 * recentes (a API devolve as 50 ultimas). Vendas e OS entram pelo preco de venda;
 * compras e perdas saem pelo custo medio atual. Sem acesso a custos nao
 * ha como estimar, e o wallpaper fica no tom neutro.
 */
export function fluxoEstimado(movimentacoes: Movimentacao[], produtos: Produto[]): number | undefined {
  if (!produtos.some((produto) => produto.precoCusto !== null)) return undefined;

  const porId = new Map(produtos.map((produto) => [produto.id, produto]));
  let fluxo = 0;
  for (const movimentacao of movimentacoes) {
    const produto = porId.get(movimentacao.produtoId);
    if (!produto || movimentacao.estornada) continue;

    const quantidade = Number(movimentacao.quantidade);
    const venda = quantidade * Number(produto.precoVenda);
    const custo = quantidade * Number(produto.precoCusto ?? 0);
    switch (movimentacao.motivo) {
      case MOTIVOS.vendaBalcao:
      case MOTIVOS.ordemDeServico:
        fluxo += venda;
        break;
      case MOTIVOS.devolucaoFornecedor:
        fluxo += custo;
        break;
      case MOTIVOS.compra:
      case MOTIVOS.perda:
        fluxo -= custo;
        break;
      case MOTIVOS.devolucaoCliente:
        fluxo -= venda;
        break;
    }
  }
  return fluxo;
}

export function sinaisDaOficina(
  painel: Painel | null,
  movimentacoes: Movimentacao[],
  produtos: Produto[],
  hoje: string,
): Sinais {
  return {
    osAbertas: osComPecasHoje(movimentacoes, hoje),
    estoqueCritico: painel?.itensAbaixoDoMinimo,
    saldo: fluxoEstimado(movimentacoes, produtos),
  };
}
