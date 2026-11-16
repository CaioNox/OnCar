"use client";

import { useState } from "react";
import { formatarMes, formatarMoeda } from "@/lib/api";
import type { ResumoMensal } from "@/lib/tipos";
import { cn } from "@/lib/utils";

const MARCAS_NO_EIXO = 4;

/**
 * Topo do eixo: o passo entre as marcas e 1, 2, 2,5 ou 5 vezes uma potencia de dez,
 * para todas as marcas cairem em numeros redondos (1 mil, 2 mil...).
 */
function escala(maior: number): number {
  if (maior <= 0) return 1000;
  const passoMinimo = maior / MARCAS_NO_EIXO;
  const potencia = 10 ** Math.floor(Math.log10(passoMinimo));
  const passo = [1, 2, 2.5, 5, 10].map((fator) => fator * potencia).find((opcao) => opcao >= passoMinimo) ?? 10 * potencia;
  return passo * MARCAS_NO_EIXO;
}

/** Rotulo curto do eixo: 0, 500, 1,5 mil, 2 mi. */
function compacto(valor: number): string {
  const formato = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 });
  if (valor >= 1_000_000) return `${formato.format(valor / 1_000_000)} mi`;
  if (valor >= 1_000) return `${formato.format(valor / 1_000)} mil`;
  return formato.format(valor);
}

/**
 * Colunas agrupadas de receita x despesa por mes, num eixo so. A dica mostra os dois
 * valores do mes sob o ponteiro ou o foco; a tabela traz os mesmos numeros sem hover.
 */
