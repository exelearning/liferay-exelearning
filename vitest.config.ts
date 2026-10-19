import { defineConfig } from 'vitest/config';

export default defineConfig({
    test: {
        environment: 'happy-dom',
        environmentOptions: {
            happyDOM: {
                url: 'https://portal.example/group/site/~/control_panel/manage',
                settings: {
                    disableCSSFileLoading: true,
                    disableIframePageLoading: true,
                    disableJavaScriptFileLoading: true,
                    handleDisabledFileLoadingAsSuccess: true,
                },
            },
        },
        include: ['frontend/test/**/*.test.ts'],
        coverage: {
            provider: 'v8',
            include: ['frontend/src/**/*.ts'],
            reporter: ['text', 'lcov', 'json-summary'],
            reportsDirectory: 'build/reports/frontend-coverage',
            thresholds: { lines: 90, statements: 90, functions: 90, branches: 85 },
        },
    },
});
