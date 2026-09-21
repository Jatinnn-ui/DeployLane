import { useEffect, useState } from 'react';
import { useThemeStore } from '@/stores/theme-store';

export interface ChartTheme {
  primary: string;
  accent: string;
  grid: string;
  tick: string;
  tooltipBg: string;
  tooltipBorder: string;
  label: string;
}

/** Dark-theme values, also used when custom properties are unreadable (e.g. jsdom). */
const FALLBACK: ChartTheme = {
  primary: '#f5f5f5',
  accent: '#a8f000',
  grid: 'rgba(255, 255, 255, 0.1)',
  tick: '#686d72',
  tooltipBg: '#101314',
  tooltipBorder: 'rgba(255, 255, 255, 0.16)',
  label: '#9a9ea3',
};

function readChartTheme(): ChartTheme {
  if (typeof document === 'undefined' || typeof window.getComputedStyle !== 'function') {
    return FALLBACK;
  }
  const styles = window.getComputedStyle(document.documentElement);
  const read = (property: string, fallback: string) =>
    styles.getPropertyValue(property).trim() || fallback;

  return {
    primary: read('--dl-chart-primary', FALLBACK.primary),
    accent: read('--dl-chart-accent', FALLBACK.accent),
    grid: read('--dl-chart-grid', FALLBACK.grid),
    tick: read('--dl-chart-tick', FALLBACK.tick),
    tooltipBg: read('--dl-chart-tooltip-bg', FALLBACK.tooltipBg),
    tooltipBorder: read('--dl-chart-tooltip-border', FALLBACK.tooltipBorder),
    label: read('--dl-chart-label', FALLBACK.label),
  };
}

/**
 * Resolves chart colours from the active theme's custom properties.
 *
 * Recharts writes `stroke` / `fill` as SVG presentation attributes, and those cannot
 * resolve `var()` — so the values have to be read from the cascade as concrete strings
 * and handed over. Re-reads whenever the theme changes.
 */
export function useChartTheme(): ChartTheme {
  const theme = useThemeStore((state) => state.theme);
  const [values, setValues] = useState<ChartTheme>(readChartTheme);

  useEffect(() => {
    setValues(readChartTheme());
  }, [theme]);

  return values;
}
