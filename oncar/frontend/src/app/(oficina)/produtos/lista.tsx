"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Erro } from "@/components/oncar/avisos";
import { CAMPO } from "@/components/oncar/campo";
import { CartaoPeca } from "@/components/oncar/cartao-peca";
import { Chip } from "@/components/oncar/chip";
import { useUsuario } from "@/components/oncar/contexto";
import { EstadoVazio } from "@/components/oncar/estado-vazio";
import { NivelDeEstoque, nivelDoEstoque, type Nivel } from "@/components/oncar/etiquetas";
import useDebounce from "@/hooks/use-debounce";
import { api, ErroDaApi, formatarMoeda, formatarQuantidade } from "@/lib/api";
import { podeGerenciarEstoque, type Opcao, type Produto } from "@/lib/tipos";
import { cn } from "@/lib/utils";

type Visao = "tabela" | "cartoes";
type Resultado = { chave: string; produtos: Produto[] };

const CHAVE_DA_VISAO = "oncar-produtos-visao";

const NIVEIS: { valor: Nivel; rotulo: string; cor: string }[] = [
  { valor: "critico", rotulo: "Repor", cor: "bg-sinal" },
  { valor: "atencao", rotulo: "Perto do mínimo", cor: "bg-atencao" },
  { valor: "ok", rotulo: "Em dia", cor: "bg-ok" },
];

function visaoInicial(): Visao {
  if (typeof window === "undefined") return "tabela";
  try {
    const salva = localStorage.getItem(CHAVE_DA_VISAO);
    if (salva === "tabela" || salva === "cartoes") return salva;
  } catch {
    // Sem localStorage: decide pela largura da tela.
  }
  return window.matchMedia("(max-width: 639px)").matches ? "cartoes" : "tabela";
}

