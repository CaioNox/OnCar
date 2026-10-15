"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Erro } from "@/components/oncar/avisos";
import { useUsuario } from "@/components/oncar/contexto";
import { CurvaDoSaldo, type PontoDoSaldo } from "@/components/oncar/curva-do-saldo";
import { EstadoVazio } from "@/components/oncar/estado-vazio";
import { NivelDeEstoque, TipoDeMovimentacao, nivelDoEstoque } from "@/components/oncar/etiquetas";
import { api, ErroDaApi, formatarDataHora, formatarMoeda, formatarQuantidade } from "@/lib/api";
import { podeGerenciarEstoque, type Movimentacao, type Produto } from "@/lib/tipos";
import { cn } from "@/lib/utils";

export function ExtratoDoProduto({ id }: { id: string }) {
  return <Conteudo id={id} />;
}

type Carga = { id: string; produto: Produto; movimentacoes: Movimentacao[] } | { id: string; erro: ErroDaApi | null };

/** RF13: extrato (kardex) de um produto, com a curva do saldo como ponto focal. */
function Conteudo({ id }: { id: string }) {
  const usuario = useUsuario();
  const exibeCusto = podeGerenciarEstoque(usuario);
  const [carga, setCarga] = useState<Carga | null>(null);

  useEffect(() => {
    let ativo = true;
    Promise.all([
      api.get<Produto>(`/produtos/${id}`),
      api.get<Movimentacao[]>(`/produtos/${id}/extrato`),
    ])
      .then(([produto, movimentacoes]) => {
        if (ativo) setCarga({ id, produto, movimentacoes });
      })
      .catch((excecao) => {
        if (ativo) setCarga({ id, erro: excecao instanceof ErroDaApi ? excecao : null });
      });
    return () => {
      ativo = false;
    };
  }, [id]);

  // Cronologico, com a variacao de cada lancamento pela diferenca de saldos (vale para ajuste e estorno).
  const linhas = useMemo(() => {
    if (!carga || !("produto" in carga)) return [];
    const cronologicas = [...carga.movimentacoes].sort(
      (a, b) => a.dataHora.localeCompare(b.dataHora) || a.id - b.id,
    );
    let anterior = 0;
    return cronologicas.map((movimentacao) => {
      const saldo = Number(movimentacao.saldoResultante);
      const variacao = saldo - anterior;
      anterior = saldo;
      return { movimentacao, saldo, variacao };
    });
  }, [carga]);

  if (!carga || carga.id !== id) return <EsqueletoDoExtrato />;

  if (!("produto" in carga)) {
    return carga.erro?.status === 404 ? (
      <EstadoVazio
        ilustracao="lupa"
        titulo="Produto não encontrado"
        acao={<VoltarParaProdutos />}
      >
        O endereço pode estar desatualizado ou o produto foi excluído.
      </EstadoVazio>
    ) : (
      <>
        <VoltarParaProdutos />
        <div className="mt-6">
          <Erro mensagem={carga.erro?.message ?? "Falha ao carregar o extrato."} />
        </div>
      </>
    );
  }

  const { produto } = carga;
  const nivel = nivelDoEstoque(produto);
  const pontos: PontoDoSaldo[] = linhas.map(({ movimentacao, saldo }) => ({
    id: movimentacao.id,
    saldo,
    dataHora: movimentacao.dataHora,
    tipo: movimentacao.tipo,
    tipoDescricao: movimentacao.tipoDescricao,
    estornada: movimentacao.estornada,
  }));
  const ultima = linhas.at(-1)?.movimentacao;
  const valorEmEstoque =
    produto.precoCusto !== null ? Number(produto.saldo) * Number(produto.precoCusto) : null;
  const margem =
    produto.precoCusto !== null && Number(produto.precoCusto) > 0
      ? Number(produto.precoVenda) / Number(produto.precoCusto) - 1
      : null;

  return (
    <section aria-labelledby="titulo-extrato">
      <VoltarParaProdutos />

      <header className="mt-8 mb-10 md:mb-14">
        <p className="rotulo">
          <span className="num normal-case tracking-normal">{produto.sku}</span>
          {" · "}
          {[produto.categoria, produto.marca].filter(Boolean).join(" · ")}
        </p>
        <h1 id="titulo-extrato" className="fonte-display mt-3 max-w-4xl text-display">
          {produto.descricao}
        </h1>
        <div className="mt-5 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
          {produto.ativo ? (
            <NivelDeEstoque nivel={nivel} />
          ) : (
            <span className="rounded-full border px-2 py-0.5 text-xs">Inativo</span>
          )}
          {produto.fornecedorPadrao && (
            <span className="inline-flex items-center gap-1.5">
              <i aria-hidden="true" className="bi bi-truck" />
              {produto.fornecedorPadrao}
            </span>
          )}
        </div>
      </header>

      <div className="grid gap-5 lg:grid-cols-12">
        <article className="vidro rounded-3xl border p-6 sm:p-8 lg:col-span-8">
          <div className="flex flex-wrap items-end justify-between gap-6">
            <div>
              <p className="rotulo">Saldo atual</p>
              <p className="mt-3 flex items-baseline gap-2">
                <span className="fonte-display text-kpi">{formatarQuantidade(produto.saldo)}</span>
                <span className="num text-sm text-muted-foreground">{produto.unidade}</span>
              </p>
            </div>
            <p className="num text-sm text-muted-foreground">
              mínimo {formatarQuantidade(produto.estoqueMinimo)} {produto.unidade}
            </p>
          </div>
          <div className="mt-8">
            {linhas.length === 0 ? (
              <p className="py-10 text-center text-sm text-muted-foreground">
                Sem lançamentos: a curva aparece com a primeira entrada.
              </p>
            ) : (
              <CurvaDoSaldo pontos={pontos} minimo={Number(produto.estoqueMinimo)} unidade={produto.unidade} />
            )}
          </div>
        </article>

        <aside className="rounded-3xl border bg-card/85 p-6 sm:p-8 lg:col-span-4">
          <p className="rotulo">Ficha</p>
          <dl className="mt-5 grid gap-5">
            <Dado rotulo="Preço de venda" valor={formatarMoeda(produto.precoVenda)} />
            {exibeCusto && <Dado rotulo="Custo médio · RN004" valor={formatarMoeda(produto.precoCusto)} />}
            {exibeCusto && valorEmEstoque !== null && (
              <Dado rotulo="Valor em estoque" valor={formatarMoeda(valorEmEstoque)} />
            )}
            {exibeCusto && margem !== null && (
              <Dado
                rotulo="Margem sobre o custo"
                valor={margem.toLocaleString("pt-BR", { style: "percent", maximumFractionDigits: 1 })}
              />
            )}
            <Dado rotulo="Lançamentos" valor={String(linhas.length)} />
            {ultima && <Dado rotulo="Último lançamento" valor={formatarDataHora(ultima.dataHora)} />}
          </dl>
        </aside>
      </div>

      <section aria-labelledby="titulo-kardex" className="mt-16 md:mt-24">
        <div className="mb-5">
          <p className="rotulo">RF13 · Kardex</p>
          <h2 id="titulo-kardex" className="fonte-display mt-2 text-2xl sm:text-3xl">
            Extrato de movimentações
          </h2>
        </div>
        <div className="overflow-hidden rounded-2xl border bg-card/90">
          {linhas.length === 0 ? (
            <EstadoVazio ilustracao="prancheta" titulo="Nenhuma movimentação">
              Entradas, saídas e ajustes deste produto vão aparecer aqui.
            </EstadoVazio>
          ) : (
            <div className="tabela-rolagem">
              <table className="tabela">
                <caption className="sr-only">Movimentações do produto, da mais recente para a mais antiga</caption>
                <thead>
                  <tr>
                    <th>Data</th>
                    <th>Tipo</th>
                    <th>Motivo</th>
                    <th className="direita">Quantidade</th>
                    <th className="direita">Saldo</th>
                    <th>Documento</th>
                    <th>Usuário</th>
                    <th>Justificativa</th>
                  </tr>
                </thead>
                <tbody>
                  {[...linhas].reverse().map(({ movimentacao, variacao }) => (
                    <tr
                      key={movimentacao.id}
                      className={cn(movimentacao.estornada && "text-muted-foreground line-through")}
                    >
                      <td className="num whitespace-nowrap text-muted-foreground">
                        {formatarDataHora(movimentacao.dataHora)}
                      </td>
                      <td>
                        <TipoDeMovimentacao movimentacao={movimentacao} />
                      </td>
                      <td>{movimentacao.motivo}</td>
                      <td className={cn("direita num", variacao > 0 && "text-ok-texto")}>
                        {variacao > 0 ? "+" : variacao < 0 ? "−" : ""}
                        {formatarQuantidade(Math.abs(variacao))}
                      </td>
                      <td className="direita num">{formatarQuantidade(movimentacao.saldoResultante)}</td>
                      <td className="num text-xs">{movimentacao.documento ?? "—"}</td>
                      <td className="whitespace-nowrap">{movimentacao.usuario}</td>
                      <td className="max-w-72 text-muted-foreground">{movimentacao.justificativa ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>
    </section>
  );
}

function VoltarParaProdutos() {
  return (
    <Link
      href="/produtos"
      className="inline-flex items-center gap-2 text-sm text-muted-foreground transition-colors hover:text-foreground"
    >
      <i aria-hidden="true" className="bi bi-arrow-left" />
      Voltar para produtos
    </Link>
  );
}

function Dado({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b pb-4 last:border-b-0 last:pb-0">
      <dt className="text-sm text-muted-foreground">{rotulo}</dt>
      <dd className="num text-sm text-foreground">{valor}</dd>
    </div>
  );
}

function EsqueletoDoExtrato() {
  return (
    <div aria-busy="true" aria-label="Carregando o extrato">
      <div className="esqueleto h-4 w-40" />
      <div className="esqueleto mt-10 h-3 w-52" />
      <div className="esqueleto mt-4 h-16 w-full max-w-2xl" />
      <div className="mt-12 grid gap-5 lg:grid-cols-12">
        <div className="esqueleto h-80 rounded-3xl lg:col-span-8" />
        <div className="esqueleto h-80 rounded-3xl lg:col-span-4" />
      </div>
    </div>
  );
}
