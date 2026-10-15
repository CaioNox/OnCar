"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import AppleActivityCard, { type ActivityData } from "@/components/kokonutui/apple-activity-card";
import { BentoCard, BentoGrid, superficieBento } from "@/components/kokonutui/bento-grid";
import { LiquidGlassCard } from "@/components/kokonutui/liquid-glass-card";
import { Erro } from "@/components/oncar/avisos";
import { useOficina, useUsuario } from "@/components/oncar/contexto";
import { EstadoVazio } from "@/components/oncar/estado-vazio";
import {
  NivelDeEstoque,
  NumeroEmReais,
  TipoDeMovimentacao,
  nivelDoEstoque,
} from "@/components/oncar/etiquetas";
import { Sparkline } from "@/components/oncar/sparkline";
import { formatarDataHora, formatarMoeda, formatarQuantidade } from "@/lib/api";
import {
  lancamentosPorDia,
  leiturasDoEstoque,
  saudacao,
  serieDoCapital,
  type Leitura,
} from "@/lib/painel";
import type { Movimentacao, Painel, Produto } from "@/lib/tipos";
import { cn } from "@/lib/utils";

export default function PaginaDoPainel() {
  return <ConteudoDoPainel />;
}

/** RF12 e RN005: indicadores, lista de reposicao e ultimas movimentacoes. */
function ConteudoDoPainel() {
  const usuario = useUsuario();
  const { painel, movimentacoes, produtos, atualizadoEm, carregado, erro, recarregar } = useOficina();

  const serie = useMemo(() => serieDoCapital(movimentacoes, produtos), [movimentacoes, produtos]);
  const porDia = useMemo(
    () => (atualizadoEm ? lancamentosPorDia(movimentacoes, atualizadoEm) : []),
    [movimentacoes, atualizadoEm],
  );

  if (!carregado) return <EsqueletoDoPainel />;
  // Sem dados (API fora do ar, rede, 500): mostra o erro e como tentar de novo.
  if (!painel || !atualizadoEm) {
    return <FalhaNoPainel mensagem={erro ?? "Falha ao carregar o painel."} aoTentar={recarregar} />;
  }

  const hoje = porDia.at(-1)?.total ?? 0;
  const repor = painel.itensAbaixoDoMinimo;
  const resumo = [
    repor === 0
      ? "Nenhuma peça no mínimo"
      : `${repor} ${repor === 1 ? "peça pede" : "peças pedem"} reposição`,
    `${hoje} ${hoje === 1 ? "lançamento" : "lançamentos"} hoje`,
  ].join(" · ");

  return (
    <section aria-labelledby="titulo-painel">
      <header className="mb-10 md:mb-14">
        <p className="rotulo">
          {atualizadoEm.toLocaleDateString("pt-BR", { weekday: "long", day: "numeric", month: "long" })}
          {" · atualizado às "}
          {atualizadoEm.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}
        </p>
        <h1 id="titulo-painel" className="fonte-display mt-3 text-4xl sm:text-5xl">
          {saudacao(atualizadoEm.getHours())}, {usuario?.nome.split(" ")[0]}.
        </h1>
        <p className="mt-3 max-w-2xl text-muted-foreground">{resumo}.</p>
      </header>

      <Erro mensagem={erro} />

      <AcoesRapidas />

      {painel.produtosAtivos === 0 && <PrimeirosPassos />}

      <BentoGrid className="md:grid-cols-2 lg:grid-cols-12">
        <BentoCard className="md:col-span-2 lg:col-span-7 lg:row-span-2" inclinacao={1.5}>
          {painel.valorTotalEmEstoque !== null ? (
            <CapitalEmEstoque
              valor={Number(painel.valorTotalEmEstoque)}
              serie={serie}
              painel={painel}
              produtos={produtos}
            />
          ) : (
            <ItensParaRepor painel={painel} />
          )}
        </BentoCard>
        <BentoCard className="lg:col-span-5">
          <KpiAbaixoDoMinimo painel={painel} />
        </BentoCard>
        <BentoCard className="lg:col-span-5">
          <KpiLancamentos porDia={porDia} />
        </BentoCard>
        <BentoCard className="md:col-span-2 lg:col-span-5">
          <Tacometros leituras={leiturasDoEstoque(painel, movimentacoes)} />
        </BentoCard>
        <BentoCard className="md:col-span-2 lg:col-span-7">
          <Reposicao lista={painel.listaDeReposicao} />
        </BentoCard>
      </BentoGrid>

      <UltimasMovimentacoes movimentacoes={painel.ultimasMovimentacoes} />
    </section>
  );
}

