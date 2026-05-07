import "dotenv/config";
import { runBuildTask } from "./buildRunner.js";
import { REPOS } from "./repos.js";

const repoKey = "VtuberCamera_KMP_ver";
const result = await runBuildTask({
  repoKey,
  repoPath: REPOS[repoKey],
  target: "androidDebug",
  requestedBy: "local npm script"
});

console.log("Android debug build completed.");
console.log(`log: ${result.logPath}`);
console.log(`apk: ${result.artifactPath ?? "not found"}`);
