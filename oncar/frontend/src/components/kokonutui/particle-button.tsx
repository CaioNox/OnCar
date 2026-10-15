"use client";

/**
 * @author: @dorianbaffier
 * @description: Particle Button
 * @version: 1.0.0
 * @date: 2025-06-26
 * @license: MIT
 * @website: https://kokonutui.com
 * @github: https://github.com/kokonut-labs/kokonutui
 *
 * Ajustes do OnCar: e o botao das acoes criticas (baixa de estoque, estorno), por
 * isso nasce em carmesim. O onClick recebido agora e repassado (o original o
 * engolia), onSuccess e chamado, o botao e medido no clique e nao no render, e
 * `controle.explodir()` dispara as particulas so depois que a API confirmar.
 */

import { AnimatePresence, motion } from "motion/react";
import { useImperativeHandle, useRef, useState } from "react";
import type { ButtonProps } from "@/components/ui/button";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export type ControleDeParticulas = { explodir: () => void };

interface ParticleButtonProps extends ButtonProps {
  onSuccess?: () => void;
  successDuration?: number;
  /** Com controle, as particulas saem quando `explodir()` for chamado; sem ele, no clique. */
  controle?: React.Ref<ControleDeParticulas>;
}

type Particula = { id: number; x: number; y: number; dx: number; dy: number; atraso: number };

function SuccessParticles({ particulas }: { particulas: Particula[] }) {
  return (
    <AnimatePresence>
      {particulas.map((particula) => (
        <motion.div
          animate={{
            scale: [0, 1, 0],
            x: [0, particula.dx],
            y: [0, particula.dy],
          }}
          className="pointer-events-none visible fixed z-[90] h-1 w-1 rounded-full bg-carmesim-claro"
          initial={{
            scale: 0,
            x: 0,
            y: 0,
          }}
          key={particula.id}
          style={{ left: particula.x, top: particula.y }}
          transition={{
            duration: 0.6,
            delay: particula.atraso,
            ease: "easeOut",
          }}
        />
      ))}
    </AnimatePresence>
  );
}

export default function ParticleButton({
  children,
  onClick,
  onSuccess,
  successDuration = 1000,
  controle,
  className,
  ...props
}: ParticleButtonProps) {
  const [particulas, setParticulas] = useState<Particula[]>([]);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const lote = useRef(0);

  const explodir = () => {
    const rect = buttonRef.current?.getBoundingClientRect();
    if (!rect) return;
    lote.current += 1;
    const base = lote.current * 10;
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;
    setParticulas(
      Array.from({ length: 6 }, (_, i) => ({
        id: base + i,
        x: centerX,
        y: centerY,
        dx: (i % 2 ? 1 : -1) * (Math.random() * 50 + 20),
        dy: -Math.random() * 50 - 20,
        atraso: i * 0.1,
      }))
    );

    window.setTimeout(() => {
      setParticulas([]);
      onSuccess?.();
    }, successDuration);
  };

  useImperativeHandle(controle, () => ({ explodir }));

  // O Button do base-ui entrega um BaseUIEvent; o tipo vem do proprio onClick dele.
  const handleClick = (e: Parameters<NonNullable<ButtonProps["onClick"]>>[0]) => {
    if (!controle) explodir();
    onClick?.(e);
  };

  return (
    <>
      <SuccessParticles particulas={particulas} />
      <Button
        className={cn(
          "relative bg-carmesim text-osso hover:bg-carmesim/88",
          particulas.length > 0 && "scale-95",
          "transition-transform duration-100",
          className
        )}
        onClick={handleClick}
        ref={buttonRef}
        {...props}
      >
        {children}
      </Button>
    </>
  );
}
