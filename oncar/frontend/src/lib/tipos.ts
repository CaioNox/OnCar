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
  /** Ausente para os perfis sem acesso financeiro (RN007). */
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
};

export type Opcao = { valor: string; descricao: string };

export type Fornecedor = { id: number; razaoSocial: string; cnpj: string; ativo: boolean };

export type Cliente = { id: number; nome: string; documento: string; ativo: boolean };

/** Perfis que enxergam custo, margem e operacoes de entrada/ajuste (RN007). */
export const PERFIS_DE_ESTOQUE: Perfil[] = ["PROPRIETARIO", "GERENTE_ESTOQUE"];

export function podeGerenciarEstoque(usuario: UsuarioSessao | null): boolean {
  return usuario != null && PERFIS_DE_ESTOQUE.includes(usuario.perfil);
}

export function podeRegistrarSaida(usuario: UsuarioSessao | null): boolean {
  return usuario != null && [...PERFIS_DE_ESTOQUE, "ATENDENTE"].includes(usuario.perfil);
}
