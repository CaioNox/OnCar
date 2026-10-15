"use client";

import {
  motion,
  useMotionTemplate,
  useMotionValue,
  useReducedMotion,
  useSpring,
} from "motion/react";
import { useEffect, useRef, useState } from "react";
import {
  generateAestheticPath,
  generatePathPoints,
  type PathType,
} from "@/components/kokonutui/background-paths";
import { useMediaQuery } from "@/hooks/use-media-query";
import { useModoFoco } from "@/hooks/use-modo-foco";
import { cn } from "@/lib/utils";
import estilos from "./garage-wallpaper.module.css";

export type GarageWallpaperProps = {
  /** Ordens de servico abertas: quanto mais OS, mais rapido (de leve) o fluxo das linhas. */
  osAbertas?: number;
  /** Pecas abaixo do estoque minimo: com pelo menos uma, UMA linha pulsa em carmesim a cada 6s. */
  estoqueCritico?: number;
  /** Saldo financeiro: positivo puxa as linhas para o musgo, negativo para um marinho mais frio. */
  saldo?: number;
  /** Halo que segue o cursor e revela a grade tecnica. Some sozinho em telas de toque. */
  lanterna?: boolean;
  /** Liga o modo foco independentemente do usuario (usado na bancada de testes). */
  focoForcado?: boolean;
  className?: string;
};

const VIEWBOX = "-2400 -800 4800 1600";

type Camada = {
  tipo: PathType;
  quantidade: number;
  largura: [number, number];
  deriva: [number, number];
  duracao: number;
};

// A geometria vem do @kokonutui/background-paths; aqui so recolorimos e afinamos o traco.
const CAMADAS: Record<PathType, Camada> = {
  primary: { tipo: "primary", quantidade: 10, largura: [1.1, 1.8], deriva: [-26, -14], duracao: 9000 },
  secondary: { tipo: "secondary", quantidade: 12, largura: [0.8, 1.3], deriva: [20, -10], duracao: 7000 },
  accent: { tipo: "accent", quantidade: 8, largura: [0.55, 0.95], deriva: [-12, -6], duracao: 5200 },
};

// Math.sin/cos do Node e do navegador podem divergir no ultimo digito, o que quebraria
// a hidratacao. Com 0,1 unidade de viewBox (~0,04px na tela) o SSR fica deterministico.
const arredondar = (valor: number) => Math.round(valor * 10) / 10;

function caminho(indice: number, tipo: PathType): string {
  return generateAestheticPath(indice, 1, tipo).replace(/-?\d+\.\d+/g, (numero) =>
    String(arredondar(Number(numero))),
  );
}

function tracar({ tipo, quantidade, largura }: Camada) {
  return Array.from({ length: quantidade }, (_, indice) => {
    const t = indice / (quantidade - 1);
    return {
      d: caminho(indice, tipo),
      largura: arredondar(largura[0] + (largura[1] - largura[0]) * t),
      opacidade: arredondar(0.55 + 0.45 * t),
    };
  });
}

const TRACOS: Record<PathType, ReturnType<typeof tracar>> = {
  primary: tracar(CAMADAS.primary),
  secondary: tracar(CAMADAS.secondary),
  accent: tracar(CAMADAS.accent),
};

// Terminais do chicote: pequenos aneis soldados em alguns fios principais.
const TERMINAIS = [2, 5, 8].flatMap((fio) => {
  const pontos = generatePathPoints(fio, 1, "primary");
  return [3, 7].map((ponto) => ({
    chave: `${fio}-${ponto}`,
    x: arredondar(pontos[ponto].x),
    y: arredondar(pontos[ponto].y),
  }));
});

const FIO_DE_ALERTA = caminho(4, "primary");
const FIOS_COM_FAISCA = [caminho(6, "primary"), caminho(1, "primary")];

/** Ate 10 OS: cada uma acelera o fluxo em 8%. */
function ritmoDasOs(osAbertas: number | undefined): number {
  return 1 + Math.min(Math.max(osAbertas ?? 0, 0), 10) * 0.08;
}

/** Percentual de musgo na cor das linhas: 70% sem saldo, 100% bem positivo, 0% bem negativo. */
function tomDoSaldo(saldo: number | undefined): number {
  if (saldo === undefined || Number.isNaN(saldo)) return 70;
  const intensidade = Math.tanh(saldo / 1500);
  return Math.round(intensidade >= 0 ? 70 + 30 * intensidade : 70 + 70 * intensidade);
}

/**
 * Wallpaper animado e interativo da "Oficina Noturna": linhas de chicote eletrico
 * sobre planta tecnica, que refletem o estado da oficina recebido por props.
 */
