// Visible on every page of the hosted test environment (ADR-0014 H2); absent elsewhere.
export default function TestEnvironmentBanner() {
  return <p className="test-environment-banner" role="note">
    <strong>Ambiente de teste.</strong> Dados fictícios e sem pagamentos reais. Não informe dados pessoais verdadeiros.
  </p>;
}
