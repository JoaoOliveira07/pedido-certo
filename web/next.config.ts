import type { NextConfig } from "next";

// Exportação estática: o painel é 100% client-side e conversa com a API por HTTP + SSE.
// Assim o mesmo build serve na Vercel ou em qualquer servidor de arquivos.
const nextConfig: NextConfig = {
  output: "export",
  reactStrictMode: true,
  poweredByHeader: false,
};

export default nextConfig;
