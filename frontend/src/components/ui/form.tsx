import * as LabelPrimitive from '@radix-ui/react-label';
import * as SwitchPrimitive from '@radix-ui/react-switch';
import { forwardRef, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

export const Label = forwardRef<
  HTMLLabelElement,
  React.ComponentPropsWithoutRef<typeof LabelPrimitive.Root>
>(({ className, ...props }, ref) => (
  <LabelPrimitive.Root
    ref={ref}
    className={cn('text-[12px] font-medium text-content-secondary', className)}
    {...props}
  />
));
Label.displayName = 'Label';

const fieldStyles =
  'box-border flex w-full items-center rounded-control border border-border-subtle bg-surface-alt px-3.5 text-[14px] font-normal not-italic leading-none text-content-primary placeholder:text-content-muted transition-[border-color,box-shadow] duration-200 hover:border-border-strong focus:border-accent-deep focus:outline-none focus:ring-0 disabled:cursor-not-allowed disabled:opacity-45 aria-[invalid=true]:border-danger';

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => (
    <input ref={ref} className={cn(fieldStyles, 'h-11', className)} {...props} />
  ),
);
Input.displayName = 'Input';

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => (
    <textarea
      ref={ref}
      className={cn(fieldStyles, 'min-h-[120px] resize-y py-2.5 font-mono leading-relaxed', className)}
      {...props}
    />
  ),
);
Textarea.displayName = 'Textarea';

export function Field({
  label,
  hint,
  error,
  htmlFor,
  children,
  className,
}: {
  label?: ReactNode;
  hint?: ReactNode;
  error?: string | null;
  htmlFor?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('space-y-1.5', className)}>
      {label ? <Label htmlFor={htmlFor}>{label}</Label> : null}
      {children}
      {error ? (
        <p role="alert" className="text-[12px] text-danger-foreground">
          {error}
        </p>
      ) : hint ? (
        <p className="text-[12px] leading-relaxed text-content-muted">{hint}</p>
      ) : null}
    </div>
  );
}

export function Switch({
  checked,
  onCheckedChange,
  disabled,
  id,
  label,
  description,
}: {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  disabled?: boolean;
  id?: string;
  label?: ReactNode;
  description?: ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-4">
      {label ? (
        <div className="min-w-0">
          <Label htmlFor={id} className="text-[13px] text-content-primary">
            {label}
          </Label>
          {description ? (
            <p className="mt-1 text-[12px] leading-relaxed text-content-muted">{description}</p>
          ) : null}
        </div>
      ) : null}
      <SwitchPrimitive.Root
        id={id}
        checked={checked}
        onCheckedChange={onCheckedChange}
        disabled={disabled}
        className={cn(
          'relative h-5 w-9 shrink-0 cursor-pointer rounded-full border border-border-subtle bg-surface transition-colors duration-300 data-[state=checked]:border-accent-deep data-[state=checked]:bg-accent-deep',
          disabled && 'cursor-not-allowed opacity-45',
        )}
      >
        <SwitchPrimitive.Thumb className="block h-3.5 w-3.5 translate-x-0.5 rounded-full bg-content-inverse shadow-sm transition-transform duration-300 data-[state=checked]:translate-x-[18px] data-[state=checked]:bg-ink-deep" />
      </SwitchPrimitive.Root>
    </div>
  );
}

export const Select = forwardRef<
  HTMLSelectElement,
  React.SelectHTMLAttributes<HTMLSelectElement>
>(({ className, children, ...props }, ref) => (
  <select
    ref={ref}
    className={cn(
      'box-border w-full appearance-none rounded-control border border-border-subtle bg-surface-alt px-3.5 py-0 text-[14px] font-normal not-italic leading-none text-content-primary transition-[border-color,box-shadow] duration-200 hover:border-border-strong focus:border-accent-deep focus:outline-none focus:ring-0 disabled:cursor-not-allowed disabled:opacity-45',
      // Data-URI chevron cannot inherit currentColor, so this is a neutral mid-grey
      // chosen to read acceptably against both the light and dark field surfaces.
      'h-11 cursor-pointer bg-[url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' viewBox=\'0 0 16 16\' fill=\'none\' stroke=\'%238a8d88\' stroke-width=\'1.75\' stroke-linecap=\'round\' stroke-linejoin=\'round\'%3E%3Cpath d=\'M4.5 6.5 8 10l3.5-3.5\'/%3E%3C/svg%3E")] bg-[length:16px] bg-[right_0.65rem_center] bg-no-repeat pr-9',
      className,
    )}
    {...props}
  >
    {children}
  </select>
));
Select.displayName = 'Select';
