import { cn } from "@/lib/utils";
import type { Movimentacao, Produto } from "@/lib/tipos";

export type Nivel = "ok" | "atencao" | "critico";

/**
 * Nivel do estoque: critico no minimo ou abaixo (RN005), atencao ate 50% acima
 * do minimo, ok dali para cima.
 */
export function nivelDoEstoque(produto: Pick<Produto, "saldo" | "estoqueMinimo" | "abaixoDoMinimo">): Nivel {
  if (produto.abaixoDoMinimo) return "critico";
  const minimo = Number(produto.estoqueMinimo);
  return minimo > 0 && Number(produto.saldo) <= minimo * 1.5 ? "atencao" : "ok";
}

const NIVEIS: Record<Nivel, { rotulo: string; classe: string; icone: string }> = {
  ok: { rotulo: "Em dia", classe: "border-ok/40 bg-ok/12 text-ok-texto", icone: "bi-check2" },
  atencao: { rotulo: "Perto do mínimo", classe: "border-atencao/45 bg-atencao/12 text-atencao-texto", icone: "bi-exclamation" },
  // Carmesim so em preenchimento, com texto osso por cima (5,7:1).
  critico: { rotulo: "Repor", classe: "border-transparent bg-sinal text-osso", icone: "bi-exclamation-triangle-fill" },
};

export function NivelDeEstoque({ nivel, className }: { nivel: Nivel; className?: string }) {
  const { rotulo, classe, icone } = NIVEIS[nivel];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[0.6875rem] font-medium whitespace-nowrap",
        classe,
        className,
      )}
    >
      <i aria-hidden="true" className={cn("bi text-[0.7rem]", icone)} />
      {rotulo}
    </span>
  );
}

const TIPOS: Record<Movimentacao["tipo"], { icone: string; classe: string }> = {
  ENTRADA: { icone: "bi-box-arrow-in-down", classe: "text-ok-texto" },
  SAIDA: { icone: "bi-box-arrow-up", classe: "text-foreground" },
  AJUSTE: { icone: "bi-sliders", classe: "text-atencao-texto" },
  ESTORNO: { icone: "bi-arrow-counterclockwise", classe: "text-muted-foreground" },
};

/** Tipo de movimentacao com icone; o texto vem da API (tipoDescricao). */
export function TipoDeMovimentacao({ movimentacao }: { movimentacao: Pick<Movimentacao, "tipo" | "tipoDescricao"> }) {
  const { icone, classe } = TIPOS[movimentacao.tipo];
  return (
    <span className={cn("inline-flex items-center gap-1.5 whitespace-nowrap", classe)}>
      <i aria-hidden="true" className={cn("bi", icone)} />
      {movimentacao.tipoDescricao}
    </span>
  );
}

/** Valor em reais no estilo de KPI: simbolo e centavos menores que o inteiro. */
export function NumeroEmReais({ valor, className }: { valor: number; className?: string }) {
  const partes = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).formatToParts(valor);
  const simbolo = partes.filter((parte) => parte.type === "currency").map((parte) => parte.value).join("");
  const inteiro = partes
    .filter((parte) => ["minusSign", "integer", "group"].includes(parte.type))
    .map((parte) => parte.value)
    .join("");
  const centavos = partes
    .filter((parte) => ["decimal", "fraction"].includes(parte.type))
    .map((parte) => parte.value)
    .join("");

  return (
    <span className={cn("inline-flex items-baseline gap-2 tabular-nums", className)}>
      <span className="text-[0.38em] text-muted-foreground">{simbolo}</span>
      <span>
        {inteiro}
        <span className="text-[0.5em] text-muted-foreground">{centavos}</span>
      </span>
    </span>
  );
}
