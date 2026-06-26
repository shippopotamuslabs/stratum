import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { limitSamplePreview } from "./sliceExport";
import { runPythonSandbox, type SandboxResult } from "./pythonSandbox";
import type { JobRef } from "./jobRef";
import { createAdminToolStorage, type ToolStorage } from "./toolStorage";
import {
  DEFAULT_INTENT,
  PHASE_BY_TOOL,
  sanitizeIntent,
  validateNormalizedHistory,
} from "./schemas";
import { ImportValidationError, validationErrorToolPayload } from "./importValidation";

export interface ToolContext {
  uid: string;
  jobId: string;
  jobRef: JobRef;
  sampleStoragePath: string;
  rawStoragePath: string;
  fullSliceStoragePath?: string;
  storage?: ToolStorage;
  runSandbox?: (scriptPath: string, inputPath: string) => Promise<SandboxResult>;
}

function resolveStorage(ctx: ToolContext): ToolStorage {
  return ctx.storage ?? createAdminToolStorage();
}

async function invokeSandbox(
  ctx: ToolContext,
  scriptPath: string,
  inputPath: string,
): Promise<SandboxResult> {
  if (ctx.runSandbox) {
    return ctx.runSandbox(scriptPath, inputPath);
  }
  return runPythonSandbox(scriptPath, inputPath, resolveStorage(ctx).bucket());
}

export async function dispatchTool(
  toolName: string,
  args: Record<string, unknown>,
  ctx: ToolContext,
): Promise<{ content: string; isError: boolean; conversionUpdated: boolean }> {
  const phase = PHASE_BY_TOOL[toolName];
  const intent = sanitizeIntent(args.intent, toolName);
  if (phase) {
    await ctx.jobRef.update({
      progressPhase: phase,
      progressMessage: intent,
      progressUpdatedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  try {
    switch (toolName) {
      case "write_conversion_script":
        return await writeConversionScript(args, ctx);
      case "execute_on_sample":
        return await executeOnSample(args, ctx);
      case "execute_on_full":
        return await executeOnFull(args, ctx);
      default:
        return { content: `Unknown tool: ${toolName}`, isError: true, conversionUpdated: false };
    }
  } catch (err) {
    if (err instanceof ImportValidationError) {
      return {
        content: validationErrorToolPayload(err.violations),
        isError: true,
        conversionUpdated: false,
      };
    }
    const message = err instanceof Error ? err.message : String(err);
    return { content: message, isError: true, conversionUpdated: false };
  }
}

async function writeConversionScript(
  args: Record<string, unknown>,
  ctx: ToolContext,
): Promise<{ content: string; isError: boolean; conversionUpdated: boolean }> {
  const script = args.script;
  if (typeof script !== "string" || script.trim().length === 0) {
    return { content: "script is required", isError: true, conversionUpdated: false };
  }
  const scriptPath = `imports/${ctx.uid}/${ctx.jobId}/script/converter.py`;
  const storage = resolveStorage(ctx);
  await storage.save(scriptPath, script, "text/plain");
  await ctx.jobRef.update({
    scriptStoragePath: scriptPath,
    progressMessage: DEFAULT_INTENT.write_conversion_script,
    updatedAt: FieldValue.serverTimestamp(),
  });
  return {
    content: JSON.stringify({ scriptStoragePath: scriptPath }),
    isError: false,
    conversionUpdated: false,
  };
}

async function executeOnSample(
  args: Record<string, unknown>,
  ctx: ToolContext,
): Promise<{ content: string; isError: boolean; conversionUpdated: boolean }> {
  const scriptPath =
    typeof args.scriptStoragePath === "string"
      ? args.scriptStoragePath
      : (await ctx.jobRef.get()).data()?.scriptStoragePath;
  if (!scriptPath || typeof scriptPath !== "string") {
    return {
      content: "No script saved yet. Call write_conversion_script first.",
      isError: true,
      conversionUpdated: false,
    };
  }
  const result = await invokeSandbox(ctx, scriptPath, ctx.sampleStoragePath);
  const preview = limitSamplePreview(result.outputJson);
  validateNormalizedHistory(JSON.parse(result.outputJson));
  await ctx.jobRef.update({
    progressMessage: `Sample looks good — ${result.rowCount} sets found`,
    updatedAt: FieldValue.serverTimestamp(),
  });
  return {
    content: JSON.stringify({
      preview,
      rowCount: result.rowCount,
      sessionCount: result.sessionCount,
    }),
    isError: false,
    conversionUpdated: false,
  };
}

async function executeOnFull(
  args: Record<string, unknown>,
  ctx: ToolContext,
): Promise<{ content: string; isError: boolean; conversionUpdated: boolean }> {
  const jobSnap = await ctx.jobRef.get();
  const jobData = jobSnap.data() ?? {};
  const scriptPath =
    typeof args.scriptStoragePath === "string" ? args.scriptStoragePath : jobData.scriptStoragePath;
  if (!scriptPath || typeof scriptPath !== "string") {
    return { content: "No script saved yet.", isError: true, conversionUpdated: false };
  }

  const storage = resolveStorage(ctx);
  const inputPath = ctx.rawStoragePath;
  await ctx.jobRef.update({
    fullSliceStoragePath: inputPath,
    updatedAt: FieldValue.serverTimestamp(),
  });

  const result = await invokeSandbox(ctx, scriptPath, inputPath);
  validateNormalizedHistory(JSON.parse(result.outputJson));

  const ts = Date.now();
  const conversionPath = `imports/${ctx.uid}/${ctx.jobId}/conversions/${ts}/normalized.json`;
  const outputBytes = Buffer.from(result.outputJson, "utf8");
  await storage.save(conversionPath, outputBytes, "application/json");

  const latestConversion = {
    storagePath: conversionPath,
    rowCount: result.rowCount,
    sessionCount: result.sessionCount,
    sizeBytes: outputBytes.length,
    createdAt: Timestamp.now(),
  };
  await ctx.jobRef.update({
    latestConversion,
    progressMessage: `Converted ${result.rowCount} sets across ${result.sessionCount} sessions`,
    updatedAt: FieldValue.serverTimestamp(),
  });

  return {
    content: JSON.stringify({
      storagePath: conversionPath,
      rowCount: result.rowCount,
      sessionCount: result.sessionCount,
    }),
    isError: false,
    conversionUpdated: true,
  };
}
