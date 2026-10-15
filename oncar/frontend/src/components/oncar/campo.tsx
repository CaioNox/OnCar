import { cn } from "@/lib/utils";

/** Estilo comum de input, select e textarea; o foco usa o contorno musgo global. */
export const CAMPO =
  "h-11 w-full rounded-lg border border-input bg-background/55 px-3.5 text-[0.9375rem] text-foreground transition-colors hover:border-foreground/25 disabled:cursor-not-allowed disabled:opacity-60 aria-invalid:border-sinal";

export const AREA_DE_TEXTO = cn(CAMPO, "h-auto min-h-24 resize-y py-2.5 leading-relaxed");

export function Rotulo({
  htmlFor,
  children,
  dica,
}: {
  htmlFor: string;
  children: React.ReactNode;
  dica?: React.ReactNode;
}) {
  return (
    <label
      htmlFor={htmlFor}
      className="mb-1.5 flex items-baseline justify-between gap-3 text-sm font-medium"
    >
      {children}
      {dica && <span className="text-xs font-normal text-muted-foreground">{dica}</span>}
    </label>
  );
}
