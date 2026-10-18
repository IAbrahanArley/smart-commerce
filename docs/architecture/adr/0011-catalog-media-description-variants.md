# ADR-0011 — Fotos, descrição formatada e variações do catálogo

Status: adotado em 2026-10-17. O responsável autorizou a evolução e confirmou HTML básico seguro e SKU/preço/estoque próprios por combinação.

## Decisões

- Preservar monólito modular, camadas explícitas, JPA/repositórios especializados, Flyway e aplicações separadas. Não incluir checkout, frete, descontos, integrações ou serviços pagos.
- Produto SIMPLE ou VARIABLE, escolhido na criação e imutável neste incremento. Produtos existentes continuam simples. Não converter automaticamente saldo ou histórico em combinações; não apagar variantes com movimentos. Cadastro variável aceita até três tipos de opções e 50 combinações explícitas. Cor, tamanho e material são exemplos, sem taxonomia rígida. Cada combinação possui SKU único por tenant, preço positivo, custo opcional e estoque próprio. Opções de combinação existente são fixas; podem ser acrescentadas combinações e editados seus preços/SKU/custo.
- V9 cria catalog_variant e uma variante padrão com o mesmo UUID para cada produto existente. Preserva IDs, saldos, sequência, requestId e auditoria; apenas redireciona FKs compostas do estoque para a unidade vendável. Colunas legadas do produto permanecem para compatibilidade. Não somar estoques de opções diferentes nem movimentar diretamente o pai variável. Preço público inicial corresponde ao menor preço; detalhe permite consultar preço/disponibilidade da combinação. SKU, custo e quantidade exata continuam privados.
- Descrição opcional: até 20.000 pontos Unicode/80.000 bytes UTF-8. Backend sanitiza com jsoup 1.23.2, confirmado na fonte oficial e compatível com Java 21, sem dependências transitivas obrigatórias. Permitir somente p, br, strong, em, ul, ol e li, sem atributos. Bloquear HTML executável, links, imagens embutidas, estilos, iframes e SVG; devolver o conteúdo canônico salvo para edição. Nunca tratar sanitização apenas no browser como garantia.
- Fotos: JPEG/PNG, até 5 MiB por arquivo e seis por produto, lados de 300 a 4096 pixels e até 16 milhões de pixels. Verificar extensão, MIME declarado, formato real, bytes e dimensões antes de decodificar. Regravar em raster novo, até 2048px no maior lado, sem metadados; preservar transparência PNG e normalizar orientação EXIF JPEG. Não aceitar GIF, SVG, WebP, HEIC, vídeos ou URLs remotas neste incremento. Não fazer importação remota/SSRF. Recomendação de envio: foto real nítida, aproximadamente 1600–2048px, sem confundir recomendação com limite obrigatório.
- No máximo dois decodes simultâneos, sem fila ilimitada. Multipart limitado a 5 MiB por arquivo e 6 MiB por requisição, lazy parsing e CSRF exclusivamente por header; origem/sessão continuam obrigatórias. JSON de catálogo limitado a 256 KiB; identidade mantém 8 KiB.
- V10 persiste bytes normalizados e metadados em PostgreSQL, na mesma transação; evita arquivos órfãos e serviço externo no ambiente atual. Não guardar nome original nem usá-lo como caminho. A primeira imagem na ordem é a capa; descrição alternativa obrigatória de até 200 caracteres. Ordenação/remoção/upload validam vínculo, produto, versão e lock, inclusive limite concorrente de seis fotos.
- Bytes e metadados privados exigem proprietário. Toda consulta pública revalida publicação da loja e produto, com 404 uniforme e no-store/nosniff. A vitrine usa proxy público estrito sem Cookie/Authorization/redirects e sem otimização/cache de imagens do Next; ocultar vale para novas requisições. Nada pode apagar conteúdo já baixado do navegador do visitante.

## Interface e limites

Formulário organizado em identificação, descrição, preço/variações, imagens e publicação. Não instalar editor/UI genérico sem necessidade. Explicar os limites antes do upload, sinalizar erros e conflitos, permitir escolher capa/ordem por controles acessíveis e impedir submissões repetidas. Guardar alterações antes de editar fotos; atualização de versão não deve descartar rascunhos. Vitrine apresenta fotos reais, descrição sanitizada e seleção de combinações; continua sendo catálogo de consulta.

Persistência de imagens aumenta banco, backups e tráfego. Antes de produção, definir cotas por tenant, monitoramento, retenção e armazenamento de objetos conforme a arquitetura de deploy, sem provisionar infraestrutura nesta tarefa. Catálogo de categorias, dados fiscais, peso/dimensões para frete, associação de foto a cada variante e editor visual avançado permanecem evoluções próprias; não simular essas capacidades.

## Evidências e referências

- [Shopify: mídia de produto](https://help.shopify.com/en/manual/products/product-media/product-media-types): referência de dimensões/bytes e formatos, não um padrão universal que obrigue a copiar limites.
- [WooCommerce: produtos variáveis](https://woocommerce.com/document/variable-product/): referência de preço/estoque por combinação.
- [OWASP: upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html) e [jsoup: sanitização](https://jsoup.org/cookbook/cleaning-html/safelist-sanitizer).
- [jsoup: versão oficial](https://jsoup.org/download), consultada em 2026-10-17.

Testes exigidos: migração preservando estoque, variantes/SKUs/precisão, concorrência, acesso cruzado, HTML malicioso, arquivos inválidos/oversize/dimensões, limite de fotos, ordem/capa e ocultação pública. SEC-003 continua aberta e inalterada, sem aceite automático. Sem commit/push implícitos.
