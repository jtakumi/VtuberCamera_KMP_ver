import { spawn } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

export type CodexMode = "ask" | "fix" | "pr";

type CodexTaskParams = {
  repoKey: string;
  repoPath: string;
  mode: CodexMode;
  prompt: string;
  requestedBy: string;
};

type CodexTaskResult = {
  finalMessage: string;
  logPath: string;
  exitCode: number;
};

const LOG_DIR = fileURLToPath(new URL("../logs/", import.meta.url));

export async function runCodexTask(params: CodexTaskParams): Promise<CodexTaskResult> {
  const systemInstruction = buildPrompt(params.mode, params.prompt);
  const sandbox = params.mode === "fix" ? "workspace-write" : "read-only";
  const startedAt = new Date();
  const logFileName = buildLogFileName(startedAt, params.repoKey, params.mode);
  const logPath = join(LOG_DIR, logFileName);

  const args = [
    "exec",
    "--cd",
    params.repoPath,
    "--sandbox",
    sandbox,
    systemInstruction
  ];

  const result = await runProcess(params.repoPath, args);

  await mkdir(LOG_DIR, { recursive: true });
  await writeFile(
    logPath,
    [
      `startedAt: ${startedAt.toISOString()}`,
      `finishedAt: ${new Date().toISOString()}`,
      `requestedBy: ${params.requestedBy}`,
      `repo: ${params.repoKey}`,
      `repoPath: ${params.repoPath}`,
      `mode: ${params.mode}`,
      `sandbox: ${sandbox}`,
      `exitCode: ${result.exitCode}`,
      "",
      "PROMPT:",
      params.prompt,
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
      finalMessage: result.stdout.trim() || "Codex task completed.",
      logPath,
      exitCode: result.exitCode
    };
  }

  throw new CodexTaskError(
    [
      `Codex failed with exit code ${result.exitCode}.`,
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

export class CodexTaskError extends Error {
  constructor(
    message: string,
    readonly logPath: string,
    readonly exitCode: number
  ) {
    super(message);
    this.name = "CodexTaskError";
  }
}

function runProcess(
  cwd: string,
  args: string[]
): Promise<{ stdout: string; stderr: string; exitCode: number }> {
  return new Promise((resolve, reject) => {
    const child = spawn("codex", args, {
      cwd,
      env: process.env,
      shell: false
    });

    let stdout = "";
    let stderr = "";

    child.stdout.on("data", (data: Buffer) => {
      stdout += data.toString();
    });

    child.stderr.on("data", (data: Buffer) => {
      stderr += data.toString();
    });

    child.on("error", reject);

    child.on("close", (code) => {
      resolve({
        stdout,
        stderr,
        exitCode: code ?? 1
      });
    });
  });
}

function buildPrompt(mode: CodexMode, userPrompt: string): string {
  if (mode === "ask") {
    return [
      "You are working in read-only investigation mode.",
      "Do not modify files.",
      "Analyze the repository and answer concisely in Japanese.",
      "",
      `User request: ${userPrompt}`
    ].join("\n");
  }

  if (mode === "fix") {
    return [
      "You are working in implementation mode.",
      "Make minimal, focused changes.",
      "Do not commit, push, or create a pull request.",
      "After editing, summarize changed files, risks, and suggested tests in Japanese.",
      "",
      `User request: ${userPrompt}`
    ].join("\n");
  }

  return [
    "You are preparing a pull request summary.",
    "Inspect the current git diff.",
    "Do not modify files.",
    "Write a Japanese PR title, summary, test plan, and risk notes.",
    "",
    `User request: ${userPrompt}`
  ].join("\n");
}

function buildLogFileName(date: Date, repoKey: string, mode: CodexMode): string {
  const stamp = date.toISOString().replace(/[:.]/g, "-");
  return `${stamp}_${sanitize(repoKey)}_${mode}.log`;
}

function sanitize(value: string): string {
  return value.replace(/[^a-zA-Z0-9_-]/g, "_");
}

function truncate(value: string, max: number): string {
  return value.length > max ? `${value.slice(0, max)}\n...truncated` : value;
}
