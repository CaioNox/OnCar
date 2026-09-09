"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, ErroDaApi } from "@/lib/api";
import { Erro } from "@/components/oncar/avisos";
import type { UsuarioSessao } from "@/lib/tipos";

/** RF01: autenticacao por e-mail e senha contra a API do OnCar. */
export default function PaginaDeLogin() {
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const router = useRouter();

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
          : "Nao foi possivel falar com o servidor. Tente novamente.",
      );
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-muted/30 px-4">
      <form
        onSubmit={entrar}
        className="w-full max-w-sm rounded-lg border bg-background p-6 shadow-sm"
      >
        <h1 className="text-center text-xl font-semibold tracking-tight">OnCar</h1>
        <p className="mb-6 text-center text-sm text-muted-foreground">
          Gestao de estoque para oficinas
        </p>

        <Erro mensagem={erro} />

        <label className="mb-1 block text-sm font-medium" htmlFor="email">
          E-mail
        </label>
        <input
          id="email"
          type="email"
          required
          autoFocus
          value={email}
          onChange={(evento) => setEmail(evento.target.value)}
          className="mb-4 w-full rounded-md border px-3 py-2 text-sm"
        />

        <label className="mb-1 block text-sm font-medium" htmlFor="senha">
          Senha
        </label>
        <input
          id="senha"
          type="password"
          required
          value={senha}
          onChange={(evento) => setSenha(evento.target.value)}
          className="mb-6 w-full rounded-md border px-3 py-2 text-sm"
        />

        <button
          type="submit"
          disabled={enviando}
          className="w-full rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60"
        >
          {enviando ? "Entrando..." : "Entrar"}
        </button>
      </form>
    </main>
  );
}
