import { renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ContextoDeSessao, useGerenciaEstoque, useOficina, useUsuario } from "@/components/oncar/contexto";
import { usuario } from "@/components/oncar/oficina.test-utils";
import type { Perfil, UsuarioSessao } from "@/lib/tipos";

function comSessao(sessao: UsuarioSessao | null) {
  return function ProvedorDeSessao({ children }: { children: React.ReactNode }) {
    return <ContextoDeSessao.Provider value={sessao}>{children}</ContextoDeSessao.Provider>;
  };
}

describe("contextos da oficina", () => {
  it("useOficina fora do AppShell falha com mensagem clara, em vez de ler dados nulos", () => {
    expect(() => renderHook(() => useOficina())).toThrow("useOficina precisa estar dentro do AppShell.");
  });

  it("sem sessao, useUsuario devolve null", () => {
    expect(renderHook(() => useUsuario()).result.current).toBeNull();
  });

  it.each<[Perfil, boolean]>([
    ["PROPRIETARIO", true],
    ["GERENTE_ESTOQUE", true],
    ["ATENDENTE", false],
    ["MECANICO", false],
  ])("useGerenciaEstoque para %s e %s", (perfil, esperado) => {
    const { result } = renderHook(() => useGerenciaEstoque(), { wrapper: comSessao(usuario(perfil)) });

    expect(result.current).toBe(esperado);
  });
});
