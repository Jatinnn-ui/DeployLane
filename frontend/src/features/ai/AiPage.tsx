import {
  Activity,
  ArrowUp,
  Bot,
  GitCompare,
  Gauge,
  Sparkles,
  User,
  Wrench,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { useAiChat } from '@/api/deployments';
import { useAiStatus, useAiTools } from '@/api/platform';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { InlineNotice, Spinner } from '@/components/ui/feedback';
import { Textarea } from '@/components/ui/form';
import { useToast } from '@/components/ui/toast';
import { cn } from '@/lib/utils';
import type { Project } from '@/types/api';

interface Turn {
  role: 'user' | 'assistant';
  content: string;
  tools?: string[];
  provider?: string;
}

const QUICK_ACTIONS = [
  { icon: Wrench, label: 'Why did my latest deployment fail?', prompt: 'Why did my latest deployment fail?' },
  { icon: Activity, label: 'Check application health', prompt: 'Is the application healthy right now?' },
  {
    icon: GitCompare,
    label: 'Compare the last two deployments',
    prompt: 'Compare the last two deployments and tell me what changed.',
  },
  {
    icon: Gauge,
    label: 'Explain resource usage',
    prompt: 'Explain the current CPU and memory usage of this project.',
  },
  {
    icon: Sparkles,
    label: 'Show recent errors',
    prompt: 'Show me the errors from the latest deployment logs.',
  },
];

/**
 * Project scoped DevOps assistant.
 *
 * The tools panel is shown on purpose: the assistant answers from a context block the backend gathered with
 * read-only tools, and listing which ones ran makes the answer auditable instead of magic. The model cannot
 * query anything itself and cannot execute a command.
 */
export function AiPage() {
  const { project } = useOutletContext<{ project: Project }>();
  const { data: status } = useAiStatus();
  const { data: tools } = useAiTools();
  const chat = useAiChat(project.id);
  const toast = useToast();

  const [turns, setTurns] = useState<Turn[]>([]);
  const [input, setInput] = useState('');
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [turns, chat.isPending]);

  const canUseAi = project.permissions.includes('USE_AI');

  const send = (message: string) => {
    const question = message.trim();
    if (!question || chat.isPending) return;

    const history = turns.map((turn) => ({ role: turn.role, content: turn.content }));
    setTurns((current) => [...current, { role: 'user', content: question }]);
    setInput('');

    chat.mutate(
      { message: question, history },
      {
        onSuccess: (response) =>
          setTurns((current) => [
            ...current,
            {
              role: 'assistant',
              content: response.answer,
              tools: response.usedTools,
              provider: response.provider,
            },
          ]),
        onError: (error) => {
          toast.error('The assistant could not answer', error);
          setTurns((current) => current.slice(0, -1));
        },
      },
    );
  };

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_300px]">
      <Card className="flex min-h-[32rem] flex-col">
        <CardHeader
          title="AI DevOps assistant"
          description={`Answers about ${project.name} from its deployments, logs, health and container stats.`}
          icon={<Bot className="h-4 w-4" />}
          actions={
            status ? (
              <Badge tone={status.externalProviderActive ? 'accent' : 'neutral'}>
                {status.activeProvider}
              </Badge>
            ) : null
          }
        />

        <CardBody className="flex flex-1 flex-col gap-4">
          {!canUseAi ? (
            <InlineNotice tone="warning">
              Your workspace role can read AI analyses but not start new AI requests.
            </InlineNotice>
          ) : status && !status.externalProviderActive ? (
            <InlineNotice>
              No AI provider is configured, so answers come from DeployLane's local rules. Set{' '}
              <code className="font-mono">AI_PROVIDER</code> and the matching API key for model backed
              answers.
            </InlineNotice>
          ) : null}

          <div className="flex-1 space-y-4 overflow-y-auto">
            {turns.length === 0 ? (
              <div className="space-y-3 py-4">
                <p className="text-[13px] text-content-secondary">
                  Ask about a failure, a slow build or the current state of the service.
                </p>
                <div className="grid gap-2 sm:grid-cols-2">
                  {QUICK_ACTIONS.map((action) => (
                    <button
                      key={action.label}
                      type="button"
                      disabled={!canUseAi}
                      onClick={() => send(action.prompt)}
                      className="flex items-center gap-2.5 rounded-lg border border-border-subtle px-3 py-2.5 text-left text-[13px] text-content-secondary transition-colors hover:border-border-strong hover:bg-surface-hover hover:text-content-primary disabled:opacity-50"
                    >
                      <action.icon className="h-3.5 w-3.5 shrink-0 text-accent" aria-hidden="true" />
                      {action.label}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              turns.map((turn, index) => <ChatBubble key={index} turn={turn} />)
            )}

            {chat.isPending ? (
              <div className="flex items-center gap-2 text-[13px] text-content-secondary">
                <Spinner />
                Gathering context and thinking...
              </div>
            ) : null}

            <div ref={endRef} />
          </div>

          <form
            className="flex items-end gap-2 border-t border-border-subtle pt-4"
            onSubmit={(event) => {
              event.preventDefault();
              send(input);
            }}
          >
            <Textarea
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder="Why did deployment #12 fail?"
              rows={2}
              disabled={!canUseAi}
              aria-label="Message"
              className="min-h-[2.75rem] flex-1 font-sans"
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault();
                  send(input);
                }
              }}
            />
            <Button
              type="submit"
              variant="primary"
              size="icon"
              className="h-9 w-9"
              disabled={!canUseAi || !input.trim()}
              loading={chat.isPending}
              aria-label="Send"
            >
              {!chat.isPending ? <ArrowUp className="h-4 w-4" /> : null}
            </Button>
          </form>
        </CardBody>
      </Card>

      <aside className="space-y-6">
        <Card>
          <CardHeader title="Available tools" description="Read-only, executed by the backend" />
          <CardBody className="space-y-1.5">
            {(tools ?? []).map((tool) => (
              <p key={tool} className="font-mono text-[11px] text-content-secondary">
                {tool}
              </p>
            ))}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="What the assistant cannot do" />
          <CardBody>
            <ul className="space-y-2 text-[12px] leading-relaxed text-content-secondary">
              <li>· Run shell commands or scripts</li>
              <li>· Read secret values (it sees variable names only)</li>
              <li>· Deploy, restart, roll back or delete anything</li>
              <li>· Change repository contents or push commits</li>
            </ul>
            <p className="mt-3 border-t border-border-subtle pt-3 text-[11px] leading-relaxed text-content-muted">
              It can recommend commands. Running them is always your decision.
            </p>
          </CardBody>
        </Card>
      </aside>
    </div>
  );
}

