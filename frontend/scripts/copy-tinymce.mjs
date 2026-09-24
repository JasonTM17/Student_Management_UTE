import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

const srcDir = path.join(rootDir, 'node_modules', 'tinymce');
const destDir = path.join(rootDir, 'public', 'tinymce');

function copyDir(from, to) {
  if (!fs.existsSync(to)) {
    fs.mkdirSync(to, { recursive: true });
  }
  fs.cpSync(from, to, { recursive: true });
}

if (fs.existsSync(srcDir)) {
  copyDir(srcDir, destDir);
  // TinyMCE 8 autosave may see an editor during route teardown before its DOM
  // has finished initializing. Its global BeforeUnload handler then calls
  // editor.dom.isEmpty and crashes. Keep draft saving for initialized editors.
  const autosavePath = path.join(destDir, 'plugins', 'autosave', 'plugin.min.js');
  const autosave = fs.readFileSync(autosavePath, 'utf8');
  const unsafeCheck = 'if(o(e))return t.dom.isEmpty(t.getBody());';
  if (autosave.split(unsafeCheck).length !== 2) {
    throw new Error('TinyMCE autosave guard needs review for this vendor version');
  }
  fs.writeFileSync(autosavePath, autosave.replace(
    unsafeCheck,
    'if(!t.dom||!t.getBody())return!0;if(o(e))return t.dom.isEmpty(t.getBody());',
  ));
  console.log('[copy-tinymce] Successfully copied TinyMCE assets to public/tinymce');
} else {
  console.warn('[copy-tinymce] node_modules/tinymce not found, skipping copy.');
}

// RT-P3-1: self-hosted Vietnamese UI language pack for TinyMCE 8, so the
// editor menus are localized for Vietnamese authors without any Tiny Cloud
// request. `tinymce-i18n` ships the packs per major version under `langs8/`.
const langPackDir = path.join(rootDir, 'node_modules', 'tinymce-i18n', 'langs8');
const viSource = path.join(langPackDir, 'vi.js');
const langsDest = path.join(destDir, 'langs');
const viDest = path.join(langsDest, 'vi.js');

if (fs.existsSync(viSource)) {
  if (!fs.existsSync(langsDest)) {
    fs.mkdirSync(langsDest, { recursive: true });
  }
  fs.copyFileSync(viSource, viDest);
  console.log('[copy-tinymce] Copied Vietnamese language pack to public/tinymce/langs/vi.js');
} else {
  console.warn('[copy-tinymce] tinymce-i18n lang pack not found, skipping Vietnamese pack copy.');
}
