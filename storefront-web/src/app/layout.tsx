import type { Metadata, Viewport } from "next";
import { Manrope } from "next/font/google";
import Link from "next/link";
import "./globals.css";
import { checkoutConfigured, hostedTestEnvironment } from "@/lib/checkout-input";

// SC-044 (DESIGN.md "Vitrine padrão V1"): Manrope, downloaded at build time and served by this app,
// so no request reaches Google when a buyer opens the store.
const manrope = Manrope({ subsets: ["latin"], weight: ["400", "500", "600", "700", "800"], display: "swap", variable: "--font-manrope" });

export const metadata: Metadata = {
  title: { default: "Vitrines | Smart Commerce", template: "%s | Smart Commerce" },
  description: "Consulte os produtos publicados pela loja.",
  robots: { index: false, follow: false },
};

export const viewport: Viewport = { themeColor: "#FFFFFF" };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR" className={manrope.variable}>
      <body>
        <a className="skip-link" href="#conteudo">Ir para o conteúdo</a>
        <div className="site-content">{children}</div>
        {/* The platform bar: the store's own footer (SC-044) sits above it on store pages. */}
        <footer className="site-footer" id="sobre-a-vitrine" aria-label="Sobre esta vitrine">
          <div className="page-width footer-content">
            <div className="footer-about">
              <p>{!checkoutConfigured()
                ? "Produtos e preços publicados pela loja para você consultar. A compra por aqui ainda não está disponível."
                : hostedTestEnvironment(process.env.DEPLOYMENT_ENVIRONMENT)
                  ? "Produtos e preços publicados pela loja. Pedidos feitos aqui são de teste."
                  : "Produtos e preços publicados pela loja. O pagamento é feito no Mercado Pago."}</p>
            </div>
            <div className="platform-credit">
              <p>Loja criada com <span className="platform-name" translate="no">Smart Commerce</span></p>
              <p><Link href="/privacidade" prefetch={false}>Privacidade</Link></p>
            </div>
          </div>
        </footer>
      </body>
    </html>
  );
}
