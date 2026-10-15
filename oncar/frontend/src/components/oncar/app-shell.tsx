"use client";

import { motion } from "motion/react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Assistente } from "@/components/oncar/assistente";
import {
  ContextoDaOficina,
  ContextoDeSessao,
  type Oficina,
} from "@/components/oncar/contexto";
import { GarageWallpaper } from "@/components/oncar/garage-wallpaper";
import { PaletaDeComandos } from "@/components/oncar/paleta-de-comandos";
import { SeletorDeTema } from "@/components/oncar/seletor-de-tema";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { api, ErroDaApi } from "@/lib/api";
import { dataLocal, sinaisDaOficina, type Sinais } from "@/lib/sinais";
import type { Movimentacao, Painel, Produto, UsuarioSessao } from "@/lib/tipos";
import { cn } from "@/lib/utils";

// Mantido para as telas que ja importavam daqui.
export { useGerenciaEstoque, useUsuario } from "@/components/oncar/contexto";

const LINKS = [
  { href: "/painel", rotulo: "Painel", icone: "bi-speedometer2" },
  { href: "/produtos", rotulo: "Produtos", icone: "bi-box-seam" },
  { href: "/movimentacoes", rotulo: "Movimentações", icone: "bi-arrow-left-right" },
];

type Dados = {
  painel: Painel | null;
  movimentacoes: Movimentacao[];
  produtos: Produto[];
  sinais: Sinais;
  atualizadoEm: Date | null;
};

const SEM_DADOS: Dados = { painel: null, movimentacoes: [], produtos: [], sinais: {}, atualizadoEm: null };

/**
 * Moldura das telas autenticadas: carrega a sessao, envia o visitante ao login
 * quando a API responde 401 (RN015), monta o menu conforme o perfil (RN007) e
 * alimenta o wallpaper com o estado da oficina.
 */
