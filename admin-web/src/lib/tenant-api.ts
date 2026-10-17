export type TenantStore = {
  id: string;
  companyName: string;
  store: { id: string; name: string; slug: string; status: "DRAFT" };
  role: "OWNER";
};
