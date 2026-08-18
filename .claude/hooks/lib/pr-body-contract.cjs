/**
 * Evidence-rich PR body contract for ak:ship (#1195).
 * ak:review-pr validates these sections without inventing missing narrative.
 */

'use strict';

/** Stable section ids used by validators and skill docs. */
const REQUIRED_SECTIONS = [
  {
    id: 'end-to-end-summary',
    headingPatterns: [
      /^#{2,3}\s+End-to-end work summary\s*$/im,
      /^#{2,3}\s+Tóm tắt công việc end-to-end\s*$/im
    ]
  },
  {
    id: 'subagent-delegation',
    headingPatterns: [
      /^#{2,3}\s+Subagent delegation\s*$/im,
      /^#{2,3}\s+Ủy thác subagent\s*$/im
    ]
  },
  {
    id: 'technical-decisions',
    headingPatterns: [
      /^#{2,3}\s+Technical decisions\s*$/im,
      /^#{2,3}\s+Quyết định kỹ thuật\s*$/im
    ]
  },
  {
    id: 'deviations-from-plan',
    headingPatterns: [
      /^#{2,3}\s+Deviations from plan\s*$/im,
      /^#{2,3}\s+Lệch so với plan\s*$/im
    ]
  },
  {
    id: 'completion-evidence',
    headingPatterns: [
      /^#{2,3}\s+Completion evidence\s*$/im,
      /^#{2,3}\s+Bằng chứng hoàn thành\s*$/im
    ]
  },
  {
    id: 'checklist',
    headingPatterns: [/^#{2,3}\s+Checklist\s*$/im]
  },
  {
    id: 'human-actions-required',
    headingPatterns: [
      /^#{2,3}\s+Human actions required\s*$/im,
      /^#{2,3}\s+Việc cần người xử lý\s*$/im
    ]
  }
];

/**
 * Traceability sections retained from the previous ship template.
 * Missing these is Important for ship-authored PRs, Suggestion otherwise.
 */
const TRACEABILITY_SECTIONS = [
  {
    id: 'linked-issues',
    headingPatterns: [
      /^#{2,3}\s+Linked Issues\s*$/im,
      /^#{2,3}\s+Issues liên quan\s*$/im
    ]
  },
  {
    id: 'ship-mode',
    headingPatterns: [
      /^#{2,3}\s+Ship Mode\s*$/im,
      /^#{2,3}\s+Chế độ ship\s*$/im
    ]
  }
];

/**
 * @param {string} body
 * @param {{ requireTraceability?: boolean }} [options]
 * @returns {{
 *   ok: boolean,
 *   missingRequired: string[],
 *   missingTraceability: string[],
 *   findings: Array<{ severity: 'Important'|'Suggestion', id: string, message: string }>
 * }}
 */
function validateShipPrBody(body, options = {}) {
  const text = typeof body === 'string' ? body : '';
  const requireTraceability = options.requireTraceability !== false;
  const missingRequired = [];
  const missingTraceability = [];
  const findings = [];

  for (const section of REQUIRED_SECTIONS) {
    const present = section.headingPatterns.some((re) => re.test(text));
    if (!present) {
      missingRequired.push(section.id);
      findings.push({
        severity: 'Important',
        id: section.id,
        message: `Missing required PR body section: ${section.id}`
      });
    }
  }

  for (const section of TRACEABILITY_SECTIONS) {
    const present = section.headingPatterns.some((re) => re.test(text));
    if (!present) {
      missingTraceability.push(section.id);
      if (requireTraceability) {
        findings.push({
          severity: 'Suggestion',
          id: section.id,
          message: `Missing traceability section: ${section.id}`
        });
      }
    }
  }

  const ok =
    missingRequired.length === 0 &&
    (!requireTraceability || missingTraceability.length === 0);

  if (requireTraceability) {
    for (const finding of findings) {
      if (
        finding.severity === 'Suggestion' &&
        (finding.id === 'linked-issues' || finding.id === 'ship-mode')
      ) {
        finding.severity = 'Important';
      }
    }
  }

  return {
    ok,
    missingRequired,
    missingTraceability,
    findings
  };
}

function main(argv = process.argv.slice(2)) {
  const fs = require('fs');
  const fileFlag = argv.indexOf('--file');
  const loose = argv.includes('--loose');
  let body = '';
  if (fileFlag >= 0 && argv[fileFlag + 1]) {
    body = fs.readFileSync(argv[fileFlag + 1], 'utf8');
  } else if (!process.stdin.isTTY) {
    body = fs.readFileSync(0, 'utf8');
  } else {
    process.stderr.write(
      'usage: pr-body-contract.cjs [--loose] --file <path> | <body on stdin>\n'
    );
    process.exit(2);
  }

  const result = validateShipPrBody(body, { requireTraceability: !loose });
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  process.exit(result.ok ? 0 : 1);
}

module.exports = {
  REQUIRED_SECTIONS,
  TRACEABILITY_SECTIONS,
  validateShipPrBody
};

if (require.main === module) {
  main();
}
