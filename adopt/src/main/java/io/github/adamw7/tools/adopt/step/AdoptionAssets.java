package io.github.adamw7.tools.adopt.step;

import java.util.List;

/**
 * The starter Claude Code configuration assets an {@link AssetsStep} installs
 * beyond the generated {@code CLAUDE.md}: an {@code AGENTS.md} pointer for
 * agents that read the cross-tool convention, a {@code .claude/settings.json}
 * that denies reading obvious secret files and wires the session-start hook, the
 * {@code .claude/hooks/session-start.sh} stub that hook runs, a starter
 * {@code .mcp.json}, and a GitHub Actions workflow that answers {@code @claude}
 * mentions on issues and pull requests. Every asset is a plain committed file,
 * so the configuration is shared with all contributors rather than living only
 * in the adopter's local checkout.
 */
public final class AdoptionAssets {

	static final String AGENTS_MD_FILE = "AGENTS.md";
	static final String SETTINGS_FILE = ".claude/settings.json";
	static final String SESSION_START_HOOK_FILE = ".claude/hooks/session-start.sh";
	static final String MCP_CONFIG_FILE = ".mcp.json";
	static final String CLAUDE_WORKFLOW_FILE = ".github/workflows/claude.yml";

	private static final String AGENTS_MD = """
			# Agent guide

			See [CLAUDE.md](CLAUDE.md) for this repository's agent instructions.
			CLAUDE.md is the source of truth; this file exists so agents that follow
			the cross-tool AGENTS.md convention find the same guidance.
			""";

	private static final String SETTINGS = """
			{
			  "permissions": {
			    "deny": [
			      "Read(./.env)",
			      "Read(./.env.*)",
			      "Read(./**/*.pem)",
			      "Read(./**/id_rsa)"
			    ]
			  },
			  "hooks": {
			    "SessionStart": [
			      {
			        "hooks": [
			          {
			            "type": "command",
			            "command": ".claude/hooks/session-start.sh"
			          }
			        ]
			      }
			    ]
			  }
			}
			""";

	private static final String SESSION_START_HOOK = """
			#!/bin/sh
			# Runs when a Claude Code session starts (wired via .claude/settings.json).
			# Add project setup here — install the toolchain, export environment
			# variables, or pre-fetch dependencies — so web and remote sessions can
			# build and test out of the box.
			exit 0
			""";

	private static final String MCP_CONFIG = """
			{
			  "mcpServers": {}
			}
			""";

	private static final String CLAUDE_WORKFLOW = """
			# Runs Claude Code when @claude is mentioned on an issue or pull request.
			# Requires the ANTHROPIC_API_KEY repository secret to be configured.
			name: Claude Code
			on:
			  issue_comment:
			    types: [created]
			  pull_request_review_comment:
			    types: [created]
			jobs:
			  claude:
			    if: contains(github.event.comment.body, '@claude')
			    runs-on: ubuntu-latest
			    permissions:
			      contents: write
			      pull-requests: write
			      issues: write
			      id-token: write
			    steps:
			      - uses: actions/checkout@v4
			      - uses: anthropics/claude-code-action@v1
			        with:
			          anthropic_api_key: ${{ secrets.ANTHROPIC_API_KEY }}
			""";

	public static final List<AssetInstaller> DEFAULTS = List.of(
			new AssetInstaller(AGENTS_MD_FILE, AGENTS_MD),
			new AssetInstaller(SETTINGS_FILE, SETTINGS),
			new AssetInstaller(SESSION_START_HOOK_FILE, SESSION_START_HOOK, true),
			new AssetInstaller(MCP_CONFIG_FILE, MCP_CONFIG),
			new AssetInstaller(CLAUDE_WORKFLOW_FILE, CLAUDE_WORKFLOW));

	private AdoptionAssets() {
	}
}
