"use client";

import { motion, useMotionValue, useReducedMotion, useSpring, useTransform } from "motion/react";
import Link from "next/link";
import { NivelDeEstoque, nivelDoEstoque } from "@/components/oncar/etiquetas";
import { formatarMoeda, formatarQuantidade } from "@/lib/api";
import type { Produto } from "@/lib/tipos";
import { cn } from "@/lib/utils";

const INCLINACAO_MAXIMA = 4;

const COR_DA_REGUA = {
  ok: "bg-ok",
  atencao: "bg-atencao",
  critico: "bg-sinal",
} as const;

/**
 * Cartao de peca do estoque: nivel em musgo, ambar ou carmesim, regua do saldo
 * contra o minimo e inclinacao 3D leve (ate 4 graus) seguindo o cursor.
 */
export function CartaoPeca({
  produto,
  exibeCusto,
  ordem,
}: {
  produto: Produto;
  exibeCusto: boolean;
  /** Posicao na grade, para a entrada em cascata (limitada aos primeiros itens). */
  ordem: number;
}) {
  const reduzido = useReducedMotion();
  const x = useMotionValue(0);
  const y = useMotionValue(0);
  const mola = { stiffness: 220, damping: 22 };
  const rotateX = useSpring(useTransform(y, [-0.5, 0.5], [INCLINACAO_MAXIMA, -INCLINACAO_MAXIMA]), mola);
  const rotateY = useSpring(useTransform(x, [-0.5, 0.5], [-INCLINACAO_MAXIMA, INCLINACAO_MAXIMA]), mola);

  const nivel = nivelDoEstoque(produto);
  const saldo = Number(produto.saldo);
  const minimo = Number(produto.estoqueMinimo);
  // Regua: o minimo fica no meio; cheia significa o dobro do minimo ou mais.
  const preenchimento = minimo > 0 ? Math.min(saldo / (minimo * 2), 1) : saldo > 0 ? 1 : 0;

  return (
    // A entrada (CSS) e a inclinacao (Motion) mexem em transform; cada uma no seu elemento.
    <div
      className="h-full animate-in duration-500 ease-oficina fill-mode-both fade-in slide-in-from-bottom-2"
      style={{ animationDelay: `${Math.min(ordem, 12) * 30}ms` }}
    >
      <motion.div
        className="h-full"
        style={reduzido ? undefined : { rotateX, rotateY, transformPerspective: 900 }}
        onMouseMove={(evento) => {
          if (reduzido) return;
          const caixa = evento.currentTarget.getBoundingClientRect();
          x.set((evento.clientX - caixa.left) / caixa.width - 0.5);
          y.set((evento.clientY - caixa.top) / caixa.height - 0.5);
        }}
        onMouseLeave={() => {
          x.set(0);
          y.set(0);
        }}
      >
        <Link
          href={`/produtos/${produto.id}`}
          className={cn(
            "group relative flex h-full flex-col rounded-2xl border bg-card/85 p-5 shadow-[0_4px_24px_rgb(0_0_0/0.16)] backdrop-blur-sm transition-[border-color,box-shadow] duration-300 hover:border-foreground/20 hover:shadow-[0_14px_36px_rgb(0_0_0/0.26)]",
            !produto.ativo && "opacity-70",
          )}
        >
          <div className="flex items-start justify-between gap-3">
            <span className="num text-xs text-muted-foreground">{produto.sku}</span>
            {produto.ativo ? (
              <NivelDeEstoque nivel={nivel} />
            ) : (
              <span className="rounded-full border px-2 py-0.5 text-[0.6875rem] text-muted-foreground">
                Inativo
              </span>
            )}
          </div>

          <h3 className="mt-3 text-[0.9375rem] leading-snug font-medium text-foreground">{produto.descricao}</h3>
          <p className="mt-1 text-xs text-muted-foreground">
            {[produto.categoria, produto.marca].filter(Boolean).join(" · ")}
          </p>

          <div className="mt-auto pt-6">
            <p className="flex items-baseline gap-1.5">
              <span className="fonte-display text-4xl">{formatarQuantidade(produto.saldo)}</span>
              <span className="num text-xs text-muted-foreground">{produto.unidade}</span>
            </p>
            <div
              role="img"
              aria-label={`Saldo ${formatarQuantidade(produto.saldo)} de mínimo ${formatarQuantidade(produto.estoqueMinimo)}`}
              className="relative mt-3 h-1.5 overflow-hidden rounded-full bg-foreground/10"
            >
              <span
                className={cn("absolute inset-y-0 left-0 rounded-full", COR_DA_REGUA[nivel])}
                style={{ width: `${preenchimento * 100}%` }}
              />
              {minimo > 0 && <span className="absolute inset-y-0 left-1/2 w-px bg-foreground/40" />}
            </div>
            <div className="num mt-2 flex justify-between text-[0.6875rem] text-muted-foreground">
              <span>mín {formatarQuantidade(produto.estoqueMinimo)}</span>
              <span>{formatarMoeda(produto.precoVenda)}</span>
            </div>
            {exibeCusto && produto.precoCusto !== null && (
              <p className="num mt-1 text-right text-[0.6875rem] text-muted-foreground">
                custo {formatarMoeda(produto.precoCusto)}
              </p>
            )}
          </div>
        </Link>
      </motion.div>
    </div>
  );
}
