/**
 * Temas do OnCar. O padrao e o "Noturna" (classe .dark no <html>); o "Papel" e o
 * tema claro. A escolha fica no localStorage e e aplicada por um script inline no
 * layout raiz antes da primeira pintura.
 */
export type Tema = "noturna" | "papel";

export const CHAVE_DO_TEMA = "oncar-tema";
