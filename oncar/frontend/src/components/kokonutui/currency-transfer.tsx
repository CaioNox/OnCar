"use client";

/**
 * @author: @dorianbaffier
 * @description: Currency Transfer
 * @version: 1.0.0
 * @date: 2025-06-26
 * @license: MIT
 * @website: https://kokonutui.com
 * @github: https://github.com/kokonut-labs/kokonutui
 *
 * Ajustes do OnCar: a coreografia do original (anel girando, check que entra
 * rodando, cartoes "de" e "para" que se fundem ao concluir) agora recebe o
 * conteudo por props e confirma entradas, saidas e ajustes de estoque. Cores
 * pelos tokens do tema; sem o tooltip de cambio.
 */

import { ArrowDownIcon, ArrowUpDown, ArrowUpIcon, Check } from "lucide-react";
import { AnimatePresence, motion, type Variants } from "motion/react";
import { cn } from "@/lib/utils";

interface CheckmarkProps {
  size?: number;
  strokeWidth?: number;
  color?: string;
  className?: string;
}

const draw: Variants = {
  hidden: { pathLength: 0, opacity: 0 },
  visible: (i: number) => ({
    pathLength: 1,
    opacity: 1,
    transition: {
      pathLength: {
        delay: i * 0.2,
        type: "spring",
        duration: 1.5,
        bounce: 0.2,
      },
      opacity: { delay: i * 0.2, duration: 0.3 },
    },
  }),
};

export function Checkmark({
  size = 100,
  strokeWidth = 2,
  color = "currentColor",
  className = "",
}: CheckmarkProps) {
  return (
    <motion.svg
      animate="visible"
      aria-hidden="true"
      className={className}
      height={size}
      initial="hidden"
      viewBox="0 0 100 100"
      width={size}
    >
      <motion.circle
        custom={0}
        cx="50"
        cy="50"
        r="42"
        stroke={color}
        style={{
          strokeWidth,
          strokeLinecap: "round",
          fill: "transparent",
        }}
        variants={draw}
      />
      <motion.path
        custom={1}
        d="M32 50L45 63L68 35"
        stroke={color}
        style={{
          strokeWidth: strokeWidth + 0.5,
          strokeLinecap: "round",
          strokeLinejoin: "round",
          fill: "transparent",
        }}
        variants={draw}
      />
    </motion.svg>
  );
}

export type LadoDaTransferencia = {
  /** "De" ou "Para" */
  rotulo: string;
  titulo: string;
  detalhe?: string;
  simbolo: React.ReactNode;
};

type Textos = { processando: string; concluido: string };

const EASE = [0.22, 1, 0.36, 1] as const;

