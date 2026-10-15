import type { Metadata, Viewport } from "next";
import { Fraunces, Geist, JetBrains_Mono } from "next/font/google";
// So a fonte de icones: o bootstrap.css nao entra, porque brigaria com o preflight do Tailwind.
import "bootstrap-icons/font/bootstrap-icons.css";
import "./globals.css";
import { Provedores } from "@/components/oncar/provedores";
import { CHAVE_DO_TEMA } from "@/lib/tema";

const fraunces = Fraunces({
  variable: "--font-fraunces",
  subsets: ["latin"],
  axes: ["opsz", "SOFT"],
});

const geist = Geist({
  variable: "--font-geist",
  subsets: ["latin"],
});

const jetbrains = JetBrains_Mono({
  variable: "--font-jetbrains",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "OnCar - Gestao de estoque para oficinas",
  description:
    "Controle de estoque e financeiro para oficinas mecanicas de pequeno e medio porte.",
};

export const viewport: Viewport = {
  themeColor: "#131c2e",
};

// O HTML sai no tema Noturna; este script roda antes da primeira pintura e troca
// para o Papel quando essa foi a escolha salva, sem piscar a tela.
const scriptDoTema = `(function(){try{if(localStorage.getItem("${CHAVE_DO_TEMA}")==="papel")document.documentElement.classList.remove("dark")}catch(e){}})()`;

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="pt-br"
      className={`dark ${fraunces.variable} ${geist.variable} ${jetbrains.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: scriptDoTema }} />
      </head>
      <body className="flex min-h-full flex-col">
        <Provedores>{children}</Provedores>
      </body>
    </html>
  );
}
