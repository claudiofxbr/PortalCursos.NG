"use client";

import React, { useEffect, useRef, useState } from "react";
import { useTheme } from "../app/context/ThemeContext";

const ThemeSelector: React.FC = () => {
  const { theme, setTheme } = useTheme();
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const themes = [
    { id: "light", name: "Claro", color: "#f4f7f6", textColor: "#1e293b", label: "Modo Claro" },
    { id: "dark", name: "Escuro", color: "#0f172a", textColor: "#f8fafc", label: "Modo Escuro" },
    { id: "blue", name: "Azul", color: "#0d123d", textColor: "#f1f5f9", label: "Azul Escuro" },
  ];

  // Fecha ao clicar fora do componente (padrão dropdown por clique)
  useEffect(() => {
    if (!isOpen) return;
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [isOpen]);

  const handleSelectTheme = (id: string) => {
    setTheme(id as any);
    setIsOpen(false);
  };

  return (
    <div
      className="theme-curtain-container"
      ref={containerRef}
    >
      <button
        type="button"
        className="theme-main-btn"
        onClick={() => setIsOpen((prev) => !prev)}
      >
        <span className="icon">🎨</span>
        <span className="text">Temas</span>
      </button>

      <div className={`theme-options-curtain ${isOpen ? "open" : ""}`}>
        {themes.map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => handleSelectTheme(t.id)}
            className={`theme-opt-btn ${theme === t.id ? "active" : ""}`}
            style={{ 
              backgroundColor: t.color, 
              color: t.textColor,
              border: theme === t.id ? `2px solid var(--secondary-color)` : "1px solid rgba(255,255,255,0.1)"
            }}
          >
            <div className="dot" style={{ backgroundColor: t.color }}></div>
            {t.name}
          </button>
        ))}
      </div>

      <style dangerouslySetInnerHTML={{ __html: `
        .theme-curtain-container {
          position: relative;
          z-index: 1000;
        }

        .theme-main-btn {
          display: flex;
          align-items: center;
          gap: 8px;
          background: var(--glass-bg);
          backdrop-filter: blur(8px);
          border: 1px solid var(--glass-border);
          color: var(--text-primary);
          padding: 8px 16px;
          border-radius: 20px;
          font-size: 0.9rem;
          transition: var(--transition-smooth);
        }

        .theme-main-btn:hover {
          background: var(--secondary-color);
          color: var(--white);
          transform: translateY(-2px);
        }

        .theme-options-curtain {
          position: absolute;
          top: 100%;
          right: 0;
          margin-top: 8px;
          display: flex;
          flex-direction: column;
          gap: 6px;
          padding: 8px;
          background: var(--glass-bg);
          backdrop-filter: blur(12px);
          border: 1px solid var(--glass-border);
          border-radius: 12px;
          box-shadow: var(--glass-shadow);
          opacity: 0;
          transform: translateY(-10px) scale(0.95);
          pointer-events: none;
          transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        }

        .theme-options-curtain.open {
          opacity: 1;
          transform: translateY(0) scale(1);
          pointer-events: all;
        }

        .theme-opt-btn {
          display: flex;
          align-items: center;
          gap: 10px;
          width: 140px;
          padding: 10px 15px;
          border-radius: 8px;
          font-size: 0.85rem;
          font-weight: 600;
          text-align: left;
          transition: transform 0.2s;
        }

        .theme-opt-btn:hover {
          transform: scale(1.05);
        }

        .theme-opt-btn.active {
          box-shadow: 0 0 10px var(--secondary-color);
        }

        .dot {
          width: 12px;
          height: 12px;
          border-radius: 50%;
          border: 1px solid rgba(255,255,255,0.3);
        }
      `}} />
    </div>
  );
};

export default ThemeSelector;
