"use client";

import { AnimatePresence, motion } from "motion/react";
import { createContext, useCallback, useContext, useRef, useState } from "react";
import { cn } from "@/lib/utils";

type Tom = "ok" | "erro" | "info";
type Toast = { id: number; mensagem: string; tom: Tom };
type Avisar = (mensagem: string, tom?: Tom) => void;

const ContextoDeToasts = createContext<Avisar>(() => undefined);

/** Dispara um toast discreto no canto inferior. */
export function useToast(): Avisar {
  return useContext(ContextoDeToasts);
}

const DURACAO_MS = 4500;
const MAXIMO_NA_TELA = 3;

const ESTILO: Record<Tom, { icone: string; faixa: string; cor: string }> = {
  ok: { icone: "bi-check2-circle", faixa: "bg-ok", cor: "text-ok-texto" },
  erro: { icone: "bi-exclamation-octagon-fill", faixa: "bg-sinal", cor: "text-sinal-texto" },
  info: { icone: "bi-info-circle", faixa: "bg-muted-foreground", cor: "text-muted-foreground" },
};

export function ProvedorDeToasts({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const proximoId = useRef(0);

  const remover = useCallback((id: number) => {
    setToasts((atuais) => atuais.filter((toast) => toast.id !== id));
  }, []);

  const avisar = useCallback<Avisar>(
    (mensagem, tom = "ok") => {
      proximoId.current += 1;
      const id = proximoId.current;
      setToasts((atuais) => [...atuais.slice(-(MAXIMO_NA_TELA - 1)), { id, mensagem, tom }]);
      window.setTimeout(() => remover(id), DURACAO_MS);
    },
    [remover],
  );

  return (
    <ContextoDeToasts.Provider value={avisar}>
      {children}
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-none fixed inset-x-4 bottom-4 z-[70] flex flex-col items-center gap-2 sm:inset-x-auto sm:right-6 sm:bottom-6 sm:items-end"
      >
        <AnimatePresence initial={false}>
          {toasts.map((toast) => {
            const estilo = ESTILO[toast.tom];
            return (
              <motion.div
                key={toast.id}
                layout
                initial={{ opacity: 0, y: 14, scale: 0.98 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 8, transition: { duration: 0.18 } }}
                transition={{ type: "spring", stiffness: 420, damping: 34 }}
                className="vidro pointer-events-auto relative flex w-full max-w-sm items-start gap-3 overflow-hidden rounded-xl border py-3 pr-2 pl-4 text-sm shadow-[0_12px_32px_-12px_rgb(0_0_0/0.5)]"
              >
                <span aria-hidden="true" className={cn("absolute inset-y-0 left-0 w-[3px]", estilo.faixa)} />
                <i aria-hidden="true" className={cn("bi mt-px text-base", estilo.icone, estilo.cor)} />
                <p className="flex-1 text-foreground">{toast.mensagem}</p>
                <button
                  type="button"
                  onClick={() => remover(toast.id)}
                  aria-label="Fechar aviso"
                  className="grid size-6 shrink-0 place-items-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"
                >
                  <i aria-hidden="true" className="bi bi-x-lg text-[0.7rem]" />
                </button>
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    </ContextoDeToasts.Provider>
  );
}
