/**
 * Preparacao comum dos testes. So entra aqui o que o jsdom nao implementa e os
 * componentes usam; cada lacuna esta explicada para nao virar "magica".
 */
import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Sem `globals: true` o Testing Library nao desmonta sozinho entre os testes.
afterEach(() => cleanup());

if (typeof window !== "undefined") {
  // matchMedia: usado pelo seletor de tema, pela lista de produtos (visao inicial) e
  // pelo Motion (prefers-reduced-motion). Padrao: nenhuma media query casa.
  // Um teste que precise de outra resposta substitui com vi.stubGlobal / vi.spyOn.
  if (!window.matchMedia) {
    window.matchMedia = (consulta: string) =>
      ({
        matches: false,
        media: consulta,
        onchange: null,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        addListener: () => undefined,
        removeListener: () => undefined,
        dispatchEvent: () => false,
      }) as MediaQueryList;
  }

  // <dialog>: o jsdom cria o elemento mas nao abre como modal. O polyfill imita o
  // navegador no que o componente Dialogo depende: o atributo `open` e o evento
  // "close" disparado ao fechar (e o que o Esc faz de verdade).
  const dialogo = window.HTMLDialogElement?.prototype;
  if (dialogo && typeof dialogo.showModal !== "function") {
    dialogo.showModal = function showModal(this: HTMLDialogElement) {
      this.setAttribute("open", "");
    };
  }
  if (dialogo && typeof dialogo.close !== "function") {
    dialogo.close = function close(this: HTMLDialogElement) {
      if (!this.hasAttribute("open")) return;
      this.removeAttribute("open");
      this.dispatchEvent(new Event("close"));
    };
  }

  // scrollTo em elementos: o assistente rola a conversa ate a ultima mensagem.
  if (!Element.prototype.scrollTo) {
    Element.prototype.scrollTo = () => undefined;
  }
}
