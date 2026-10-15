/**
 * Assistente da oficina - previa local.
 *
 * O back-end ainda nao tem o assistente de IA (RF19). Ate la, as respostas sao
 * montadas aqui a partir dos endpoints que ja existem, com regras simples por
 * palavra-chave. Quando houver um endpoint de IA, basta trocar `responder`.
 */
import { api, formatarDataHora, formatarMoeda, formatarNumero } from "@/lib/api";
import type { Movimentacao, Painel, Produto } from "@/lib/tipos";

export type ItemDaResposta = {
  chave: string;
  titulo: string;
  detalhe?: string;
  href?: string;
  alerta?: boolean;
};

export type Resposta = {
  texto: string;
  itens?: ItemDaResposta[];
  sugestoes?: string[];
};

export const SUGESTOES = [
  "O que está abaixo do mínimo?",
  "Qual o saldo do filtro de óleo?",
  "Últimas saídas",
  "Quanto vale o estoque?",
];

const PALAVRAS_VAZIAS = new Set(
  "qual quais o a os as do da dos das de tem temos ha quanto quantos quantas saldo estoque no na nos nas em um uma me mostra mostre ver e por favor produto produtos peca pecas item itens temos existe".split(
    " ",
  ),
);

/** Minusculas e sem acento, para comparar como o usuario digita. */
export function normalizar(texto: string): string {
  return texto
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase()
    .trim();
}

/** Todas as palavras do termo aparecem em algum campo do produto. */
export function produtoCasa(produto: Produto, palavras: string[]): boolean {
  const alvo = normalizar(
    `${produto.sku} ${produto.descricao} ${produto.marca ?? ""} ${produto.categoria}`,
  );
  return palavras.every((palavra) => alvo.includes(palavra));
}

function itemDeProduto(produto: Produto): ItemDaResposta {
  return {
    chave: `produto-${produto.id}`,
    titulo: `${produto.sku} · ${produto.descricao}`,
    detalhe: `saldo ${formatarNumero(produto.saldo, 3)} ${produto.unidade} · mínimo ${formatarNumero(produto.estoqueMinimo, 3)}`,
    href: `/produtos/${produto.id}`,
    alerta: produto.abaixoDoMinimo,
  };
}

function itemDeMovimentacao(movimentacao: Movimentacao): ItemDaResposta {
  return {
    chave: `movimentacao-${movimentacao.id}`,
    titulo: `${movimentacao.tipoDescricao} · ${movimentacao.produtoDescricao}`,
    detalhe: `${formatarNumero(movimentacao.quantidade, 3)} · ${formatarDataHora(movimentacao.dataHora)}${movimentacao.documento ? ` · ${movimentacao.documento}` : ""}`,
    href: `/produtos/${movimentacao.produtoId}`,
  };
}

function plural(quantidade: number, singular: string, varios: string) {
  return `${quantidade} ${quantidade === 1 ? singular : varios}`;
}

export async function responder(pergunta: string, produtos: Produto[]): Promise<Resposta> {
  const texto = normalizar(pergunta);

  // OS ou nota fiscal pelo numero do documento
  const documento = texto.match(/\b(os|nf-?e|nf|nota)\s*[-#:]?\s*(\d+)/);
  if (documento) {
    const numero = documento[2];
    const movimentacoes = await api.get<Movimentacao[]>("/movimentacoes");
    const encontradas = movimentacoes.filter((movimentacao) =>
      normalizar(movimentacao.documento ?? "").includes(numero),
    );
    return encontradas.length
      ? {
          texto: `Encontrei ${plural(encontradas.length, "lançamento", "lançamentos")} com o documento ${numero} entre as movimentações recentes:`,
          itens: encontradas.slice(0, 6).map(itemDeMovimentacao),
        }
      : { texto: `Nenhuma movimentação recente cita o documento ${numero}.` };
  }

  if (/(abaixo|minimo|repor|reposicao|acabando|faltando|falta|critic|ruptura)/.test(texto)) {
    const reposicao = await api.get<Produto[]>("/produtos/reposicao");
    return reposicao.length
      ? {
          texto: `${plural(reposicao.length, "item está", "itens estão")} no mínimo ou abaixo dele:`,
          itens: reposicao.map(itemDeProduto),
        }
      : { texto: "Nenhum item precisa de reposição agora. Estoque saudável." };
  }

  if (/(valor|capital|quanto vale|custo total|dinheiro parado)/.test(texto)) {
    const painel = await api.get<Painel>("/painel");
    return painel.valorTotalEmEstoque === null
      ? { texto: "O seu perfil não enxerga custos (RN007). Um proprietário ou gerente de estoque consegue ver esse valor." }
      : {
          texto: `O estoque vale ${formatarMoeda(painel.valorTotalEmEstoque)} a custo médio, somando ${plural(painel.produtosAtivos, "produto ativo", "produtos ativos")}.`,
        };
  }

  if (/(ultim|recente|movimenta|entrou|entrada|saiu|saida|historico|lancamento)/.test(texto)) {
    const tipo = /(entrou|entrada)/.test(texto) ? "ENTRADA" : /(saiu|saida)/.test(texto) ? "SAIDA" : null;
    const movimentacoes = (await api.get<Movimentacao[]>("/movimentacoes")).filter(
      (movimentacao) => !tipo || movimentacao.tipo === tipo,
    );
    const nome = tipo === "ENTRADA" ? "entradas" : tipo === "SAIDA" ? "saídas" : "movimentações";
    return movimentacoes.length
      ? { texto: `As últimas ${nome}:`, itens: movimentacoes.slice(0, 5).map(itemDeMovimentacao) }
      : { texto: `Não há ${nome} registradas ainda.` };
  }

  const palavras = texto
    .replace(/[?!.,;:]/g, " ")
    .split(/\s+/)
    .filter((palavra) => palavra.length > 1 && !PALAVRAS_VAZIAS.has(palavra));
  if (palavras.length) {
    const encontrados = produtos.filter((produto) => produto.ativo && produtoCasa(produto, palavras));
    if (encontrados.length) {
      return {
        texto:
          encontrados.length === 1
            ? "Achei este produto:"
            : `Achei ${encontrados.length} produtos com "${palavras.join(" ")}":`,
        itens: encontrados.slice(0, 6).map(itemDeProduto),
      };
    }
  }

  return {
    texto: "Ainda não sei responder isso. Por enquanto entendo perguntas como estas:",
    sugestoes: SUGESTOES,
  };
}
