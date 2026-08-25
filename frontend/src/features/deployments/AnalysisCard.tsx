import { Check, Copy, RefreshCw, Sparkles, Wrench } from 'lucide-react';
import { useState } from 'react';
import { useAnalyzeDeployment, useDeploymentAnalysis } from '@/api/deployments';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { EmptyState, InlineNotice, Skeleton } from '@/components/ui/feedback';
import { useToast } from '@/components/ui/toast';
import { copyToClipboard } from '@/lib/utils';
import type { Deployment, Severity } from '@/types/api';

const SEVERITY_TONE: Record<Severity, 'success' | 'warning' | 'danger' | 'neutral'> = {
  LOW: 'neutral',
  MEDIUM: 'warning',
  HIGH: 'danger',
  CRITICAL: 'danger',
};

/**
 * AI failure analysis.
 *
 * What is deliberately visible here: which analyzer produced the result, how confident it is, and the exact
 * log lines it based the conclusion on. An explanation you cannot check is not much better than a guess, so
 * the evidence is part of the output rather than hidden behind it.
 */
export function AnalysisCard({ deployment }: { deployment: Deployment }) {
  const isFailed = deployment.status === 'FAILED';
  const { data: analysis, isLoading } = useDeploymentAnalysis(deployment.id, isFailed);
  const analyze = useAnalyzeDeployment(deployment.id);
  const toast = useToast();

  if (!isFailed) {
    return null;
  }

  const runAnalysis = (force: boolean) => {
    analyze.mutate(force, {
      onError: (error) => toast.error('Analysis failed', error),
    });
  };

  const confidencePercent =
    analysis?.confidence !== null && analysis?.confidence !== undefined
      ? Math.round(analysis.confidence * 100)
      : null;

  return (
    <Card>
      <CardHeader
        title="AI deployment analysis"
        icon={<Sparkles className="h-4 w-4" />}
        description={
          analysis?.provider
            ? `${analysis.provider}${analysis.model ? ` · ${analysis.model}` : ''}`
            : 'Explains why this deployment failed, from its own logs'
        }
        actions={
          <div className="flex items-center gap-2">
            {analysis?.severity ? (
              <Badge tone={SEVERITY_TONE[analysis.severity]}>{analysis.severity}</Badge>
            ) : null}
            {confidencePercent !== null ? (
              <Badge tone={confidencePercent >= 80 ? 'success' : confidencePercent >= 50 ? 'warning' : 'neutral'}>
                {confidencePercent}% confidence
              </Badge>
            ) : null}
            <Button
              variant="ghost"
              size="icon"
              onClick={() => runAnalysis(true)}
              loading={analyze.isPending}
              aria-label="Re-run analysis"
            >
              <RefreshCw className="h-3.5 w-3.5" />
            </Button>
          </div>
        }
      />

      <CardBody className="space-y-4">
        {isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-3 w-full" />
            <Skeleton className="h-3 w-4/5" />
          </div>
        ) : !analysis || analysis.status === 'UNAVAILABLE' ? (
          <EmptyState
            icon={Sparkles}
            title="No analysis yet"
            description={
              analysis?.errorMessage ??
              'Run the analyzer to get a root cause, the evidence behind it and a suggested fix.'
            }
            action={
              <Button variant="primary" size="sm" onClick={() => runAnalysis(false)} loading={analyze.isPending}>
                Analyse this failure
              </Button>
            }
            className="py-8"
          />
        ) : analysis.status === 'PENDING' ? (
          <div className="flex items-center gap-2 text-[13px] text-content-secondary">
            <RefreshCw className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
            Analysing the failure...
          </div>
        ) : (
          <>
            {analysis.provider === 'heuristic' ? (
              <InlineNotice>
                Produced by the built-in rule based analyzer, not a language model. Set{' '}
                <code className="font-mono">AI_PROVIDER</code> and an API key for a model backed
                explanation.
              </InlineNotice>
            ) : null}

            <Section title="Root cause">
              <p className="text-[13px] leading-relaxed text-content-primary">{analysis.rootCause}</p>
              {analysis.summary && analysis.summary !== analysis.rootCause ? (
                <p className="mt-1.5 text-[12px] leading-relaxed text-content-secondary">
                  {analysis.summary}
                </p>
              ) : null}
            </Section>

            {analysis.evidence.length > 0 ? (
              <Section title="Evidence">
                <ul className="space-y-1 rounded-lg border border-border-subtle bg-canvas px-3 py-2">
                  {analysis.evidence.map((line, index) => (
                    <li
                      key={index}
                      className="break-all font-mono text-[11px] leading-relaxed text-content-secondary"
                    >
                      {line}
                    </li>
                  ))}
                </ul>
              </Section>
            ) : null}

            {analysis.suggestedFixes.length > 0 ? (
              <Section title="Recommended fix">
                <ol className="space-y-3">
                  {analysis.suggestedFixes.map((fix, index) => (
                    <li key={index} className="flex gap-3">
                      <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-md border border-border-subtle bg-surface-raised text-[11px] tabular-nums text-content-secondary">
                        {index + 1}
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="flex items-center gap-1.5 text-[13px] font-medium text-content-primary">
                          <Wrench className="h-3 w-3 text-content-muted" aria-hidden="true" />
                          {fix.title}
                        </p>
                        {fix.description ? (
                          <p className="mt-0.5 text-[12px] leading-relaxed text-content-secondary">
                            {fix.description}
                          </p>
                        ) : null}
                        {fix.command ? <CommandBlock command={fix.command} /> : null}
                      </div>
                    </li>
                  ))}
                </ol>
              </Section>
            ) : null}

            <p className="border-t border-border-subtle pt-3 text-[11px] leading-relaxed text-content-muted">
              DeployLane never sends secret values to an AI provider. Only variable names, build
              configuration and redacted log lines are shared, and suggested commands are never executed
              automatically.
            </p>
          </>
        )}
      </CardBody>
    </Card>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <h3 className="mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-content-muted">
        {title}
      </h3>
      {children}
    </section>
  );
}

function CommandBlock({ command }: { command: string }) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    if (await copyToClipboard(command)) {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    }
  };

  return (
    <div className="mt-2 flex items-center gap-2 rounded-lg border border-border-subtle bg-canvas px-3 py-2">
      <code className="min-w-0 flex-1 break-all font-mono text-[11px] text-content-primary">
        {command}
      </code>
      <button
        type="button"
        onClick={copy}
        className="shrink-0 rounded p-1 text-content-muted transition-colors hover:bg-surface-hover hover:text-content-primary"
        aria-label={copied ? 'Copied' : 'Copy command'}
      >
        {copied ? (
          <Check className="h-3.5 w-3.5 text-success" />
        ) : (
          <Copy className="h-3.5 w-3.5" />
        )}
      </button>
    </div>
  );
}
