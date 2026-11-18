/** Dados de teste no formato dos DTOs da API; cada teste muda so o que importa para ele. */
import type { Movimentacao, Painel, Produto } from "@/lib/tipos";

export function produto(campos: Partial<Produto> = {}): Produto {
  return {
    id: 1,
    sku: "FL-001",
    descricao: "Filtro de oleo",
    categoria: "Filtro",
    unidade: "UN",
    marca: "Tecfil",
    saldo: "10.000",
    estoqueMinimo: "2.000",
    precoVenda: "50.00",
    precoCusto: "20.0000",
    abaixoDoMinimo: false,
    ativo: true,
    fornecedorPadrao: null,
    ...campos,
  };
}

export function movimentacao(campos: Partial<Movimentacao> = {}): Movimentacao {
  return {
    id: 1,
    produtoId: 1,
    produtoSku: "FL-001",
    produtoDescricao: "Filtro de oleo",
    tipo: "SAIDA",
    tipoDescricao: "Saida",
    motivo: "Venda de balcao",
    quantidade: "1.000",
    saldoResultante: "9.000",
    documento: null,
    justificativa: null,
    usuario: "Gerente",
    dataHora: "2026-11-18T10:00:00",
    competencia: "2026-11-18",
    estornada: false,
    estornavel: true,
    ...campos,
  };
}

export function painel(campos: Partial<Painel> = {}): Painel {
  return {
    produtosAtivos: 0,
    itensAbaixoDoMinimo: 0,
    valorTotalEmEstoque: null,
    listaDeReposicao: [],
    ultimasMovimentacoes: [],
    financeiro: null,
    maiorGiro: [],
    ...campos,
  };
}
