import { Movimentacoes, type Aba } from "./movimentacoes";

const ABAS: Aba[] = ["entrada", "saida", "ajuste"];

function texto(valor: string | string[] | undefined): string | undefined {
  return typeof valor === "string" && valor.trim() ? valor.trim() : undefined;
}

/**
 * Entradas, saidas, ajustes e estornos. A busca global chega aqui com `?aba=`, `?cliente=`
 * (pre-seleciona o cliente da saida) ou `?documento=` (filtra o historico).
 */
export default async function PaginaDeMovimentacoes({ searchParams }: PageProps<"/movimentacoes">) {
  const parametros = await searchParams;
  const aba = texto(parametros.aba);
  const cliente = Number(texto(parametros.cliente));

  return (
    <Movimentacoes
      abaInicial={ABAS.find((opcao) => opcao === aba) ?? null}
      clienteInicial={Number.isInteger(cliente) && cliente > 0 ? cliente : null}
      documentoInicial={texto(parametros.documento) ?? null}
    />
  );
}
