import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { ApiError } from '@/lib/api-client';
import { cn } from '@/lib/utils';

type ToastTone = 'success' | 'error' | 'info';

interface Toast {
  id: number;
  tone: ToastTone;
  title: string;
  description?: string;
}

interface ToastContextValue {
  push: (toast: Omit<Toast, 'id'>) => void;
  success: (title: string, description?: string) => void;
  error: (title: string, error?: unknown) => void;
  info: (title: string, description?: string) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const AUTO_DISMISS_MS = 6000;

/**
 * Minimal toast system.
 *
 * Deliberately hand rolled rather than another dependency: the only requirement is "confirm an action or
 * explain a failure", and `error()` understands {@link ApiError} so a failed deploy shows the backend's
 * message and request id instead of "Error".
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const push = useCallback(
    (toast: Omit<Toast, 'id'>) => {
      const id = Date.now() + Math.random();
      setToasts((current) => [...current.slice(-3), { ...toast, id }]);
      window.setTimeout(() => dismiss(id), AUTO_DISMISS_MS);
    },
    [dismiss],
  );

  const value = useMemo<ToastContextValue>(
    () => ({
      push,
      success: (title, description) => push({ tone: 'success', title, description }),
      info: (title, description) => push({ tone: 'info', title, description }),
      error: (title, error) => {
        const description =
          error instanceof ApiError
            ? `${error.message}${error.requestId ? ` (request ${error.requestId})` : ''}`
            : error instanceof Error
              ? error.message
              : undefined;
        push({ tone: 'error', title, description });
      },
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className="pointer-events-none fixed bottom-4 right-4 z-[100] flex w-full max-w-sm flex-col gap-2"
        role="region"
        aria-label="Notifications"
      >
        <AnimatePresence initial={false}>
          {toasts.map((toast) => (
            <motion.div
              key={toast.id}
              initial={{ opacity: 0, y: 12, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8, scale: 0.98 }}
              transition={{ duration: 0.3, ease: [0.075, 0.82, 0.165, 1] }}
              className={cn(
                'pointer-events-auto flex items-start gap-3 rounded-[14px] border bg-surface-alt px-4 py-3.5 shadow-lg',
                toast.tone === 'success' && 'border-success-border',
                toast.tone === 'error' && 'border-danger-border',
                toast.tone === 'info' && 'border-border-subtle',
              )}
              role="status"
              aria-live={toast.tone === 'error' ? 'assertive' : 'polite'}
            >
              <ToastIcon tone={toast.tone} />
              <div className="min-w-0 flex-1">
                <p className="text-[14px] font-medium text-content-primary">{toast.title}</p>
                {toast.description ? (
                  <p className="mt-0.5 break-words text-[12px] leading-relaxed text-content-secondary">
                    {toast.description}
                  </p>
                ) : null}
              </div>
              <button
                type="button"
                onClick={() => dismiss(toast.id)}
                className="rounded-[8px] p-0.5 text-content-muted transition-colors hover:text-content-primary"
                aria-label="Dismiss"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  );
}

function ToastIcon({ tone }: { tone: ToastTone }) {
  const className = 'mt-0.5 h-4 w-4 shrink-0';
  if (tone === 'success') return <CheckCircle2 className={cn(className, 'text-success')} />;
  if (tone === 'error') return <AlertCircle className={cn(className, 'text-danger')} />;
  return <Info className={cn(className, 'text-info')} />;
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used inside a ToastProvider');
  }
  return context;
}
