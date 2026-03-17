const google = require("eslint-config-google");

module.exports = [
  google,
  {
    languageOptions: {
      ecmaVersion: 6,
      sourceType: "commonjs",
      globals: {
        process: "readonly",
        __dirname: "readonly",
        __filename: "readonly",
        exports: "writable",
        module: "readonly",
        require: "readonly",
      }
    },
    rules: {
      "valid-jsdoc": "off",
      "require-jsdoc": "off",
      "quotes": ["error", "double"]
    }
  }
];
