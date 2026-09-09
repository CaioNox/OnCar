"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { createContext, useContext, useEffect, useState } from "react";
import { api, ErroDaApi } from "@/lib/api";
import { podeGerenciarEstoque, type UsuarioSessao } from "@/lib/tipos";

const ContextoDeSessao = createContext<UsuarioSessao | null>(null);

/** Usuario autenticado da sessao corrente. */
export function useUsuario(): UsuarioSessao | null {
  return useContext(ContextoDeSessao);
}

const LINKS = [
  { href: "/painel", rotulo: "Painel" },
  { href: "/produtos", rotulo: "Produtos" },
  { href: "/movimentacoes", rotulo: "Movimentacoes" },
];

/**
 * Moldura das telas autenticadas: carrega a sessao, envia o visitante ao login
 * quando a API responde 401 (RN015) e monta o menu conforme o perfil (RN007).
 */
export function AppShell({ children }: { children: React.ReactNode }) {
  const [usuario, setUsuario] = useState<UsuarioSessao | null>(null);
  const [carregando, setCarregando] = useState(true);
  const router = useRouter();
  const caminho = usePathname();

  useEffect(() => {
    api
      .get<UsuarioSessao>("/sessao")
      .then(setUsuario)
      .catch((erro) => {
        if (erro instanceof ErroDaApi && erro.naoAutenticado) {
          router.replace("/login");
          return;
        }
        setUsuario(null);
      })
      .finally(() => setCarregando(false));
  }, [router]);

  async function sair() {
    await api.delete("/sessao").catch(() => undefined);
    router.replace("/login");
  }

  if (carregando) {
    return (
      <main className="flex min-h-screen items-center justify-center text-muted-foreground">
        Carregando...
      </main>
    );
  }

  if (!usuario) return null;

  return (
    <ContextoDeSessao.Provider value={usuario}>
      <div className="min-h-screen bg-muted/30">
        <header className="border-b bg-background">
          <nav className="mx-auto flex max-w-6xl flex-wrap items-center gap-4 px-4 py-3">
            <Link href="/painel" className="text-lg font-semibold tracking-tight">
              OnCar
            </Link>
            <ul className="flex flex-1 flex-wrap gap-1">
              {LINKS.map((link) => (
                <li key={link.href}>
                  <Link
                    href={link.href}
                    className={`rounded-md px-3 py-1.5 text-sm transition-colors ${
                      caminho.startsWith(link.href)
                        ? "bg-muted font-medium text-foreground"
                        : "text-muted-foreground hover:bg-muted/60"
                    }`}
                  >
                    {link.rotulo}
                  </Link>
                </li>
              ))}
            </ul>
            <span className="text-sm text-muted-foreground">
              {usuario.nome} · {usuario.perfilDescricao}
            </span>
            <button
              type="button"
              onClick={sair}
              className="rounded-md border px-3 py-1.5 text-sm hover:bg-muted"
            >
              Sair
            </button>
          </nav>
        </header>
        <main className="mx-auto max-w-6xl px-4 py-6">{children}</main>
      </div>
    </ContextoDeSessao.Provider>
  );
}

/** Atalho para telas que so fazem sentido para quem administra o estoque. */
export function useGerenciaEstoque(): boolean {
  return podeGerenciarEstoque(useUsuario());
}
