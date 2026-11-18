import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useMediaQuery } from "@/hooks/use-media-query";

/** MediaQueryList controlavel: o teste muda `matches` e avisa os ouvintes. */
function midiaControlada(inicial: boolean) {
  const ouvintes = new Set<() => void>();
  const lista = {
    matches: inicial,
    addEventListener: (_: string, ouvinte: () => void) => ouvintes.add(ouvinte),
    removeEventListener: (_: string, ouvinte: () => void) => ouvintes.delete(ouvinte),
  };
  vi.spyOn(window, "matchMedia").mockImplementation(() => lista as unknown as MediaQueryList);
  return {
    mudar(valor: boolean) {
      lista.matches = valor;
      ouvintes.forEach((ouvinte) => ouvinte());
    },
    ouvintes,
  };
}

describe("useMediaQuery", () => {
  it("le a media query no cliente", () => {
    midiaControlada(true);

    expect(renderHook(() => useMediaQuery("(max-width: 639px)")).result.current).toBe(true);
  });

  it("acompanha a mudanca (girar o celular, redimensionar a janela)", () => {
    const midia = midiaControlada(false);
    const { result } = renderHook(() => useMediaQuery("(max-width: 639px)"));

    act(() => midia.mudar(true));

    expect(result.current).toBe(true);
  });

  it("desmontar remove o ouvinte (sem vazamento)", () => {
    const midia = midiaControlada(false);
    const { unmount } = renderHook(() => useMediaQuery("(max-width: 639px)"));
    expect(midia.ouvintes.size).toBe(1);

    unmount();

    expect(midia.ouvintes.size).toBe(0);
  });
});
