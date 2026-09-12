// tailwind.config.js
/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{js,ts,jsx,tsx}",
    "./app/**/*.{js,ts,jsx,tsx}" // Next.js app router files
  ],
  theme: {
    extend: {
      colors: require('./src/lib/design/tokens').colors,
      spacing: require('./src/lib/design/tokens').spacing,
      borderRadius: require('./src/lib/design/tokens').radii,
      boxShadow: require('./src/lib/design/tokens').shadows,
      fontFamily: require('./src/lib/design/tokens').fontFamily,
      fontSize: require('./src/lib/design/tokens').fontSize,
    },
  },
  darkMode: "class",
  plugins: [],
};
