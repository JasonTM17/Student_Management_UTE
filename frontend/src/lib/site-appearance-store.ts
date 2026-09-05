import { promises as fs } from 'node:fs';
import path from 'node:path';
import {
  DEFAULT_SITE_APPEARANCE,
  sanitizeSiteAppearance,
  type SiteAppearance,
} from '@/lib/site-appearance';

function appearancePath(): string {
  if (process.env.SITE_APPEARANCE_PATH) {
    return process.env.SITE_APPEARANCE_PATH;
  }
  if (process.env.VERCEL || process.env.AWS_LAMBDA_FUNCTION_NAME) {
    return path.join('/tmp', 'campuscore-site-appearance.json');
  }
  return path.join('.data', 'campuscore-site-appearance.json');
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
  try {
    await fs.mkdir(path.dirname(appearancePath()), { recursive: true });
    await fs.writeFile(appearancePath(), `${JSON.stringify(next, null, 2)}\n`, 'utf8');
  } catch (err) {
    console.warn('Could not persist site appearance to filesystem:', err);
  }
  return next;
}
