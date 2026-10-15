"use client";

import { useEffect, useRef } from "react";
import { cn } from "@/lib/utils";

/**
 * Dialogo modal nativo: o navegador prende o foco, fecha com Esc e devolve o
 * foco a quem abriu. O conteudo so monta aberto, para comecar sempre do zero.
 */
export function Dialogo({
  aberto,
  aoFechar,
  rotulo,
  className,
  children,
}: {
  aberto: boolean;
  aoFechar: () => void;
  rotulo: string;
  className?: string;
  children: React.ReactNode;
}) {
  const referencia = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialogo = referencia.current;
    if (!dialogo) return;
    if (aberto && !dialogo.open) dialogo.showModal();
    if (!aberto && dialogo.open) dialogo.close();
  }, [aberto]);

  return (
    <dialog
      ref={referencia}
      aria-label={rotulo}
      onClose={aoFechar}
      // Clique fora do painel (no fundo escurecido) fecha.
      onClick={(evento) => {
        if (evento.target === evento.currentTarget) aoFechar();
      }}
      className={cn(
        "mx-auto overflow-hidden rounded-2xl border bg-popover p-0 text-popover-foreground shadow-[0_40px_120px_-40px_rgb(0_0_0/0.8)] backdrop:bg-marinho-profundo/70 open:animate-in open:fade-in-0 open:duration-200",
        className,
      )}
    >
      {aberto && children}
    </dialog>
  );
}
