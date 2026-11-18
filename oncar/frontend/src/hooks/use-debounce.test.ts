import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { vi } from "vitest";
import useDebounce from "@/hooks/use-debounce";

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe("useDebounce", () => {
  it("comeca com o valor inicial, sem esperar", () => {
    const { result } = renderHook(() => useDebounce("filtro", 300));

    expect(result.current).toBe("filtro");
  });

  it("so entrega o valor novo depois do intervalo", () => {
    const { result, rerender } = renderHook(({ valor }) => useDebounce(valor, 300), {
      initialProps: { valor: "f" },
    });

    rerender({ valor: "fi" });
    act(() => vi.advanceTimersByTime(299));
    expect(result.current).toBe("f");

    act(() => vi.advanceTimersByTime(1));
    expect(result.current).toBe("fi");
  });

  it("cada tecla reinicia a espera: digitando rapido, so o ultimo valor chega (uma busca na API)", () => {
    const { result, rerender } = renderHook(({ valor }) => useDebounce(valor, 300), {
      initialProps: { valor: "" },
    });

    for (const parcial of ["f", "fi", "fil", "filt"]) {
      rerender({ valor: parcial });
      act(() => vi.advanceTimersByTime(200));
    }
    expect(result.current).toBe("");

    act(() => vi.advanceTimersByTime(100));
    expect(result.current).toBe("filt");
  });

  it("o intervalo padrao e meio segundo", () => {
    const { result, rerender } = renderHook(({ valor }) => useDebounce(valor), { initialProps: { valor: 1 } });

    rerender({ valor: 2 });
    act(() => vi.advanceTimersByTime(499));
    expect(result.current).toBe(1);
    act(() => vi.advanceTimersByTime(1));
    expect(result.current).toBe(2);
  });
});