export function AppShell({ children }: { children: React.ReactNode }) {
  const [usuario, setUsuario] = useState<UsuarioSessao | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [falhaNaSessao, setFalhaNaSessao] = useState(false);
  const [dados, setDados] = useState<Dados>(SEM_DADOS);
  const [carregado, setCarregado] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [buscaAberta, setBuscaAberta] = useState(false);
  const [assistenteAberto, setAssistenteAberto] = useState(false);
  const emAndamento = useRef<Promise<void> | null>(null);
  const router = useRouter();
  const caminho = usePathname();

  const carregarSessao = useCallback(() => {
    api
      .get<UsuarioSessao>("/sessao")
      .then(setUsuario)
      .catch((excecao) => {
        if (excecao instanceof ErroDaApi && excecao.naoAutenticado) {
          router.replace("/login");
          return;
        }
        setFalhaNaSessao(true);
      })
      .finally(() => setCarregando(false));
  }, [router]);

  useEffect(() => {
    carregarSessao();
  }, [carregarSessao]);

  // Uma carga por vez: a tela e o shell podem pedir juntos sem duplicar requisicoes.
  const recarregar = useCallback(() => {
    if (emAndamento.current) return emAndamento.current;
    const promessa = Promise.all([
      api.get<Painel>("/painel"),
      api.get<Movimentacao[]>("/movimentacoes"),
      api.get<Produto[]>("/produtos?somenteAtivos=false"),
    ])
      .then(([painel, movimentacoes, produtos]) => {
        const agora = new Date();
        const sinais = sinaisDaOficina(painel, movimentacoes, produtos, dataLocal(agora));
        setDados({ painel, movimentacoes, produtos, sinais, atualizadoEm: agora });
        setErro(null);
      })
      .catch((excecao) => {
        if (excecao instanceof ErroDaApi && excecao.naoAutenticado) {
          router.replace("/login");
          return;
        }
        setErro(excecao instanceof ErroDaApi ? excecao.message : "Falha ao carregar os dados da oficina.");
      })
      .finally(() => {
        setCarregado(true);
        emAndamento.current = null;
      });
    emAndamento.current = promessa;
    return promessa;
  }, [router]);

  // Dados frescos a cada navegacao. Nada de polling: a sessao expira por inatividade (RN015).
  useEffect(() => {
    if (usuario) void recarregar();
  }, [usuario, caminho, recarregar]);

  useEffect(() => {
    const aoTeclar = (evento: KeyboardEvent) => {
      if ((evento.ctrlKey || evento.metaKey) && evento.key.toLowerCase() === "k") {
        evento.preventDefault();
        setBuscaAberta((aberta) => !aberta);
      }
    };
    window.addEventListener("keydown", aoTeclar);
    return () => window.removeEventListener("keydown", aoTeclar);
  }, []);

  const abrirBusca = useCallback(() => setBuscaAberta(true), []);
  const abrirAssistente = useCallback(() => setAssistenteAberto(true), []);
  const fecharBusca = useCallback(() => setBuscaAberta(false), []);
  const fecharAssistente = useCallback(() => setAssistenteAberto(false), []);

  const oficina = useMemo<Oficina>(
    () => ({ ...dados, carregado, erro, recarregar, abrirBusca, abrirAssistente }),
    [dados, carregado, erro, recarregar, abrirBusca, abrirAssistente],
  );

  async function sair() {
    await api.delete("/sessao").catch(() => undefined);
    router.replace("/login");
  }

  if (carregando) return <EsqueletoDoShell />;

  if (falhaNaSessao) {
    return (
      <div className="relative isolate grid min-h-screen place-items-center px-4">
        <GarageWallpaper />
        <div className="vidro relative z-10 max-w-md rounded-2xl border p-8 text-center">
          <i aria-hidden="true" className="bi bi-plug text-3xl text-muted-foreground" />
          <h1 className="fonte-display mt-4 text-3xl">Sem conexão com a oficina</h1>
          <p className="mt-3 text-muted-foreground">
            Não foi possível falar com o servidor do OnCar. Confira se o back-end está no ar e
            tente de novo.
          </p>
          <button
            type="button"
            onClick={() => {
              setFalhaNaSessao(false);
              setCarregando(true);
              carregarSessao();
            }}
            className="mt-6 h-10 rounded-lg bg-primary px-5 text-sm font-medium text-primary-foreground hover:bg-primary/85"
          >
            Tentar de novo
          </button>
        </div>
      </div>
    );
  }

  if (!usuario) return null;

  return (
    <ContextoDeSessao.Provider value={usuario}>
      <ContextoDaOficina.Provider value={oficina}>
        <div className="relative isolate min-h-screen">
          <GarageWallpaper {...dados.sinais} />

          <a
            href="#conteudo"
            className="sr-only z-[80] rounded-lg bg-primary px-4 py-2 text-sm text-primary-foreground focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
          >
            Pular para o conteúdo
          </a>

          <Cabecalho usuario={usuario} caminho={caminho} aoBuscar={abrirBusca} aoPerguntar={abrirAssistente} aoSair={sair} />

          <main
            id="conteudo"
            tabIndex={-1}
            className="relative z-10 mx-auto w-full max-w-7xl px-4 pt-10 pb-24 outline-none sm:px-6 md:pt-16 lg:px-8"
          >
            {children}
          </main>

          <PaletaDeComandos aberta={buscaAberta} aoFechar={fecharBusca} />
          <Assistente aberto={assistenteAberto} aoFechar={fecharAssistente} />
        </div>
      </ContextoDaOficina.Provider>
    </ContextoDeSessao.Provider>
  );
}