/** Ponto focal da tela: o KPI financeiro, em vidro liquido. */
function CapitalEmEstoque({
  valor,
  serie,
  painel,
  produtos,
}: {
  valor: number;
  serie: number[];
  painel: Painel;
  produtos: Produto[];
}) {
  const variacao = serie.length >= 2 ? serie[serie.length - 1] - serie[0] : 0;
  const maior = produtos
    .filter((produto) => produto.ativo)
    .map((produto) => ({ produto, valor: Number(produto.saldo) * Number(produto.precoCusto ?? 0) }))
    .sort((a, b) => b.valor - a.valor)[0];

  return (
    <LiquidGlassCard glassSize="lg" className="h-full rounded-3xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="rotulo flex items-center gap-2">
          <i aria-hidden="true" className="bi bi-cash-stack text-sm text-ok-texto" />
          Capital em estoque
        </p>
        <span className="rotulo">custo médio · RN004</span>
      </div>

      <p className="fonte-display mt-7 text-kpi">
        <NumeroEmReais valor={valor} />
      </p>
      <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
        {serie.length >= 2 ? (
          <>
            <i
              aria-hidden="true"
              className={cn("bi", variacao >= 0 ? "bi-arrow-up-right text-ok-texto" : "bi-arrow-down-right")}
            />
            {variacao >= 0 ? "+" : "−"}
            {formatarMoeda(Math.abs(variacao))} nas últimas {serie.length - 1} movimentações
          </>
        ) : (
          "Sem movimentações recentes"
        )}
      </p>

      <Sparkline
        valores={serie}
        rotulo={`Evolução estimada do capital nas últimas ${Math.max(serie.length - 1, 0)} movimentações, a custo médio atual`}
        className="mt-8 h-28 w-full"
      />

      <dl className="mt-8 grid grid-cols-2 gap-x-6 gap-y-4 border-t pt-6 sm:grid-cols-3">
        <Estatistica rotulo="Produtos ativos" valor={String(painel.produtosAtivos)} />
        <Estatistica
          rotulo="Média por produto"
          valor={formatarMoeda(valor / Math.max(painel.produtosAtivos, 1))}
        />
        {maior && (
          <Estatistica rotulo="Maior posição" valor={`${maior.produto.sku} · ${formatarMoeda(maior.valor)}`} />
        )}
      </dl>
    </LiquidGlassCard>
  );
}

/** Quem nao ve custos (RN007) ganha a reposicao como ponto focal. */
function ItensParaRepor({ painel }: { painel: Painel }) {
  const repor = painel.itensAbaixoDoMinimo;
  return (
    <LiquidGlassCard glassSize="lg" className="h-full rounded-3xl">
      <p className="rotulo flex items-center gap-2">
        <i aria-hidden="true" className="bi bi-boxes text-sm text-ok-texto" />
        Itens para repor
      </p>
      <p className="fonte-display mt-7 text-kpi">{repor}</p>
      <p className="mt-3 text-sm text-muted-foreground">
        {repor > 0
          ? `de ${painel.produtosAtivos} produtos ativos estão no mínimo ou abaixo dele (RN005).`
          : "Nenhuma peça precisa de reposição agora."}
      </p>
      {repor > 0 && <NivelDeEstoque nivel="critico" className="mt-5" />}
      <p className="mt-10 border-t pt-6 text-xs text-muted-foreground">
        O capital em estoque aparece só para proprietário e gerente de estoque (RN007).
      </p>
    </LiquidGlassCard>
  );
}

