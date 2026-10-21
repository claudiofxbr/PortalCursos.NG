"use client";

import React, { createContext, useContext, useEffect, useState } from "react";

export type Theme = "light" | "dark" | "blue";

interface ThemeContextType {
  theme: Theme;
  setTheme: (theme: Theme) => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = useState<Theme>("light");
  const [mounted, setMounted] = useState(false);

  // Inicializar o tema a partir do localStorage no carregamento
  useEffect(() => {
    const savedTheme = localStorage.getItem("app-theme") as Theme;
    if (savedTheme && ["light", "dark", "blue"].includes(savedTheme)) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- lê API do navegador (não existe no servidor) depois da montagem
      setThemeState(savedTheme);
    }
    setMounted(true);
  }, []);

  // Atualizar a classe do body sempre que o tema mudar
  useEffect(() => {
    if (!mounted) return;

    const body = document.body;
    body.classList.remove("light-theme", "dark-theme", "blue-theme");
    body.classList.add(`${theme}-theme`);
    
    // Salvar preferência
    localStorage.setItem("app-theme", theme);
  }, [theme, mounted]);

  const setTheme = (newTheme: Theme) => {
    setThemeState(newTheme);
  };

  return (
    <ThemeContext.Provider value={{ theme, setTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (context === undefined) {
    throw new Error("useTheme deve ser usado dentro de um ThemeProvider");
  }
  return context;
}