function ChatBubble({ turn }: { turn: Turn }) {
  const isUser = turn.role === 'user';
  return (
    <div className={cn('flex gap-3', isUser && 'flex-row-reverse')}>
      <div
        className={cn(
          'flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border',
          isUser
            ? 'border-border-subtle bg-surface-raised text-content-secondary'
            : 'border-accent-border bg-accent-soft text-accent',
        )}
      >
        {isUser ? <User className="h-3.5 w-3.5" /> : <Bot className="h-3.5 w-3.5" />}
      </div>
      <div className={cn('min-w-0 max-w-[85%] space-y-1.5', isUser && 'text-right')}>
        <div
          className={cn(
            'inline-block rounded-xl px-3.5 py-2.5 text-left text-[13px] leading-relaxed',
            isUser
              ? 'bg-accent text-on-accent'
              : 'border border-border-subtle bg-surface-raised text-content-primary',
          )}
        >
          <p className="whitespace-pre-wrap break-words">{turn.content}</p>
        </div>
        {turn.tools && turn.tools.length > 0 ? (
          <p className="text-[10px] text-content-muted">
            context: {turn.tools.join(', ')}
            {turn.provider ? ` · ${turn.provider}` : ''}
          </p>
        ) : null}
      </div>
    </div>
  );
}