function KpiAbaixoDoMinimo({ painel }: { painel: Painel }) {
  const ativos = painel.produtosAtivos;
  const repor = painel.itensAbaixoDoMinimo;
  const segmentos = Math.min(ativos, 24);
  const criticos = ativos > 24 ? Math.round((repor / ativos) * 24) : repor;

  return (
    <LiquidGlassCard glassEffect={false} className="h-full rounded-3xl backdrop-blur-md">
      <div className="flex items-start justify-between gap-3">
        <p className="rotulo">Abaixo do mínimo</p>
        {repor > 0 && <NivelDeEstoque nivel="critico" />}
      </div>
      <p className="fonte-display mt-4 text-5xl">
        {repor}
        <span className="ml-2 text-lg text-muted-foreground">/ {ativos}</span>
      </p>
      <div
        role="img"
        aria-label={`${repor} de ${ativos} produtos ativos no mínimo ou abaixo`}
        className="mt-5 flex h-2 gap-1"
      >
        {Array.from({ length: Math.max(segmentos, 1) }, (_, indice) => (
          <span
            key={indice}
            className={cn("flex-1 rounded-full", indice < criticos ? "bg-sinal" : "bg-ok/55")}
          />
        ))}
      </div>
      <Link
        href="/produtos?nivel=critico"
        className="mt-5 inline-flex items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
      >
        Ver peças para repor <i aria-hidden="true" className="bi bi-arrow-right" />
      </Link>
    </LiquidGlassCard>
  );
}

function KpiLancamentos({ porDia }: { porDia: { dia: string; total: number }[] }) {
  const semana = porDia.reduce((soma, dia) => soma + dia.total, 0);
  const hoje = porDia.at(-1)?.total ?? 0;
  return (
    <LiquidGlassCard glassEffect={false} className="h-full rounded-3xl backdrop-blur-md">
      <p className="rotulo">Lançamentos · 7 dias</p>
      <div className="mt-4 flex items-end justify-between gap-6">
        <p className="fonte-display text-5xl">{semana}</p>
        <Sparkline
          valores={porDia.map((dia) => dia.total)}
          rotulo={`Lançamentos por dia na última semana: ${porDia.map((dia) => dia.total).join(", ")}`}
          className="mb-1 h-12 w-40"
        />
      </div>
      <p className="mt-4 text-sm text-muted-foreground">
        {hoje} hoje · média de{" "}
        {(semana / Math.max(porDia.length, 1)).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} por dia
      </p>
    </LiquidGlassCard>
  );
}

// Cores dos aneis: carmesim e ambar so quando uma leitura de estoque cai demais.
function corDoAnel(leitura: Leitura, indice: number): Pick<ActivityData, "color" | "colorEnd"> {
  if (leitura.alertaDeEstoque && leitura.percentual < 40) {
    return { color: "var(--color-carmesim-claro)", colorEnd: "var(--color-carmesim)" };
  }
  if (leitura.alertaDeEstoque && leitura.percentual < 70) {
    return { color: "#e8c06a", colorEnd: "var(--color-ambar)" };
  }
  return [
    { color: "var(--color-liquen)", colorEnd: "var(--color-musgo-claro)" },
    { color: "var(--color-musgo-claro)", colorEnd: "var(--color-musgo)" },
    { color: "#7f9cc4", colorEnd: "var(--color-marinho-frio)" },
  ][indice % 3];
}

function Tacometros({ leituras }: { leituras: Leitura[] }) {
  return (
    <div className={superficieBento}>
      <p className="rotulo">Tacômetros do estoque</p>
      <AppleActivityCard
        className="mt-7"
        activities={leituras.map((leitura, indice) => ({
          label: leitura.rotulo,
          value: leitura.percentual,
          current: leitura.atual,
          target: leitura.total,
          unit: leitura.unidade,
          ...corDoAnel(leitura, indice),
        }))}
      />
      <p className="mt-auto pt-7 text-xs leading-relaxed text-muted-foreground">
        Faturamento × meta e OS concluídas entram aqui quando a API tiver os módulos de ordens de
        serviço e relatórios (RF14 e RF16).
      </p>
    </div>
  );
}

