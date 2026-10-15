"use client";

/**
 * @author: @kokonutui
 * @description: A modern search bar component with action buttons and suggestions
 * @version: 1.0.0
 * @date: 2025-06-26
 * @license: MIT
 * @website: https://kokonutui.com
 * @github: https://github.com/kokonut-labs/kokonutui
 *
 * Ajustes do OnCar: acoes, busca e selecao controladas por props (vira a paleta
 * de comandos do Ctrl+K), grupos, acoes desativadas, padrao ARIA de combobox e
 * textos em portugues. A lista fica sempre aberta porque vive dentro de um dialogo.
 */

import { Search, Send } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useId, useState } from "react";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

export interface Action {
  id: string;
  label: string;
  icon: React.ReactNode;
  description?: string;
  short?: string;
  end?: string;
  /** Titulo do grupo em que a acao aparece (Pecas, Clientes...). */
  group?: string;
  disabled?: boolean;
}

const ANIMATION_VARIANTS = {
  container: {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: {
        staggerChildren: 0.025,
      },
    },
  },
  item: {
    hidden: { opacity: 0, y: 6 },
    show: {
      opacity: 1,
      y: 0,
      transition: { duration: 0.18 },
    },
  },
} as const;

function ActionSearchBar({
  actions,
  query,
  onQueryChange,
  onSelect,
  label = "Buscar",
  placeholder = "O que você procura?",
  autoFocus = false,
  emptyMessage = "Nada encontrado.",
  footer,
}: {
  actions: Action[];
  query: string;
  onQueryChange: (query: string) => void;
  onSelect: (action: Action) => void;
  label?: string;
  placeholder?: string;
  autoFocus?: boolean;
  emptyMessage?: React.ReactNode;
  footer?: React.ReactNode;
}) {
  const [activeIndex, setActiveIndex] = useState(0);
  const idBase = useId();
  const listId = `${idBase}-lista`;
  const optionId = (action: Action) => `${idBase}-${action.id}`;

  // Sempre dentro da lista atual: quando o filtro encolhe, o destaque acompanha.
  const indiceAtivo = Math.min(activeIndex, Math.max(actions.length - 1, 0));
  const acaoAtiva = actions[indiceAtivo];

  const moverPara = (indice: number) => {
    setActiveIndex(indice);
    const alvo = actions[indice];
    if (alvo) {
      requestAnimationFrame(() =>
        document.getElementById(optionId(alvo))?.scrollIntoView({ block: "nearest" })
      );
    }
  };

  const proximoHabilitado = (inicio: number, passo: 1 | -1) => {
    for (let i = 1; i <= actions.length; i++) {
      const indice = (inicio + passo * i + actions.length) % actions.length;
      if (!actions[indice].disabled) return indice;
    }
    return inicio;
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (!actions.length) return;

    switch (e.key) {
      case "ArrowDown":
        e.preventDefault();
        moverPara(proximoHabilitado(indiceAtivo, 1));
        break;
      case "ArrowUp":
        e.preventDefault();
        moverPara(proximoHabilitado(indiceAtivo, -1));
        break;
      case "Enter":
        e.preventDefault();
        if (acaoAtiva && !acaoAtiva.disabled) onSelect(acaoAtiva);
        break;
    }
  };

  // Agrupa preservando a ordem de chegada.
  const grupos: { titulo: string; itens: { action: Action; indice: number }[] }[] = [];
  actions.forEach((action, indice) => {
    const titulo = action.group ?? "";
    const grupo = grupos.find((existente) => existente.titulo === titulo);
    if (grupo) grupo.itens.push({ action, indice });
    else grupos.push({ titulo, itens: [{ action, indice }] });
  });

  return (
    <div className="flex w-full flex-col">
      <label className="sr-only" htmlFor={`${idBase}-busca`}>
        {label}
      </label>
      <div className="relative">
        <Input
          aria-activedescendant={acaoAtiva ? optionId(acaoAtiva) : undefined}
          aria-autocomplete="list"
          aria-controls={listId}
          aria-expanded={true}
          autoComplete="off"
          autoFocus={autoFocus}
          className="h-14 rounded-none border-0 border-b bg-transparent py-1.5 pr-12 pl-5 text-base dark:bg-transparent focus-visible:border-ring focus-visible:outline-none"
          id={`${idBase}-busca`}
          onChange={(e) => {
            onQueryChange(e.target.value);
            setActiveIndex(0);
          }}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          role="combobox"
          spellCheck={false}
          type="text"
          value={query}
        />
        <div className="absolute top-1/2 right-5 h-4 w-4 -translate-y-1/2">
          <AnimatePresence mode="popLayout">
            {query.length > 0 ? (
              <motion.div
                animate={{ y: 0, opacity: 1 }}
                exit={{ y: 20, opacity: 0 }}
                initial={{ y: -20, opacity: 0 }}
                key="send"
                transition={{ duration: 0.2 }}
              >
                <Send className="h-4 w-4 text-muted-foreground" />
              </motion.div>
            ) : (
              <motion.div
                animate={{ y: 0, opacity: 1 }}
                exit={{ y: 20, opacity: 0 }}
                initial={{ y: -20, opacity: 0 }}
                key="search"
                transition={{ duration: 0.2 }}
              >
                <Search className="h-4 w-4 text-muted-foreground" />
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>

      <motion.div
        animate="show"
        aria-label="Resultados"
        className="max-h-[min(60vh,28rem)] overflow-y-auto overscroll-contain p-2"
        id={listId}
        initial="hidden"
        role="listbox"
        variants={ANIMATION_VARIANTS.container}
      >
        {actions.length === 0 && (
          <div className="px-3 py-10 text-center text-sm text-muted-foreground">{emptyMessage}</div>
        )}
        {grupos.map((grupo) => (
          <div aria-label={grupo.titulo || undefined} key={grupo.titulo} role="group">
            {grupo.titulo && (
              <div aria-hidden="true" className="rotulo px-3 pt-3 pb-1.5">
                {grupo.titulo}
              </div>
            )}
            {grupo.itens.map(({ action, indice }) => {
              const ativa = indice === indiceAtivo;
              return (
                <motion.div
                  aria-disabled={action.disabled || undefined}
                  aria-selected={ativa}
                  className={cn(
                    "flex cursor-pointer items-center justify-between gap-3 rounded-lg px-3 py-2.5",
                    ativa && !action.disabled && "bg-accent",
                    action.disabled && "cursor-not-allowed opacity-60"
                  )}
                  id={optionId(action)}
                  key={action.id}
                  onClick={() => !action.disabled && onSelect(action)}
                  onMouseDown={(e) => e.preventDefault()}
                  onMouseMove={() => indice !== indiceAtivo && setActiveIndex(indice)}
                  role="option"
                  variants={ANIMATION_VARIANTS.item}
                >
                  <div className="flex min-w-0 items-center gap-3">
                    <span aria-hidden="true" className="grid size-8 shrink-0 place-items-center rounded-md bg-foreground/5 text-muted-foreground">
                      {action.icon}
                    </span>
                    <div className="min-w-0">
                      <div className="truncate text-sm font-medium text-foreground">
                        {action.label}
                      </div>
                      {action.description && (
                        <div className="truncate text-xs text-muted-foreground">
                          {action.description}
                        </div>
                      )}
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {action.short && (
                      <kbd className="num rounded border px-1.5 py-0.5 text-[0.65rem] text-muted-foreground">
                        {action.short}
                      </kbd>
                    )}
                    {action.end && (
                      <span className="rotulo text-right">{action.end}</span>
                    )}
                  </div>
                </motion.div>
              );
            })}
          </div>
        ))}
      </motion.div>

      <div className="flex items-center justify-between gap-4 border-t px-5 py-2.5 text-xs text-muted-foreground">
        <span className="num">↑↓ navegar · Enter abrir · Esc fechar</span>
        {footer}
      </div>
    </div>
  );
}

export default ActionSearchBar;
