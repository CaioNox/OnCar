"use client";

/**
 * @author: @dorianbaffier
 * @description: Beams Background
 * @version: 1.0.0
 * @date: 2025-06-26
 * @license: MIT
 * @website: https://kokonutui.com
 * @github: https://github.com/kokonut-labs/kokonutui
 *
 * Ajustes do OnCar: feixes em musgo + marinho, `children` no lugar do titulo de
 * demonstracao, modo foco (20% da velocidade e 30% mais escuro), movimento
 * reduzido, pausa com a aba oculta e canvas em meia resolucao (o blur esconde).
 */

import { useReducedMotion } from "motion/react";
import { useEffect, useRef } from "react";
import { cn } from "@/lib/utils";

interface AnimatedGradientBackgroundProps {
  className?: string;
  children?: React.ReactNode;
  intensity?: "subtle" | "medium" | "strong";
  /** Modo foco: desacelera para 20% e escurece 30%. */
  foco?: boolean;
}

type Cor = { h: number; s: number; l: number };

interface Beam {
  x: number;
  y: number;
  width: number;
  length: number;
  angle: number;
  speed: number;
  opacity: number;
  cor: Cor;
  pulse: number;
  pulseSpeed: number;
}

// Musgo (h 84) e marinho frio (h 216) da paleta, para cada tema.
const CORES: Record<"escuro" | "claro", Cor[]> = {
  escuro: [
    { h: 84, s: 30, l: 46 },
    { h: 84, s: 24, l: 36 },
    { h: 216, s: 42, l: 50 },
  ],
  claro: [
    { h: 84, s: 26, l: 42 },
    { h: 216, s: 36, l: 48 },
  ],
};

const opacityMap = {
  subtle: 0.7,
  medium: 0.85,
  strong: 1,
};

const MINIMUM_BEAMS = 20;
const ESCALA_DO_CANVAS = 0.5;

function sortearCor(cores: Cor[]): Cor {
  return cores[Math.floor(Math.random() * cores.length)];
}

function createBeam(width: number, height: number, cores: Cor[]): Beam {
  const angle = -35 + Math.random() * 10;
  return {
    x: Math.random() * width * 1.5 - width * 0.25,
    y: Math.random() * height * 1.5 - height * 0.25,
    width: 30 + Math.random() * 60,
    length: height * 2.5,
    angle,
    speed: 0.6 + Math.random() * 1.2,
    opacity: 0.12 + Math.random() * 0.16,
    cor: sortearCor(cores),
    pulse: Math.random() * Math.PI * 2,
    pulseSpeed: 0.02 + Math.random() * 0.03,
  };
}

