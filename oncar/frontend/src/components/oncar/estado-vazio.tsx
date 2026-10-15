import { cn } from "@/lib/utils";

type Ilustracao = "caixa" | "prancheta" | "engrenagem" | "lupa";

/** Estado vazio com desenho de planta tecnica em musgo. */
export function EstadoVazio({
  ilustracao,
  titulo,
  children,
  acao,
  className,
}: {
  ilustracao: Ilustracao;
  titulo: string;
  children?: React.ReactNode;
  acao?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col items-center px-6 py-12 text-center", className)}>
      <Desenho tipo={ilustracao} />
      <p className="fonte-display mt-5 text-xl">{titulo}</p>
      {children && <p className="mt-2 max-w-sm text-sm text-muted-foreground">{children}</p>}
      {acao && <div className="mt-6">{acao}</div>}
    </div>
  );
}

function Desenho({ tipo }: { tipo: Ilustracao }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 160 120"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.25}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-28 w-auto text-ok"
    >
      {/* Moldura de prancha: cantoneiras e linha de cota */}
      <g opacity={0.55}>
        <path d="M18 14h10M18 14v10M142 14h-10M142 14v10M18 106h10M18 106v-10M142 106h-10M142 106v-10" />
        <path d="M30 112h100" strokeDasharray="2 4" />
        <path d="M30 109v6M130 109v6" />
      </g>
      {tipo === "caixa" && (
        <g>
          <path d="M52 58 80 70v30L52 88Z" />
          <path d="M80 70 108 58v30L80 100" />
          <path d="M52 58 38 51l28-12 14 7M108 58l14-7-28-12-14 7" />
          <path d="M80 30v16" strokeDasharray="3 4" opacity={0.6} />
          <path d="M60 76l12 5" opacity={0.5} />
        </g>
      )}
      {tipo === "prancheta" && (
        <g>
          <rect x={50} y={26} width={60} height={76} rx={6} />
          <rect x={66} y={20} width={28} height={12} rx={3} />
          <path d="M60 50h40M60 62h40M60 74h26" strokeDasharray="3 4" opacity={0.7} />
          <path d="M60 86h14" opacity={0.7} />
        </g>
      )}
      {tipo === "engrenagem" && (
        <g>
          <circle cx={72} cy={62} r={21} strokeWidth={6} strokeDasharray="5.2 5.8" opacity={0.45} />
          <circle cx={72} cy={62} r={16} />
          <circle cx={72} cy={62} r={6} />
          <circle cx={110} cy={46} r={11} />
          <path d="m105 46 3.6 3.6L116 42" />
          <path d="M88 55l11-5" strokeDasharray="2 3" opacity={0.6} />
        </g>
      )}
      {tipo === "lupa" && (
        <g>
          <circle cx={72} cy={56} r={22} />
          <path d="M72 40v32M56 56h32" strokeDasharray="2 4" opacity={0.55} />
          <path d="m88 72 18 18" strokeWidth={3} />
        </g>
      )}
    </svg>
  );
}
