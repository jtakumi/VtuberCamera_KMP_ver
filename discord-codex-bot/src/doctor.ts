import "dotenv/config";
import { access } from "node:fs/promises";
import { join } from "node:path";
import { runProcess } from "./processRunner.js";

type Check = {
  name: string;
  ok: boolean;
  detail: string;
};

const checks: Check[] = [];

await checkCommand("Node", process.execPath, ["--version"]);
await checkCommand("npm", process.platform === "win32" ? "npm.cmd" : "npm", [
  "--version"
]);
await checkCommand(
  "Codex",
  process.env.CODEX_BIN || (process.platform === "win32" ? "codex.cmd" : "codex"),
  ["--version"]
);

checkEnv("DISCORD_TOKEN");
checkEnv("DISCORD_CLIENT_ID");
checkEnv("DISCORD_GUILD_ID");
checkEnv("ALLOWED_USER_IDS");
checkOptionalEnv("CODEX_BIN");
checkOptionalEnv("GRADLE_USER_HOME");

const repoPath = process.env.REPO_VTUBERCAMERA_KMP_VER;
if (repoPath) {
  await checkPath("Repo VtuberCamera_KMP_ver", repoPath);
  await checkPath(
    "Gradle wrapper VtuberCamera_KMP_ver",
    join(repoPath, process.platform === "win32" ? "gradlew.bat" : "gradlew")
  );
  await checkPath(
    "Android SDK local.properties VtuberCamera_KMP_ver",
    join(repoPath, "local.properties")
  );
}

for (const check of checks) {
  console.log(`${check.ok ? "OK" : "FAIL"} ${check.name}: ${check.detail}`);
}

if (checks.some((check) => !check.ok)) {
  process.exitCode = 1;
}

async function checkCommand(name: string, command: string, args: string[]) {
  try {
    const result = await runProcess({
      cwd: process.cwd(),
      command,
      args,
      env: process.env,
      timeoutMs: 15_000
    });
    checks.push({
      name,
      ok: result.exitCode === 0,
      detail:
        result.stdout.trim().split(/\r?\n/)[0] ||
        result.stderr.trim().split(/\r?\n/)[0] ||
        `exit code ${result.exitCode}`
    });
  } catch (error) {
    checks.push({
      name,
      ok: false,
      detail: error instanceof Error ? error.message : String(error)
    });
  }
}

function checkEnv(name: string) {
  const value = process.env[name];
  checks.push({
    name: `Env ${name}`,
    ok: Boolean(value),
    detail: value ? "set" : "missing"
  });
}

function checkOptionalEnv(name: string) {
  const value = process.env[name];
  checks.push({
    name: `Env ${name}`,
    ok: true,
    detail: value ? value : "not set"
  });
}

async function checkPath(name: string, path: string) {
  try {
    await access(path);
    checks.push({ name, ok: true, detail: path });
  } catch {
    checks.push({ name, ok: false, detail: `missing: ${path}` });
  }
}
