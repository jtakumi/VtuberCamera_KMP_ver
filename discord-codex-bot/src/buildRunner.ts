import { mkdir, stat, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { runProcess } from "./processRunner.js";

export type BuildTarget = "androidDebug";

type BuildTaskParams = {
  repoKey: string;
  repoPath: string;
  target: BuildTarget;
  requestedBy: string;
};

type BuildTaskResult = {
  logPath: string;
  artifactPath?: string;
  exitCode: number;
};

const LOG_DIR = fileURLToPath(new URL("../logs/", import.meta.url));
const BUILD_TIMEOUT_MS = 20 * 60 * 1000;

export async function runBuildTask(
  params: BuildTaskParams
): Promise<BuildTaskResult> {
  const startedAt = new Date();
  const logPath = join(
    LOG_DIR,
    buildLogFileName(startedAt, params.repoKey, params.target)
  );
  const gradleUserHome =
    process.env.GRADLE_USER_HOME || join(params.repoPath, ".gradle-bot");
  const command = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
  const args = buildArgs(params.target);
  const env = {
    ...process.env,
    GRADLE_USER_HOME: gradleUserHome
  };

  await mkdir(LOG_DIR, { recursive: true });
  await mkdir(gradleUserHome, { recursive: true });

  const result = await runProcess({
    cwd: params.repoPath,
    command,
    args,
    env,
    timeoutMs: BUILD_TIMEOUT_MS
  });
  const artifactPath = await findArtifact(params.repoPath, params.target);

  await writeFile(
    logPath,
    [
      `startedAt: ${startedAt.toISOString()}`,
      `finishedAt: ${new Date().toISOString()}`,
      `requestedBy: ${params.requestedBy}`,
      `repo: ${params.repoKey}`,
      `repoPath: ${params.repoPath}`,
      `target: ${params.target}`,
      `command: ${command} ${args.join(" ")}`,
      `gradleUserHome: ${gradleUserHome}`,
      `timeoutMs: ${BUILD_TIMEOUT_MS}`,
      `timedOut: ${result.timedOut}`,
      `exitCode: ${result.exitCode}`,
      artifactPath ? `artifactPath: ${artifactPath}` : "artifactPath:",
      "",
      "STDOUT:",
      result.stdout,
      "",
      "STDERR:",
      result.stderr
    ].join("\n"),
    "utf8"
  );

  if (result.exitCode === 0) {
    return {
      logPath,
      artifactPath,
      exitCode: result.exitCode
    };
  }

  throw new BuildTaskError(
    [
      result.timedOut
        ? "Build timed out after 20 minutes."
        : `Build failed with exit code ${result.exitCode}.`,
      "",
      "STDERR:",
      truncate(result.stderr, 3500),
      "",
      "STDOUT:",
      truncate(result.stdout, 1500),
      "",
      `Log: ${logPath}`
    ].join("\n"),
    logPath,
    result.exitCode
  );
}

export class BuildTaskError extends Error {
  constructor(
    message: string,
    readonly logPath: string,
    readonly exitCode: number
  ) {
    super(message);
    this.name = "BuildTaskError";
  }
}

export function isBuildTarget(value: string): value is BuildTarget {
  return value === "androidDebug";
}

function buildArgs(target: BuildTarget): string[] {
  if (target === "androidDebug") {
    return [":composeApp:assembleDebug", "--console=plain"];
  }

  const exhaustive: never = target;
  return exhaustive;
}

async function findArtifact(
  repoPath: string,
  target: BuildTarget
): Promise<string | undefined> {
  if (target !== "androidDebug") return undefined;

  const artifactPath = join(
    repoPath,
    "composeApp",
    "build",
    "outputs",
    "apk",
    "debug",
    "composeApp-debug.apk"
  );

  try {
    await stat(artifactPath);
    return artifactPath;
  } catch (error) {
    const code = (error as NodeJS.ErrnoException).code;
    if (code === "ENOENT") return undefined;
    throw error;
  }
}

function buildLogFileName(date: Date, repoKey: string, target: BuildTarget): string {
  const stamp = date.toISOString().replace(/[:.]/g, "-");
  return `${stamp}_${sanitize(repoKey)}_build_${target}.log`;
}

function sanitize(value: string): string {
  return value.replace(/[^a-zA-Z0-9_-]/g, "_");
}

function truncate(value: string, max: number): string {
  return value.length > max ? `${value.slice(0, max)}\n...truncated` : value;
}
