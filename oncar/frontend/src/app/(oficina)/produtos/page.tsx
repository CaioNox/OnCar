import { ListaDeProdutos } from "./lista";

/** pesquisa de produtos. `?nivel=critico` chega do painel ja filtrado. */
export default async function PaginaDeProdutos({ searchParams }: PageProps<"/produtos">) {
  const { nivel } = await searchParams;
  return <ListaDeProdutos nivelInicial={nivel === "critico" || nivel === "atencao" ? nivel : null} />;
}
