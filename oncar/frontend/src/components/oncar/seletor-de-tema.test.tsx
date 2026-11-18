import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { alternarTema, SeletorDeTema } from "@/components/oncar/seletor-de-tema";
import { CHAVE_DO_TEMA } from "@/lib/tema";

const html = document.documentElement;

beforeEach(() => {
  // O layout raiz aplica "dark" antes da primeira pintura; o padrao e o tema Noturna.
  html.classList.add("dark");
  localStorage.clear();
});

afterEach(() => {
  html.classList.remove("dark");
  Reflect.deleteProperty(document, "startViewTransition");
});

describe("alternarTema", () => {
  it("troca a classe do <html> e guarda a escolha", () => {
    alternarTema();
    expect(html).not.toHaveClass("dark");
    expect(localStorage.getItem(CHAVE_DO_TEMA)).toBe("papel");

    alternarTema();
    expect(html).toHaveClass("dark");
    expect(localStorage.getItem(CHAVE_DO_TEMA)).toBe("noturna");
  });

  it("sem localStorage (modo privado) o tema troca do mesmo jeito", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("QuotaExceededError");
    });

    expect(() => alternarTema()).not.toThrow();
    expect(html).not.toHaveClass("dark");
  });

  it("com View Transitions disponivel, a troca acontece dentro da transicao", () => {
    const startViewTransition = vi.fn((trocar: () => void) => {
      trocar();
      return { ready: Promise.resolve() };
    });
    Object.defineProperty(document, "startViewTransition", { value: startViewTransition, configurable: true });

    alternarTema();

    expect(startViewTransition).toHaveBeenCalledTimes(1);
    expect(html).not.toHaveClass("dark");
  });

  it("quem pede menos movimento nao ganha a transicao animada", () => {
    const startViewTransition = vi.fn();
    Object.defineProperty(document, "startViewTransition", { value: startViewTransition, configurable: true });
    vi.spyOn(window, "matchMedia").mockReturnValue({ matches: true } as MediaQueryList);

    alternarTema();

    expect(startViewTransition).not.toHaveBeenCalled();
    expect(html).not.toHaveClass("dark");
  });

  it("transicao abortada nao vira erro nao tratado", async () => {
    const recusa = Promise.reject(new DOMException("Transition was skipped", "AbortError"));
    Object.defineProperty(document, "startViewTransition", {
      value: (trocar: () => void) => {
        trocar();
        return { ready: recusa };
      },
      configurable: true,
    });

    alternarTema();

    await expect(recusa.catch(() => "tratada")).resolves.toBe("tratada");
  });
});

describe("<SeletorDeTema>", () => {
  it("o rotulo diz para qual tema o botao leva e acompanha a troca", async () => {
    render(<SeletorDeTema />);
    expect(screen.getByRole("button", { name: "Mudar para o tema Papel (claro)" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button"));

    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Mudar para o tema Oficina Noturna (escuro)" })).toBeInTheDocument(),
    );
  });

  it("reage tambem a mudancas de tema feitas fora dele (outro botao, outro script)", async () => {
    render(<SeletorDeTema />);

    html.classList.remove("dark");

    await waitFor(() => expect(screen.getByRole("button")).toHaveAttribute("title", "Tema: Papel"));
  });
});
