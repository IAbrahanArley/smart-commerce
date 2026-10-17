// Credentials remain in HttpOnly cookies. No browser storage or secret logging.
export async function authRequest(path: string, body?: object): Promise<Response> {
  return apiRequest(`/api/auth/${path}`, { method: "POST", body });
}

export async function apiRequest(path: string, options: { method?: "GET" | "POST" | "PUT" | "DELETE"; body?: object | FormData; signal?: AbortSignal } = {}): Promise<Response> {
  const method = options.method ?? "GET";
  if (method === "GET") return fetch(path, {
    credentials: "same-origin", cache: "no-store", signal: options.signal,
  });
  const csrfResponse = await fetch("/api/auth/csrf", {
    credentials: "same-origin", cache: "no-store", signal: options.signal,
  });
  if (!csrfResponse.ok) throw new Error("Não foi possível conectar. Tente novamente.");
  const csrf: { token: string; headerName: string } = await csrfResponse.json();
  const multipart = options.body instanceof FormData;
  return fetch(path, {
    method, credentials: "same-origin", cache: "no-store",
    headers: multipart ? { [csrf.headerName]: csrf.token } : { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
    body: multipart ? options.body as FormData : options.body ? JSON.stringify(options.body) : undefined, signal: options.signal,
  });
}

export function requestError(status: number): string {
  if (status === 429) return "Muitas tentativas. Aguarde alguns minutos antes de tentar novamente.";
  if (status === 503) return "Serviço temporariamente ocupado. Aguarde e tente novamente.";
  if (status === 403) return "Não foi possível validar a solicitação. Tente novamente.";
  return "Não foi possível concluir. Confira os dados e tente novamente.";
}
