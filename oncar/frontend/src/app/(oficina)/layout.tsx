import { AppShell } from "@/components/oncar/app-shell";

/** Telas autenticadas: o shell (e o wallpaper) persiste entre as navegacoes. */
export default function LayoutDaOficina({ children }: { children: React.ReactNode }) {
  return <AppShell>{children}</AppShell>;
}
