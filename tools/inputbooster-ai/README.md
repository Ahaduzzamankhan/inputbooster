# InputBooster AI Developer

A local, prompt-driven coding agent for the InputBooster repository. It embeds the OpenCode host through `@opencode/sdk`, creates a session scoped to the selected workspace, and lets you ask an agent to inspect and modify the actual Java/Gradle source.

This is a development tool, **not a Minecraft mod dependency**. It does not add a JavaScript runtime or AI network calls to the shipped mod.

## Requirements

- [Bun](https://bun.sh/) (the embedded OpenCode SDK is distributed for the Bun-based OpenCode runtime)
- A supported OpenCode model/provider configured and authenticated in your local OpenCode environment
- Network access for the selected model provider

The SDK does not provide free model usage or bypass provider quotas. Usage follows the configured provider/account limits.

## Setup

From the repository root:

```bash
cd tools/inputbooster-ai
bun install
```

## Run

From `tools/inputbooster-ai`:

```bash
bun run start
```

By default, the workspace is the current working directory. To target the repository root while launching from this directory:

```bash
bun run start -- --directory ../..
```

You can also pass an absolute path:

```bash
bun run start -- --directory "C:/Projects/InputBooster"
```

The agent creates one OpenCode session for the selected workspace. Enter multiple prompts in the same session to maintain task context. Type `/exit` to close it.

## Example prompts

- `Inspect the input pipeline and explain where attack press events are captured.`
- `Implement a Sodium configuration screen integration. Inspect the existing settings and preserve their behavior.`
- `Find and fix the Gradle compilation failure. Do not change unrelated code.`
- `Review the Fabric 26.2 and 26.3 compatibility paths and report concrete issues.`

## Safety and workflow

- The agent operates on the local files in the selected workspace. Review its diff before accepting changes.
- Run builds and tests yourself or ask the agent to run the relevant project commands.
- The agent is instructed not to commit, push, publish releases, or modify remote Git state without an explicit request.
- Do not run this tool against an untrusted repository without reviewing the agent's proposed tool actions.
- Never paste API keys, access tokens, or other secrets into prompts.
- The repository's `AI_CONTEXT.md` is part of the agent's required project context.

## Architecture

```text
Terminal prompt
    |
    v
InputBooster AI Developer (Bun + TypeScript)
    |
    v
@opencode/sdk (embedded OpenCode host)
    |
    v
OpenCode session scoped to the selected workspace
    |
    +--> inspect source and documentation
    +--> edit project files
    +--> run configured development tools
    +--> report changes and verification results
```

The tool uses the embedded SDK API documented at https://opencode.ai/docs/build/sdk/.
