'use client';
import React, { useId } from 'react';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
  className?: string;
  size?: 'sm' | 'md' | 'lg' | 'xl';
}

export default function Modal({ open, onClose, title, children, className, size = 'md' }: ModalProps) {
  const titleId = useId();

  if (!open) return null;
  const sizes = { sm: 'max-w-sm', md: 'max-w-md', lg: 'max-w-lg', xl: 'max-w-2xl' };
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      {/*
        role="dialog" / aria-modal: without them a screen reader announces the
        overlay and its contents as ordinary page content, so a user who opens
        the order detail has no indication a dialog is open or that focus is
        confined to it. aria-labelledby points at the heading, so the dialog is
        announced by its title rather than as an unnamed "dialog".
      */}
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        className={cn('relative bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full mx-4', sizes[size], className)}
      >
        {title && (
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
            <h2 id={titleId} className="text-lg font-semibold text-gray-900 dark:text-gray-100">{title}</h2>
            <button
              type="button"
              onClick={onClose}
              aria-label="Close dialog"
              className="text-gray-400 hover:text-gray-600 dark:text-gray-200"
            >
              <X size={20} />
            </button>
          </div>
        )}
        <div className="p-6">{children}</div>
      </div>
    </div>
  );
}
