import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { createServer } from "node:http";
import { createServer as createTcpServer } from "node:net";
import { fileURLToPath } from "node:url";

const slug = "task-5-proxy-check";
const imagePath = `/public/artworks/${slug}/image`;
const requests = [];
const image = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
  "base64",
);

const backend = createServer((request, response) => {
  requests.push(request.url);
  if (request.url === `/public/artworks/${slug}`) {
    response.writeHead(200, { "Content-Type": "application/json" });
    response.end(JSON.stringify({
      title: "Proxy Study",
      credit: "Integration Artist",
      imageUrl: imagePath,
      descriptions: [{ label: "Objective", text: "A small blue square." }],
    }));
    return;
  }
  if (request.url === imagePath) {
    response.writeHead(200, { "Content-Type": "image/png", "Content-Length": image.length });
    response.end(image);
    return;
  }
  response.writeHead(404).end();
});

let vite;
try {
  const backendPort = await listen(backend);
  const frontendPort = await availablePort();
  const logs = [];
  vite = spawn(process.execPath, [
    fileURLToPath(new URL("../node_modules/vite/bin/vite.js", import.meta.url)),
    "--host", "127.0.0.1", "--port", String(frontendPort), "--strictPort",
  ], {
    cwd: fileURLToPath(new URL("..", import.meta.url)),
    env: { ...process.env, CANVAS_BACKEND_ORIGIN: `http://127.0.0.1:${backendPort}` },
    stdio: ["ignore", "pipe", "pipe"],
  });
  vite.stdout.on("data", (chunk) => logs.push(chunk.toString()));
  vite.stderr.on("data", (chunk) => logs.push(chunk.toString()));
  await waitForUrl(`http://127.0.0.1:${frontendPort}/`);

  const chrome = browserExecutable();
  const pageUrl = `http://127.0.0.1:${frontendPort}/artworks/${slug}`;
  const { code, stdout, stderr } = await run(chrome, [
    "--headless=new",
    "--no-sandbox",
    "--disable-gpu",
    "--disable-dev-shm-usage",
    "--virtual-time-budget=3000",
    "--dump-dom",
    pageUrl,
  ]);
  if (code !== 0) throw new Error(`Browser exited ${code}: ${stderr}`);
  assert(stdout.includes("Proxy Study"), "public artwork JSON did not render through Vite");
  assert(stdout.includes(`src="${imagePath}"`), "backend image URL was not rendered");
  assert(requests.includes(`/public/artworks/${slug}`), "Vite did not proxy the public artwork JSON request");
  assert(requests.includes(imagePath), "the browser did not load the public image through Vite");
  process.stdout.write("Task 5 frontend proxy integration passed: public JSON and image loaded through Vite.\n");
} finally {
  if (vite && vite.exitCode === null) vite.kill("SIGTERM");
  await new Promise((resolve) => backend.close(resolve));
}

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => resolve(server.address().port));
  });
}

async function availablePort() {
  const server = createTcpServer();
  const port = await listen(server);
  await new Promise((resolve) => server.close(resolve));
  return port;
}

async function waitForUrl(url) {
  let lastError;
  for (let attempt = 0; attempt < 80; attempt += 1) {
    try {
      const response = await fetch(url);
      if (response.ok) return;
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`Vite did not start at ${url}: ${lastError ?? "timed out"}`);
}

function browserExecutable() {
  const candidates = [process.env.BROWSER_EXECUTABLE_PATH, "/usr/bin/google-chrome", "/usr/bin/chromium"]
    .filter(Boolean);
  const executable = candidates.find((candidate) => existsSync(candidate));
  if (!executable) throw new Error("Set BROWSER_EXECUTABLE_PATH to a Chromium-compatible browser executable.");
  return executable;
}

function run(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"] });
    let stdout = "";
    let stderr = "";
    child.stdout.on("data", (chunk) => { stdout += chunk; });
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    child.once("error", reject);
    child.once("close", (code) => resolve({ code, stdout, stderr }));
  });
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}
