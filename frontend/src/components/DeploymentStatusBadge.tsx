import {
  Ban,
  CheckCircle2,
  CircleSlash,
  Clock,
  Hammer,
  HeartPulse,
  Package,
  Rocket,
  Search,
  XCircle,
  type LucideIcon,
} from 'lucide-react';
import { Badge, StatusDot } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import type { DeploymentStatus } from '@/types/api';

type Tone = 'neutral' | 'accent' | 'success' | 'warning' | 'danger' | 'info';

interface StatusPresentation {
  label: string;
  tone: Tone;
  icon: LucideIcon;
  inProgress: boolean;
}

/**
 * Single source of truth for how a deployment status looks.
 *
 * Colour carries meaning here: violet means "working", green "serving", red "broken", grey "no longer
 * live". Every list, card and header reads from this map so the same status never appears in two colours.
 */
const PRESENTATION: Record<DeploymentStatus, StatusPresentation> = {
  QUEUED: { label: 'Queued', tone: 'neutral', icon: Clock, inProgress: true },
  CLONING: { label: 'Cloning', tone: 'accent', icon: Search, inProgress: true },
  DETECTING: { label: 'Detecting', tone: 'accent', icon: Search, inProgress: true },
  BUILDING: { label: 'Building', tone: 'accent', icon: Hammer, inProgress: true },
  IMAGE_BUILDING: { label: 'Building image', tone: 'accent', icon: Package, inProgress: true },
  STARTING: { label: 'Starting', tone: 'accent', icon: Rocket, inProgress: true },
  HEALTH_CHECKING: { label: 'Health checking', tone: 'info', icon: HeartPulse, inProgress: true },
  READY: { label: 'Ready', tone: 'success', icon: CheckCircle2, inProgress: false },
  FAILED: { label: 'Failed', tone: 'danger', icon: XCircle, inProgress: false },
  CANCELLED: { label: 'Cancelled', tone: 'warning', icon: Ban, inProgress: false },
  STOPPED: { label: 'Stopped', tone: 'neutral', icon: CircleSlash, inProgress: false },
};

export function statusPresentation(status: DeploymentStatus): StatusPresentation {
  return PRESENTATION[status] ?? PRESENTATION.QUEUED;
}

export function isDeploymentInProgress(status?: DeploymentStatus | null): boolean {
  return status ? statusPresentation(status).inProgress : false;
}

export function DeploymentStatusBadge({
  status,
  className,
  showIcon = true,
}: {
  status: DeploymentStatus;
  className?: string;
  showIcon?: boolean;
}) {
  const presentation = statusPresentation(status);
  const Icon = presentation.icon;
  return (
    <Badge tone={presentation.tone} className={className}>
      {showIcon ? (
        <Icon
          className={cn('h-3 w-3', presentation.inProgress && status !== 'QUEUED' && 'animate-pulse')}
          aria-hidden="true"
        />
      ) : null}
      {presentation.label}
    </Badge>
  );
}

/** Compact dot + label, used where a full badge would be too heavy (project cards, tables). */
export function DeploymentStatusIndicator({
  status,
  className,
}: {
  status: DeploymentStatus;
  className?: string;
}) {
  const presentation = statusPresentation(status);
  return (
    <span className={cn('inline-flex items-center gap-2 text-[12px]', className)}>
      <StatusDot tone={presentation.tone} pulse={presentation.inProgress} />
      <span className="text-content-secondary">{presentation.label}</span>
    </span>
  );
}

export function HealthIndicator({
  status,
  className,
}: {
  status: string | undefined;
  className?: string;
}) {
  const tone: Tone =
    status === 'HEALTHY'
      ? 'success'
      : status === 'UNHEALTHY' || status === 'FAILED'
        ? 'danger'
        : 'neutral';
  const label =
    status === 'HEALTHY'
      ? 'Healthy'
      : status === 'UNHEALTHY'
        ? 'Unhealthy'
        : status === 'FAILED'
          ? 'Last deploy failed'
          : status === 'NOT_LIVE'
            ? 'Not live'
            : 'Never deployed';

  return (
    <span className={cn('inline-flex items-center gap-2 text-[13px]', className)}>
      <StatusDot tone={tone} pulse={tone === 'success'} />
      <span className="text-content-primary">{label}</span>
    </span>
  );
}
