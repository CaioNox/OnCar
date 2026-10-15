import { ExtratoDoProduto } from "./extrato";

/** RF13: extrato (kardex) de um produto. */
export default async function PaginaDoProduto({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <ExtratoDoProduto id={id} />;
}
