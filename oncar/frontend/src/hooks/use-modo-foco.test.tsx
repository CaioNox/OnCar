import { act, fireEvent, render, renderHook, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useModoFoco } from "@/hooks/use-modo-foco";

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

/** O hook confere a ociosidade a cada 500 ms; avancar o relogio dispara essas conferencias. */
const passar = (ms: number) => act(() => vi.advanceTimersByTime(ms));

function Formulario() {
  const foco = useModoFoco(8000);
  return (
    <div>
      <p data-testid="foco">{String(foco)}</p>
      <input aria-label="Descricao" />
      <input aria-label="Aceito" type="checkbox" />
      <button type="button">Salvar</button>
    </div>
  );
}

const estado = () => screen.getByTestId("foco").textContent;

describe("useModoFoco", () => {
  it("liga depois do tempo ocioso e desliga ao mexer o ponteiro", () => {
    const { result } = renderHook(() => useModoFoco(8000));
    expect(result.current).toBe(false);

    passar(8000);
    expect(result.current).toBe(true);

    act(() => {
      window.dispatchEvent(new Event("pointermove"));
    });
    expect(result.current).toBe(false);
  });

  it("qualquer movimento antes do prazo reinicia a contagem", () => {
    const { result } = renderHook(() => useModoFoco(8000));

    passar(6000);
    act(() => {
      window.dispatchEvent(new Event("wheel"));
    });
    passar(6000);

    expect(result.current).toBe(false);
  });

  it("liga enquanto um campo de digitacao tem o foco", () => {
    render(<Formulario />);

    fireEvent.focusIn(screen.getByLabelText("Descricao"));

    expect(estado()).toBe("true");
  });

  it("checkbox e botao nao contam como preencher formulario", () => {
    render(<Formulario />);

    fireEvent.focusIn(screen.getByLabelText("Aceito"));
    expect(estado()).toBe("false");
    fireEvent.focusIn(screen.getByRole("button", { name: "Salvar" }));
    expect(estado()).toBe("false");
  });

  it("sair do campo para fora do formulario desliga", () => {
    render(<Formulario />);
    const campo = screen.getByLabelText("Descricao");

    fireEvent.focusIn(campo);
    fireEvent.focusOut(campo, { relatedTarget: screen.getByRole("button") });

    expect(estado()).toBe("false");
  });

  it("desmontar limpa o intervalo e os ouvintes", () => {
    const remover = vi.spyOn(window, "removeEventListener");
    const { unmount } = renderHook(() => useModoFoco());

    unmount();

    expect(vi.getTimerCount()).toBe(0);
    expect(remover.mock.calls.map(([evento]) => evento)).toEqual(
      expect.arrayContaining(["pointermove", "pointerdown", "wheel"]),
    );
  });
});
