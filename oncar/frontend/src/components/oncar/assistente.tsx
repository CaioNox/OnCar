"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import AI_Prompt from "@/components/kokonutui/ai-prompt";
import ShimmerText from "@/components/kokonutui/shimmer-text";
import { useOficina } from "@/components/oncar/contexto";
import { Dialogo } from "@/components/oncar/dialogo";
import { responder, SUGESTOES, type Resposta } from "@/lib/assistente";
import { cn } from "@/lib/utils";

type Mensagem = { id: number; autor: "voce" | "assistente"; texto: string; resposta?: Resposta };

// Abaixo disso o "pensando" so pisca; um respiro minimo deixa a leitura calma.
const PENSAMENTO_MINIMO_MS = 450;

/**
 * Assistente da oficina em painel lateral. Por enquanto responde com os dados
 * do estoque (previa local); a conversa fica guardada enquanto a aba estiver aberta.
 */
export function Assistente({ aberto, aoFechar }: { aberto: boolean; aoFechar: () => void }) {
  const [mensagens, setMensagens] = useState<Mensagem[]>([]);
  const [pensando, setPensando] = useState(false);
  const proximoId = useRef(0);
  const { produtos } = useOficina();

  async function perguntar(texto: string) {
    const acrescentar = (mensagem: Omit<Mensagem, "id">) => {
      proximoId.current += 1;
      const id = proximoId.current;
      setMensagens((atuais) => [...atuais, { ...mensagem, id }]);
    };

    acrescentar({ autor: "voce", texto });
    setPensando(true);
    const inicio = performance.now();
    let resposta: Resposta;
    try {
      resposta = await responder(texto, produtos);
    } catch {
      resposta = { texto: "Não consegui consultar o estoque agora. Tente de novo em instantes." };
    }
    const espera = PENSAMENTO_MINIMO_MS - (performance.now() - inicio);
    if (espera > 0) await new Promise((resolver) => setTimeout(resolver, espera));
    acrescentar({ autor: "assistente", texto: resposta.texto, resposta });
    setPensando(false);
  }

  return (
    <Dialogo
      aberto={aberto}
      aoFechar={aoFechar}
      rotulo="Assistente da oficina"
      className="fixed inset-y-0 right-0 left-auto m-0 h-dvh max-h-dvh w-[min(28rem,100vw)] max-w-none rounded-none rounded-l-2xl border-y-0 border-r-0 open:slide-in-from-right-8"
    >
      <Conversa
        mensagens={mensagens}
        pensando={pensando}
        aoPerguntar={perguntar}
        aoFechar={aoFechar}
      />
    </Dialogo>
  );
}

function Conversa({
  mensagens,
  pensando,
  aoPerguntar,
  aoFechar,
}: {
  mensagens: Mensagem[];
  pensando: boolean;
  aoPerguntar: (texto: string) => void;
  aoFechar: () => void;
}) {
  const rolagem = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const area = rolagem.current;
    if (area) area.scrollTo({ top: area.scrollHeight, behavior: "smooth" });
  }, [mensagens.length, pensando]);

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-start justify-between gap-4 border-b px-5 pt-5 pb-4">
        <div>
          <p className="rotulo">Assistente</p>
          <h2 className="fonte-display mt-1 text-2xl">Assistente da oficina</h2>
        </div>
        <button
          type="button"
          onClick={aoFechar}
          aria-label="Fechar assistente"
          className="grid size-9 place-items-center rounded-full text-muted-foreground hover:bg-accent hover:text-foreground"
        >
          <i aria-hidden="true" className="bi bi-x-lg" />
        </button>
      </header>

      <div ref={rolagem} aria-live="polite" className="flex-1 space-y-5 overflow-y-auto px-5 py-6">
        {mensagens.length === 0 && (
          <div>
            <p className="text-sm text-muted-foreground">
              Pergunte sobre peças, saldos e lançamentos. Por exemplo:
            </p>
            <Sugestoes aoEscolher={aoPerguntar} desativado={pensando} />
          </div>
        )}

        {mensagens.map((mensagem) =>
          mensagem.autor === "voce" ? (
            <p
              key={mensagem.id}
              className="ml-auto w-fit max-w-[85%] rounded-2xl rounded-br-md bg-primary px-4 py-2.5 text-sm text-primary-foreground"
            >
              {mensagem.texto}
            </p>
          ) : (
            <div key={mensagem.id} className="max-w-[92%] text-sm">
              <p className="text-foreground">{mensagem.texto}</p>
              {mensagem.resposta?.itens && (
                <ul className="mt-3 divide-y overflow-hidden rounded-xl border bg-background/40">
                  {mensagem.resposta.itens.map((item) => {
                    const conteudo = (
                      <>
                        <span className="flex items-center gap-2 font-medium">
                          {item.alerta && (
                            <span aria-label="abaixo do mínimo" className="size-2 shrink-0 rounded-full bg-sinal" />
                          )}
                          {item.titulo}
                        </span>
                        {item.detalhe && (
                          <span className="num mt-0.5 block text-xs text-muted-foreground">{item.detalhe}</span>
                        )}
                      </>
                    );
                    return (
                      <li key={item.chave}>
                        {item.href ? (
                          <Link href={item.href} onClick={aoFechar} className="block px-3.5 py-2.5 transition-colors hover:bg-accent">
                            {conteudo}
                          </Link>
                        ) : (
                          <div className="px-3.5 py-2.5">{conteudo}</div>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}
              {mensagem.resposta?.sugestoes && (
                <Sugestoes aoEscolher={aoPerguntar} desativado={pensando} />
              )}
            </div>
          ),
        )}

        {pensando && <ShimmerText text="Pensando…" className="text-sm" />}
      </div>

      <div className="border-t p-4">
        <AI_Prompt
          headerText="Pergunte sobre o estoque"
          headerAction={<span className="num">dados reais · sem IA</span>}
          placeholder="Ex.: o que está abaixo do mínimo?"
          label="Pergunta para o assistente"
          disabled={pensando}
          onSubmit={aoPerguntar}
        />
        <p className="mt-2.5 text-[0.6875rem] leading-relaxed text-muted-foreground">
          As respostas usam os dados atuais do seu estoque.
        </p>
      </div>
    </div>
  );
}

function Sugestoes({ aoEscolher, desativado }: { aoEscolher: (texto: string) => void; desativado: boolean }) {
  return (
    <div className="mt-3 flex flex-wrap gap-2">
      {SUGESTOES.map((sugestao) => (
        <button
          key={sugestao}
          type="button"
          disabled={desativado}
          onClick={() => aoEscolher(sugestao)}
          className={cn(
            "rounded-full border px-3 py-1.5 text-xs text-muted-foreground transition-colors hover:border-ok hover:text-foreground",
            "disabled:cursor-not-allowed disabled:opacity-50",
          )}
        >
          {sugestao}
        </button>
      ))}
    </div>
  );
}
