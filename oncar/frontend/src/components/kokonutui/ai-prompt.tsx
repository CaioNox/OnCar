"use client";

/**
 * @author: @kokonutui
 * @description: AI Prompt Input
 * @version: 1.0.0
 * @date: 2025-06-26
 * @license: MIT
 * @website: https://kokonutui.com
 * @github: https://github.com/kokonut-labs/kokonutui
 *
 * Ajustes do OnCar: sem seletor de modelos nem anexo (o back-end ainda nao tem
 * IA), botao de enviar funcional, rotulo acessivel, id estavel e cores do tema.
 */

import { ArrowRight } from "lucide-react";
import { useId, useState } from "react";
import { Textarea } from "@/components/ui/textarea";
import { useAutoResizeTextarea } from "@/hooks/use-auto-resize-textarea";
import { cn } from "@/lib/utils";

interface AIPromptProps {
  placeholder?: string;
  headerText?: string;
  headerAction?: React.ReactNode;
  /** Rotulo lido pelo leitor de tela para o campo de texto. */
  label?: string;
  disabled?: boolean;
  onSubmit?: (value: string) => void;
  className?: string;
}

export default function AI_Prompt({
  placeholder = "What can I do for you?",
  headerText = "Assistente",
  headerAction,
  label = "Mensagem",
  disabled = false,
  onSubmit,
  className,
}: AIPromptProps) {
  const [value, setValue] = useState("");
  const { textareaRef, adjustHeight } = useAutoResizeTextarea({
    minHeight: 64,
    maxHeight: 200,
  });
  const id = useId();

  const enviar = () => {
    const texto = value.trim();
    if (!texto || disabled) return;
    onSubmit?.(texto);
    setValue("");
    adjustHeight(true);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      enviar();
    }
  };

  return (
    <div className={cn("w-full", className)}>
      <div className="rounded-2xl bg-foreground/5 p-1.5 pt-3 ring-1 ring-border has-[textarea:focus-visible]:ring-2 has-[textarea:focus-visible]:ring-ring">
        <div className="mx-2 mb-2 flex items-center gap-2">
          <div className="flex flex-1 items-center gap-2">
            <i aria-hidden="true" className="bi bi-stars text-xs text-ok-texto" />
            <h3 className="text-xs tracking-tight text-foreground/90">{headerText}</h3>
          </div>
          {headerAction && (
            <div className="text-xs tracking-tight text-muted-foreground">{headerAction}</div>
          )}
        </div>
        <div className="relative flex flex-col">
          <label className="sr-only" htmlFor={id}>
            {label}
          </label>
          <div className="overflow-y-auto" style={{ maxHeight: "240px" }}>
            <Textarea
              className="min-h-[64px] w-full resize-none rounded-xl rounded-b-none border-none bg-foreground/5 px-4 py-3 text-[0.9375rem] placeholder:text-muted-foreground focus-visible:outline-none dark:bg-foreground/5"
              disabled={disabled}
              id={id}
              onChange={(e) => {
                setValue(e.target.value);
                adjustHeight();
              }}
              onKeyDown={handleKeyDown}
              placeholder={placeholder}
              ref={textareaRef}
              value={value}
            />
          </div>

          <div className="flex h-12 items-center justify-between gap-3 rounded-b-xl bg-foreground/5 px-3">
            <span className="num text-[0.6875rem] text-muted-foreground">
              Enter envia · Shift+Enter quebra linha
            </span>
            <button
              aria-label="Enviar pergunta"
              className="rounded-lg bg-foreground/5 p-2 transition-colors hover:bg-foreground/10 disabled:cursor-not-allowed"
              disabled={!value.trim() || disabled}
              onClick={enviar}
              type="button"
            >
              <ArrowRight
                className={cn(
                  "h-4 w-4 transition-opacity duration-200",
                  value.trim() && !disabled ? "opacity-100" : "opacity-30"
                )}
              />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
