/**
 * Formato mínimo de um erro de requisição (axios) usado pelas telas. `catch` entrega `unknown`; este utilitário o
 * estreita sem `any`. Objetos (inclusive `Error`) voltam como a mesma referência, então log e rastreio não mudam.
 */
export interface HttpErrorLike {
  response?: { status?: number; data?: { message?: string; error?: string; details?: string; hint?: string } };
  message?: string;
  silent?: boolean;
  code?: string;
}

export function asHttpError(value: unknown): HttpErrorLike {
  return typeof value === 'object' && value !== null ? (value as HttpErrorLike) : {};
}
