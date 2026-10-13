import { createContext, useContext } from "react";

export type Theme = "light" | "dark";

export const THEME_STORAGE_KEY = "fincore-theme";

export const ThemeContext = createContext<{
  theme: Theme;
  toggle: () => void;
}>({ theme: "light", toggle: () => {} });

export function initialTheme(): Theme {
  const stored = localStorage.getItem(THEME_STORAGE_KEY);
  if (stored === "light" || stored === "dark") return stored;
  return window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
}

export function useTheme() {
  return useContext(ThemeContext);
}
