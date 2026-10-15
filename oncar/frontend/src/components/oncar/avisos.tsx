"use client";

/** Mensagem de erro em portugues; sucesso vai por toast. */
export function Erro({ mensagem, detalhes }: { mensagem: string | null; detalhes?: string[] }) {
  if (!mensagem) return null;
  return (
    <div
      role="alert"
      className="mb-5 flex gap-3 rounded-xl border border-sinal/45 bg-sinal/12 px-4 py-3 text-sm"
    >
      <i aria-hidden="true" className="bi bi-exclamation-octagon-fill mt-px text-sinal-texto" />
      <div>
        <p className="font-medium text-foreground">{mensagem}</p>
        {detalhes && detalhes.length > 0 && (
          <ul className="mt-1 list-inside list-disc text-muted-foreground">
            {detalhes.map((detalhe) => (
              <li key={detalhe}>{detalhe}</li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