export function GraficoReceitaDespesa({ meses, className }: { meses: ResumoMensal[]; className?: string }) {
  const [ativo, setAtivo] = useState<number | null>(null);
  const [comoTabela, setComoTabela] = useState(false);

  const valores = meses.map((mes) => ({
    rotulo: mes.mes ? formatarMes(mes.mes, "short") : "",
    nome: mes.mes ? formatarMes(mes.mes) : "Total",
    receita: Number(mes.receita),
    despesa: Number(mes.despesaTotal),
    resultado: Number(mes.resultado),
  }));
  const topo = escala(Math.max(0, ...valores.flatMap((mes) => [mes.receita, mes.despesa])));
  const altura = (valor: number) => `${Math.max((valor / topo) * 100, valor > 0 ? 1 : 0)}%`;
  const vazio = valores.every((mes) => mes.receita === 0 && mes.despesa === 0);

  return (
    <div className={className}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <ul aria-label="Legenda" className="flex items-center gap-4 text-xs text-muted-foreground">
          <li className="flex items-center gap-1.5">
            <span aria-hidden="true" className="size-2.5 rounded-[2px] bg-grafico-receita" />
            Receita
          </li>
          <li className="flex items-center gap-1.5">
            <span aria-hidden="true" className="size-2.5 rounded-[2px] bg-grafico-despesa" />
            Despesa
          </li>
        </ul>
        <button
          type="button"
          aria-pressed={comoTabela}
          onClick={() => setComoTabela((atual) => !atual)}
          className="inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-xs text-muted-foreground transition-colors hover:text-foreground"
        >
          <i aria-hidden="true" className={cn("bi", comoTabela ? "bi-bar-chart" : "bi-table")} />
          {comoTabela ? "Ver gráfico" : "Ver tabela"}
        </button>
      </div>

      {comoTabela ? (
        <div className="tabela-rolagem mt-4">
          <table className="tabela">
            <caption className="sr-only">Receita, despesa e resultado por mês</caption>
            <thead>
              <tr>
                <th>Mês</th>
                <th className="direita">Receita</th>
                <th className="direita">Despesa</th>
                <th className="direita">Resultado</th>
              </tr>
            </thead>
            <tbody>
              {valores.map((mes) => (
                <tr key={mes.nome}>
                  <td>{mes.nome}</td>
                  <td className="direita num">{formatarMoeda(mes.receita)}</td>
                  <td className="direita num">{formatarMoeda(mes.despesa)}</td>
                  <td className={cn("direita num", mes.resultado < 0 && "text-sinal-texto")}>
                    {formatarMoeda(mes.resultado)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : vazio ? (
        <p className="mt-6 text-sm text-muted-foreground">
          Ainda não há vendas nem despesas lançadas nestes meses.
        </p>
      ) : (
        <div className="mt-5 grid grid-cols-[auto_1fr] gap-x-3">
          {/* eixo y: valores arredondados, alinhados as linhas de grade */}
          <div aria-hidden="true" className="relative h-48 w-14">
            {Array.from({ length: MARCAS_NO_EIXO + 1 }, (_, passo) => (
              <span
                key={passo}
                className="num absolute right-0 translate-y-1/2 text-[0.6875rem] whitespace-nowrap text-muted-foreground"
                style={{ bottom: `${(passo / MARCAS_NO_EIXO) * 100}%` }}
              >
                {compacto((topo * passo) / MARCAS_NO_EIXO)}
              </span>
            ))}
          </div>

          <div role="group" aria-label="Receita e despesa por mês" className="relative h-48">
            {Array.from({ length: MARCAS_NO_EIXO + 1 }, (_, passo) => (
              <span
                key={passo}
                aria-hidden="true"
                className="absolute inset-x-0 h-px bg-border"
                style={{ bottom: `${(passo / MARCAS_NO_EIXO) * 100}%` }}
              />
            ))}
            <div className="relative flex h-full items-end justify-around">
              {valores.map((mes, indice) => (
                <div
                  key={mes.nome}
                  tabIndex={0}
                  aria-label={`${mes.nome}: receita ${formatarMoeda(mes.receita)}, despesa ${formatarMoeda(mes.despesa)}`}
                  onPointerEnter={() => setAtivo(indice)}
                  onPointerLeave={() => setAtivo((atual) => (atual === indice ? null : atual))}
                  onFocus={() => setAtivo(indice)}
                  onBlur={() => setAtivo((atual) => (atual === indice ? null : atual))}
                  // a coluna inteira do mes e o alvo: mais facil que acertar uma barra fina
                  className="relative flex h-full flex-1 items-end justify-center gap-[2px] rounded-md outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <span
                    aria-hidden="true"
                    className={cn(
                      "w-3 rounded-t-[4px] bg-grafico-receita transition-[filter] sm:w-5",
                      ativo === indice && "brightness-110",
                    )}
                    style={{ height: altura(mes.receita) }}
                  />
                  <span
                    aria-hidden="true"
                    className={cn(
                      "w-3 rounded-t-[4px] bg-grafico-despesa transition-[filter] sm:w-5",
                      ativo === indice && "brightness-110",
                    )}
                    style={{ height: altura(mes.despesa) }}
                  />
                  {ativo === indice && <Dica mes={mes} alinhamento={dicaAlinhada(indice, valores.length)} />}
                </div>
              ))}
            </div>
          </div>

          <span aria-hidden="true" />
          <div aria-hidden="true" className="flex justify-around pt-2">
            {valores.map((mes, indice) => (
              <span
                key={mes.nome}
                className={cn(
                  "flex-1 text-center text-[0.6875rem] text-muted-foreground",
                  indice === valores.length - 1 && "font-medium text-foreground",
                )}
              >
                {mes.rotulo}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/** Nas pontas a dica encosta para dentro, para nao sair do cartao. */
function dicaAlinhada(indice: number, total: number): "inicio" | "centro" | "fim" {
  if (indice === 0) return "inicio";
  if (indice === total - 1) return "fim";
  return "centro";
}

function Dica({
  mes,
  alinhamento,
}: {
  mes: { nome: string; receita: number; despesa: number; resultado: number };
  alinhamento: "inicio" | "centro" | "fim";
}) {
  return (
    <div
      role="presentation"
      className={cn(
        "pointer-events-none absolute bottom-full z-10 mb-2 w-max min-w-44 rounded-lg border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-lg",
        alinhamento === "inicio" && "left-0",
        alinhamento === "centro" && "left-1/2 -translate-x-1/2",
        alinhamento === "fim" && "right-0",
      )}
    >
      <p className="mb-1.5 text-muted-foreground">{mes.nome}</p>
      <LinhaDaDica cor="bg-grafico-receita" rotulo="Receita" valor={mes.receita} />
      <LinhaDaDica cor="bg-grafico-despesa" rotulo="Despesa" valor={mes.despesa} />
      <p className="mt-1.5 flex justify-between gap-4 border-t pt-1.5">
        <span className="text-muted-foreground">Resultado</span>
        <span className={cn("num font-medium", mes.resultado < 0 && "text-sinal-texto")}>
          {formatarMoeda(mes.resultado)}
        </span>
      </p>
    </div>
  );
}

function LinhaDaDica({ cor, rotulo, valor }: { cor: string; rotulo: string; valor: number }) {
  return (
    <p className="flex items-center justify-between gap-4">
      <span className="flex items-center gap-1.5 text-muted-foreground">
        <span aria-hidden="true" className={cn("h-0.5 w-3 rounded-full", cor)} />
        {rotulo}
      </span>
      <span className="num font-medium">{formatarMoeda(valor)}</span>
    </p>
  );
}
