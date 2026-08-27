'use client';

import * as React from 'react';
import { ConfirmModal } from '@/components/ui/modal';

type ConfirmationOptions = {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  variant?: 'default' | 'destructive';
};

export function useConfirmationDialog() {
  const resolverRef = React.useRef<((confirmed: boolean) => void) | null>(null);
  const [options, setOptions] = React.useState<ConfirmationOptions | null>(null);

  const closeDialog = React.useCallback((confirmed: boolean) => {
    resolverRef.current?.(confirmed);
    resolverRef.current = null;
    setOptions(null);
  }, []);

  const confirm = React.useCallback((nextOptions: ConfirmationOptions) => {
    return new Promise<boolean>((resolve) => {
      resolverRef.current = resolve;
      setOptions(nextOptions);
    });
  }, []);

  React.useEffect(() => {
    return () => {
      resolverRef.current?.(false);
      resolverRef.current = null;
    };
  }, []);

  const dialog = (
    <ConfirmModal
      isOpen={Boolean(options)}
      onClose={() => closeDialog(false)}
      onConfirm={() => closeDialog(true)}
      title={options?.title ?? ''}
      message={options?.message ?? ''}
      confirmText={options?.confirmText}
      cancelText={options?.cancelText}
      variant={options?.variant ?? 'default'}
    />
  );

  return {
    confirm,
    confirmationDialog: dialog,
  };
}
