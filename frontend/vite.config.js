var _a, _b, _c;
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
var runtime = globalThis;
var apiTarget = (_c = (_b = (_a = runtime.process) === null || _a === void 0 ? void 0 : _a.env) === null || _b === void 0 ? void 0 : _b.VITE_API_TARGET) !== null && _c !== void 0 ? _c : 'http://localhost:8080';
export default defineConfig({
    plugins: [react()],
    server: {
        port: 3000,
        proxy: {
            '/api': apiTarget,
            '/actuator': apiTarget,
            '/ws': { target: apiTarget.replace(/^http/, 'ws'), ws: true },
        },
    },
});
