import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EstadoVazio } from "@/components/oncar/estado-vazio";

describe("<EstadoVazio>", () => {
  it("mostra titulo, explicacao e acao", () => {
    render(
      <EstadoVazio ilustracao="lupa" titulo="Nada encontrado" acao={<button type="button">Limpar filtros</button>}>
        Nenhum produto combina com a busca.
      </EstadoVazio>,
    );

    expect(screen.getByText("Nada encontrado")).toBeInTheDocument();
    expect(screen.getByText("Nenhum produto combina com a busca.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Limpar filtros" })).toBeInTheDocument();
  });

  it("sem texto nem acao nao deixa paragrafos vazios", () => {
    const { container } = render(<EstadoVazio ilustracao="caixa" titulo="Sem lancamentos" />);

    expect(container.querySelectorAll("p")).toHaveLength(1);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("a ilustracao e decorativa e fica escondida do leitor de tela", () => {
    const { container } = render(<EstadoVazio ilustracao="engrenagem" titulo="Tudo certo" />);

    expect(container.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });
});
