// RaTeX's `ratex-wasm` npm package only declares its "." entry under the ESM
// `import` condition. Kotlin/JS production webpack resolves with CommonJS
// conditions (require/production) and omits `import`, so it can't find the
// package root. Add `import` to the resolved condition names (keeping webpack's
// defaults via the `...` token) so the ESM entry is honored.
config.resolve = config.resolve || {};
config.resolve.conditionNames = ["...", "import"];
