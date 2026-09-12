import React from 'react';
import { cn } from '@/lib/utils';

interface CardProps { children: React.ReactNode; className?: string; }
interface CardHeaderProps { children: React.ReactNode; className?: string; }
interface CardContentProps { children: React.ReactNode; className?: string; }
interface CardFooterProps { children: React.ReactNode; className?: string; }

export function Card({ children, className }: CardProps) {
  return <div className={cn('bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm', className)}>{children}</div>;
}
export function CardHeader({ children, className }: CardHeaderProps) {
  return <div className={cn('px-6 py-4 border-b border-gray-200 dark:border-gray-700', className)}>{children}</div>;
}
export function CardContent({ children, className }: CardContentProps) {
  return <div className={cn('px-6 py-4', className)}>{children}</div>;
}
export function CardFooter({ children, className }: CardFooterProps) {
  return <div className={cn('px-6 py-4 border-t border-gray-200 dark:border-gray-700', className)}>{children}</div>;
}
