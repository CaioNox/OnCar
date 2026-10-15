"use client";

import { MotionConfig } from "motion/react";
import { ProvedorDeToasts } from "@/components/oncar/toasts";

/** Provedores globais: o Motion respeita o prefers-reduced-motion e os toasts ficam disponiveis. */
export function Provedores({ children }: { children: React.ReactNode }) {
  return (
    <MotionConfig reducedMotion="user">
      <ProvedorDeToasts>{children}</ProvedorDeToasts>
    </MotionConfig>
  );
}
