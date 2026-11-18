import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import PaginaDeLogin from "@/app/login/page";

/*
 * Dependencias externas da tela, substituidas:
 * - next/navigation: o roteador do Next so existe dentro do App Router;
 * - BeamsBackground: fundo animado em <canvas>, que o jsdom nao desenha. Vira um
 *   container simples, porque o que se testa aqui e o formulario, nao a animacao.
 * - fetch: a API do back-end.
 */
const { roteador } = vi.hoisted(() => ({ roteador: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => roteador }));
vi.mock("@/components/kokonutui/beams-background", () => ({
  default: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

type Chamada = { url: string; init?: RequestInit };
let chamadas: Chamada[];

function api(resposta: () => Promise<Response>) {
  chamadas = [];
  vi.spyOn(globalThis, "fetch").mockImplementation(async (url, init) => {
    chamadas.push({ url: String(url), init });
    // A primeira chamada de uma escrita e a preparacao do CSRF (GET /api/sessao).
    if (String(url) === "/api/sessao" && !init?.method) return new Response("{}", { status: 200 });
    return resposta();
  });
}

const json = (corpo: unknown, status: number) => new Response(JSON.stringify(corpo), { status });

async function preencher(email = "dono@oficina.com.br", senha = "oncar2026") {
  const usuario = userEvent.setup();
  await usuario.type(screen.getByLabelText("E-mail"), email);
  await usuario.type(screen.getByLabelText("Senha"), senha);
  return usuario;
}

beforeEach(() => {
  roteador.replace.mockReset();
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
});

describe("tela de login", () => {
  it("envia e-mail e senha para POST /api/sessao e vai para o painel", async () => {
    api(async () => json({ id: 1, nome: "Dono", perfil: "PROPRIETARIO" }, 200));
    render(<PaginaDeLogin />);
    const usuario = await preencher();

    await usuario.click(screen.getByRole("button", { name: "Entrar" }));

    const login = chamadas.find((chamada) => chamada.init?.method === "POST");
    expect(login?.url).toBe("/api/sessao");
    expect(JSON.parse(String(login?.init?.body))).toEqual({ email: "dono@oficina.com.br", senha: "oncar2026" });
    expect(login?.init?.credentials).toBe("include");
    expect(roteador.replace).toHaveBeenCalledWith("/painel");
  });

  it("credencial recusada mostra a mensagem do servidor e fica na tela", async () => {
    api(async () => json({ mensagem: "E-mail ou senha incorretos." }, 401));
    render(<PaginaDeLogin />);
    const usuario = await preencher();

    await usuario.click(screen.getByRole("button", { name: "Entrar" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("E-mail ou senha incorretos.");
    expect(roteador.replace).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Entrar" })).toBeEnabled();
  });

  it("servidor fora do ar vira mensagem amigavel, sem detalhes tecnicos", async () => {
    api(async () => {
      throw new TypeError("Failed to fetch");
    });
    render(<PaginaDeLogin />);
    const usuario = await preencher();

    await usuario.click(screen.getByRole("button", { name: "Entrar" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Não foi possível falar com o servidor. Tente novamente.",
    );
    expect(screen.getByRole("alert")).not.toHaveTextContent("Failed to fetch");
  });

  it("enquanto envia, o botao fica desabilitado (evita login duplo)", async () => {
    let responder: (resposta: Response) => void = () => undefined;
    api(() => new Promise((resolver) => (responder = resolver)));
    render(<PaginaDeLogin />);
    const usuario = await preencher();

    await usuario.click(screen.getByRole("button", { name: "Entrar" }));

    expect(screen.getByRole("button", { name: "Entrando…" })).toBeDisabled();
    responder(json({ id: 1 }, 200));
    expect(await screen.findByRole("button", { name: "Entrar" })).toBeEnabled();
  });

  it("um erro anterior some ao tentar de novo", async () => {
    let tentativa = 0;
    api(async () => (++tentativa === 1 ? json({ mensagem: "Conta bloqueada." }, 423) : json({ id: 1 }, 200)));
    render(<PaginaDeLogin />);
    const usuario = await preencher();

    await usuario.click(screen.getByRole("button", { name: "Entrar" }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    await usuario.click(screen.getByRole("button", { name: "Entrar" }));

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(roteador.replace).toHaveBeenCalledWith("/painel");
  });

  it("campos vazios nao chegam a chamar a API (validacao nativa: required)", async () => {
    api(async () => json({}, 200));
    render(<PaginaDeLogin />);

    await userEvent.click(screen.getByRole("button", { name: "Entrar" }));

    expect(screen.getByLabelText("E-mail")).toBeRequired();
    expect(screen.getByLabelText("Senha")).toBeRequired();
    expect(chamadas).toHaveLength(0);
  });

  it("a senha comeca oculta e o botao alterna a visibilidade", async () => {
    render(<PaginaDeLogin />);
    const senha = screen.getByLabelText("Senha");
    expect(senha).toHaveAttribute("type", "password");

    await userEvent.click(screen.getByRole("button", { name: "Mostrar senha" }));
    expect(senha).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Ocultar senha" })).toHaveAttribute("aria-pressed", "true");

    await userEvent.click(screen.getByRole("button", { name: "Ocultar senha" }));
    expect(senha).toHaveAttribute("type", "password");
  });

  it("os campos pedem ao navegador o preenchimento certo (gerenciador de senhas)", () => {
    render(<PaginaDeLogin />);

    expect(screen.getByLabelText("E-mail")).toHaveAttribute("autocomplete", "username");
    expect(screen.getByLabelText("Senha")).toHaveAttribute("autocomplete", "current-password");
  });
});
