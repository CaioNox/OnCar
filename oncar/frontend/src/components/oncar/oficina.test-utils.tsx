/**
 * Renderizacao com os contextos que o AppShell fornece em producao (sessao e dados
 * da oficina). Cada teste informa so o perfil e os dados que importam para ele.
 */
import { render, type RenderOptions } from "@testing-library/react";
import { vi } from "vitest";
import { ContextoDaOficina, ContextoDeSessao, type Oficina } from "@/components/oncar/contexto";
import type { Perfil, UsuarioSessao } from "@/lib/tipos";

export function usuario(perfil: Perfil = "PROPRIETARIO"): UsuarioSessao {
  return { id: 1, nome: "Usuario de teste", email: "teste@oficina.com.br", perfil, perfilDescricao: perfil };
}

export function oficina(campos: Partial<Oficina> = {}): Oficina {
  return {
    painel: null,
    movimentacoes: [],
    produtos: [],
    sinais: {},
    atualizadoEm: null,
    carregado: true,
    erro: null,
    recarregar: vi.fn(async () => undefined),
    abrirBusca: vi.fn(),
    abrirAssistente: vi.fn(),
    ...campos,
  };
}

type Contexto = { sessao?: UsuarioSessao | null; dados?: Oficina };

export function renderizarNaOficina(
  elemento: React.ReactElement,
  { sessao = usuario(), dados = oficina() }: Contexto = {},
  opcoes?: RenderOptions,
) {
  return render(
    <ContextoDeSessao.Provider value={sessao}>
      <ContextoDaOficina.Provider value={dados}>{elemento}</ContextoDaOficina.Provider>
    </ContextoDeSessao.Provider>,
    opcoes,
  );
}
