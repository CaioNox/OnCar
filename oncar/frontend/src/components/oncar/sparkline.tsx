import { useId } from "react";
import { cn } from "@/lib/utils";

/**
 * Mini grafico de linha em musgo. Estica para caber no container; o ultimo
 * ponto ganha um marcador. Com menos de dois valores desenha so a linha de base.
 */
export function Sparkline({
  valores,
  rotulo,
  className,
  area = true,
}: {
  valores: number[];
  /** Descricao lida pelo leitor de tela. */
  rotulo: string;
  className?: string;
  area?: boolean;
}) {
  const gradiente = useId();
  const largura = 100;
  const altura = 32;
  const margem = 3;

  const minimo = Math.min(...valores);
  const maximo = Math.max(...valores);
  const faixa = maximo - minimo || 1;
  const pontos =
    valores.length >= 2
      ? valores.map((valor, indice) => ({
          x: (indice / (valores.length - 1)) * largura,
          y: altura - margem - ((valor - minimo) / faixa) * (altura - margem * 2),
        }))
      : [
          { x: 0, y: altura / 2 },
          { x: largura, y: altura / 2 },
        ];

  const linha = pontos.map((ponto, indice) => `${indice ? "L" : "M"}${ponto.x.toFixed(2)} ${ponto.y.toFixed(2)}`).join(" ");
  const ultimo = pontos[pontos.length - 1];

  return (
    <div className={cn("relative text-ok", className)} role="img" aria-label={rotulo}>
      <svg
        aria-hidden="true"
        className="size-full overflow-visible"
        preserveAspectRatio="none"
        viewBox={`0 0 ${largura} ${altura}`}
      >
        <defs>
          <linearGradient id={gradiente} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
            <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
          </linearGradient>
        </defs>
        {area && <path d={`${linha} L${largura} ${altura} L0 ${altura} Z`} fill={`url(#${gradiente})`} />}
        <path
          d={linha}
          fill="none"
          stroke="currentColor"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={1.75}
          vectorEffect="non-scaling-stroke"
        />
      </svg>
      <span
        aria-hidden="true"
        className="absolute size-2 -translate-x-1/2 -translate-y-1/2 rounded-full bg-liquen shadow-[0_0_0_4px_rgb(107_125_78/0.25)]"
        style={{ left: `${ultimo.x}%`, top: `${(ultimo.y / altura) * 100}%` }}
      />
    </div>
  );
}
