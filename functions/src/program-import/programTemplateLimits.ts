/**
 * Shared abuse guards for program-template import.
 *
 * Program files are tiny structured documents (real-world samples are single-digit KB), so the cap
 * is deliberately tight. Enforced server-side before any parse or LLM call so a direct Callable
 * invocation (bypassing the client) cannot burn LLM budget on a huge or junk file. Mirrors the
 * Kotlin `ProgramTemplateImportLimits` in `:core:domain`.
 */
export const MAX_PROGRAM_TEMPLATE_FILE_BYTES = 1 * 1024 * 1024;

/**
 * Reject files exceeding the size cap. Throws a tagged error so the caller can mark the job
 * `rejected` (user-facing "not a program" / "too large") rather than `failed`.
 */
export function assertWithinSizeLimit(byteLength: number): void {
  if (byteLength > MAX_PROGRAM_TEMPLATE_FILE_BYTES) {
    throw new ProgramTemplateTooLargeError(byteLength);
  }
}

/** Thrown when an uploaded program template exceeds [MAX_PROGRAM_TEMPLATE_FILE_BYTES]. */
export class ProgramTemplateTooLargeError extends Error {
  constructor(byteLength: number) {
    super(
      `File is too large (${byteLength} bytes). Program files must be under 1 MB.`,
    );
    this.name = "ProgramTemplateTooLargeError";
  }
}
