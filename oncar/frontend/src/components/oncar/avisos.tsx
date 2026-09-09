"use client";

/** Mensagens de erro e de sucesso em portugues, no padrao da RNF13. */
export function Erro({ mensagem, detalhes }: { mensagem: string | null; detalhes?: string[] }) {
  if (!mensagem) return null;
  return (
    <div className="mb-4 rounded-md border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
      <p>{mensagem}</p>
      {detalhes && detalhes.length > 0 && (
        <ul className="mt-1 list-inside list-disc">
          {detalhes.map((detalhe) => (
            <li key={detalhe}>{detalhe}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function Sucesso({ mensagem }: { mensagem: string | null }) {
  if (!mensagem) return null;
  return (
    <div className="mb-4 rounded-md border border-emerald-600/30 bg-emerald-600/10 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-400">
      {mensagem}
    </div>
  );
}

export function Vazio({ children }: { children: React.ReactNode }) {
  return (
    <p className="px-4 py-6 text-center text-sm text-muted-foreground">{children}</p>
  );
}
