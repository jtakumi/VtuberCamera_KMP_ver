import "dotenv/config";
import {
  ChatInputCommandInteraction,
  Client,
  Events,
  GatewayIntentBits
} from "discord.js";
import {
  BuildTaskError,
  isBuildTarget,
  runBuildTask
} from "./buildRunner.js";
import { CodexMode, CodexTaskError, runCodexTask } from "./codexRunner.js";
import { isRepoKey, REPOS, RepoKey } from "./repos.js";

const token = requiredEnv("DISCORD_TOKEN");
const allowedUserIds = parseAllowedUserIds(process.env.ALLOWED_USER_IDS);
const runningRepos = new Set<RepoKey>();

const client = new Client({
  intents: [GatewayIntentBits.Guilds]
});

client.once(Events.ClientReady, (readyClient) => {
  console.log(`Logged in as ${readyClient.user.tag}`);
});

client.on(Events.InteractionCreate, async (interaction) => {
  if (!interaction.isChatInputCommand()) return;
  if (interaction.commandName !== "codex") return;

  try {
    await handleCodexCommand(interaction);
  } catch (error) {
    console.error("Unhandled /codex error:", error);
    await replyWithFallback(
      interaction,
      "Codex Bot で予期しないエラーが発生しました。"
    );
  }
});

async function handleCodexCommand(interaction: ChatInputCommandInteraction) {
  if (!allowedUserIds.has(interaction.user.id)) {
    await interaction.reply({
      content: "この Bot を実行する権限がありません。",
      ephemeral: true
    });
    return;
  }

  const subcommand = interaction.options.getSubcommand();

  if (subcommand === "task") {
    await handleTaskCommand(interaction);
    return;
  }

  if (subcommand === "build") {
    await handleBuildCommand(interaction);
    return;
  }

  await interaction.reply({
    content: "Unknown subcommand.",
    ephemeral: true
  });
}

async function handleTaskCommand(interaction: ChatInputCommandInteraction) {
  const repo = interaction.options.getString("repo", true);
  const mode = interaction.options.getString("mode", true);
  const prompt = interaction.options.getString("prompt", true);

  if (!isRepoKey(repo)) {
    await interaction.reply({
      content: "許可されていない repo です。",
      ephemeral: true
    });
    return;
  }

  if (!isCodexMode(mode)) {
    await interaction.reply({
      content: "mode は ask / fix / pr のいずれかを指定してください。",
      ephemeral: true
    });
    return;
  }

  if (!(await reserveRepo(interaction, repo))) return;

  await interaction.deferReply({ ephemeral: true });
  await interaction.editReply(
    [
      "Codex task を受け付けました。",
      "",
      `repo: ${repo}`,
      `mode: ${mode}`,
      "",
      "完了したらこのチャンネルに結果を投稿します。"
    ].join("\n")
  );

  const channel = interaction.channel;

  try {
    const result = await runCodexTask({
      repoKey: repo,
      repoPath: REPOS[repo],
      mode,
      prompt,
      requestedBy: `${interaction.user.tag} (${interaction.user.id})`
    });

    if (channel?.isSendable()) {
      await channel.send(
        [
          "Codex task completed",
          "",
          `repo: \`${repo}\``,
          `mode: \`${mode}\``,
          `log: \`${result.logPath}\``,
          "",
          "```md",
          truncateForDiscord(result.finalMessage),
          "```"
        ].join("\n")
      );
    }
  } catch (error) {
    const text = error instanceof Error ? error.message : String(error);
    const logPath = error instanceof CodexTaskError ? error.logPath : undefined;

    if (channel?.isSendable()) {
      await channel.send(
        [
          "Codex task failed",
          "",
          `repo: \`${repo}\``,
          `mode: \`${mode}\``,
          logPath ? `log: \`${logPath}\`` : undefined,
          "",
          "```text",
          truncateForDiscord(text),
          "```"
        ]
          .filter((line): line is string => line !== undefined)
          .join("\n")
      );
    }
  } finally {
    runningRepos.delete(repo);
  }
}

async function handleBuildCommand(interaction: ChatInputCommandInteraction) {
  const repo = interaction.options.getString("repo", true);
  const target = interaction.options.getString("target", true);

  if (!isRepoKey(repo)) {
    await interaction.reply({
      content: "許可されていない repo です。",
      ephemeral: true
    });
    return;
  }

  if (!isBuildTarget(target)) {
    await interaction.reply({
      content: "target は androidDebug を指定してください。",
      ephemeral: true
    });
    return;
  }

  if (!(await reserveRepo(interaction, repo))) return;

  await interaction.deferReply({ ephemeral: true });
  await interaction.editReply(
    [
      "Android build を受け付けました。",
      "",
      `repo: ${repo}`,
      `target: ${target}`,
      "",
      "完了したらこのチャンネルに結果を投稿します。初回は依存関係の取得で時間がかかります。"
    ].join("\n")
  );

  const channel = interaction.channel;

  try {
    const result = await runBuildTask({
      repoKey: repo,
      repoPath: REPOS[repo],
      target,
      requestedBy: `${interaction.user.tag} (${interaction.user.id})`
    });

    if (channel?.isSendable()) {
      await channel.send(
        [
          "Android build completed",
          "",
          `repo: \`${repo}\``,
          `target: \`${target}\``,
          `log: \`${result.logPath}\``,
          result.artifactPath ? `apk: \`${result.artifactPath}\`` : "apk: not found"
        ].join("\n")
      );
    }
  } catch (error) {
    const text = error instanceof Error ? error.message : String(error);
    const logPath = error instanceof BuildTaskError ? error.logPath : undefined;

    if (channel?.isSendable()) {
      await channel.send(
        [
          "Android build failed",
          "",
          `repo: \`${repo}\``,
          `target: \`${target}\``,
          logPath ? `log: \`${logPath}\`` : undefined,
          "",
          "```text",
          truncateForDiscord(text),
          "```"
        ]
          .filter((line): line is string => line !== undefined)
          .join("\n")
      );
    }
  } finally {
    runningRepos.delete(repo);
  }
}

async function reserveRepo(
  interaction: ChatInputCommandInteraction,
  repo: RepoKey
): Promise<boolean> {
  if (runningRepos.has(repo)) {
    await interaction.reply({
      content: `repo \`${repo}\` では既に Codex/build task が実行中です。完了後にもう一度実行してください。`,
      ephemeral: true
    });
    return false;
  }

  runningRepos.add(repo);
  return true;
}

function parseAllowedUserIds(value: string | undefined): Set<string> {
  const ids = new Set(
    (value ?? "")
      .split(",")
      .map((id) => id.trim())
      .filter(Boolean)
  );

  if (ids.size === 0) {
    throw new Error("ALLOWED_USER_IDS must contain at least one Discord user ID.");
  }

  return ids;
}

function isCodexMode(value: string): value is CodexMode {
  return value === "ask" || value === "fix" || value === "pr";
}

function truncateForDiscord(value: string): string {
  const max = 1800;
  return value.length > max ? `${value.slice(0, max)}\n...truncated` : value;
}

async function replyWithFallback(
  interaction: ChatInputCommandInteraction,
  content: string
) {
  if (interaction.deferred || interaction.replied) {
    await interaction.editReply(content);
  } else {
    await interaction.reply({ content, ephemeral: true });
  }
}

function requiredEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

await client.login(token);
