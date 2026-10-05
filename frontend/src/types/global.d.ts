/**
 * Global module declarations for static asset imports in frontend source.
 *
 * `next-env.d.ts` (which Next.js regenerates) normally supplies these, but it
 * is git-ignored and is absent in a fresh checkout. Without declarations,
 * `tsc --noEmit` fails with:
 *
 *   error TS2882: Cannot find module or type declarations for side-effect
 *   import of './globals.css'.
 *
 * This committed file makes the type check self-contained.
 */
declare module '*.css';
declare module '*.module.css';
declare module '*.module.scss';
declare module '*.scss';
declare module '*.module.sass';
declare module '*.sass';
declare module '*.less';
declare module '*.module.less';
declare module '*.svg';
declare module '*.png';
declare module '*.jpg';
declare module '*.jpeg';
declare module '*.gif';
declare module '*.webp';
declare module '*.avif';
declare module '*.ico';
declare module '*.bmp';
declare module '*.woff';
declare module '*.woff2';
declare module '*.ttf';
declare module '*.eot';
declare module '*.mp4';
declare module '*.webm';
declare module '*.mp3';
declare module '*.wav';
declare module '*.pdf';