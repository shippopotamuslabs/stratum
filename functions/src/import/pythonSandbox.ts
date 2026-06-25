import { spawnSync } from "node:child_process";
import * as admin from "firebase-admin";

const BLOCKED = [
  "import os",
  "import subprocess",
  "import socket",
  "open(",
  "__import__",
  "eval(",
  "exec(",
];

export interface SandboxResult {
  outputJson: string;
  rowCount: number;
  sessionCount: number;
}

export function validateScriptSafety(script: string): void {
  const lower = script.toLowerCase();
  for (const token of BLOCKED) {
    if (lower.includes(token)) {
      throw new Error(`Blocked token in script: ${token}`);
    }
  }
}

/** Runs a conversion script against input text (no Storage). Used by sandbox and tests. */
export function executeConversionScript(script: string, inputText: string): SandboxResult {
  validateScriptSafety(script);

  const wrapper = `
import json
import sys

raw_input = sys.stdin.read()

${script}

if "convert" not in dir():
    raise RuntimeError("Script must define convert(raw_input: str) -> dict")
result = convert(raw_input)
print(json.dumps(result))
`;

  const proc = spawnSync("python3", ["-c", wrapper], {
    input: inputText,
    encoding: "utf8",
    timeout: 60_000,
    maxBuffer: 10 * 1024 * 1024,
  });

  if (proc.error) {
    throw new Error(proc.error.message);
  }
  if (proc.status !== 0) {
    throw new Error(proc.stderr || proc.stdout || "Script execution failed");
  }

  const outputJson = proc.stdout.trim();
  const parsed = JSON.parse(outputJson) as { sets?: unknown[]; sessions?: unknown[] };
  return {
    outputJson,
    rowCount: parsed.sets?.length ?? 0,
    sessionCount: parsed.sessions?.length ?? 0,
  };
}

export async function runPythonSandbox(
  scriptStoragePath: string,
  inputStoragePath: string,
  storage = admin.storage().bucket(),
): Promise<SandboxResult> {
  const [scriptBytes] = await storage.file(scriptStoragePath).download();
  const [inputBytes] = await storage.file(inputStoragePath).download();
  const script = scriptBytes.toString("utf8");
  const inputText = inputBytes.toString("utf8");
  return executeConversionScript(script, inputText);
}
