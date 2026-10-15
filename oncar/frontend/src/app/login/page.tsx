"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import BeamsBackground from "@/components/kokonutui/beams-background";
import { Erro } from "@/components/oncar/avisos";
import { CAMPO, Rotulo } from "@/components/oncar/campo";
import { SeletorDeTema } from "@/components/oncar/seletor-de-tema";
import { Button } from "@/components/ui/button";
import { useModoFoco } from "@/hooks/use-modo-foco";
import { api, ErroDaApi } from "@/lib/api";
import type { UsuarioSessao } from "@/lib/tipos";
import { cn } from "@/lib/utils";

/** autenticacao por e-mail e senha contra a API do OnCar. */
export default function PaginaDeLogin() {
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [senhaVisivel, setSenhaVisivel] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const router = useRouter();
  const foco = useModoFoco();

  async function entrar(evento: React.FormEvent) {
    evento.preventDefault();
    setErro(null);
    setEnviando(true);
    try {
      await api.post<UsuarioSessao>("/sessao", { email, senha });
      router.replace("/painel");
    } catch (excecao) {
      setErro(
        excecao instanceof ErroDaApi
          ? excecao.message
          : "Não foi possível falar com o servidor. Tente novamente.",
      );
    } finally {
      setEnviando(false);
    }
  }

  return (
    <BeamsBackground foco={foco} intensity="medium">
      <div className="absolute top-4 right-4 sm:top-6 sm:right-6">
        <SeletorDeTema />
      </div>

      <main className="grid min-h-screen place-items-center px-4 py-16">
        <div className="w-full max-w-[26rem] animate-in duration-700 ease-oficina fill-mode-both fade-in slide-in-from-bottom-4">
          <p className="rotulo flex items-center gap-2">
            <i aria-hidden="true" className="bi bi-car-front text-sm text-ok-texto" />
            Acesso à oficina
          </p>
          <h1 className="fonte-display mt-3 text-display">OnCar</h1>
          <p className="mt-3 max-w-sm text-muted-foreground">
            Estoque e financeiro para oficinas mecânicas, do balcão ao elevador.
          </p>

          <form
            onSubmit={entrar}
            className="vidro mt-10 rounded-2xl border p-6 shadow-[0_32px_80px_-40px_rgb(0_0_0/0.75)] sm:p-8"
          >
            <Erro mensagem={erro} />

            <div className="grid gap-5">
              <div>
                <Rotulo htmlFor="email">E-mail</Rotulo>
                <input
                  id="email"
                  type="email"
                  required
                  autoFocus
                  autoComplete="username"
                  placeholder="voce@oficina.com.br"
                  value={email}
                  onChange={(evento) => setEmail(evento.target.value)}
                  className={CAMPO}
                />
              </div>

              <div>
                <Rotulo htmlFor="senha">Senha</Rotulo>
                <div className="relative">
                  <input
                    id="senha"
                    type={senhaVisivel ? "text" : "password"}
                    required
                    autoComplete="current-password"
                    value={senha}
                    onChange={(evento) => setSenha(evento.target.value)}
                    className={cn(CAMPO, "pr-12")}
                  />
                  <button
                    type="button"
                    onClick={() => setSenhaVisivel((visivel) => !visivel)}
                    aria-label={senhaVisivel ? "Ocultar senha" : "Mostrar senha"}
                    aria-pressed={senhaVisivel}
                    className="absolute inset-y-0 right-1 my-auto grid size-9 place-items-center rounded-md text-muted-foreground transition-colors hover:text-foreground"
                  >
                    <i aria-hidden="true" className={cn("bi", senhaVisivel ? "bi-eye-slash" : "bi-eye")} />
                  </button>
                </div>
              </div>

              <Button type="submit" disabled={enviando} className="mt-1 h-11 w-full text-[0.9375rem]">
                {enviando ? "Entrando…" : "Entrar"}
                {!enviando && <i aria-hidden="true" className="bi bi-arrow-right" />}
              </Button>
            </div>
          </form>

          <p className="num mt-6 text-center text-[0.6875rem] tracking-wide text-muted-foreground">
            Sessão encerra após 30 min sem uso
          </p>
        </div>
      </main>
    </BeamsBackground>
  );
}
