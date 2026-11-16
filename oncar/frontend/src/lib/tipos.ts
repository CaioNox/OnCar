/** Tipos espelhando os DTOs da API do OnCar (br.com.oncar.web.api.dto). */

export type Perfil =
  | "PROPRIETARIO"
  | "GERENTE_ESTOQUE"
  | "ATENDENTE"
  | "MECANICO";

export type UsuarioSessao = {
  id: number;
  nome: string;
  email: string;
  perfil: Perfil;
  perfilDescricao: string;
};

export type Produto = {
  id: number;
  sku: string;
  descricao: string;
  categoria: string;
  unidade: string;
  marca: string | null;
  saldo: string;
  estoqueMinimo: string;
  precoVenda: string;
  /** Ausente para os perfis sem acesso financeiro. */
  precoCusto: string | null;
  abaixoDoMinimo: boolean;
  ativo: boolean;
  fornecedorPadrao: string | null;
};

export type Movimentacao = {
  id: number;
  produtoId: number;
  produtoSku: string;
  produtoDescricao: string;
  tipo: "ENTRADA" | "SAIDA" | "AJUSTE" | "ESTORNO";
  tipoDescricao: string;
  motivo: string;
  quantidade: string;
  saldoResultante: string;
  documento: string | null;
  justificativa: string | null;
  usuario: string;
  dataHora: string;
  competencia: string;
  estornada: boolean;
  estornavel: boolean;
};

export type Painel = {
  produtosAtivos: number;
  itensAbaixoDoMinimo: number;
  valorTotalEmEstoque: string | null;
  listaDeReposicao: Produto[];
  ultimasMovimentacoes: Movimentacao[];
  /** Ausente para quem nao ve receita e despesa (so o proprietario ve). */
  financeiro: IndicadoresFinanceiros | null;
  /** Pecas que mais sairam no mes por venda ou OS. */
  maiorGiro: ProdutoGiro[];
};

/**
 * Apuracao de um mes por competencia. Despesa total = custo das pecas vendidas +
 * perdas + despesas operacionais; compras viram estoque e ficam a parte.
 * `mes` (AAAA-MM) e nulo no consolidado de um periodo.
 */
export type ResumoMensal = {
  mes: string | null;
  receita: string;
  custoDasVendas: string;
  margemBruta: string;
  perdas: string;
  despesasOperacionais: string;
  despesaTotal: string;
  resultado: string;
  compras: string;
};

export type IndicadoresFinanceiros = {
  mesAtual: ResumoMensal;
  /** Do mes mais antigo ao atual. */
  historico: ResumoMensal[];
};

export type ProdutoGiro = {
  produtoId: number;
  sku: string;
  descricao: string;
  unidade: string;
  quantidade: string;
  saidas: number;
  /** Ausente para quem nao ve o financeiro. */
  receita: string | null;
};

export type Despesa = {
  id: number;
  descricao: string;
  categoria: string;
  categoriaDescricao: string;
  valor: string;
  /** AAAA-MM-DD */
  competencia: string;
  fornecedorId: number | null;
  fornecedor: string | null;
  documento: string | null;
  observacao: string | null;
  usuario: string;
  lancadaEm: string;
};

export type Periodo = { inicio: string; fim: string };

export type TotalPorGrupo = { grupo: string; lancamentos: number; quantidade: string; valor: string };

export type LinhaEntrada = {
  movimentacaoId: number;
  competencia: string;
  dataHora: string;
  produtoId: number;
  sku: string;
  produto: string;
  categoria: string;
  fornecedor: string | null;
  documento: string | null;
  quantidade: string;
  unidade: string;
  custoUnitario: string;
  valorTotal: string;
  usuario: string;
};

export type RelatorioEntradas = {
  periodo: Periodo;
  linhas: LinhaEntrada[];
  valorTotal: string;
  porFornecedor: TotalPorGrupo[];
  porCategoria: TotalPorGrupo[];
};

export type LinhaSaida = {
  movimentacaoId: number;
  competencia: string;
  dataHora: string;
  produtoId: number;
  sku: string;
  produto: string;
  categoria: string;
  motivo: string;
  cliente: string | null;
  documento: string | null;
  quantidade: string;
  unidade: string;
  custoTotal: string;
  /** Nulo nas saidas que nao geram receita (devolucao, perda). */
  valorVenda: string | null;
  usuario: string;
};

export type RelatorioSaidas = {
  periodo: Periodo;
  linhas: LinhaSaida[];
  custoTotal: string;
  receita: string;
  custoDasVendas: string;
  margemBruta: string;
  porMotivo: TotalPorGrupo[];
  porCategoria: TotalPorGrupo[];
};

export type LinhaDespesa = {
  despesaId: number;
  competencia: string;
  descricao: string;
  categoria: string;
  fornecedor: string | null;
  documento: string | null;
  valor: string;
  usuario: string;
};

export type RelatorioGastos = {
  periodo: Periodo;
  despesas: LinhaDespesa[];
  totalDespesas: string;
  despesasPorCategoria: TotalPorGrupo[];
  totalCompras: string;
  comprasPorFornecedor: TotalPorGrupo[];
  totalGasto: string;
  meses: ResumoMensal[];
  consolidado: ResumoMensal;
};

export type LinhaPosicao = {
  produtoId: number;
  sku: string;
  produto: string;
  categoria: string;
  unidade: string;
  fornecedor: string | null;
  saldoInicial: string;
  entradas: string;
  saidas: string;
  ajustes: string;
  saldoFinal: string;
  estoqueMinimo: string;
  custoMedio: string;
  valorFinal: string;
  abaixoDoMinimo: boolean;
  ativo: boolean;
};

export type RelatorioPosicao = {
  periodo: Periodo;
  linhas: LinhaPosicao[];
  valorTotal: string;
  produtosAbaixoDoMinimo: number;
};

export type FiltrosDeRelatorio = {
  categoriasDeProduto: Opcao[];
  categoriasDeDespesa: Opcao[];
  fornecedores: Fornecedor[];
  usuarios: Opcao[];
};

export type Opcao = { valor: string; descricao: string };

export type Fornecedor = { id: number; razaoSocial: string; cnpj: string; ativo: boolean };

export type Cliente = { id: number; nome: string; documento: string; ativo: boolean };

/** Perfis que enxergam custo, margem e operacoes de entrada/ajuste. */
export const PERFIS_DE_ESTOQUE: Perfil[] = ["PROPRIETARIO", "GERENTE_ESTOQUE"];

export function podeGerenciarEstoque(usuario: UsuarioSessao | null): boolean {
  return usuario != null && PERFIS_DE_ESTOQUE.includes(usuario.perfil);
}

export function podeRegistrarSaida(usuario: UsuarioSessao | null): boolean {
  return usuario != null && [...PERFIS_DE_ESTOQUE, "ATENDENTE"].includes(usuario.perfil);
}

/** Receita, despesas e resultado consolidados: so o proprietario. */
export function podeVerFinanceiro(usuario: UsuarioSessao | null): boolean {
  return usuario?.perfil === "PROPRIETARIO";
}

/** Relatorios operacionais (entradas, saidas, posicao): proprietario e gerente. */
export function podeEmitirRelatorios(usuario: UsuarioSessao | null): boolean {
  return podeGerenciarEstoque(usuario);
}
