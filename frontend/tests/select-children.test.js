const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

function load(relativePath) {
  const source = fs.readFileSync(path.resolve(__dirname, '..', relativePath), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
      jsx: ts.JsxEmit.React, esModuleInterop: true },
  }).outputText;
  const record = { exports: {} };
  const resolve = name => name === '@/lib/utils' ? load('src/lib/utils.ts') : require(name);
  Function('module', 'exports', 'require', output)(record, record.exports, resolve);
  return record.exports;
}

const { Select } = load('src/components/ui/select.tsx');
const option = (value, label) => React.createElement('option', { value, key: value }, label);
const render = props => renderToStaticMarkup(React.createElement(Select, props));

test('Select renders native option children and the controlled selected value', () => {
  const html = render({ id: 'section', label: 'Class section', value: 'audit', onChange() {},
    children: [option('demo', 'Demo class'), option('audit', 'Audit class')] });
  assert.match(html, /<option value="demo">Demo class<\/option>/);
  assert.match(html, /<option value="audit" selected="">Audit class<\/option>/);
  assert.match(html, /<label for="section"/);
});

test('Select preserves groups and options precedence over native children', () => {
  const props = { children: option('child', 'Native child'),
    options: [{ value: 'array', label: 'Array option' }] };
  const array = render(props);
  assert.match(array, /Array option/);
  assert.doesNotMatch(array, /Native child/);
  const grouped = render({ ...props, groups: [{ label: 'Semester', options: [{ value: 'group', label: 'Grouped option' }] }] });
  assert.match(grouped, /<optgroup label="Semester"><option value="group">Grouped option/);
  assert.doesNotMatch(grouped, /Array option|Native child/);
});

test('Select explicit empty groups or options remain empty', () => {
  assert.doesNotMatch(render({ options: [], children: option('child', 'Native child') }), /<option/);
  assert.doesNotMatch(render({ groups: [], options: [{ value: 'array', label: 'Array option' }],
    children: option('child', 'Native child') }), /<option/);
});