function Reposicao({ lista }: { lista: Produto[] }) {
  return (
    <div className={superficieBento}>
      <p className="rotulo">Reposição · RN005</p>
      {lista.length === 0 ? (
        <EstadoVazio ilustracao="engrenagem" titulo="Tudo em ordem" className="py-8">
          Nenhuma peça está no estoque mínimo.
        </EstadoVazio>
      ) : (
        <ul className="-mx-2 mt-4 divide-y">
          {lista.slice(0, 5).map((produto) => (
            <li key={produto.id}>
              <Link
                href={`/produtos/${produto.id}`}
                className="flex items-center gap-4 rounded-lg px-2 py-3 transition-colors hover:bg-accent"
              >
                <span className="num w-20 shrink-0 text-xs text-muted-foreground">{produto.sku}</span>
                <span className="min-w-0 flex-1 truncate text-sm">{produto.descricao}</span>
                <span className="num hidden text-xs text-muted-foreground sm:inline">
                  {formatarQuantidade(produto.saldo)} / {formatarQuantidade(produto.estoqueMinimo)} {produto.unidade}
                </span>
                <NivelDeEstoque nivel={nivelDoEstoque(produto)} />
              </Link>
            </li>
          ))}
        </ul>
      )}
      {lista.length > 0 && (
        <Link
          href="/produtos?nivel=critico"
          className="mt-auto inline-flex items-center gap-1.5 self-start pt-6 text-sm text-muted-foreground transition-colors hover:text-foreground"
        >
          {lista.length > 5 ? `Ver as ${lista.length} peças no estoque` : "Abrir no estoque"}
          <i aria-hidden="true" className="bi bi-arrow-right" />
        </Link>
      )}
    </div>
  );
}

