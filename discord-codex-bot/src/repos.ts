export const REPOS = {
  VtuberCamera_KMP_ver: "/Users/j____takumi/Android_development/VtuberCamera_KMP_ver"
} as const;

export type RepoKey = keyof typeof REPOS;

export function isRepoKey(value: string): value is RepoKey {
  return Object.hasOwn(REPOS, value);
}
