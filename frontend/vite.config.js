var _a, _b, _c, _d, _e, _f;
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
var runtime = globalThis;
var apiTarget = (_c = (_b = (_a = runtime.process) === null || _a === void 0 ? void 0 : _a.env) === null || _b === void 0 ? void 0 : _b.VITE_API_TARGET) !== null && _c !== void 0 ? _c : 'http://localhost:8080';
var agentTarget = (_f = (_e = (_d = runtime.process) === null || _d === void 0 ? void 0 : _d.env) === null || _e === void 0 ? void 0 : _e.VITE_AGENT_TARGET) !== null && _f !== void 0 ? _f : 'http://localhost:8010';
export default defineConfig({
    plugins: [react()],
    server: {
        port: 3000,
        proxy: {
            '/api': apiTarget,
            '/copilot': agentTarget,
            '/actuator': apiTarget,
            '/ws': { target: apiTarget.replace(/^http/, 'ws'), ws: true },
        },
    },
});