function UltimasMovimentacoes({ movimentacoes }: { movimentacoes: Movimentacao[] }) {
  return (
    <section aria-labelledby="titulo-ultimas" className="mt-16 md:mt-24">
      <div className="mb-5 flex items-end justify-between gap-4">
        <div>
          <p className="rotulo">Kardex recente · RF13</p>
          <h2 id="titulo-ultimas" className="fonte-display mt-2 text-2xl sm:text-3xl">
            Últimas movimentações
          </h2>
        </div>
        <Link
          href="/movimentacoes"
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
        >
          Ver todas <i aria-hidden="true" className="bi bi-arrow-right" />
        </Link>
      </div>

      <div className="overflow-hidden rounded-2xl border bg-card/90">
        {movimentacoes.length === 0 ? (
          <EstadoVazio ilustracao="prancheta" titulo="Nada lançado ainda">
            As entradas, saídas e ajustes aparecem aqui assim que forem registrados.
          </EstadoVazio>
        ) : (
          <div className="tabela-rolagem">
            <table className="tabela">
              <thead>
                <tr>
                  <th>Data</th>
                  <th>Produto</th>
                  <th>Tipo</th>
                  <th className="direita">Quantidade</th>
                  <th className="direita">Saldo</th>
                  <th>Usuário</th>
                </tr>
              </thead>
              <tbody>
                {movimentacoes.slice(0, 8).map((movimentacao) => (
                  <tr key={movimentacao.id} className={cn(movimentacao.estornada && "text-muted-foreground line-through")}>
                    <td className="num whitespace-nowrap text-muted-foreground">{formatarDataHora(movimentacao.dataHora)}</td>
                    <td>
                      <Link href={`/produtos/${movimentacao.produtoId}`} className="hover:underline">
                        <span className="num mr-2 text-xs text-muted-foreground">{movimentacao.produtoSku}</span>
                        {movimentacao.produtoDescricao}
                      </Link>
                    </td>
                    <td>
                      <TipoDeMovimentacao movimentacao={movimentacao} />
                    </td>
                    <td className="direita num">{formatarQuantidade(movimentacao.quantidade)}</td>
                    <td className="direita num">{formatarQuantidade(movimentacao.saldoResultante)}</td>
                    <td className="whitespace-nowrap">{movimentacao.usuario}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}

function FalhaNoPainel({ mensagem, aoTentar }: { mensagem: string; aoTentar: () => Promise<void> }) {
  const [tentando, setTentando] = useState(false);
  return (
    <section className="mx-auto max-w-xl py-16 text-center">
      <EstadoVazio ilustracao="engrenagem" titulo="Não deu para abrir o painel">
        Verifique se o backend está rodando em{" "}
        <code className="num">{process.env.NEXT_PUBLIC_API_URL}</code> e tente de novo.
      </EstadoVazio>
      <div className="mt-6 text-left">
        <Erro mensagem={mensagem} />
      </div>
      <button
        type="button"
        disabled={tentando}
        onClick={() => {
          setTentando(true);
          void aoTentar().finally(() => setTentando(false));
        }}
        className="mt-2 inline-flex items-center gap-2 rounded-full border px-5 py-2.5 text-sm transition-colors hover:bg-accent disabled:opacity-60"
      >
        <i aria-hidden="true" className={cn("bi bi-arrow-clockwise", tentando && "animate-spin")} />
        {tentando ? "Tentando…" : "Tentar de novo"}
      </button>
    </section>
  );
}

const ACOES = [
  { href: "/movimentacoes", icone: "bi-box-arrow-in-down", rotulo: "Registrar entrada", exemplo: "Ex.: chegaram 10 filtros de óleo" },
  { href: "/movimentacoes", icone: "bi-box-arrow-up", rotulo: "Registrar saída", exemplo: "Ex.: 4 velas para a OS do Gol" },
  { href: "/produtos?nivel=critico", icone: "bi-cart-check", rotulo: "Montar pedido", exemplo: "Peças no mínimo para comprar" },
  { href: "/produtos", icone: "bi-search", rotulo: "Consultar peça", exemplo: "Ex.: pastilha de freio dianteira" },
] as const;

/** Atalhos para o que mais se faz no balcao, com um exemplo de uso em cada. */
function AcoesRapidas() {
  return (
    <nav aria-label="Ações rápidas" className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
      {ACOES.map((acao) => (
        <Link
          key={acao.rotulo}
          href={acao.href}
          className="flex items-start gap-3 rounded-2xl border bg-card/80 p-4 backdrop-blur-md transition-colors hover:bg-accent"
        >
          <i aria-hidden="true" className={cn("bi mt-0.5 text-lg text-ok-texto", acao.icone)} />
          <span className="min-w-0">
            <span className="block text-sm font-medium">{acao.rotulo}</span>
            <span className="mt-0.5 block truncate text-xs text-muted-foreground">{acao.exemplo}</span>
          </span>
        </Link>
      ))}
    </nav>
  );
}

/** Oficina sem produtos: explica o fluxo com exemplos em vez de mostrar zeros. */
function PrimeirosPassos() {
  const passos = [
    { titulo: "Cadastre as peças", texto: "SKU, descrição, unidade e mínimo. Ex.: FO-001 · Filtro de óleo · UN · mínimo 5." },
    { titulo: "Lance a entrada inicial", texto: "Informe quantidade e custo; o custo médio (RN004) é calculado sozinho." },
    { titulo: "Registre as saídas", texto: "Cada baixa atualiza saldo e kardex; no mínimo, a peça entra na reposição." },
  ];
  return (
    <section aria-label="Primeiros passos" className="mb-6 rounded-3xl border bg-card/85 p-6 backdrop-blur-md">
      <p className="rotulo">Primeiros passos</p>
      <ol className="mt-4 grid gap-5 md:grid-cols-3">
        {passos.map((passo, indice) => (
          <li key={passo.titulo} className="flex gap-3">
            <span className="num flex size-7 shrink-0 items-center justify-center rounded-full border text-xs">
              {indice + 1}
            </span>
            <div>
              <p className="text-sm font-medium">{passo.titulo}</p>
              <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{passo.texto}</p>
            </div>
          </li>
        ))}
      </ol>
      <Link
        href="/produtos"
        className="mt-6 inline-flex items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
      >
        Cadastrar a primeira peça <i aria-hidden="true" className="bi bi-arrow-right" />
      </Link>
    </section>
  );
}

function Estatistica({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div>
      <dt className="rotulo">{rotulo}</dt>
      <dd className="num mt-1.5 text-sm text-foreground">{valor}</dd>
    </div>
  );
}

function EsqueletoDoPainel() {
  return (
    <div aria-busy="true" aria-label="Carregando o painel">
      <div className="esqueleto h-3 w-56" />
      <div className="esqueleto mt-4 h-12 w-80 max-w-full" />
      <div className="esqueleto mt-4 h-4 w-96 max-w-full" />
      <div className="mt-10 grid gap-4 md:mt-14 md:grid-cols-2 lg:grid-cols-12 lg:gap-5">
        <div className="esqueleto h-96 rounded-3xl md:col-span-2 lg:col-span-7 lg:row-span-2 lg:h-auto" />
        <div className="esqueleto h-48 rounded-3xl lg:col-span-5" />
        <div className="esqueleto h-48 rounded-3xl lg:col-span-5" />
      </div>
      <div className="esqueleto mt-16 h-72 rounded-2xl md:mt-24" />
    </div>
  );
}
