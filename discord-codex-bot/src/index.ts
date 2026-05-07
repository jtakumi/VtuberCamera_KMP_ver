import "dotenv/config";
import {
  ChatInputCommandInteraction,
  Client,
  Events,
  GatewayIntentBits,
  PermissionFlagsBits
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
    await replyWithFallback(interaction, "Codex Bot hit an unexpected error.");
  }
});

async function handleCodexCommand(interaction: ChatInputCommandInteraction) {
  if (!allowedUserIds.has(interaction.user.id)) {
    await interaction.reply({
      content: "You are not allowed to run this bot.",
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
      content: "That repo is not allowed.",
      ephemeral: true
    });
    return;
  }

  if (!isCodexMode(mode)) {
    await interaction.reply({
      content: "mode must be ask, fix, or pr.",
      ephemeral: true
    });
    return;
  }

  if (!(await reserveRepo(interaction, repo))) return;

  await interaction.deferReply({ ephemeral: true });
  await interaction.editReply(
    [
      "Codex task accepted.",
      "",
      `repo: ${repo}`,
      `mode: ${mode}`,
      "",
      "I will post the result in this channel when it finishes."
    ].join("\n")
  );

  try {
    const result = await runCodexTask({
      repoKey: repo,
      repoPath: REPOS[repo],
      mode,
      prompt,
      requestedBy: `${interaction.user.tag} (${interaction.user.id})`
    });

    await sendChannelNotification(
      interaction,
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
  } catch (error) {
    const text = error instanceof Error ? error.message : String(error);
    const logPath = error instanceof CodexTaskError ? error.logPath : undefined;

    await sendChannelNotification(
      interaction,
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
  } finally {
    runningRepos.delete(repo);
  }
}

async function handleBuildCommand(interaction: ChatInputCommandInteraction) {
  const repo = interaction.options.getString("repo", true);
  const target = interaction.options.getString("target", true);

  if (!isRepoKey(repo)) {
    await interaction.reply({
      content: "That repo is not allowed.",
      ephemeral: true
    });
    return;
  }

  if (!isBuildTarget(target)) {
    await interaction.reply({
      content: "target must be androidDebug.",
      ephemeral: true
    });
    return;
  }

  if (!(await reserveRepo(interaction, repo))) return;

  await interaction.deferReply({ ephemeral: true });
  await interaction.editReply(
    [
      "Android build accepted.",
      "",
      `repo: ${repo}`,
      `target: ${target}`,
      "",
      "I will post the result in this channel when it finishes."
    ].join("\n")
  );

  try {
    const result = await runBuildTask({
      repoKey: repo,
      repoPath: REPOS[repo],
      target,
      requestedBy: `${interaction.user.tag} (${interaction.user.id})`
    });

    await sendChannelNotification(
      interaction,
      [
        "Android build completed",
        "",
        `repo: \`${repo}\``,
        `target: \`${target}\``,
        `log: \`${result.logPath}\``,
        result.artifactPath ? `apk: \`${result.artifactPath}\`` : "apk: not found"
      ].join("\n")
    );
  } catch (error) {
    const text = error instanceof Error ? error.message : String(error);
    const logPath = error instanceof BuildTaskError ? error.logPath : undefined;

    await sendChannelNotification(
      interaction,
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
      content: `A Codex/build task is already running for repo \`${repo}\`. Try again after it finishes.`,
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

async function sendChannelNotification(
  interaction: ChatInputCommandInteraction,
  content: string
) {
  const channel = interaction.channel;
  if (!channel?.isSendable()) {
    await interaction.editReply(
      truncateForDiscordMessage(
        [
          "Task finished, but this interaction channel is not sendable.",
          "",
          content
        ].join("\n")
      )
    );
    return;
  }

  const missingPermissions = getMissingSendPermissions(interaction);
  if (missingPermissions.length > 0) {
    await interaction.editReply(
      truncateForDiscordMessage(
        [
          "Task finished, but I cannot post the result in this channel.",
          `Missing bot permission(s): ${missingPermissions.join(", ")}`,
          "Grant the bot View Channel and Send Messages in this channel, then retry.",
          "",
          content
        ].join("\n")
      )
    );
    return;
  }

  try {
    await channel.send(content);
  } catch (error) {
    const reason = describeDiscordSendError(error);
    console.error(`Failed to send Discord channel notification: ${reason}`);
    await interaction.editReply(
      truncateForDiscordMessage(
        [
          "Task finished, but I could not post the result in this channel.",
          `Discord error: ${reason}`,
          "",
          content
        ].join("\n")
      )
    );
  }
}

function getMissingSendPermissions(
  interaction: ChatInputCommandInteraction
): string[] {
  const channel = interaction.channel;
  const botUser = interaction.client.user;

  if (!channel || !botUser || !("permissionsFor" in channel)) return [];

  const permissions = channel.permissionsFor(botUser);
  if (!permissions) return [];

  const required = [
    ["View Channel", PermissionFlagsBits.ViewChannel],
    ["Send Messages", PermissionFlagsBits.SendMessages]
  ] as const;

  return required
    .filter(([, permission]) => !permissions.has(permission))
    .map(([label]) => label);
}

function describeDiscordSendError(error: unknown): string {
  if (isDiscordApiError(error)) {
    const advice =
      error.code === 50001
        ? " The bot likely lacks access to this channel; grant View Channel and Send Messages."
        : "";

    return `${error.name}[${error.code}]: ${error.message}${advice}`;
  }

  return error instanceof Error ? error.message : String(error);
}

function isDiscordApiError(
  error: unknown
): error is Error & { code: number | string } {
  return (
    error instanceof Error &&
    "code" in error &&
    (typeof error.code === "number" || typeof error.code === "string")
  );
}

function truncateForDiscordMessage(value: string): string {
  const max = 1950;
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