export default function CurrencyTransfer({
  concluido,
  titulo,
  subtitulo,
  rodape,
  de,
  para,
  className,
}: {
  concluido: boolean;
  titulo: Textos;
  subtitulo: Textos;
  rodape: Textos;
  de: LadoDaTransferencia;
  para: LadoDaTransferencia;
  className?: string;
}) {
  return (
    <div
      aria-live="polite"
      className={cn("mx-auto flex w-full max-w-sm flex-col items-center py-2", className)}
    >
      <div className="flex h-[100px] items-center justify-center">
        <div className="relative flex h-[100px] w-[100px] items-center justify-center">
          <motion.div
            animate={{ opacity: [0, 1, 0.8] }}
            className="absolute inset-0 rounded-full bg-ok/15 blur-2xl"
            initial={{ opacity: 0 }}
            transition={{ duration: 1.5, times: [0, 0.5, 1], ease: EASE }}
          />
          <AnimatePresence mode="wait">
            {concluido ? (
              <motion.div
                animate={{ opacity: 1, rotate: 0 }}
                className="flex h-[100px] w-[100px] items-center justify-center"
                initial={{ opacity: 0, rotate: -180 }}
                key="completed"
                transition={{ duration: 0.6, ease: "easeInOut" }}
              >
                <div className="relative z-10 rounded-full border border-ok bg-card p-5">
                  <Check className="h-10 w-10 text-ok-texto" strokeWidth={3.5} />
                </div>
              </motion.div>
            ) : (
              <motion.div
                animate={{ opacity: 1 }}
                className="flex h-[100px] w-[100px] items-center justify-center"
                exit={{ opacity: 0, rotate: 360 }}
                initial={{ opacity: 0 }}
                key="progress"
                transition={{ duration: 0.6, ease: "easeInOut" }}
              >
                <div className="relative z-10">
                  <motion.div
                    animate={{ rotate: 360, scale: [1, 1.02, 1] }}
                    className="absolute inset-0 rounded-full border-2 border-transparent"
                    style={{
                      borderLeftColor: "var(--ok)",
                      borderTopColor: "color-mix(in oklab, var(--ok) 25%, transparent)",
                      filter: "blur(0.5px)",
                    }}
                    transition={{
                      rotate: { duration: 3, repeat: Number.POSITIVE_INFINITY, ease: "linear" },
                      scale: { duration: 2, repeat: Number.POSITIVE_INFINITY, ease: "easeInOut" },
                    }}
                  />
                  <div className="relative z-10 rounded-full bg-card p-5 shadow-[0_0_15px_rgb(107_125_78/0.2)]">
                    <ArrowUpDown className="h-10 w-10 text-ok-texto" />
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>

      <div className="mt-6 w-full space-y-2 text-center">
        <AnimatePresence mode="wait">
          <motion.h3
            animate={{ opacity: 1, y: 0 }}
            className="fonte-display text-2xl"
            exit={{ opacity: 0, y: -20 }}
            initial={{ opacity: 0, y: 20 }}
            key={concluido ? "titulo-ok" : "titulo-andamento"}
            transition={{ duration: 0.5, ease: EASE }}
          >
            {concluido ? titulo.concluido : titulo.processando}
          </motion.h3>
        </AnimatePresence>
        <AnimatePresence mode="wait">
          <motion.p
            animate={{ opacity: 1, y: 0 }}
            className="num text-xs text-ok-texto"
            exit={{ opacity: 0, y: -10 }}
            initial={{ opacity: 0, y: 10 }}
            key={concluido ? "sub-ok" : "sub-andamento"}
            transition={{ duration: 0.4, ease: EASE }}
          >
            {concluido ? subtitulo.concluido : subtitulo.processando}
          </motion.p>
        </AnimatePresence>
      </div>

      <motion.div
        animate={{ gap: concluido ? "0px" : "12px" }}
        className="mt-6 flex w-full flex-col items-start"
        initial={{ gap: "12px" }}
        transition={{ duration: 0.6, ease: [0.32, 0.72, 0, 1] }}
      >
        {[de, para].map((lado, indice) => (
          <div
            key={lado.rotulo}
            className={cn(
              "w-full rounded-xl border bg-background/50 p-3 transition-all duration-300",
              concluido
                ? indice === 0
                  ? "rounded-b-none border-b-0"
                  : "rounded-t-none border-t-0"
                : "hover:border-ok/40"
            )}
          >
            <span className="rotulo flex items-center gap-1.5">
              {indice === 0 ? <ArrowUpIcon className="h-3 w-3" /> : <ArrowDownIcon className="h-3 w-3" />}
              {lado.rotulo}
            </span>
            <div className="mt-1.5 flex items-center gap-2.5">
              <span className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border bg-card text-sm shadow-lg">
                {lado.simbolo}
              </span>
              <div className="min-w-0 text-left">
                <motion.p
                  animate={{ opacity: concluido ? 1 : 0.55 }}
                  className="truncate text-sm font-medium"
                  transition={{ duration: 0.3, ease: EASE }}
                >
                  {lado.titulo}
                </motion.p>
                {lado.detalhe && <p className="num truncate text-xs text-muted-foreground">{lado.detalhe}</p>}
              </div>
            </div>
          </div>
        ))}
      </motion.div>

      <AnimatePresence mode="wait">
        <motion.p
          animate={{ opacity: 1, y: 0 }}
          className="mt-4 text-xs text-muted-foreground"
          exit={{ opacity: 0, y: -10 }}
          initial={{ opacity: 0, y: 10 }}
          key={concluido ? "rodape-ok" : "rodape-andamento"}
          transition={{ duration: 0.4, ease: EASE }}
        >
          {concluido ? rodape.concluido : rodape.processando}
        </motion.p>
      </AnimatePresence>
    </div>
  );
}
