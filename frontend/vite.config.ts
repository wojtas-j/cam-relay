import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import fs from "fs";

export default defineConfig({
    plugins: [react()],
    server: {
        https: {
            key: fs.readFileSync("/app/certs/localhost-key.pem"),
            cert: fs.readFileSync("/app/certs/localhost.pem"),
        },
        port: 3000,
        host: "0.0.0.0",
    },
    preview: {
        https: {
            key: fs.readFileSync("/app/certs/localhost-key.pem"),
            cert: fs.readFileSync("/app/certs/localhost.pem"),
        },
        port: 4173,
        host: "0.0.0.0",
    },
});
