import { describe, expect, it } from 'vitest';
import { formatBytes, formatDuration, formatUptime, humanizeEnum, relativeTime, truncate } from '@/lib/utils';

describe('formatDuration', () => {
  it('formats sub-second, second, minute and hour scales', () => {
    expect(formatDuration(450)).toBe('450ms');
    expect(formatDuration(42_000)).toBe('42s');
    expect(formatDuration(192_000)).toBe('3m 12s');
    expect(formatDuration(3_900_000)).toBe('1h 05m');
  });

  it('returns a dash for a missing duration rather than 0', () => {
    // Durations are absent until a deployment finishes; showing "0ms" would be a lie.
    expect(formatDuration(null)).toBe('-');
    expect(formatDuration(undefined)).toBe('-');
  });
});

describe('formatBytes', () => {
  it('scales to the closest unit', () => {
    expect(formatBytes(0)).toBe('0 B');
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(1024)).toBe('1 KB');
    expect(formatBytes(400 * 1024 * 1024)).toBe('400 MB');
    expect(formatBytes(1024 * 1024 * 1024)).toBe('1 GB');
  });

  it('handles absent samples', () => {
    expect(formatBytes(null)).toBe('-');
  });
});

describe('formatUptime', () => {
  it('converts seconds to a readable duration', () => {
    expect(formatUptime(90)).toBe('1m 30s');
    expect(formatUptime(22_860)).toBe('6h 21m');
    expect(formatUptime(null)).toBe('-');
  });
});

describe('relativeTime', () => {
  it('describes recent and older timestamps', () => {
    const now = Date.now();
    expect(relativeTime(new Date(now - 2_000).toISOString())).toBe('just now');
    expect(relativeTime(new Date(now - 3 * 60_000).toISOString())).toBe('3 minutes ago');
    expect(relativeTime(new Date(now - 60 * 60_000).toISOString())).toBe('1 hour ago');
  });

  it('is null safe and rejects garbage', () => {
    expect(relativeTime(null)).toBe('-');
    expect(relativeTime('not-a-date')).toBe('-');
  });
});

describe('humanizeEnum', () => {
  it('turns enum constants into sentences', () => {
    expect(humanizeEnum('DEPLOYMENT_FAILED')).toBe('Deployment failed');
    expect(humanizeEnum('GIT_PUSH')).toBe('Git push');
    expect(humanizeEnum(null)).toBe('');
  });
});

describe('truncate', () => {
  it('shortens long commit messages with an ellipsis', () => {
    expect(truncate('short', 20)).toBe('short');
    expect(truncate('a'.repeat(30), 10)).toHaveLength(10);
    expect(truncate(undefined, 10)).toBe('');
  });
});