function Cabecalho({
  usuario,
  caminho,
  aoBuscar,
  aoPerguntar,
  aoSair,
}: {
  usuario: UsuarioSessao;
  caminho: string;
  aoBuscar: () => void;
  aoPerguntar: () => void;
  aoSair: () => void;
}) {
  return (
    <header className="vidro sticky top-0 z-40 border-b">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-2 px-4 sm:gap-4 sm:px-6 lg:px-8">
        <Link href="/painel" className="flex shrink-0 items-center gap-2.5 rounded-lg" aria-label="OnCar, ir para o painel">
          <span className="grid size-8 place-items-center rounded-lg bg-primary text-primary-foreground">
            <i aria-hidden="true" className="bi bi-car-front" />
          </span>
          <span aria-hidden="true" className="fonte-display hidden text-xl sm:inline">
            OnCar
          </span>
        </Link>

        <nav aria-label="Principal" className="min-w-0 flex-1">
          <ul className="flex items-center gap-0.5 sm:gap-1">
            {LINKS.map((link) => {
              const ativo = caminho.startsWith(link.href);
              return (
                <li key={link.href} className="relative">
                  <Link
                    href={link.href}
                    aria-current={ativo ? "page" : undefined}
                    title={link.rotulo}
                    className={cn(
                      "flex h-9 items-center gap-2 rounded-lg px-2.5 text-sm transition-colors lg:px-3",
                      ativo ? "text-foreground" : "text-muted-foreground hover:bg-accent hover:text-foreground",
                    )}
                  >
                    <i aria-hidden="true" className={cn("bi", link.icone)} />
                    <span className="sr-only md:not-sr-only">{link.rotulo}</span>
                  </Link>
                  {ativo && (
                    // Agulha de painel: desliza entre os itens ao navegar.
                    <motion.span
                      layoutId="agulha-do-menu"
                      aria-hidden="true"
                      className="absolute inset-x-2 -bottom-[15px] h-[2px] rounded-full bg-ok"
                      transition={{ type: "spring", stiffness: 380, damping: 32 }}
                    />
                  )}
                </li>
              );
            })}
          </ul>
        </nav>

        <button
          type="button"
          onClick={aoBuscar}
          className="hidden h-9 items-center gap-2.5 rounded-lg border bg-background/40 px-3 text-sm text-muted-foreground transition-colors hover:border-foreground/25 hover:text-foreground sm:flex lg:w-72"
        >
          <i aria-hidden="true" className="bi bi-search" />
          <span className="hidden flex-1 text-left lg:inline">Buscar peça, cliente, OS…</span>
          <span className="lg:hidden">Buscar</span>
          <kbd className="num rounded border px-1.5 py-0.5 text-[0.625rem]">Ctrl K</kbd>
        </button>
        <button
          type="button"
          onClick={aoBuscar}
          aria-label="Buscar"
          className="grid size-9 place-items-center rounded-full text-muted-foreground hover:bg-accent hover:text-foreground sm:hidden"
        >
          <i aria-hidden="true" className="bi bi-search" />
        </button>

        <button
          type="button"
          onClick={aoPerguntar}
          aria-label="Abrir o assistente da oficina"
          title="Assistente"
          className="grid size-9 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
        >
          <i aria-hidden="true" className="bi bi-stars" />
        </button>
        <SeletorDeTema />
        <MenuDoUsuario usuario={usuario} aoSair={aoSair} />
      </div>
    </header>
  );
}

function MenuDoUsuario({ usuario, aoSair }: { usuario: UsuarioSessao; aoSair: () => void }) {
  const partes = usuario.nome.split(" ").filter(Boolean);
  const iniciais = partes
    .slice(0, 2)
    .map((parte) => parte[0])
    .join("")
    .toUpperCase();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <button
            type="button"
            aria-label={`Conta de ${usuario.nome}`}
            className="flex shrink-0 items-center gap-2.5 rounded-full p-0.5 transition-colors hover:bg-accent md:pr-3"
          />
        }
      >
        <span className="num grid size-8 place-items-center rounded-full bg-secondary text-xs text-secondary-foreground">
          {iniciais}
        </span>
        <span className="hidden text-left leading-tight md:block">
          <span className="block text-sm">{partes[0]}</span>
          <span className="block text-[0.6875rem] text-muted-foreground">{usuario.perfilDescricao}</span>
        </span>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuGroup>
          <DropdownMenuLabel className="px-2 py-1.5">
            <span className="block text-sm text-foreground">{usuario.nome}</span>
            <span className="num block truncate text-[0.6875rem]">{usuario.email}</span>
          </DropdownMenuLabel>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={aoSair} className="gap-2 px-2 py-1.5">
          <i aria-hidden="true" className="bi bi-box-arrow-right" />
          Sair
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/** Enquanto a sessao carrega: a moldura em esqueleto, sem spinner. */
function EsqueletoDoShell() {
  return (
    <div className="relative isolate min-h-screen" aria-busy="true" aria-label="Carregando">
      <GarageWallpaper />
      <div className="vidro sticky top-0 z-40 h-16 border-b" />
      <div className="relative z-10 mx-auto grid max-w-7xl gap-6 px-4 pt-16 sm:px-6 lg:px-8">
        <div className="esqueleto h-4 w-40" />
        <div className="esqueleto h-16 w-full max-w-xl" />
        <div className="mt-6 grid gap-6 md:grid-cols-3">
          <div className="esqueleto h-56 md:col-span-2" />
          <div className="esqueleto h-56" />
        </div>
      </div>
    </div>
  );
}
