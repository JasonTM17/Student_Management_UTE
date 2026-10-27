import { access, writeFile, unlink } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const directory = path.resolve(root, process.env.DOCS_CAPTURE_OUTPUT || 'docs/assets/showcase-2026-10');
const tours = [];
for (const locale of ['vi', 'en']) {
  tours.push({ name: `student-tour-${locale}`, files: ['student-dashboard', 'student-registration', 'student-schedule', 'student-transcript', 'student-thesis', 'student-assistant-answer'].map(x => `${x}-${locale}.png`) });
  tours.push({ name: `staff-tour-${locale}`, files: ['lecturer-dashboard', 'lecturer-grades', 'lecturer-attendance', 'lecturer-mail', 'admin-dashboard', 'admin-knowledge'].map(x => `${x}-${locale}.png`) });
  tours.push({ name: `thesis-governance-tour-${locale}`, files: ['student-thesis', 'lecturer-thesis', 'dean-dashboard', 'dean-thesis', 'admin-thesis', 'admin-audit'].map(x => `${x}-${locale}.png`) });
}
tours.push({ name: 'language-tour', files: ['public-home-vi.png', 'public-home-en.png', 'student-dashboard-vi.png', 'student-dashboard-en.png', 'dean-dashboard-vi.png', 'dean-dashboard-en.png'] });

const list = path.join(directory, '.tour-frames.txt');
try {
  for (const tour of tours) {
    for (const file of tour.files) await access(path.join(directory, file));
    const lines = tour.files.flatMap(file => [`file '${path.join(directory, file).replaceAll('\\', '/')}'`, 'duration 4']);
    lines.push(`file '${path.join(directory, tour.files.at(-1)).replaceAll('\\', '/')}'`);
    await writeFile(list, lines.join('\n') + '\n');
    const result = spawnSync('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', list,
      '-filter_complex', 'fps=2,scale=1200:-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=3',
      '-t', String(tour.files.length * 4), '-loop', '0', path.join(directory, `${tour.name}.gif`)], { encoding: 'utf8' });
    if (result.error || result.status !== 0) throw new Error(result.error?.message || result.stderr || 'FFmpeg failed');
    console.log(`ENCODE ${tour.name}.gif`);
  }
  await writeFile(path.join(directory, 'tours.json'), JSON.stringify({
    kind: 'Animated sequence of actual screenshots; not continuous action recordings.',
    secondsPerScreen: 4, width: 1200, tours,
  }, null, 2) + '\n');
} finally { await unlink(list).catch(error => { if (error.code !== 'ENOENT') throw error; }); }
