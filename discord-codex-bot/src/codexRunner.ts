import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { runProcess } from "./processRunner.js";

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
  const lastMessagePath = join(
    LOG_DIR,
    logFileName.replace(/\.log$/, ".last-message.md")
  );
  const codexBin = process.env.CODEX_BIN || defaultCodexBin();

  await mkdir(LOG_DIR, { recursive: true });

  const args = [
    "exec",
    "--cd",
    params.repoPath,
    "--sandbox",
    sandbox,
    "--output-last-message",
    lastMessagePath,
    "-"
  ];

  const result = await runProcess({
    cwd: params.repoPath,
    command: codexBin,
    args,
    env: process.env,
    input: systemInstruction
  });
  const finalMessage = await readTextIfPresent(lastMessagePath);

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
      `codexBin: ${codexBin}`,
      `lastMessagePath: ${lastMessagePath}`,
      `exitCode: ${result.exitCode}`,
      "",
      "PROMPT:",
      params.prompt,
      "",
      "LAST_MESSAGE:",
      finalMessage,
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
      finalMessage:
        finalMessage.trim() || result.stdout.trim() || "Codex task completed.",
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
      truncate(finalMessage || result.stdout, 1500),
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

function defaultCodexBin(): string {
  return process.platform === "win32" ? "codex.cmd" : "codex";
}

async function readTextIfPresent(path: string): Promise<string> {
  try {
    return await readFile(path, "utf8");
  } catch (error) {
    const code = (error as NodeJS.ErrnoException).code;
    if (code === "ENOENT") return "";
    throw error;
  }
}
