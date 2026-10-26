import StatusBadge from "@/components/status-badge";

export const demoProducts = [
  { name: "Camiseta essencial", sku: "CAM-001", price: "R$ 79,90", stock: "24 unidades", options: "3 tamanhos", active: true },
  { name: "Tênis casual", sku: "TEN-002", price: "R$ 189,90", stock: "12 unidades", options: "4 tamanhos", active: true },
  { name: "Bolsa de algodão", sku: "BOL-003", price: "R$ 49,90", stock: "8 unidades", options: "Produto simples", active: true },
  { name: "Boné clássico", sku: "BON-004", price: "R$ 59,90", stock: "Sem estoque", options: "Produto simples", active: false },
];

export default function DemoProducts({ availableOnly = false }: { availableOnly?: boolean }) {
  return <div className="demo-table-wrap" data-ui>
    <table className="demo-table"><caption>Exemplo de catálogo · dados fictícios</caption>
      <thead><tr><th scope="col">Produto</th><th scope="col">Preço</th><th scope="col">Estoque</th><th scope="col">Vitrine</th></tr></thead>
      <tbody>{demoProducts.filter(item => !availableOnly || item.active).map(item => <tr key={item.sku}>
        <th scope="row">{item.name}<small>{item.sku} · {item.options}</small></th>
        <td data-label="Preço">{item.price}</td><td data-label="Estoque">{item.stock}</td>
        <td data-label="Vitrine"><StatusBadge tone={item.active ? "positive" : "neutral"}>{item.active ? "Visível" : "Oculto"}</StatusBadge></td>
      </tr>)}</tbody>
    </table>
  </div>;
}
