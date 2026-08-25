import { create } from 'zustand';

export type Theme = 'dark' | 'light';

/**
 * Shared with the inline bootstrap script in index.html. Both must agree, or the
 * pre-paint theme and the React theme disagree and the page flashes.
 */
export const THEME_STORAGE_KEY = 'deploylane.theme';
const DEFAULT_THEME: Theme = 'dark';

interface ThemeState {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  toggleTheme: () => void;
}

function isTheme(value: unknown): value is Theme {
  return value === 'dark' || value === 'light';
}

/** Reads whatever the bootstrap script already committed to the DOM. */
function readInitialTheme(): Theme {
  if (typeof document === 'undefined') {
    return DEFAULT_THEME;
  }
  const fromDom = document.documentElement.dataset.theme;
  if (isTheme(fromDom)) {
    return fromDom;
  }
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    if (isTheme(stored)) {
      return stored;
    }
  } catch {
    // Private mode / storage disabled — fall through to the default.
  }
  return DEFAULT_THEME;
}

function applyTheme(theme: Theme) {
  if (typeof document === 'undefined') return;

  document.documentElement.dataset.theme = theme;

  // Keeps native form controls, scrollbars and the browser chrome in step.
  const meta = document.querySelector('meta[name="color-scheme"]');
  if (meta) {
    meta.setAttribute('content', theme);
  }

  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // Preference simply will not persist; the UI still switches.
  }
}

/**
 * Theme selection.
 *
 * Deliberately not derived from `prefers-color-scheme`: the choice is explicit and
 * sticky, because a deployment dashboard is often read in a fixed environment and a
 * theme that changes itself at sunset is a surprise, not a feature.
 */
export const useThemeStore = create<ThemeState>((set, get) => ({
  theme: readInitialTheme(),

  setTheme: (theme) => {
    applyTheme(theme);
    set({ theme });
  },

  toggleTheme: () => {
    const next: Theme = get().theme === 'dark' ? 'light' : 'dark';
    applyTheme(next);
    set({ theme: next });
  },
}));
