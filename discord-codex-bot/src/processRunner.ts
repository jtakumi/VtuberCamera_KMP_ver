import { spawn } from "node:child_process";

export type ProcessResult = {
  stdout: string;
  stderr: string;
  exitCode: number;
  timedOut: boolean;
};

type RunProcessOptions = {
  cwd: string;
  command: string;
  args: string[];
  env?: NodeJS.ProcessEnv;
  input?: string;
  timeoutMs?: number;
};

export function runProcess(options: RunProcessOptions): Promise<ProcessResult> {
  return new Promise((resolve, reject) => {
    const child = spawnCommand(options.command, options.args, {
      cwd: options.cwd,
      env: options.env ?? process.env
    });

    let stdout = "";
    let stderr = "";
    let timedOut = false;

    const timeout =
      options.timeoutMs === undefined
        ? undefined
        : setTimeout(() => {
            timedOut = true;
            child.kill("SIGTERM");
          }, options.timeoutMs);

    child.stdout.on("data", (data: Buffer) => {
      stdout += data.toString();
    });

    child.stderr.on("data", (data: Buffer) => {
      stderr += data.toString();
    });

    if (options.input !== undefined) {
      child.stdin.write(options.input);
    }
    child.stdin.end();

    child.on("error", (error) => {
      if (timeout) clearTimeout(timeout);
      reject(error);
    });

    child.on("close", (code) => {
      if (timeout) clearTimeout(timeout);
      resolve({
        stdout,
        stderr,
        exitCode: timedOut ? 124 : code ?? 1,
        timedOut
      });
    });
  });
}

function spawnCommand(
  command: string,
  args: string[],
  options: { cwd: string; env: NodeJS.ProcessEnv }
) {
  if (process.platform === "win32" && /\.(cmd|bat)$/i.test(command)) {
    return spawn(command, args, {
      cwd: options.cwd,
      env: options.env,
      shell: true
    });
  }

  return spawn(command, args, {
    cwd: options.cwd,
    env: options.env,
    shell: false
  });
}
