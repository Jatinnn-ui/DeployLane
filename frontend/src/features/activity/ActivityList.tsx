import {
  Ban,
  FileCog,
  GitPullRequestArrow,
  KeyRound,
  PackagePlus,
  RotateCcw,
  Sparkles,
  Trash2,
  UserPlus,
  XCircle,
  CheckCircle2,
  type LucideIcon,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { cn, humanizeEnum, relativeTime } from '@/lib/utils';
import type { ActivityEntry } from '@/types/api';

const ICONS: Record<string, { icon: LucideIcon; tone: string }> = {
  PROJECT_IMPORTED: { icon: PackagePlus, tone: 'text-accent' },
  PROJECT_UPDATED: { icon: FileCog, tone: 'text-content-muted' },
  PROJECT_DELETED: { icon: Trash2, tone: 'text-danger' },
  ENVIRONMENT_CREATED: { icon: FileCog, tone: 'text-content-muted' },
  ENVIRONMENT_UPDATED: { icon: FileCog, tone: 'text-content-muted' },
  VARIABLE_CREATED: { icon: KeyRound, tone: 'text-content-muted' },
  VARIABLE_UPDATED: { icon: KeyRound, tone: 'text-content-muted' },
  VARIABLE_DELETED: { icon: KeyRound, tone: 'text-content-muted' },
  VARIABLES_BULK_UPDATED: { icon: KeyRound, tone: 'text-content-muted' },
  DEPLOYMENT_CREATED: { icon: PackagePlus, tone: 'text-accent' },
  DEPLOYMENT_SUCCEEDED: { icon: CheckCircle2, tone: 'text-success' },
  DEPLOYMENT_FAILED: { icon: XCircle, tone: 'text-danger' },
  DEPLOYMENT_CANCELLED: { icon: Ban, tone: 'text-warning' },
  DEPLOYMENT_ROLLED_BACK: { icon: RotateCcw, tone: 'text-warning' },
  AUTO_DEPLOY_TRIGGERED: { icon: GitPullRequestArrow, tone: 'text-accent' },
  AI_ANALYSIS_COMPLETED: { icon: Sparkles, tone: 'text-accent' },
  MEMBER_ADDED: { icon: UserPlus, tone: 'text-content-muted' },
};

/** Renders one activity entry as a sentence, with metadata as supporting detail. */
function describe(entry: ActivityEntry): string {
  const actor = entry.actorName ?? 'Someone';
  const number = entry.metadata?.deploymentNumber;
  const suffix = number ? ` #${number}` : '';

  switch (entry.action) {
    case 'PROJECT_IMPORTED':
      return `${actor} imported ${String(entry.metadata?.repository ?? 'a repository')}`;
    case 'PROJECT_UPDATED':
      return `${actor} updated project settings`;
    case 'PROJECT_DELETED':
      return `${actor} deleted ${String(entry.metadata?.name ?? 'a project')}`;
    case 'DEPLOYMENT_CREATED':
      return `${actor} started deployment${suffix}`;
    case 'DEPLOYMENT_SUCCEEDED':
      return `Deployment${suffix} went live`;
    case 'DEPLOYMENT_FAILED':
      return `Deployment${suffix} failed`;
    case 'DEPLOYMENT_CANCELLED':
      return `Deployment${suffix} was cancelled`;
    case 'DEPLOYMENT_ROLLED_BACK':
      return `${actor} rolled back to deployment${suffix}`;
    case 'AUTO_DEPLOY_TRIGGERED':
      return `Push to ${String(entry.metadata?.branch ?? 'a branch')} triggered deployment${suffix}`;
    case 'AI_ANALYSIS_COMPLETED':
      return `Failure analysis completed (${String(entry.metadata?.provider ?? 'ai')})`;
    case 'VARIABLES_BULK_UPDATED':
      return `${actor} updated environment variables`;
    case 'VARIABLE_CREATED':
      return `${actor} added ${String(entry.metadata?.key ?? 'a variable')}`;
    case 'VARIABLE_UPDATED':
      return `${actor} rotated ${String(entry.metadata?.key ?? 'a variable')}`;
    case 'VARIABLE_DELETED':
      return `${actor} removed ${String(entry.metadata?.key ?? 'a variable')}`;
    default:
      return `${actor} · ${humanizeEnum(entry.action)}`;
  }
}

export function ActivityList({
  entries,
  compact = false,
}: {
  entries: ActivityEntry[];
  compact?: boolean;
}) {
  return (
    <ul className="divide-y divide-border-subtle">
      {entries.map((entry) => {
        const presentation = ICONS[entry.action] ?? { icon: FileCog, tone: 'text-content-muted' };
        const Icon = presentation.icon;
        const deploymentId =
          entry.resourceType === 'DEPLOYMENT' && entry.resourceId ? entry.resourceId : null;

        const content = (
          <div className={cn('flex items-start gap-3', compact ? 'px-4 py-2.5' : 'px-4 py-3')}>
            <Icon className={cn('mt-0.5 h-3.5 w-3.5 shrink-0', presentation.tone)} aria-hidden="true" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-[13px] text-content-primary">{describe(entry)}</p>
              <p className="mt-0.5 text-[11px] text-content-muted">
                {relativeTime(entry.createdAt)}
                {entry.metadata?.stage ? ` · ${String(entry.metadata.stage)}` : ''}
              </p>
              {!compact && entry.metadata?.message ? (
                <p className="mt-1 line-clamp-2 font-mono text-[11px] text-content-secondary">
                  {String(entry.metadata.message)}
                </p>
              ) : null}
            </div>
          </div>
        );

        return (
          <li key={entry.id} className="transition-colors hover:bg-surface-hover">
            {deploymentId ? (
              <Link to={`/deployments/${deploymentId}`} className="block">
                {content}
              </Link>
            ) : (
              content
            )}
          </li>
        );
      })}
    </ul>
  );
}
