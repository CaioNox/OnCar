import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AREA_DE_TEXTO, CAMPO, Rotulo } from "@/components/oncar/campo";

describe("<Rotulo>", () => {
  it("liga o texto ao campo pelo htmlFor: o campo e encontrado pelo nome acessivel", () => {
    render(
      <>
        <Rotulo htmlFor="email">E-mail</Rotulo>
        <input id="email" className={CAMPO} />
      </>,
    );

    expect(screen.getByLabelText("E-mail")).toHaveAttribute("id", "email");
  });

  it("mostra a dica ao lado do rotulo so quando informada", () => {
    const { rerender } = render(
      <Rotulo htmlFor="obs" dica="opcional">
        Observacao
      </Rotulo>,
    );
    expect(screen.getByText("opcional")).toBeInTheDocument();

    rerender(<Rotulo htmlFor="obs">Observacao</Rotulo>);
    expect(screen.queryByText("opcional")).not.toBeInTheDocument();
  });
});

describe("classes de campo", () => {
  it("a area de texto herda o estilo do campo e marca o estado invalido", () => {
    expect(AREA_DE_TEXTO).toContain("aria-invalid:border-sinal");
    expect(AREA_DE_TEXTO).toContain("resize-y");
    expect(AREA_DE_TEXTO).not.toContain(" h-11 ");
  });
});
