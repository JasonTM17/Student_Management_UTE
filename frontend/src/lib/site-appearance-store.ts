import { promises as fs } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {
  DEFAULT_SITE_APPEARANCE,
  sanitizeSiteAppearance,
  type SiteAppearance,
} from '@/lib/site-appearance';

function appearancePath(): string {
  return (
    process.env.SITE_APPEARANCE_PATH ||
    path.join(os.tmpdir(), 'campuscore-site-appearance.json')
  );
}

let memory: SiteAppearance | null = null;

export async function readSiteAppearance(): Promise<SiteAppearance> {
  if (memory) {
    return memory;
  }

  try {
    const raw = await fs.readFile(appearancePath(), 'utf8');
    memory = sanitizeSiteAppearance(JSON.parse(raw));
    return memory;
  } catch {
    memory = { ...DEFAULT_SITE_APPEARANCE };
    return memory;
  }
}

export async function writeSiteAppearance(
  input: unknown,
): Promise<SiteAppearance> {
  const next = sanitizeSiteAppearance({
    ...sanitizeSiteAppearance(input),
    version: Date.now(),
    updatedAt: new Date().toISOString(),
  });
  memory = next;
  await fs.mkdir(path.dirname(appearancePath()), { recursive: true });
  await fs.writeFile(appearancePath(), `${JSON.stringify(next, null, 2)}\n`, 'utf8');
  return next;
}
