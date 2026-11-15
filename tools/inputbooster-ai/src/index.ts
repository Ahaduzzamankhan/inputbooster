import { OpenCode } from "@opencode/sdk"
import { access } from "node:fs/promises"
import { resolve } from "node:path"
import { createInterface } from "node:readline/promises"
import { stdin, stdout } from "node:process"

const args = process.argv.slice(2)
const directoryFlag = args.indexOf("--directory")
const requestedDirectory =
  directoryFlag >= 0 ? args[directoryFlag + 1] : process.cwd()

if (!requestedDirectory || requestedDirectory.startsWith("--")) {
  console.error("Usage: bun run src/index.ts [--directory <project-path>]")
  process.exit(1)
}

const projectDirectory = resolve(requestedDirectory)

try {
  await access(projectDirectory)
} catch {
  console.error(`Project directory does not exist: ${projectDirectory}`)
  process.exit(1)
}

const contextFile = resolve(projectDirectory, "AI_CONTEXT.md")
const systemGuidance = [
  "You are the local software-engineering agent for the InputBooster Minecraft mod.",
  "Work directly in the selected repository directory using your available coding tools.",
  "Before changing code, read AI_CONTEXT.md and the relevant architecture/build documentation.",
  "Treat the current source code and build configuration as authoritative when documentation conflicts.",
  "Preserve existing behavior, public APIs, supported loader/game-version targets, and performance-sensitive input-thread invariants.",
  "Inspect call sites and related modules before editing. Do not make speculative broad refactors.",
  "After changes, run the most relevant available formatting, compilation, and test commands.",
  "If a test cannot run because a dependency, game runtime, or environment is unavailable, report that limitation instead of claiming success.",
  "Summarize changed files, design decisions, commands executed, test results, and remaining risks.",
  "Do not commit, push, publish releases, or modify remote Git state unless the user explicitly asks.",
].join("\n")

console.log("\nInputBooster AI Developer")
console.log("------------------------")
console.log(`Workspace: ${projectDirectory}`)
console.log("Engine: embedded OpenCode SDK")
console.log("Enter a task in natural language. Type /exit to quit.\n")

if (await access(contextFile).then(() => true).catch(() => false)) {
  console.log("Repository AI_CONTEXT.md detected.")
} else {
  console.warn("Warning: AI_CONTEXT.md was not found in this directory.")
}

const terminal = createInterface({ input: stdin, output: stdout })
const opencode = await OpenCode.create()

try {
  const session = await opencode.sessions.create({
    location: { directory: projectDirectory },
  })

  console.log(`Session: ${session.id}\n`)

  while (true) {
    const task = (await terminal.question("inputbooster-ai> ")).trim()

    if (!task) continue
    if (task === "/exit" || task === "/quit") break

    console.log("\nWorking...\n")

    try {
      const result = await opencode.sessions.prompt({
        sessionID: session.id,
        text: `${systemGuidance}\n\nUSER TASK:\n${task}`,
      })

      console.log("\nAgent response:")
      console.log(JSON.stringify(result, null, 2))
      console.log()
    } catch (error) {
      console.error("\nTask failed:")
      console.error(error instanceof Error ? error.message : error)
      console.log()
    }
  }
} finally {
  terminal.close()
  await opencode.close()
}

console.log("InputBooster AI Developer stopped.")
