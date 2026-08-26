type Point = [number, number];

/**
 * Replaces each vertex of a polygon with a quadratic through pulled-back edge points, which is
 * how the hero hardware gets its rounded bevels while staying a single flat path per face.
 */
export function roundedPath(points: Point[], radius: number): string {
  const count = points.length;
  const parts: string[] = [];

  for (let i = 0; i < count; i += 1) {
    const current = points[i];
    const previous = points[(i - 1 + count) % count];
    const next = points[(i + 1) % count];
    const back = pullBack(current, previous, radius);
    const forward = pullBack(current, next, radius);

    parts.push(
      i === 0 ? `M ${format(back)}` : `L ${format(back)}`,
      `Q ${format(current)} ${format(forward)}`,
    );
  }

  return `${parts.join(' ')} Z`;
}

function pullBack(from: Point, toward: Point, radius: number): Point {
  const dx = toward[0] - from[0];
  const dy = toward[1] - from[1];
  const length = Math.hypot(dx, dy) || 1;
  // Clamped so a short edge cannot invert the curve.
  const distance = Math.min(radius, length / 2);
  return [from[0] + (dx / length) * distance, from[1] + (dy / length) * distance];
}

function format([x, y]: Point): string {
  return `${Math.round(x * 100) / 100} ${Math.round(y * 100) / 100}`;
}

export type { Point };
