package com.smartcommerce.catalog.services;

import com.smartcommerce.catalog.domain.ProductValues;
import com.smartcommerce.catalog.dto.response.ProductView;
import com.smartcommerce.catalog.entities.Product;
import com.smartcommerce.catalog.exceptions.CatalogException;
import com.smartcommerce.catalog.exceptions.CatalogPersistenceException;
import com.smartcommerce.catalog.repositories.CatalogRepository;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
  private final CatalogRepository products;

  public CatalogService(CatalogRepository products) {
    this.products = products;
  }

  @Transactional
  public ProductView create(UUID actor, UUID tenant, ProductValues input) {
    return execute(
        () -> {
          UUID store = products.requireAccess(actor, tenant);
          return view(
              products.create(actor, tenant, store, UUID.randomUUID(), input, Instant.now()));
        });
  }

  @Transactional
  public List<ProductView> list(UUID actor, UUID tenant) {
    return execute(
        () -> {
          products.requireAccess(actor, tenant);
          return products.list(actor, tenant).stream().map(CatalogService::view).toList();
        });
  }

  @Transactional
  public ProductView find(UUID actor, UUID tenant, UUID product) {
    return execute(
        () -> {
          products.requireAccess(actor, tenant);
          return view(products.find(actor, tenant, product).orElseThrow(CatalogService::missing));
        });
  }

  @Transactional
  public ProductView update(
      UUID actor, UUID tenant, UUID product, ProductValues input, long version) {
    return execute(
        () -> {
          products.requireAccess(actor, tenant);
          // Resolve absence before attempting a versioned write: cross-tenant IDs always return
          // 404.
          products.find(actor, tenant, product).orElseThrow(CatalogService::missing);
          if (version < 0 || version == Long.MAX_VALUE)
            throw new IllegalArgumentException("Invalid version");
          return view(products.update(actor, tenant, product, input, version, Instant.now()));
        });
  }

  private static ProductView view(Product product) {
    return new ProductView(
        product.id(),
        product.name(),
        product.sku(),
        product.price().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
        product.cost() == null
            ? null
            : product.cost().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
        product.currency(),
        product.version());
  }

  private static <T> T execute(Supplier<T> work) {
    try {
      return work.get();
    } catch (CatalogPersistenceException exception) {
      throw switch (exception.reason()) {
        case MISSING -> missing();
        case VERSION_CONFLICT ->
            new CatalogException(
                CatalogException.Reason.VERSION_CONFLICT,
                "O produto foi alterado. Atualize a página antes de salvar novamente.");
      };
    } catch (DataIntegrityViolationException exception) {
      throw new CatalogException(
          CatalogException.Reason.SKU_CONFLICT, "Já existe um produto com esse SKU nesta loja.");
    }
  }

  static CatalogException missing() {
    return new CatalogException(CatalogException.Reason.MISSING, "Produto ou loja não encontrado.");
  }
}
