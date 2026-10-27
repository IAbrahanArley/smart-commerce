-- Sprint 8, SC-041 (ADR-0018 V1): the flat categories of V30 become a tree of up to 3 levels.
-- Additive. Existing categories become roots, published, ordered by name, with a slug derived from
-- the name. The depth limit and cycles are enforced by the application while it holds every
-- category row of the tenant FOR UPDATE; the database keeps the parent in the same tenant.
ALTER TABLE catalog_category
    ADD COLUMN parent_id UUID,
    ADD COLUMN slug VARCHAR(80) COLLATE "C",
    ADD COLUMN position INTEGER NOT NULL DEFAULT 0 CHECK (position >= 0),
    ADD COLUMN published BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE catalog_category
    ADD CONSTRAINT catalog_category_parent_fk FOREIGN KEY (parent_id, tenant_id)
        REFERENCES catalog_category(id, tenant_id) ON DELETE RESTRICT,
    ADD CONSTRAINT catalog_category_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id);

-- Slug from the name: Portuguese accents folded, anything else becomes a hyphen.
UPDATE catalog_category SET slug = coalesce(nullif(rtrim(left(btrim(regexp_replace(
    translate(lower(name),
        'áàâãäåçéèêëíìîïñóòôõöúùûüýÿ',
        'aaaaaaceeeeiiiinooooouuuuyy'),
    '[^a-z0-9]+', '-', 'g'), '-'), 60), '-'), ''), 'categoria');

-- Two names that fold to the same slug in one store: the later ones get the start of their id
-- (an existing slug ending in those 8 hex characters is not a realistic name).
WITH numbered AS (
    SELECT id, row_number() OVER (PARTITION BY store_id, slug ORDER BY name_key, id) AS n
    FROM catalog_category)
UPDATE catalog_category c SET slug = c.slug || '-' || left(c.id::text, 8)
FROM numbered WHERE numbered.id = c.id AND numbered.n > 1;

WITH ordered AS (
    SELECT id, row_number() OVER (PARTITION BY store_id ORDER BY name_key, id) - 1 AS n
    FROM catalog_category)
UPDATE catalog_category c SET position = ordered.n FROM ordered WHERE ordered.id = c.id;

ALTER TABLE catalog_category
    ALTER COLUMN slug SET NOT NULL,
    ADD CONSTRAINT catalog_category_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    ADD CONSTRAINT catalog_category_slug_unique UNIQUE (store_id, slug);

-- Names are unique among siblings ("Camisetas" may exist under Feminino and under Masculino).
ALTER TABLE catalog_category DROP CONSTRAINT catalog_category_name_unique;
CREATE UNIQUE INDEX catalog_category_sibling_name_unique ON catalog_category
    (tenant_id, coalesce(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), name_key);
CREATE INDEX catalog_category_parent_idx ON catalog_category(tenant_id, parent_id);
