// @vitest-environment node
/**
 * Matriz de permissoes da interface. Ela so esconde botoes e telas: quem garante o
 * acesso e o back-end (SecurityConfig + PermissoesApi). Mesmo assim precisa bater com
 * ele, senao a tela oferece uma acao que a API recusa com 403 (ou esconde uma permitida).
 */
import { describe, expect, it } from "vitest";
import {
  podeEmitirRelatorios,
  podeGerenciarEstoque,
  podeRegistrarSaida,
  podeVerFinanceiro,
  type Perfil,
  type UsuarioSessao,
} from "@/lib/tipos";

const sessao = (perfil: Perfil): UsuarioSessao => ({
  id: 1,
  nome: "x",
  email: "x@oficina.com.br",
  perfil,
  perfilDescricao: perfil,
});

describe("permissoes por perfil", () => {
  it.each<[Perfil, { estoque: boolean; saida: boolean; financeiro: boolean; relatorios: boolean }]>([
    ["PROPRIETARIO", { estoque: true, saida: true, financeiro: true, relatorios: true }],
    ["GERENTE_ESTOQUE", { estoque: true, saida: true, financeiro: false, relatorios: true }],
    ["ATENDENTE", { estoque: false, saida: true, financeiro: false, relatorios: false }],
    ["MECANICO", { estoque: false, saida: false, financeiro: false, relatorios: false }],
  ])("%s", (perfil, esperado) => {
    const usuario = sessao(perfil);

    expect({
      estoque: podeGerenciarEstoque(usuario),
      saida: podeRegistrarSaida(usuario),
      financeiro: podeVerFinanceiro(usuario),
      relatorios: podeEmitirRelatorios(usuario),
    }).toEqual(esperado);
  });

  it("sem sessao nada e liberado", () => {
    expect([
      podeGerenciarEstoque(null),
      podeRegistrarSaida(null),
      podeVerFinanceiro(null),
      podeEmitirRelatorios(null),
    ]).toEqual([false, false, false, false]);
  });
});
