/**
 * Cliente da API REST do OnCar.
 *
 * A autenticacao usa o cookie de sessao emitido pelo Spring Security, entao toda
 * requisicao envia credenciais. Para as escritas, o token do CSRF e lido do cookie
 * XSRF-TOKEN e devolvido no cabecalho X-XSRF-TOKEN.
 */

export class ErroDaApi extends Error {
  readonly status: number;
  readonly detalhes: string[];

  constructor(status: number, mensagem: string, detalhes: string[] = []) {
    super(mensagem);
    this.name = "ErroDaApi";
    this.status = status;
    this.detalhes = detalhes;
  }

  get naoAutenticado(): boolean {
    return this.status === 401;
  }
}

function tokenCsrf(): string | null {
  if (typeof document === "undefined") return null;
  const cookie = document.cookie
    .split("; ")
    .find((item) => item.startsWith("XSRF-TOKEN="));
  return cookie ? decodeURIComponent(cookie.split("=").slice(1).join("=")) : null;
}

async function requisitar<T>(
  caminho: string,
  opcoes: RequestInit = {},
): Promise<T> {
  const cabecalhos = new Headers(opcoes.headers);
  if (opcoes.body) cabecalhos.set("Content-Type", "application/json");
  const token = tokenCsrf();
  if (token) cabecalhos.set("X-XSRF-TOKEN", token);

  const resposta = await fetch(`/api${caminho}`, {
    ...opcoes,
    headers: cabecalhos,
    credentials: "include",
    cache: "no-store",
  });

  if (resposta.status === 204) return undefined as T;

  const corpo = await resposta.text();
  const conteudo = corpo ? JSON.parse(corpo) : null;

  if (!resposta.ok) {
    const mensagem =
      conteudo?.mensagem ??
      (resposta.status === 401
        ? "Sua sessao expirou. Entre novamente para continuar."
        : "Nao foi possivel concluir a operacao. Tente novamente.");
    throw new ErroDaApi(resposta.status, mensagem, conteudo?.detalhes ?? []);
  }
  return conteudo as T;
}

/** Garante que o cookie XSRF-TOKEN exista antes da primeira escrita. */
async function prepararCsrf(): Promise<void> {
  if (tokenCsrf()) return;
  await fetch("/api/sessao", { credentials: "include", cache: "no-store" }).catch(
    () => undefined,
  );
}

export const api = {
  get: <T>(caminho: string) => requisitar<T>(caminho),

  async post<T>(caminho: string, corpo?: unknown): Promise<T> {
    await prepararCsrf();
    return requisitar<T>(caminho, {
      method: "POST",
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
    });
  },

  async delete<T>(caminho: string): Promise<T> {
    await prepararCsrf();
    return requisitar<T>(caminho, { method: "DELETE" });
  },
};

/** Formata numeros vindos da API (BigDecimal serializado como string). */
export function formatarNumero(valor: string | number | null, casas = 2): string {
  if (valor === null || valor === undefined) return "-";
  return new Intl.NumberFormat("pt-BR", {
    minimumFractionDigits: casas,
    maximumFractionDigits: casas,
  }).format(Number(valor));
}

export function formatarMoeda(valor: string | number | null): string {
  if (valor === null || valor === undefined) return "-";
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
  }).format(Number(valor));
}

export function formatarDataHora(valor: string): string {
  return new Date(valor).toLocaleString("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  });
}
