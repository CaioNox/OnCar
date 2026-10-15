import { cn } from "@/lib/utils";

/** Filtro em forma de chip: botao de alternancia com aria-pressed. */
export function Chip({
  ativo,
  aoAlternar,
  children,
  className,
}: {
  ativo: boolean;
  aoAlternar: () => void;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <button
      type="button"
      aria-pressed={ativo}
      onClick={aoAlternar}
      className={cn(
        "inline-flex h-8 shrink-0 items-center gap-1.5 rounded-full border px-3.5 text-xs whitespace-nowrap transition-colors",
        ativo
          ? "border-ok bg-ok/15 font-medium text-foreground"
          : "bg-background/40 text-muted-foreground hover:border-foreground/25 hover:text-foreground",
        className,
      )}
    >
      {children}
    </button>
  );
}
