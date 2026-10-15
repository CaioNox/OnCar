import { formatarDataHora, formatarQuantidade } from "@/lib/api";
import type { Movimentacao } from "@/lib/tipos";

export type PontoDoSaldo = {
  id: number;
  saldo: number;
  dataHora: string;
  tipo: Movimentacao["tipo"];
  tipoDescricao: string;
  estornada: boolean;
};

const COR_DO_PONTO: Record<Movimentacao["tipo"], string> = {
  ENTRADA: "var(--ok-texto)",
  SAIDA: "var(--foreground)",
  AJUSTE: "var(--atencao-texto)",
  ESTORNO: "var(--muted-foreground)",
};

const LARGURA = 640;
const ALTURA = 200;
const MARGEM = { topo: 16, direita: 14, base: 24, esquerda: 40 };

/**
 * Curva do saldo em degraus: cada lancamento muda o saldo e ele fica ate o
 * proximo. A linha tracejada marca o estoque minimo.
 */
export function CurvaDoSaldo({
  pontos,
  minimo,
  unidade,
}: {
  /** Em ordem cronologica, do primeiro lancamento ao ultimo. */
  pontos: PontoDoSaldo[];
  minimo: number;
  unidade: string;
}) {
  // O produto nasce com saldo zero: a curva comeca antes do primeiro lancamento.
  const serie = [{ saldo: 0 }, ...pontos];
  const maximo = Math.max(minimo * 1.3, ...serie.map((ponto) => ponto.saldo), 1);
  // Uma casa a mais no fim: o saldo atual tambem ganha o seu degrau.
  const x = (indice: number) =>
    MARGEM.esquerda + (indice / serie.length) * (LARGURA - MARGEM.esquerda - MARGEM.direita);
  const y = (valor: number) => MARGEM.topo + (1 - valor / maximo) * (ALTURA - MARGEM.topo - MARGEM.base);

  const degraus = `${serie
    .map((ponto, indice) => (indice === 0 ? `M${x(0)} ${y(ponto.saldo)}` : `H${x(indice)} V${y(ponto.saldo)}`))
    .join(" ")} H${x(serie.length)}`;
  const area = `${degraus} V${y(0)} H${x(0)} Z`;
  const ultimo = pontos.at(-1);

  return (
    <svg
      role="img"
      aria-label={`Curva do saldo em ${pontos.length} lançamentos; saldo atual ${formatarQuantidade(ultimo?.saldo ?? 0)} ${unidade}, mínimo ${formatarQuantidade(minimo)}.`}
      viewBox={`0 0 ${LARGURA} ${ALTURA}`}
      className="h-auto w-full overflow-visible text-ok"
    >
      <defs>
        <linearGradient id="curva-do-saldo-area" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.26" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
        </linearGradient>
      </defs>

      {/* Eixo: zero e o topo da escala */}
      <g className="text-muted-foreground" fontSize={10} fontFamily="var(--font-mono)">
        <line x1={MARGEM.esquerda} x2={LARGURA - MARGEM.direita} y1={y(0)} y2={y(0)} stroke="currentColor" strokeOpacity={0.3} />
        <text x={MARGEM.esquerda - 8} y={y(0) + 3} textAnchor="end" fill="currentColor">
          0
        </text>
        <text x={MARGEM.esquerda - 8} y={y(maximo) + 3} textAnchor="end" fill="currentColor">
          {formatarQuantidade(Math.round(maximo))}
        </text>
      </g>

      {minimo > 0 && (
        <g className="text-muted-foreground">
          <line
            x1={MARGEM.esquerda}
            x2={LARGURA - MARGEM.direita}
            y1={y(minimo)}
            y2={y(minimo)}
            stroke="currentColor"
            strokeDasharray="4 5"
            strokeOpacity={0.7}
          />
          <text x={LARGURA - MARGEM.direita} y={y(minimo) - 6} textAnchor="end" fontSize={10} fontFamily="var(--font-mono)" fill="currentColor">
            mínimo {formatarQuantidade(minimo)}
          </text>
        </g>
      )}

      <path d={area} fill="url(#curva-do-saldo-area)" />
      <path d={degraus} fill="none" stroke="currentColor" strokeWidth={2} strokeLinejoin="round" />

      {pontos.map((ponto, indice) => (
        <circle
          key={ponto.id}
          cx={x(indice + 1)}
          cy={y(ponto.saldo)}
          r={4}
          fill={ponto.estornada ? "var(--card)" : COR_DO_PONTO[ponto.tipo]}
          stroke={COR_DO_PONTO[ponto.tipo]}
          strokeWidth={1.5}
        >
          <title>
            {`${ponto.tipoDescricao} · ${formatarDataHora(ponto.dataHora)} · saldo ${formatarQuantidade(ponto.saldo)} ${unidade}${ponto.estornada ? " · estornada" : ""}`}
          </title>
        </circle>
      ))}
    </svg>
  );
}
