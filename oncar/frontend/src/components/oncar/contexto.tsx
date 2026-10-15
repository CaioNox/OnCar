"use client";

import { createContext, useContext } from "react";
import type { Sinais } from "@/lib/sinais";
import {
  podeGerenciarEstoque,
  type Movimentacao,
  type Painel,
  type Produto,
  type UsuarioSessao,
} from "@/lib/tipos";

export const ContextoDeSessao = createContext<UsuarioSessao | null>(null);

/** Usuario autenticado da sessao corrente. */
export function useUsuario(): UsuarioSessao | null {
  return useContext(ContextoDeSessao);
}

/** Atalho para telas que so fazem sentido para quem administra o estoque. */
export function useGerenciaEstoque(): boolean {
  return podeGerenciarEstoque(useUsuario());
}

export type Oficina = {
  painel: Painel | null;
  /** As 50 movimentacoes mais recentes, como a API devolve. */
  movimentacoes: Movimentacao[];
  /** Todos os produtos (ativos e inativos): precos, busca global e assistente. */
  produtos: Produto[];
  sinais: Sinais;
  /** Momento da ultima carga; as telas usam como "agora" sem ler o relogio no render. */
  atualizadoEm: Date | null;
  carregado: boolean;
  erro: string | null;
  recarregar: () => Promise<void>;
  abrirBusca: () => void;
  abrirAssistente: () => void;
};

export const ContextoDaOficina = createContext<Oficina | null>(null);

/** Dados compartilhados pelas telas autenticadas e pelo wallpaper. */
export function useOficina(): Oficina {
  const oficina = useContext(ContextoDaOficina);
  if (!oficina) throw new Error("useOficina precisa estar dentro do AppShell.");
  return oficina;
}
