/** Minimal Firestore document ref surface used by import tools and session. */
export interface JobRef {
  update(patch: Record<string, unknown>): Promise<unknown>;
  get(): Promise<{ data: () => Record<string, unknown> | undefined }>;
}

export class FakeJobRef implements JobRef {
  readonly updates: Record<string, unknown>[] = [];

  constructor(public data: Record<string, unknown> = {}) {}

  async update(patch: Record<string, unknown>): Promise<void> {
    this.updates.push(patch);
    Object.assign(this.data, patch);
  }

  async get(): Promise<{ data: () => Record<string, unknown> | undefined }> {
    return {
      data: () => this.data,
    };
  }

  progressUpdates(): Array<{ phase?: string; message?: string }> {
    return this.updates
      .filter((u) => "progressPhase" in u || "progressMessage" in u)
      .map((u) => ({
        phase: u.progressPhase as string | undefined,
        message: u.progressMessage as string | undefined,
      }));
  }
}
