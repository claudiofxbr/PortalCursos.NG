import { test, expect } from '@playwright/test';

test.describe('PortalCursos.NG - Fluxos Principais E2E', () => {
  
  test('deve carregar a página inicial e exibir o título principal', async ({ page }) => {
    // Acessar página inicial do frontend
    await page.goto('/');
    
    // Validar título do portal ou elementos chave
    await expect(page).toHaveTitle(/PortalCursos/i);
  });

  test('deve redirecionar para login ao tentar acessar rota protegida sem autenticação', async ({ page }) => {
    // Tenta acessar a página de chamados (infraestrutura) diretamente
    await page.goto('/repairs');
    
    // Deve conter redirecionamento para auth/login
    await page.waitForURL('**/auth/login');
    await expect(page.locator('input[type="password"]')).toBeVisible();
  });
});
