import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Chip } from "@/components/oncar/chip";

describe("<Chip>", () => {
  it("expoe o estado ligado/desligado em aria-pressed", () => {
    const { rerender } = render(
      <Chip ativo={false} aoAlternar={() => undefined}>
        Somente ativos
      </Chip>,
    );
    expect(screen.getByRole("button", { name: "Somente ativos" })).toHaveAttribute("aria-pressed", "false");

    rerender(
      <Chip ativo aoAlternar={() => undefined}>
        Somente ativos
      </Chip>,
    );
    expect(screen.getByRole("button", { name: "Somente ativos" })).toHaveAttribute("aria-pressed", "true");
  });

  it("clique e teclado (Enter e Espaco) alternam o filtro", async () => {
    const aoAlternar = vi.fn();
    render(
      <Chip ativo={false} aoAlternar={aoAlternar}>
        Repor
      </Chip>,
    );
    const usuario = userEvent.setup();

    await usuario.click(screen.getByRole("button", { name: "Repor" }));
    await usuario.keyboard("{Enter}");
    await usuario.keyboard(" ");

    expect(aoAlternar).toHaveBeenCalledTimes(3);
  });

  it("e type=button: dentro de um formulario nao o envia", () => {
    render(
      <Chip ativo={false} aoAlternar={() => undefined}>
        Filtro
      </Chip>,
    );

    expect(screen.getByRole("button")).toHaveAttribute("type", "button");
  });
});
