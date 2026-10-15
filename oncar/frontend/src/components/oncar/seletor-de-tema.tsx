"use client";

import { useSyncExternalStore } from "react";
import { cn } from "@/lib/utils";
import { CHAVE_DO_TEMA, type Tema } from "@/lib/tema";

function assinar(avisar: () => void) {
  const observador = new MutationObserver(avisar);
  observador.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
  return () => observador.disconnect();
}

function temaAtual(): Tema {
  return document.documentElement.classList.contains("dark") ? "noturna" : "papel";
}

/** Tema aplicado no <html>; o servidor sempre renderiza o Noturna. */
export function useTema(): Tema {
  return useSyncExternalStore(assinar, temaAtual, () => "noturna");
}

export function alternarTema() {
  const trocar = () => {
    const papel = !document.documentElement.classList.toggle("dark");
    try {
      localStorage.setItem(CHAVE_DO_TEMA, papel ? "papel" : "noturna");
    } catch {
      // Sem localStorage (modo privado): o tema vale so para esta aba.
    }
  };

  const semMovimento = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (!semMovimento && !document.hidden && "startViewTransition" in document) {
    // Se a transicao for abortada o tema troca do mesmo jeito; so descartamos a rejeicao.
    document.startViewTransition(trocar).ready.catch(() => undefined);
  } else {
    trocar();
  }
}

export function SeletorDeTema({ className }: { className?: string }) {
  const tema = useTema();
  const proximo = tema === "noturna" ? "Papel (claro)" : "Oficina Noturna (escuro)";

  return (
    <button
      type="button"
      onClick={alternarTema}
      aria-label={`Mudar para o tema ${proximo}`}
      title={`Tema: ${tema === "noturna" ? "Oficina Noturna" : "Papel"}`}
      className={cn(
        "grid size-9 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-accent hover:text-foreground",
        className,
      )}
    >
      <i aria-hidden="true" className={`bi ${tema === "noturna" ? "bi-moon-stars" : "bi-sun"} text-[1.05rem]`} />
    </button>
  );
}