export function GarageWallpaper({
  osAbertas,
  estoqueCritico,
  saldo,
  lanterna = true,
  focoForcado = false,
  className,
}: GarageWallpaperProps) {
  const reduzido = useReducedMotion() ?? false;
  const focoDetectado = useModoFoco();
  const emFoco = focoForcado || focoDetectado;
  const ponteiroFino = useMediaQuery("(hover: hover) and (pointer: fine) and (min-width: 768px)");
  const comLanterna = lanterna && ponteiroFino && !reduzido;
  const critico = (estoqueCritico ?? 0) > 0;

  const primaria = useRef<SVGSVGElement>(null);
  const secundaria = useRef<SVGSVGElement>(null);
  const acento = useRef<SVGSVGElement>(null);
  const faiscas = useRef<SVGSVGElement>(null);
  const alerta = useRef<SVGSVGElement>(null);
  const fluxo = useRef<Animation[]>([]);
  const pulso = useRef<Animation | null>(null);

  // Velocidade das linhas: sobe com as OS e cai para 20% no modo foco, sempre por mola.
  const ritmoAlvo = ritmoDasOs(osAbertas) * (emFoco ? 0.2 : 1);
  const ritmo = useSpring(ritmoAlvo, { stiffness: 22, damping: 14 });

  useEffect(() => {
    ritmo.set(ritmoAlvo);
  }, [ritmo, ritmoAlvo]);

  useEffect(
    () =>
      ritmo.on("change", (valor) => {
        for (const animacao of fluxo.current) animacao.updatePlaybackRate(valor);
      }),
    [ritmo],
  );

  // Deriva das camadas e faiscas: WAAPI em transform roda no compositor, sem repintar.
  useEffect(() => {
    if (reduzido) return;

    const derivar = (alvo: Element | null, { deriva: [dx, dy], duracao }: Camada) =>
      alvo?.animate(
        [{ transform: "translate3d(0, 0, 0)" }, { transform: `translate3d(${dx}px, ${dy}px, 0)` }],
        { duration: duracao, iterations: Infinity, direction: "alternate", easing: "ease-in-out" },
      );

    // Faisca: um traco curto que percorre o fio e some pela outra ponta.
    const correr = (caminho: SVGPathElement, indice: number) =>
      caminho.animate([{ strokeDashoffset: "0.04" }, { strokeDashoffset: "-1.96" }], {
        duration: 11000,
        delay: 2000 + indice * 5400,
        iterations: Infinity,
        easing: "linear",
        fill: "backwards",
      });

    const animacoes = [
      // A camada primaria, as faiscas e a linha de alerta andam juntas para nao descolar do fio.
      derivar(primaria.current, CAMADAS.primary),
      derivar(faiscas.current, CAMADAS.primary),
      derivar(alerta.current, CAMADAS.primary),
      derivar(secundaria.current, CAMADAS.secondary),
      derivar(acento.current, CAMADAS.accent),
      ...Array.from(faiscas.current?.querySelectorAll("path") ?? [], correr),
    ].filter((animacao): animacao is Animation => animacao !== undefined);

    for (const animacao of animacoes) {
      animacao.updatePlaybackRate(ritmo.get());
      if (document.hidden) animacao.pause();
    }
    fluxo.current = animacoes;

    return () => {
      for (const animacao of animacoes) animacao.cancel();
      fluxo.current = [];
    };
  }, [reduzido, ritmo]);

  // Estoque critico: UMA linha pulsa em carmesim, lenta, a cada 6 segundos.
  useEffect(() => {
    const elemento = alerta.current;
    if (!critico || reduzido || !elemento) return;

    const animacao = elemento.animate(
      [{ opacity: 0 }, { opacity: 0.62, offset: 0.16 }, { opacity: 0.08, offset: 0.42 }, { opacity: 0 }],
      { duration: 6000, iterations: Infinity, easing: "ease-in-out" },
    );
    if (document.hidden) animacao.pause();
    pulso.current = animacao;

    return () => {
      animacao.cancel();
      pulso.current = null;
    };
  }, [critico, reduzido]);

  useEffect(() => {
    pulso.current?.updatePlaybackRate(emFoco ? 0.2 : 1);
  }, [emFoco, critico, reduzido]);

  // Aba em segundo plano: tudo pausa.
  useEffect(() => {
    const aoMudarVisibilidade = () => {
      const animacoes = pulso.current ? [...fluxo.current, pulso.current] : fluxo.current;
      for (const animacao of animacoes) {
        if (document.hidden) animacao.pause();
        else animacao.play();
      }
    };
    document.addEventListener("visibilitychange", aoMudarVisibilidade);
    return () => document.removeEventListener("visibilitychange", aoMudarVisibilidade);
  }, []);

  // Lanterna do mecanico: segue o cursor por mola, sem ficar presa ao pixel.
  const ponteiroX = useMotionValue(0);
  const ponteiroY = useMotionValue(0);
  const luzX = useSpring(ponteiroX, { stiffness: 150, damping: 24, mass: 0.6 });
  const luzY = useSpring(ponteiroY, { stiffness: 150, damping: 24, mass: 0.6 });
  const mascara = useMotionTemplate`radial-gradient(circle 280px at ${luzX}px ${luzY}px, #000 0%, rgb(0 0 0 / 0.5) 42%, transparent 100%)`;
  const [acesa, setAcesa] = useState(false);
  const acesaAgora = useRef(false);

  useEffect(() => {
    if (!comLanterna) return;

    const mover = (evento: PointerEvent) => {
      if (evento.pointerType !== "mouse") return;
      ponteiroX.set(evento.clientX);
      ponteiroY.set(evento.clientY);
      if (!acesaAgora.current) {
        // Primeiro movimento: acende no lugar, sem atravessar a tela.
        luzX.jump(evento.clientX);
        luzY.jump(evento.clientY);
        acesaAgora.current = true;
        setAcesa(true);
      }
    };
    const sair = (evento: PointerEvent) => {
      if (evento.relatedTarget === null && acesaAgora.current) {
        acesaAgora.current = false;
        setAcesa(false);
      }
    };

    window.addEventListener("pointermove", mover, { passive: true });
    window.addEventListener("pointerout", sair, { passive: true });
    return () => {
      window.removeEventListener("pointermove", mover);
      window.removeEventListener("pointerout", sair);
    };
  }, [comLanterna, ponteiroX, ponteiroY, luzX, luzY]);

  const svg = { viewBox: VIEWBOX, preserveAspectRatio: "xMidYMid slice", fill: "none" } as const;

  return (
    <div aria-hidden="true" className={cn(estilos.raiz, className)}>
      <div className={estilos.vinheta} />

      {comLanterna && (
        <>
          <motion.div className={estilos.halo} data-acesa={acesa} style={{ x: luzX, y: luzY }} />
          <motion.div
            className={estilos.grade}
            data-acesa={acesa}
            style={{ maskImage: mascara, WebkitMaskImage: mascara }}
          />
        </>
      )}

      <div className={estilos.linhas} style={{ "--tom": `${tomDoSaldo(saldo)}%` } as React.CSSProperties}>
        {(["accent", "secondary"] as const).map((tipo) => (
          <svg
            key={tipo}
            ref={tipo === "accent" ? acento : secundaria}
            className={cn(estilos.camada, tipo === "accent" ? estilos.acento : estilos.secundaria)}
            {...svg}
          >
            {TRACOS[tipo].map((traco) => (
              <path
                key={traco.d}
                d={traco.d}
                stroke="currentColor"
                strokeWidth={traco.largura}
                strokeOpacity={traco.opacidade}
                strokeLinecap="round"
                vectorEffect="non-scaling-stroke"
              />
            ))}
          </svg>
        ))}

        <svg ref={primaria} className={cn(estilos.camada, estilos.primaria)} {...svg}>
          {TRACOS.primary.map((traco) => (
            <path
              key={traco.d}
              d={traco.d}
              stroke="currentColor"
              strokeWidth={traco.largura}
              strokeOpacity={traco.opacidade}
              strokeLinecap="round"
              vectorEffect="non-scaling-stroke"
            />
          ))}
          {TERMINAIS.map((terminal) => (
            <circle
              key={terminal.chave}
              cx={terminal.x}
              cy={terminal.y}
              r={9}
              stroke="currentColor"
              strokeWidth={1.4}
              style={{ fill: "var(--wallpaper-base)" }}
              vectorEffect="non-scaling-stroke"
            />
          ))}
        </svg>

        {!reduzido && (
          <svg ref={faiscas} className={cn(estilos.camada, estilos.faiscas)} {...svg}>
            {FIOS_COM_FAISCA.map((d) => (
              <path
                key={d}
                d={d}
                pathLength={1}
                stroke="currentColor"
                strokeWidth={5}
                strokeLinecap="round"
                strokeDasharray="0.04 1.96"
                strokeDashoffset={0.04}
              />
            ))}
          </svg>
        )}
      </div>

      <svg
        ref={alerta}
        className={cn(estilos.camada, estilos.alerta)}
        data-ativo={critico}
        data-estatico={critico && reduzido}
        {...svg}
      >
        <path
          d={FIO_DE_ALERTA}
          stroke="currentColor"
          strokeWidth={2.2}
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />
      </svg>

      <div className={estilos.grao} />
      <div className={estilos.veu} data-foco={emFoco} />
    </div>
  );
}
