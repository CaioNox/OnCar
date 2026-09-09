"use client";

import { useRef } from "react";
import { motion, useScroll, useSpring, useTransform } from "motion/react";
import ScrollText from "@/components/kokonutui/scroll-text";

export default function DemoPage() {
  // Progresso de scroll da página inteira (0 → 1)
  const { scrollYProgress } = useScroll();
  const barraProgresso = useSpring(scrollYProgress, {
    stiffness: 120,
    damping: 30,
    restDelta: 0.001,
  });

  // Progresso relativo a um elemento: começa quando o topo dele
  // encosta no fim da viewport e termina quando sai por cima.
  const alvo = useRef<HTMLDivElement>(null);
  const { scrollYProgress: progressoAlvo } = useScroll({
    target: alvo,
    offset: ["start end", "end start"],
  });
  const escala = useTransform(progressoAlvo, [0, 0.5, 1], [0.7, 1, 0.7]);
  const rotacao = useTransform(progressoAlvo, [0, 1], [-8, 8]);
  const opacidade = useTransform(progressoAlvo, [0, 0.3, 0.7, 1], [0, 1, 1, 0]);

  return (
    <main className="min-h-screen bg-neutral-950 text-neutral-100">
      {/* Barra de progresso fixa no topo */}
      <motion.div
        style={{ scaleX: barraProgresso }}
        className="fixed inset-x-0 top-0 z-50 h-1 origin-left bg-emerald-400"
      />

      <section className="flex h-screen flex-col items-center justify-center gap-4">
        <h1 className="text-4xl font-semibold tracking-tight">
          Motion + KokonutUI
        </h1>
        <p className="text-neutral-400">role a página ↓</p>
      </section>

      {/* useScroll com target + useTransform */}
      <section className="flex h-screen items-center justify-center">
        <div ref={alvo}>
          <motion.div
            style={{ scale: escala, rotate: rotacao, opacity: opacidade }}
            className="grid h-64 w-64 place-items-center rounded-3xl bg-gradient-to-br from-emerald-400 to-cyan-500 text-lg font-medium text-neutral-950"
          >
            useScroll + useTransform
          </motion.div>
        </div>
      </section>

      {/* whileInView: dispara uma vez ao entrar na viewport */}
      <section className="mx-auto flex min-h-screen max-w-2xl flex-col justify-center gap-4 px-6">
        {["Entra de baixo", "Um de cada vez", "Sem repetir"].map((texto, i) => (
          <motion.div
            key={texto}
            initial={{ opacity: 0, y: 40 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.6 }}
            transition={{ duration: 0.5, delay: i * 0.12 }}
            className="rounded-xl border border-neutral-800 bg-neutral-900 p-6"
          >
            {texto}
          </motion.div>
        ))}
      </section>

      {/* Componente do KokonutUI (já é scroll-driven) */}
      <section className="flex min-h-screen items-center justify-center px-6">
        <ScrollText texts={["Componente", "do KokonutUI", "via shadcn CLI"]} />
      </section>

      <section className="h-screen" />
    </main>
  );
}