export default function BeamsBackground({
  className,
  children,
  intensity = "strong",
  foco = false,
}: AnimatedGradientBackgroundProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const beamsRef = useRef<Beam[]>([]);
  const animationFrameRef = useRef<number>(0);
  const ritmoAlvo = useRef(1);
  const reduzido = useReducedMotion() ?? false;

  useEffect(() => {
    ritmoAlvo.current = foco ? 0.2 : 1;
  }, [foco]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const tela = { largura: 0, altura: 0 };
    let ritmo = ritmoAlvo.current;
    const cores = () =>
      document.documentElement.classList.contains("dark") ? CORES.escuro : CORES.claro;

    // Trocar o tema recolore os feixes na hora.
    const observer = new MutationObserver(() => {
      const paleta = cores();
      for (const beam of beamsRef.current) beam.cor = sortearCor(paleta);
      if (reduzido) desenharQuadro();
    });
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["class"],
    });

    function resetBeam(beam: Beam, index: number) {
      const column = index % 3;
      const spacing = tela.largura / 3;

      beam.y = tela.altura + 100;
      beam.x =
        column * spacing + spacing / 2 + (Math.random() - 0.5) * spacing * 0.5;
      beam.width = 100 + Math.random() * 100;
      beam.speed = 0.5 + Math.random() * 0.4;
      beam.cor = sortearCor(cores());
      beam.opacity = 0.2 + Math.random() * 0.1;
      return beam;
    }

    function drawBeam(context: CanvasRenderingContext2D, beam: Beam) {
      context.save();
      context.translate(beam.x, beam.y);
      context.rotate((beam.angle * Math.PI) / 180);

      const pulsingOpacity =
        beam.opacity *
        (0.8 + Math.sin(beam.pulse) * 0.2) *
        opacityMap[intensity];

      const { h, s, l } = beam.cor;
      const cor = (alfa: number) => `hsla(${h}, ${s}%, ${l}%, ${alfa})`;
      const gradient = context.createLinearGradient(0, 0, 0, beam.length);
      gradient.addColorStop(0, cor(0));
      gradient.addColorStop(0.1, cor(pulsingOpacity * 0.5));
      gradient.addColorStop(0.4, cor(pulsingOpacity));
      gradient.addColorStop(0.6, cor(pulsingOpacity));
      gradient.addColorStop(0.9, cor(pulsingOpacity * 0.5));
      gradient.addColorStop(1, cor(0));

      context.fillStyle = gradient;
      context.fillRect(-beam.width / 2, 0, beam.width, beam.length);
      context.restore();
    }

    function desenharQuadro() {
      if (!ctx) return;
      ctx.clearRect(0, 0, tela.largura, tela.altura);
      for (const beam of beamsRef.current) drawBeam(ctx, beam);
    }

    const updateCanvasSize = () => {
      tela.largura = window.innerWidth;
      tela.altura = window.innerHeight;
      canvas.width = Math.round(tela.largura * ESCALA_DO_CANVAS);
      canvas.height = Math.round(tela.altura * ESCALA_DO_CANVAS);
      canvas.style.width = `${tela.largura}px`;
      canvas.style.height = `${tela.altura}px`;
      ctx.setTransform(ESCALA_DO_CANVAS, 0, 0, ESCALA_DO_CANVAS, 0, 0);

      const paleta = cores();
      beamsRef.current = Array.from({ length: MINIMUM_BEAMS * 1.5 }, () =>
        createBeam(tela.largura, tela.altura, paleta)
      );
      if (reduzido) desenharQuadro();
    };

    function animate() {
      // O ritmo persegue o alvo aos poucos: o modo foco entra e sai sem tranco.
      ritmo += (ritmoAlvo.current - ritmo) * 0.04;
      beamsRef.current.forEach((beam, index) => {
        beam.y -= beam.speed * ritmo;
        beam.pulse += beam.pulseSpeed * ritmo;

        // Reset beam when it goes off screen
        if (beam.y + beam.length < -100) {
          resetBeam(beam, index);
        }
      });
      desenharQuadro();
      animationFrameRef.current = requestAnimationFrame(animate);
    }

    const parar = () => cancelAnimationFrame(animationFrameRef.current);
    const aoMudarVisibilidade = () => {
      parar();
      if (!document.hidden) animationFrameRef.current = requestAnimationFrame(animate);
    };

    updateCanvasSize();
    window.addEventListener("resize", updateCanvasSize);
    if (!reduzido) {
      animate();
      document.addEventListener("visibilitychange", aoMudarVisibilidade);
    }

    return () => {
      window.removeEventListener("resize", updateCanvasSize);
      document.removeEventListener("visibilitychange", aoMudarVisibilidade);
      parar();
      observer.disconnect();
    };
  }, [intensity, reduzido]);

  return (
    <div
      className={cn(
        "relative isolate min-h-screen w-full overflow-hidden bg-background",
        className
      )}
    >
      <canvas
        aria-hidden="true"
        className="pointer-events-none fixed inset-0"
        ref={canvasRef}
        style={{ filter: "blur(30px)" }}
      />

      <div
        aria-hidden="true"
        className="pointer-events-none fixed inset-0 bg-(--wallpaper-veu) transition-opacity duration-[1600ms] ease-oficina"
        style={{ opacity: foco ? 0.3 : 0 }}
      />

      <div className="relative z-10">{children}</div>
    </div>
  );
}
