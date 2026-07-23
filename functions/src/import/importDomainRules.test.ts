import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  buildImportAgentSystemPrompt,
  buildImportAgentUserPrompt,
  IMPORT_DOMAIN_RULES_CHECKLIST,
} from "./importDomainRules";
import { IMPORT_AGENT_SYSTEM_PROMPT } from "./schemas";

describe("importDomainRules prompts", () => {
  it("system prompt includes domain checklist and warm-up guidance", () => {
    const prompt = buildImportAgentSystemPrompt();
    assert.match(prompt, /reps: positive integer/i);
    assert.match(prompt, /reps=0/);
    assert.match(prompt, /zero validationErrors/);
    assert.match(prompt, /Do NOT name the source app unless/i);
    assert.match(prompt, /weightUnit "Lb"/);
    assert.equal(prompt, IMPORT_AGENT_SYSTEM_PROMPT);
  });

  it("system prompt injects kg fallback when requested", () => {
    const prompt = buildImportAgentSystemPrompt("Kg");
    assert.match(prompt, /weightUnit "Kg"/);
  });

  it("user prompt repeats rules and includes example JSON", () => {
    const prompt = buildImportAgentUserPrompt("date,reps,weight\n2025-01-01,5,100");
    assert.ok(prompt.includes(IMPORT_DOMAIN_RULES_CHECKLIST));
    assert.match(prompt, /"sessions"/);
    assert.match(prompt, /validationErrors/);
    assert.match(prompt, /date,reps,weight/);
  });
});
