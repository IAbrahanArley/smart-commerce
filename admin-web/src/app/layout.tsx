import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Inter } from "next/font/google";
import "./globals.css";
import TestEnvironmentBanner from "@/components/test-environment-banner";
import { hostedTestEnvironment } from "@/lib/deployment";

// Dashtail typography, self-hosted by next/font at build time (no request to Google from the browser).
const inter = Inter({ subsets: ["latin"], variable: "--font-inter", display: "swap" });

export const metadata: Metadata = {
  title: "Smart Commerce | Administração",
  description: "Ambiente administrativo do Smart Commerce.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt-BR" className={inter.variable}>
      <body>
        <a className="skip-link" href="#conteudo">Pular para o conteúdo</a>
        {hostedTestEnvironment(process.env.DEPLOYMENT_ENVIRONMENT) ? <TestEnvironmentBanner /> : null}
        {children}
      </body>
    </html>
  );
}
