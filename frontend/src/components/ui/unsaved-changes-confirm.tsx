'use client';

import { ConfirmModal } from '@/components/ui/modal';

export interface UnsavedChangesConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * In-app half of the unsaved-changes guard (`useUnsavedChangesGuard`):
 * rendered by the caller when `confirmOpen` is true, and runs the deferred
 * action on confirm. Built on the shared `ConfirmModal` so any editor can
 * reuse the same mechanism without re-implementing the dialog.
 */
export function UnsavedChangesConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onCancel,
}: UnsavedChangesConfirmDialogProps) {
  return (
    <ConfirmModal
      isOpen={open}
      onClose={onCancel}
      onConfirm={onConfirm}
      title={title}
      message={description}
      confirmText={confirmLabel}
      cancelText={cancelLabel}
      variant="destructive"
    />
  );
}
