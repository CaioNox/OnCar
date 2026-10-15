import { useEffect, useRef, useState } from "react";

// Campos de digitacao; checkbox, radio, range e botoes nao contam como "preencher formulario".
const CAMPOS = [
  "input:not([type])",
  ...["text", "search", "email", "password", "number", "tel", "url", "date", "datetime-local", "month", "time", "week"].map(
    (tipo) => `input[type=${tipo}]`,
  ),
  "textarea",
  "select",
  "[contenteditable='true']",
  "[role='textbox']",
  "[role='combobox']",
].join(", ");

function campoTemFoco(): boolean {
  const ativo = document.activeElement;
  return ativo instanceof Element && ativo.matches(CAMPOS);
}

/**
 * Modo foco (descanso dos olhos): liga depois de `ociosoMs` sem mexer o ponteiro
 * ou enquanto um campo de formulario tem o foco. Mexer o ponteiro desliga, a nao
 * ser que o usuario ainda esteja com o cursor de texto num campo.
 */
export function useModoFoco(ociosoMs = 8000): boolean {
  const [ocioso, setOcioso] = useState(false);
  const [campoFocado, setCampoFocado] = useState(false);
  const ultimaAtividade = useRef(0);
  const oci = useRef(false);

  useEffect(() => {
    ultimaAtividade.current = performance.now();

    // O ponteiro dispara a 60-120 Hz; aqui so marcamos a hora e acordamos se preciso.
    const atividade = () => {
      ultimaAtividade.current = performance.now();
      if (oci.current) {
        oci.current = false;
        setOcioso(false);
      }
    };

    const verificar = window.setInterval(() => {
      if (!oci.current && performance.now() - ultimaAtividade.current >= ociosoMs) {
        oci.current = true;
        setOcioso(true);
      }
      // Um campo removido da tela nem sempre dispara focusout; o relogio confere.
      setCampoFocado(campoTemFoco());
    }, 500);

    const aoFocar = (evento: FocusEvent) => {
      if (evento.target instanceof Element && evento.target.matches(CAMPOS)) setCampoFocado(true);
    };
    const aoDesfocar = (evento: FocusEvent) => {
      const proximo = evento.relatedTarget;
      setCampoFocado(proximo instanceof Element && proximo.matches(CAMPOS));
    };

    window.addEventListener("pointermove", atividade, { passive: true });
    window.addEventListener("pointerdown", atividade, { passive: true });
    window.addEventListener("wheel", atividade, { passive: true });
    document.addEventListener("focusin", aoFocar);
    document.addEventListener("focusout", aoDesfocar);
    return () => {
      window.clearInterval(verificar);
      window.removeEventListener("pointermove", atividade);
      window.removeEventListener("pointerdown", atividade);
      window.removeEventListener("wheel", atividade);
      document.removeEventListener("focusin", aoFocar);
      document.removeEventListener("focusout", aoDesfocar);
    };
  }, [ociosoMs]);

  return ocioso || campoFocado;
}
