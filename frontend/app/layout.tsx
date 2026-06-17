import type { Metadata } from "next";
import "./globals.css";
import { AuthProvider } from "./context/AuthContext";
import { ThemeProvider } from "./context/ThemeContext";
import AuthModal from "../components/AuthModal";
import AppShell from "../components/AppShell";
import ServerWarmer from "../components/ServerWarmer";
import ConnectivityGuard from "../components/ConnectivityGuard";
import SecurityGate from "../components/SecurityGate";

export const metadata: Metadata = {
  title: "PortalCursos OMEGA-SUPREME",
  description: "Sistema de Alta Resiliência Institucional",
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
            <SecurityGate>
              <ConnectivityGuard>
                <AuthModal />
                <AppShell>
                  {children}
                </AppShell>
              </ConnectivityGuard>
            </SecurityGate>
          </AuthProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
