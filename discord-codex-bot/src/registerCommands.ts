import "dotenv/config";
import { REST, Routes, SlashCommandBuilder } from "discord.js";
import { REPOS } from "./repos.js";

const token = requiredEnv("DISCORD_TOKEN");
const clientId = requiredEnv("DISCORD_CLIENT_ID");
const guildId = requiredEnv("DISCORD_GUILD_ID");

const repoChoices = Object.keys(REPOS).map((repo) => ({
  name: repo,
  value: repo
}));

const commands = [
  new SlashCommandBuilder()
    .setName("codex")
    .setDescription("Run Codex tasks from Discord")
    .addSubcommand((subcommand) =>
      subcommand
        .setName("task")
        .setDescription("Run a Codex task")
        .addStringOption((option) =>
          option
            .setName("repo")
            .setDescription("Target repository")
            .setRequired(true)
            .addChoices(...repoChoices)
        )
        .addStringOption((option) =>
          option
            .setName("mode")
            .setDescription("Execution mode")
            .setRequired(true)
            .addChoices(
              { name: "ask", value: "ask" },
              { name: "fix", value: "fix" },
              { name: "pr", value: "pr" }
            )
        )
        .addStringOption((option) =>
          option
            .setName("prompt")
            .setDescription("Task prompt")
            .setRequired(true)
        )
    )
].map((command) => command.toJSON());

const rest = new REST({ version: "10" }).setToken(token);

await rest.put(Routes.applicationGuildCommands(clientId, guildId), {
  body: commands
});

console.log("Registered Discord slash commands.");

function requiredEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}
