import { mesAtual } from "@/lib/api";
import { Relatorios, type TipoDeRelatorio } from "./relatorios";

const TIPOS: TipoDeRelatorio[] = ["entradas", "saidas", "gastos", "posicao"];
const MES = /^\d{4}-(0[1-9]|1[0-2])$/;

function texto(valor: string | string[] | undefined): string | undefined {
  return typeof valor === "string" && valor.trim() ? valor.trim() : undefined;
}

/** Relatorios mensais. `?tipo=` escolhe a aba; `?inicio=` e `?fim=` (AAAA-MM) o periodo de competencia. */
export default async function PaginaDeRelatorios({ searchParams }: PageProps<"/relatorios">) {
  const parametros = await searchParams;
  const tipo = TIPOS.find((opcao) => opcao === texto(parametros.tipo)) ?? "entradas";
  const inicio = MES.test(texto(parametros.inicio) ?? "") ? texto(parametros.inicio)! : mesAtual();
  const fim = MES.test(texto(parametros.fim) ?? "") ? texto(parametros.fim)! : inicio;
  return <Relatorios tipoInicial={tipo} inicioInicial={inicio} fimInicial={fim < inicio ? inicio : fim} />;
}
