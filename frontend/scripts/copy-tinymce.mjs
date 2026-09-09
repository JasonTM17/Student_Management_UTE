import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

const srcDir = path.join(rootDir, 'node_modules', 'tinymce');
const destDir = path.join(rootDir, 'public', 'tinymce');

if (fs.existsSync(srcDir)) {
  if (!fs.existsSync(destDir)) {
    fs.mkdirSync(destDir, { recursive: true });
  }
  fs.cpSync(srcDir, destDir, { recursive: true });
  console.log('[copy-tinymce] Successfully copied TinyMCE assets to public/tinymce');
} else {
  console.warn('[copy-tinymce] node_modules/tinymce not found, skipping copy.');
}
