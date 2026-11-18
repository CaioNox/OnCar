import { act, render, renderHook, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ProvedorDeToasts, useToast } from "@/components/oncar/toasts";

/** Botao de teste que dispara um aviso com o texto informado. */
function Disparador({ mensagem, tom }: { mensagem: string; tom?: "ok" | "erro" | "info" }) {
  const avisar = useToast();
  return (
    <button type="button" onClick={() => avisar(mensagem, tom)}>
      {`avisar ${mensagem}`}
    </button>
  );
}

const regiao = () => screen.getByRole("status");

afterEach(() => vi.useRealTimers());

describe("toasts", () => {
  it("fora do provedor, avisar e inofensivo (nao derruba a tela)", () => {
    const { result } = renderHook(() => useToast());

    expect(() => result.current("Salvo")).not.toThrow();
  });

  it("o aviso aparece numa regiao status/polite, lida sem interromper o usuario", async () => {
    render(
      <ProvedorDeToasts>
        <Disparador mensagem="Despesa lancada" />
      </ProvedorDeToasts>,
    );

    await userEvent.click(screen.getByRole("button", { name: "avisar Despesa lancada" }));

    expect(regiao()).toHaveAttribute("aria-live", "polite");
    expect(regiao()).toHaveTextContent("Despesa lancada");
  });

  it("mostra no maximo tres; o mais antigo sai primeiro", async () => {
    render(
      <ProvedorDeToasts>
        {["A", "B", "C", "D"].map((letra) => (
          <Disparador key={letra} mensagem={letra} />
        ))}
      </ProvedorDeToasts>,
    );
    const usuario = userEvent.setup();

    for (const letra of ["A", "B", "C", "D"]) {
      await usuario.click(screen.getByRole("button", { name: `avisar ${letra}` }));
    }

    await waitFor(() => expect(regiao().querySelectorAll("p")).toHaveLength(3));
    const textos = [...regiao().querySelectorAll("p")].map((p) => p.textContent);
    expect(textos).toEqual(["B", "C", "D"]);
  });

  it("o botao Fechar aviso remove o toast na hora", async () => {
    render(
      <ProvedorDeToasts>
        <Disparador mensagem="Produto salvo" />
      </ProvedorDeToasts>,
    );
    const usuario = userEvent.setup();
    await usuario.click(screen.getByRole("button", { name: "avisar Produto salvo" }));

    await usuario.click(screen.getByRole("button", { name: "Fechar aviso" }));

    await waitFor(() => expect(regiao()).not.toHaveTextContent("Produto salvo"));
  });

  it("some sozinho depois de 4,5 segundos", async () => {
    // So o setTimeout e falso: a animacao de saida do Motion continua no relogio real.
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] });
    render(
      <ProvedorDeToasts>
        <Disparador mensagem="Entrada registrada" />
      </ProvedorDeToasts>,
    );
    act(() => screen.getByRole("button", { name: "avisar Entrada registrada" }).click());

    act(() => vi.advanceTimersByTime(4499));
    expect(regiao()).toHaveTextContent("Entrada registrada");

    act(() => vi.advanceTimersByTime(1));
    vi.useRealTimers();
    await waitFor(() => expect(regiao()).not.toHaveTextContent("Entrada registrada"));
  });
});
