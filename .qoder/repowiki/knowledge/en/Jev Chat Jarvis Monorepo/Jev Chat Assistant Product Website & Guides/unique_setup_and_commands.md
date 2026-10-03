Guide generation requires Node.js:
- `node tools/build-guides.mjs` — regenerate all guides into `guides/`.
- `node tools/build-guides.mjs --check` — CI-style check that generated files match source (exits 1 on stale output).
- `node tools/check-i18n.mjs --fix` — synchronizes Chinese text in HTML with `i18n.js` translation keys.