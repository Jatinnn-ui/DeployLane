import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { LogTerminal } from '@/features/logs/LogTerminal';
import { ToastProvider } from '@/components/ui/toast';
import { TooltipProvider } from '@/components/ui/overlay';
import type { LogEntry } from '@/types/api';

const entries: LogEntry[] = [
  {
    sequence: 1,
    timestamp: '2026-02-11T12:43:21.000Z',
    level: 'INFO',
    source: 'SYSTEM',
    message: 'Preparing deployment...',
  },
  {
    sequence: 2,
    timestamp: '2026-02-11T12:43:26.000Z',
    level: 'INFO',
    source: 'GIT',
    message: 'Repository cloned successfully.',
  },
  {
    sequence: 3,
    timestamp: '2026-02-11T12:43:41.000Z',
    level: 'INFO',
    source: 'BUILD',
    message: 'Running npm run build...',
  },
  {
    sequence: 4,
    timestamp: '2026-02-11T12:43:58.000Z',
    level: 'ERROR',
    source: 'BUILD',
    message: 'npm ERR! Cannot find module date-fns',
  },
  {
    sequence: 5,
    timestamp: '2026-02-11T12:44:02.000Z',
    level: 'WARN',
    source: 'HEALTH',
    message: 'Attempt 3: connection refused',
  },
];

function renderTerminal(props: Partial<React.ComponentProps<typeof LogTerminal>> = {}) {
  return render(
    <TooltipProvider>
      <ToastProvider>
        <LogTerminal entries={entries} {...props} />
      </ToastProvider>
    </TooltipProvider>,
  );
}

describe('LogTerminal', () => {
  it('renders every line with its source', () => {
    renderTerminal();

    expect(screen.getByText('Preparing deployment...')).toBeInTheDocument();
    expect(screen.getByText('npm ERR! Cannot find module date-fns')).toBeInTheDocument();
    expect(screen.getByText('5 lines', { exact: false })).toBeInTheDocument();
  });

  it('surfaces the error count, which is what a failed build is judged by', () => {
    renderTerminal();
    expect(screen.getByText(/1 errors/)).toBeInTheDocument();
  });

  it('filters by source without discarding the buffer', async () => {
    const user = userEvent.setup();
    renderTerminal();

    await user.selectOptions(screen.getByLabelText('Source'), 'GIT');

    expect(screen.getByText('Repository cloned successfully.')).toBeInTheDocument();
    expect(screen.queryByText('Preparing deployment...')).not.toBeInTheDocument();
    expect(screen.getByText(/4 lines hidden by filters/)).toBeInTheDocument();

    // Clearing the filter brings everything back, proving nothing was thrown away.
    await user.selectOptions(screen.getByLabelText('Source'), 'ALL');
    expect(screen.getByText('Preparing deployment...')).toBeInTheDocument();
  });

  it('filters by minimum level', async () => {
    const user = userEvent.setup();
    renderTerminal();

    await user.selectOptions(screen.getByLabelText('Minimum level'), 'WARN');

    expect(screen.getByText('npm ERR! Cannot find module date-fns')).toBeInTheDocument();
    expect(screen.getByText('Attempt 3: connection refused')).toBeInTheDocument();
    expect(screen.queryByText('Repository cloned successfully.')).not.toBeInTheDocument();
  });

  it('searches within messages', async () => {
    const user = userEvent.setup();
    renderTerminal();

    await user.type(screen.getByLabelText('Search logs'), 'npm');

    expect(screen.getByText('Running npm run build...')).toBeInTheDocument();
    expect(screen.getByText('npm ERR! Cannot find module date-fns')).toBeInTheDocument();
    expect(screen.queryByText('Preparing deployment...')).not.toBeInTheDocument();
  });

  it('reports an empty stream instead of rendering nothing', () => {
    render(
      <TooltipProvider>
        <ToastProvider>
          <LogTerminal entries={[]} emptyMessage="Waiting for the first line..." />
        </ToastProvider>
      </TooltipProvider>,
    );

    expect(screen.getByText('Waiting for the first line...')).toBeInTheDocument();
  });

  it('shows the live connection state', () => {
    renderTerminal({ socketState: 'open' });
    expect(screen.getByText('Live')).toBeInTheDocument();
  });

  it('explains a broken stream rather than looking idle', () => {
    renderTerminal({ socketState: 'error' });
    expect(screen.getByText('Stream unavailable')).toBeInTheDocument();
  });

  it('can toggle timestamps off for narrow screens', async () => {
    const user = userEvent.setup();
    // Timestamps render in the viewer's local timezone, so the expectation is derived rather than
    // hard coded to a UTC string.
    const local = new Date(entries[0]!.timestamp);
    const expected = [local.getHours(), local.getMinutes(), local.getSeconds()]
      .map((part) => String(part).padStart(2, '0'))
      .join(':');

    renderTerminal();

    expect(screen.getByText(expected)).toBeInTheDocument();
    await user.click(screen.getByLabelText('Toggle timestamps'));
    expect(screen.queryByText(expected)).not.toBeInTheDocument();
  });

  it('exposes the log region to assistive technology', () => {
    renderTerminal();
    expect(screen.getByRole('log', { name: 'Deployment logs' })).toBeInTheDocument();
  });
});
