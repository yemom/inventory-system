// Design tokens generated from the approved Stitch UI design
// Colors, spacing, radii, shadows, typography, breakpoints, dark palette (if any)
export const colors = {
  primary: "#2563EB", // example primary blue
  secondary: "#10B981",
  accent: "#F59E0B",
  background: "#F9FAFB",
  surface: "#FFFFFF",
  muted: "#6B7280",
  danger: "#EF4444",
  success: "#22C55E",
  warning: "#F59E0B",
  // Add more colors from Stitch palette as needed
};

export const spacing = {
  xs: "0.25rem",
  sm: "0.5rem",
  md: "1rem",
  lg: "1.5rem",
  xl: "2rem",
  "2xl": "3rem",
};

export const radii = {
  none: "0",
  sm: "0.125rem",
  md: "0.375rem",
  lg: "0.5rem",
  full: "9999px",
};

export const shadows = {
  sm: "0 1px 2px 0 rgba(0,0,0,0.05)",
  md: "0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -1px rgba(0,0,0,0.06)",
  lg: "0 10px 15px -3px rgba(0,0,0,0.1), 0 4px 6px -2px rgba(0,0,0,0.05)",
};

export const fontFamily = {
  sans: "Inter, system-ui, sans-serif",
  mono: '"Consolas", monospace',
};

export const fontSize = {
  xs: "0.75rem",
  sm: "0.875rem",
  base: "1rem",
  lg: "1.125rem",
  xl: "1.25rem",
  "2xl": "1.5rem",
  "3xl": "1.875rem",
  "4xl": "2.25rem",
  "5xl": "3rem",
  "6xl": "3.75rem",
};

export const breakpoints = {
  sm: "640px",
  md: "768px",
  lg: "1024px",
  xl: "1280px",
  "2xl": "1536px",
};

export const darkColors = {
  background: "#1F2937",
  surface: "#111827",
  muted: "#9CA3AF",
  // Add more dark mode colors as needed
};

export type Theme = "light" | "dark";
