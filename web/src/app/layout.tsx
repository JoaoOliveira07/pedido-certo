import type { Metadata, Viewport } from "next";
import "@fontsource-variable/archivo/wdth.css";
import "./globals.css";

export const metadata: Metadata = {
  title: "Pedido Certo",
  description:
    "Demonstração ao vivo de pedidos que chegam ao ERP exatamente uma vez: Idempotency-Key, outbox transacional e consumidor idempotente.",
  metadataBase: new URL("https://pedidos.jopes.com.br"),
  openGraph: { title: "Pedido Certo", locale: "pt_BR", type: "website" },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#edf0f3" },
    { media: "(prefers-color-scheme: dark)", color: "#0e1726" },
  ],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
