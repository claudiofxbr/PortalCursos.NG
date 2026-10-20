/** Papel como chega do servidor: texto ("ROLE_ADMIN") ou objeto ({ name: "ROLE_ADMIN" }). */
export type RoleLike = string | { name?: string | null };

export const roleName = (role: RoleLike): string => (typeof role === 'string' ? role : role.name ?? '');
