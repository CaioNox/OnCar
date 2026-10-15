"use client";

/**
 * @author: @dorianbaffier
 * @description: Bento Grid
 * @version: 1.0.0
 * @date: 2025-06-26
 * @license: MIT
 * @website: https://kokonutui.com
 * @github: https://github.com/kokonut-labs/kokonutui
 *
 * Ajustes do OnCar: o grid e o cartao (vidro, inclinacao 3D e subida no hover)
 * viraram pecas reutilizaveis que recebem o conteudo por children. As
 * demonstracoes que vinham junto (codigo digitando, timeline, voz, logos de IA)
 * sairam, e o cartao fica parado para quem prefere menos movimento.
 */

import {
  motion,
  useMotionValue,
  useReducedMotion,
  useTransform,
  type Variants,
} from "motion/react";
import { cn } from "@/lib/utils";

const fadeInUp: Variants = {
  hidden: { opacity: 0, y: 20 },
  visible: {
    opacity: 1,
    y: 0,
    transition: {
      duration: 0.5,
      ease: "easeOut",
    },
  },
};

const staggerContainer: Variants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.08,
      delayChildren: 0.1,
    },
  },
};

/** Superficie de vidro do cartao bento, para quem nao usa o LiquidGlassCard. */
export const superficieBento =
  "relative flex h-full flex-col overflow-hidden rounded-2xl border bg-gradient-to-b from-card/75 via-card/55 to-card/40 p-5 shadow-[0_4px_24px_rgb(0_0_0/0.18)] backdrop-blur-md transition-[border-color,box-shadow] duration-500 ease-out before:pointer-events-none before:absolute before:inset-0 before:rounded-2xl before:bg-gradient-to-b before:from-foreground/[0.04] before:to-transparent group-hover/bento:border-foreground/15 group-hover/bento:shadow-[0_12px_36px_rgb(0_0_0/0.28)] sm:p-6";

export function BentoGrid({
  className,
  children,
}: {
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <motion.div
      animate="visible"
      className={cn("grid gap-4 lg:gap-5", className)}
      initial="hidden"
      variants={staggerContainer}
    >
      {children}
    </motion.div>
  );
}

/**
 * Cartao do grid: entra em cascata, sobe de leve no hover e inclina seguindo o
 * cursor. `inclinacao` e o angulo maximo em graus.
 */
export function BentoCard({
  className,
  inclinacao = 2,
  children,
}: {
  className?: string;
  inclinacao?: number;
  children: React.ReactNode;
}) {
  const reduzido = useReducedMotion();
  const x = useMotionValue(0);
  const y = useMotionValue(0);
  const rotateX = useTransform(y, [-100, 100], [inclinacao, -inclinacao]);
  const rotateY = useTransform(x, [-100, 100], [-inclinacao, inclinacao]);

  function handleMouseMove(event: React.MouseEvent<HTMLDivElement>) {
    if (reduzido) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const xPct = (event.clientX - rect.left) / rect.width - 0.5;
    const yPct = (event.clientY - rect.top) / rect.height - 0.5;
    x.set(xPct * 100);
    y.set(yPct * 100);
  }

  function handleMouseLeave() {
    x.set(0);
    y.set(0);
  }

  return (
    <motion.div
      className={cn("group/bento h-full", className)}
      onMouseLeave={handleMouseLeave}
      onMouseMove={handleMouseMove}
      style={
        reduzido
          ? undefined
          : { rotateX, rotateY, transformPerspective: 1100, transformStyle: "preserve-3d" }
      }
      transition={{ type: "spring", stiffness: 300, damping: 24 }}
      variants={fadeInUp}
      whileHover={reduzido ? undefined : { y: -3 }}
    >
      {children}
    </motion.div>
  );
}

export default BentoGrid;
