// Route ids reach API paths; only canonical UUIDs pass (".." or other segments never do).
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function validRouteId(value: string | undefined): value is string {
  return typeof value === "string" && UUID.test(value);
}
