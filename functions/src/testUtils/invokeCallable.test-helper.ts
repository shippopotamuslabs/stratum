/**
 * Test-only helper for invoking Firebase v2 `onCall` HTTPS callables.
 *
 * `onCall` returns a `CallableFunction` wrapper, not a plain async function,
 * so unit tests drive it through its hidden `.run(request)` entrypoint. The
 * wrapper type does not expose `run` publicly, hence the single contained
 * `unknown` cast here instead of one per spec.
 */

/** Minimal structural shape of an `onCall` wrapper needed for unit tests. */
export type CallableLike = {
  run: (request: never) => Promise<unknown>;
};

/** Invoke [fn] as a callable with [request], preserving `this` binding. */
export function invokeCallable(fn: unknown, request: unknown): Promise<unknown> {
  return (fn as unknown as CallableLike).run.bind(fn)(request as never);
}
