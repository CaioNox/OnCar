"use client";

import Link from "next/link";
import { useId, useState } from "react";
import { GarageWallpaper } from "@/components/oncar/garage-wallpaper";
import { SeletorDeTema } from "@/components/oncar/seletor-de-tema";
import { formatarMoeda } from "@/lib/api";

/**
 * Bancada de testes do <GarageWallpaper />: simula o estado da oficina para ver
 * cada comportamento isolado, sem depender dos dados da API.
 */
export default function BancadaDoWallpaper() {
  const [osAbertas, setOsAbertas] = useState(3);
  const [estoqueCritico, setEstoqueCritico] = useState(1);
  const [saldo, setSaldo] = useState(1200);
  const [lanterna, setLanterna] = useState(true);
  const [focoForcado, setFocoForcado] = useState(false);

  return (
    <main className="relative isolate min-h-screen">
      <GarageWallpaper
        osAbertas={osAbertas}
        estoqueCritico={estoqueCritico}
        saldo={saldo}
        lanterna={lanterna}
        focoForcado={focoForcado}
      />

      <div className="relative z-10 mx-auto flex max-w-6xl flex-col gap-12 px-4 py-16 sm:px-6 md:py-24 lg:px-8">
        <header className="flex items-start justify-between gap-6">
          <div>
            <p className="rotulo">Bancada de testes · GarageWallpaper</p>
            <h1 className="fonte-display mt-4 max-w-3xl text-display">Planta técnica ao vivo</h1>
            <p className="mt-5 max-w-xl text-muted-foreground">
              Simule o estado da oficina e veja o wallpaper reagir. Pare o mouse por 8 segundos
              ou clique no campo de teste para entrar no modo foco.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <SeletorDeTema />
            <Link
              href="/painel"
              className="rounded-full border px-4 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
            >
              Ir ao painel
            </Link>
          </div>
        </header>

        <section
          aria-labelledby="titulo-controles"
          className="vidro grid max-w-md gap-6 rounded-2xl border p-6 shadow-[0_24px_64px_-32px_rgb(0_0_0/0.6)]"
        >
          <h2 id="titulo-controles" className="rotulo">
            Estado simulado
          </h2>

          <Controle
            rotulo="OS abertas"
            valor={osAbertas}
            leitura={`${osAbertas} · fluxo ${(1 + Math.min(osAbertas, 10) * 0.08).toFixed(2).replace(".", ",")}×`}
            min={0}
            max={12}
            aoMudar={setOsAbertas}
          />
          <Controle
            rotulo="Peças abaixo do mínimo"
            valor={estoqueCritico}
            leitura={estoqueCritico > 0 ? `${estoqueCritico} · linha carmesim a cada 6s` : "nenhuma"}
            min={0}
            max={10}
            aoMudar={setEstoqueCritico}
          />
          <Controle
            rotulo="Saldo financeiro"
            valor={saldo}
            leitura={formatarMoeda(saldo)}
            min={-5000}
            max={5000}
            passo={100}
            aoMudar={setSaldo}
          />

          <div className="grid gap-3 text-sm">
            <Alternador rotulo="Lanterna do mecânico" ligado={lanterna} aoMudar={setLanterna} />
            <Alternador rotulo="Forçar modo foco" ligado={focoForcado} aoMudar={setFocoForcado} />
          </div>

          <label className="grid gap-1.5 text-sm">
            <span className="text-muted-foreground">Campo de teste do modo foco</span>
            <input
              placeholder="Clique aqui e o wallpaper desacelera"
              className="h-10 rounded-lg border border-input bg-background/60 px-3 text-sm"
            />
          </label>
        </section>
      </div>
    </main>
  );
}

function Controle({
  rotulo,
  valor,
  leitura,
  min,
  max,
  passo = 1,
  aoMudar,
}: {
  rotulo: string;
  valor: number;
  leitura: string;
  min: number;
  max: number;
  passo?: number;
  aoMudar: (valor: number) => void;
}) {
  const id = useId();
  return (
    <div className="grid gap-2">
      <div className="flex items-baseline justify-between gap-4 text-sm">
        <label htmlFor={id}>{rotulo}</label>
        <output htmlFor={id} className="num text-xs text-muted-foreground">
          {leitura}
        </output>
      </div>
      <input
        id={id}
        type="range"
        min={min}
        max={max}
        step={passo}
        value={valor}
        onChange={(evento) => aoMudar(Number(evento.target.value))}
        style={{ "--preenchido": `${((valor - min) / (max - min)) * 100}%` } as React.CSSProperties}
        className="w-full"
      />
    </div>
  );
}

function Alternador({
  rotulo,
  ligado,
  aoMudar,
}: {
  rotulo: string;
  ligado: boolean;
  aoMudar: (ligado: boolean) => void;
}) {
  return (
    <label className="flex cursor-pointer items-center justify-between gap-4">
      <span>{rotulo}</span>
      <input
        type="checkbox"
        role="switch"
        checked={ligado}
        onChange={(evento) => aoMudar(evento.target.checked)}
        className="size-4 accent-musgo-claro"
      />
    </label>
  );
}
