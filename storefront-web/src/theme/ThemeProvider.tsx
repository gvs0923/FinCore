import { useEffect, useState, type ReactNode } from "react";
import {
  ThemeContext,
  THEME_STORAGE_KEY,
  initialTheme,
  type Theme,
} from "./theme";

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(initialTheme);

  useEffect(() => {
    document.documentElement.classList.toggle("dark", theme === "dark");
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  }, [theme]);

  return (
    <ThemeContext.Provider
      value={{
        theme,
        toggle: () => setTheme((t) => (t === "dark" ? "light" : "dark")),
      }}
    >
      {children}
    </ThemeContext.Provider>
  );
}
