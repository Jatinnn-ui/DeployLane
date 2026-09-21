import { Check, Copy } from 'lucide-react';
import { useCallback, useRef, useState } from 'react';
import { cn } from '@/lib/utils';
import { copyToClipboard } from '@/lib/utils';
import { Tooltip } from '@/components/ui/overlay';

export interface CopyButtonProps {
  /** The text value to copy. */
  value: string;
  /** Accessible label. Defaults to "Copy to clipboard". */
  label?: string;
  /** Tooltip shown on hover before copying. */
  tooltip?: string;
  /** Additional class names for the button element. */
  className?: string;
}

/**
 * Reusable copy-to-clipboard button with animated success feedback.
 *
 * Shows the Copy icon at rest, briefly switches to a Check icon on success,
 * then reverts. Uses design system tokens for all colors and transitions.
 */
export function CopyButton({
  value,
  label = 'Copy to clipboard',
  tooltip = 'Copy',
  className,
}: CopyButtonProps) {
  const [copied, setCopied] = useState(false);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleCopy = useCallback(async () => {
    const ok = await copyToClipboard(value);
    if (!ok) return;

    setCopied(true);
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    timeoutRef.current = setTimeout(() => setCopied(false), 1800);
  }, [value]);

  return (
    <Tooltip content={copied ? 'Copied!' : tooltip}>
      <button
        type="button"
        onClick={handleCopy}
        aria-label={label}
        className={cn(
          'inline-flex h-7 w-7 items-center justify-center rounded-md text-content-muted transition-colors duration-200 hover:bg-surface-hover hover:text-content-primary focus-visible:outline-2 focus-visible:outline-accent-deep focus-visible:outline-offset-2',
          copied && 'text-success',
          className,
        )}
      >
        {copied ? (
          <Check className="h-3.5 w-3.5" aria-hidden="true" />
        ) : (
          <Copy className="h-3.5 w-3.5" aria-hidden="true" />
        )}
      </button>
    </Tooltip>
  );
}
