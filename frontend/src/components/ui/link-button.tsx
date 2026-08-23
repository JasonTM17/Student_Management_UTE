'use client';

import * as React from 'react';
import type { VariantProps } from 'class-variance-authority';

import { LocalizedLink } from '@/components/LocalizedLink';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';

type LocalizedLinkProps = React.ComponentProps<typeof LocalizedLink>;

export interface LinkButtonProps
  extends Omit<LocalizedLinkProps, 'className'>,
    VariantProps<typeof buttonVariants> {
  className?: string;
}

export const LinkButton = React.forwardRef<HTMLAnchorElement, LinkButtonProps>(
  function LinkButton({ className, size, variant, ...props }, ref) {
    return (
      <LocalizedLink
        ref={ref}
        className={cn(buttonVariants({ size, variant }), className)}
        {...props}
      />
    );
  },
);