/** busca por descricao, SKU, marca ou fornecedor, exibindo saldo e preco. */
export function ListaDeProdutos({ nivelInicial }: { nivelInicial: Nivel | null }) {
  const usuario = useUsuario();
  const exibeCusto = podeGerenciarEstoque(usuario);

  const [categorias, setCategorias] = useState<Opcao[]>([]);
  const [termo, setTermo] = useState("");
  const [categoria, setCategoria] = useState("");
  const [somenteAtivos, setSomenteAtivos] = useState(true);
  const [nivel, setNivel] = useState<Nivel | null>(nivelInicial);
  const [visao, setVisao] = useState<Visao>(visaoInicial);
  const [resultado, setResultado] = useState<Resultado | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  const termoBuscado = useDebounce(termo.trim(), 300);

  // A busca roda quando os filtros aplicados mudam; carregando = a resposta ainda e de outra chave.
  const parametros = new URLSearchParams();
  if (termoBuscado) parametros.set("termo", termoBuscado);
  if (categoria) parametros.set("categoria", categoria);
  parametros.set("somenteAtivos", String(somenteAtivos));
  const chave = parametros.toString();
  const carregando = resultado?.chave !== chave;

  useEffect(() => {
    api.get<Opcao[]>("/categorias").then(setCategorias).catch(() => undefined);
  }, []);

  useEffect(() => {
    let ativo = true;
    api
      .get<Produto[]>(`/produtos?${chave}`)
      .then((encontrados) => {
        if (!ativo) return;
        setResultado({ chave, produtos: encontrados });
        setErro(null);
      })
      .catch((excecao) => {
        if (!ativo) return;
        setResultado((anterior) => ({ chave, produtos: anterior?.produtos ?? [] }));
        setErro(excecao instanceof ErroDaApi ? excecao.message : "Falha ao buscar produtos.");
      });
    return () => {
      ativo = false;
    };
  }, [chave]);

  const produtos = resultado?.produtos;
  const visiveis = useMemo(
    () =>
      (produtos ?? []).filter(
        (produto) => !nivel || (produto.ativo && nivelDoEstoque(produto) === nivel),
      ),
    [produtos, nivel],
  );
  const paraRepor = visiveis.filter((produto) => produto.ativo && produto.abaixoDoMinimo).length;

  function mudarVisao(nova: Visao) {
    setVisao(nova);
    try {
      localStorage.setItem(CHAVE_DA_VISAO, nova);
    } catch {
      // A preferencia vale so para esta aba.
    }
  }

  function limparFiltros() {
    setTermo("");
    setCategoria("");
    setSomenteAtivos(true);
    setNivel(null);
  }

  return (
    <section aria-labelledby="titulo-produtos">
      <header className="mb-10 flex flex-col gap-6 md:mb-14 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="rotulo">Estoque</p>
          <h1 id="titulo-produtos" className="fonte-display mt-3 text-display">
            Produtos
          </h1>
          <p className="mt-4 text-muted-foreground" aria-live="polite">
            {carregando
              ? "Buscando no estoque…"
              : `${visiveis.length} ${visiveis.length === 1 ? "produto" : "produtos"}${
                  paraRepor ? ` · ${paraRepor} para repor` : ""
                }`}
          </p>
        </div>

        <div role="group" aria-label="Visualização" className="inline-flex self-start rounded-full border bg-background/40 p-1 lg:self-auto">
          {(
            [
              ["tabela", "bi-table", "Tabela"],
              ["cartoes", "bi-grid-3x2-gap", "Cartões"],
            ] as const
          ).map(([valor, icone, rotulo]) => (
            <button
              key={valor}
              type="button"
              aria-pressed={visao === valor}
              onClick={() => mudarVisao(valor)}
              className={cn(
                "flex h-8 items-center gap-2 rounded-full px-3.5 text-xs transition-colors",
                visao === valor ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground",
              )}
            >
              <i aria-hidden="true" className={cn("bi", icone)} />
              {rotulo}
            </button>
          ))}
        </div>
      </header>

      <Erro mensagem={erro} />

      <div className="mb-8 flex flex-col gap-4">
        <form role="search" className="relative max-w-xl" onSubmit={(evento) => evento.preventDefault()}>
          <label htmlFor="busca-produtos" className="sr-only">
            Buscar por descrição, SKU, marca ou fornecedor
          </label>
          <i
            aria-hidden="true"
            className="bi bi-search pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-muted-foreground"
          />
          <input
            id="busca-produtos"
            type="search"
            value={termo}
            onChange={(evento) => setTermo(evento.target.value)}
            placeholder="Descrição, SKU, marca ou fornecedor"
            className={cn(CAMPO, "pl-10")}
          />
        </form>

        <div role="group" aria-label="Categoria" className="flex flex-wrap items-center gap-2">
          <Chip ativo={!categoria} aoAlternar={() => setCategoria("")}>
            Todas as categorias
          </Chip>
          {categorias.map((opcao) => (
            <Chip
              key={opcao.valor}
              ativo={categoria === opcao.valor}
              aoAlternar={() => setCategoria((atual) => (atual === opcao.valor ? "" : opcao.valor))}
            >
              {opcao.descricao}
            </Chip>
          ))}
        </div>

        <div role="group" aria-label="Nível do estoque e situação" className="flex flex-wrap items-center gap-2">
          {NIVEIS.map((opcao) => (
            <Chip
              key={opcao.valor}
              ativo={nivel === opcao.valor}
              aoAlternar={() => setNivel((atual) => (atual === opcao.valor ? null : opcao.valor))}
            >
              <span aria-hidden="true" className={cn("size-1.5 rounded-full", opcao.cor)} />
              {opcao.rotulo}
            </Chip>
          ))}
          <span aria-hidden="true" className="mx-1 h-5 w-px bg-border" />
          <Chip ativo={somenteAtivos} aoAlternar={() => setSomenteAtivos((ativos) => !ativos)}>
            Somente ativos
          </Chip>
        </div>
      </div>

      {!resultado ? (
        <EsqueletoDaLista visao={visao} />
      ) : visiveis.length === 0 ? (
        <div className="rounded-2xl border bg-card/80">
          <EstadoVazio
            ilustracao="lupa"
            titulo="Nada encontrado"
            acao={
              <button
                type="button"
                onClick={limparFiltros}
                className="inline-flex h-9 items-center gap-2 rounded-lg border px-4 text-sm hover:bg-accent"
              >
                <i aria-hidden="true" className="bi bi-x-circle" />
                Limpar filtros
              </button>
            }
          >
            Nenhum produto combina com a busca e os filtros escolhidos.
          </EstadoVazio>
        </div>
      ) : visao === "tabela" ? (
        <div
          aria-busy={carregando}
          className={cn("overflow-hidden rounded-2xl border bg-card/90 transition-opacity", carregando && "opacity-60")}
        >
          <div className="tabela-rolagem">
            <table className="tabela">
              <caption className="sr-only">Produtos encontrados</caption>
              <thead>
                <tr>
                  <th>SKU</th>
                  <th>Descrição</th>
                  <th>Categoria</th>
                  <th className="direita">Saldo</th>
                  <th className="direita">Mínimo</th>
                  <th>Nível</th>
                  {exibeCusto && <th className="direita">Custo médio</th>}
                  <th className="direita">Preço de venda</th>
                  <th>Situação</th>
                </tr>
              </thead>
              <tbody>
                {visiveis.map((produto) => (
                  <tr key={produto.id}>
                    <td className="num text-xs whitespace-nowrap text-muted-foreground">{produto.sku}</td>
                    <td>
                      <Link href={`/produtos/${produto.id}`} className="font-medium hover:underline">
                        {produto.descricao}
                      </Link>
                      {produto.marca && <span className="block text-xs text-muted-foreground">{produto.marca}</span>}
                    </td>
                    <td className="text-muted-foreground">{produto.categoria}</td>
                    <td className="direita num whitespace-nowrap">
                      {formatarQuantidade(produto.saldo)}{" "}
                      <span className="text-muted-foreground">{produto.unidade}</span>
                    </td>
                    <td className="direita num text-muted-foreground">{formatarQuantidade(produto.estoqueMinimo)}</td>
                    <td>{produto.ativo ? <NivelDeEstoque nivel={nivelDoEstoque(produto)} /> : "—"}</td>
                    {exibeCusto && <td className="direita num">{formatarMoeda(produto.precoCusto)}</td>}
                    <td className="direita num">{formatarMoeda(produto.precoVenda)}</td>
                    <td>
                      {produto.ativo ? (
                        <span className="text-xs text-ok-texto">Ativo</span>
                      ) : (
                        <span className="text-xs text-muted-foreground">Inativo</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <ul
          aria-busy={carregando}
          className={cn(
            "grid gap-4 transition-opacity sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4",
            carregando && "opacity-60",
          )}
        >
          {visiveis.map((produto, indice) => (
            <li key={produto.id}>
              <CartaoPeca produto={produto} exibeCusto={exibeCusto} ordem={indice} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function EsqueletoDaLista({ visao }: { visao: Visao }) {
  if (visao === "cartoes") {
    return (
      <div aria-busy="true" aria-label="Carregando produtos" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {Array.from({ length: 8 }, (_, indice) => (
          <div key={indice} className="esqueleto h-60 rounded-2xl" />
        ))}
      </div>
    );
  }
  return (
    <div aria-busy="true" aria-label="Carregando produtos" className="overflow-hidden rounded-2xl border bg-card/90">
      <div className="esqueleto h-10 rounded-none" />
      {Array.from({ length: 7 }, (_, indice) => (
        <div key={indice} className="flex items-center gap-6 border-t px-4 py-3.5">
          <div className="esqueleto h-3 w-16" />
          <div className="esqueleto h-3 flex-1" />
          <div className="esqueleto h-3 w-20" />
          <div className="esqueleto h-5 w-16 rounded-full" />
        </div>
      ))}
    </div>
  );
}
