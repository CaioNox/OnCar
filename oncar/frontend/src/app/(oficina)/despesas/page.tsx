import { mesAtual } from "@/lib/api";
import { Despesas } from "./despesas";

const MES = /^\d{4}-(0[1-9]|1[0-2])$/;

function mes(valor: string | string[] | undefined): string | undefined {
  return typeof valor === "string" && MES.test(valor) ? valor : undefined;
}

/** Despesas operacionais por mes de competencia. `?nova=1` chega da busca global com o formulario aberto. */
export default async function PaginaDeDespesas({ searchParams }: PageProps<"/despesas">) {
  const parametros = await searchParams;
  const inicio = mes(parametros.inicio) ?? mesAtual();
  const fim = mes(parametros.fim) ?? inicio;
  return <Despesas inicioInicial={inicio} fimInicial={fim < inicio ? inicio : fim} abrirFormulario={parametros.nova === "1"} />;
}
