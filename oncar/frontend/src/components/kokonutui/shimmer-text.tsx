"use client";

/**
 * @author: @dorianbaffier
 * @description: Shimmer Text
 * @version: 1.0.0
 * @date: 2025-06-26
 * @license: MIT
 * @website: https://kokonutui.com
 * @github: https://github.com/kokonut-labs/kokonutui
 *
 * Ajustes do OnCar: texto em linha (span, sem titulo nem espacamento proprio),
 * cores do tema e brilho parado quando o usuario prefere menos movimento.
 */

import { motion, useReducedMotion } from "motion/react";
import { cn } from "@/lib/utils";

interface Text_01Props {
  text: string;
  className?: string;
}

export default function ShimmerText({
  text = "Text Shimmer",
  className,
}: Text_01Props) {
  const reduzido = useReducedMotion();

  if (reduzido) {
    return <span className={cn("text-muted-foreground", className)}>{text}</span>;
  }

  return (
    <motion.span
      animate={{
        backgroundPosition: ["200% center", "-200% center"],
      }}
      className={cn(
        "inline-block bg-[length:200%_100%] bg-gradient-to-r from-muted-foreground via-foreground to-muted-foreground bg-clip-text text-transparent",
        className
      )}
      transition={{
        duration: 2.2,
        ease: "linear",
        repeat: Number.POSITIVE_INFINITY,
      }}
    >
      {text}
    </motion.span>
  );
}
