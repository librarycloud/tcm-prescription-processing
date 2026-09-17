export default {
  'web-admin/**/*.{js,vue}': [
    'npx --prefix web-admin eslint --config web-admin/eslint.config.js --fix',
    'npx --prefix web-admin prettier --write'
  ],
  'web-user/**/*.{js,vue}': [
    'npx --prefix web-user eslint --config web-user/eslint.config.js --fix',
    'npx --prefix web-user prettier --write'
  ],
  'backend/**/*.js': () => 'npm --prefix backend test'
};
