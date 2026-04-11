import type { Metadata } from "next";
import "./globals.css";
import { AuthProvider } from "./context/AuthContext";
import { ThemeProvider } from "./context/ThemeContext";
import AuthModal from "../components/AuthModal";
import AppShell from "../components/AppShell";
import ServerWarmer from "../components/ServerWarmer";
import InfrastructureIndicator from "../components/InfrastructureIndicator";

export const metadata: Metadata = {
  title: "PortalCursos NG-02 | Pós-Graduação",
  description: "Sistema Premium de Gestão para Pós-Graduação",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR">
      <body suppressHydrationWarning={true}>
        <ThemeProvider>
          <AuthProvider>
            <ServerWarmer />
            <InfrastructureIndicator />
            <AuthModal />
            <AppShell>
              {children}
            </AppShell>
          </AuthProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
