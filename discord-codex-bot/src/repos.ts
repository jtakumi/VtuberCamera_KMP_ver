function requiredRepoPath(envKey: string, repoName: string): string {
  const value = process.env[envKey];
  if (!value) {
    throw new Error(
      `Missing required environment variable: ${envKey} (absolute path to ${repoName})`
    );
  }
  return value;
}

export const REPOS = {
  VtuberCamera_KMP_ver: requiredRepoPath(
    "REPO_VTUBERCAMERA_KMP_VER",
    "VtuberCamera_KMP_ver"
  )
} as const;

export type RepoKey = keyof typeof REPOS;

export function isRepoKey(value: string): value is RepoKey {
  return Object.hasOwn(REPOS, value);
}
