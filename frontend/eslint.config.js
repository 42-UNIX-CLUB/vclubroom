import js from "@eslint/js";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";
import tseslint from "typescript-eslint";

const FEATURES = [
  "auth", "discovery", "clubs", "membership", "groups", "spaces", "forum",
  "chat", "media", "calendar", "notifications", "history", "admin", "support", "platform", "developer",
];

export default [
  { ignores: ["dist", "src/shared/api/schema.d.ts"] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["src/**/*.{ts,tsx}"],
    languageOptions: { globals: globals.browser },
    plugins: { "react-hooks": reactHooks },
    rules: {
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "warn",
      // Features talk to each other only through their public index (mirror of Spring Modulith rules).
      // Inside a feature, use relative imports.
      "no-restricted-imports": ["error", {
        patterns: [{
          group: FEATURES.map((f) => `@/features/${f}/*`),
          message: "Import other features only via their public API: '@/features/<feature>'.",
        }],
      }],
    },
  },
  {
    // shared/ must never depend on features/
    files: ["src/shared/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": ["error", { patterns: [{ group: ["@/features/*"], message: "shared must not import features." }] }],
    },
  },
];
