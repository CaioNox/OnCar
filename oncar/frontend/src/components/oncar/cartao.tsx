/** Indicador numerico da tela inicial. */
export function Cartao({
  titulo,
  valor,
  destaque = false,
}: {
  titulo: string;
  valor: string | number;
  destaque?: boolean;
}) {
  return (
    <div
      className={`rounded-lg border bg-background p-4 ${
        destaque ? "border-amber-500/50 bg-amber-500/5" : ""
      }`}
    >
      <p className="text-xs text-muted-foreground">{titulo}</p>
      <p className="mt-1 text-2xl font-semibold tracking-tight">{valor}</p>
    </div>
  );
}
