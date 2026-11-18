import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Sparkline } from "@/components/oncar/sparkline";

const linhaDo = (container: HTMLElement) =>
  container.querySelectorAll("path")[container.querySelectorAll("path").length - 1].getAttribute("d");

describe("<Sparkline>", () => {
  it("e uma imagem com descricao para o leitor de tela", () => {
    render(<Sparkline valores={[1, 2, 3]} rotulo="Capital em estoque nos ultimos lancamentos" />);

    expect(screen.getByRole("img", { name: "Capital em estoque nos ultimos lancamentos" })).toBeInTheDocument();
  });

  it("estica a serie de ponta a ponta: o menor valor embaixo e o maior em cima", () => {
    const { container } = render(<Sparkline valores={[0, 10]} rotulo="serie" area={false} />);

    // altura 32 com margem 3: minimo em y=29, maximo em y=3
    expect(linhaDo(container)).toBe("M0.00 29.00 L100.00 3.00");
  });

  it("com menos de dois valores desenha so a linha de base no meio", () => {
    const { container } = render(<Sparkline valores={[7]} rotulo="serie" area={false} />);

    expect(linhaDo(container)).toBe("M0.00 16.00 L100.00 16.00");
  });

  it("serie constante nao divide por zero e fica na base", () => {
    const { container } = render(<Sparkline valores={[5, 5, 5]} rotulo="serie" area={false} />);

    expect(linhaDo(container)).toBe("M0.00 29.00 L50.00 29.00 L100.00 29.00");
  });

  it("o marcador fica sobre o ultimo ponto", () => {
    const { container } = render(<Sparkline valores={[0, 10]} rotulo="serie" />);
    const marcador = container.querySelector("span[aria-hidden='true']") as HTMLElement;

    expect(marcador.style.left).toBe("100%");
    expect(parseFloat(marcador.style.top)).toBeCloseTo((3 / 32) * 100);
  });

  it("a area sob a curva pode ser desligada", () => {
    const comArea = render(<Sparkline valores={[1, 2]} rotulo="a" />);
    expect(comArea.container.querySelectorAll("path")).toHaveLength(2);
    comArea.unmount();

    const semArea = render(<Sparkline valores={[1, 2]} rotulo="b" area={false} />);
    expect(semArea.container.querySelectorAll("path")).toHaveLength(1);
  });
});
