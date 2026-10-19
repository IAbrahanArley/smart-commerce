import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import TestEnvironmentBanner from "@/components/test-environment-banner";
import { hostedTestEnvironment } from "@/lib/deployment";

export const metadata: Metadata = {
  title: "Smart Commerce | Administração",
  description: "Ambiente administrativo do Smart Commerce.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>
        <a className="skip-link" href="#conteudo">Pular para o conteúdo</a>
        {hostedTestEnvironment(process.env.DEPLOYMENT_ENVIRONMENT) ? <TestEnvironmentBanner /> : null}
        {children}
      </body>
    </html>
  );
}
