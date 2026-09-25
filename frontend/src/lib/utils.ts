import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * Format money safely.
 */
export function formatCurrency(
  value: number | string | null | undefined,
  currency = 'ETB'
): string {
  const amount = Number(value);

  if (!Number.isFinite(amount)) {
    return new Intl.NumberFormat('en-ET', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
    }).format(0);
  }

  return new Intl.NumberFormat('en-ET', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(amount);
}

/**
 * Convert a value into a valid Date.
 *
 * Returns null instead of creating an Invalid Date.
 */
function toValidDate(
  value: string | Date | null | undefined
): Date | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }

  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value;
  }

  const date = new Date(value);

  return Number.isNaN(date.getTime()) ? null : date;
}

/**
 * Format date safely.
 *
 * Never throws "Invalid time value".
 */
export function formatDate(
  date: string | Date | null | undefined
): string {
  const validDate = toValidDate(date);

  if (!validDate) {
    return '—';
  }

  return new Intl.DateTimeFormat('en-ET', {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
  }).format(validDate);
}

/**
 * Format date + time safely.
 */
export function formatDateTime(
  date: string | Date | null | undefined
): string {
  const validDate = toValidDate(date);

  if (!validDate) {
    return '—';
  }

  return new Intl.DateTimeFormat('en-ET', {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(validDate);
}

export function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function getInitials(
  name: string | null | undefined
): string {
  if (!name?.trim()) {
    return '—';
  }

  return name
    .trim()
    .split(/\s+/)
    .map((n) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);
}

export function slugify(
  text: string | null | undefined
): string {
  if (!text) {
    return '';
  }

  return text
    .toLowerCase()
    .trim()
    .replace(/\s+/g, '-')
    .replace(/[^\w-]+/g, '');
}