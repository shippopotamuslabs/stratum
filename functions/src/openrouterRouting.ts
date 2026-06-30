/**
 * OpenRouter provider routing preferences for Stratum Firebase callables.
 *
 * Default matches pocket-crew OpenRouterRoutingConfiguration: sort by throughput,
 * allow fallbacks, deny data-collection providers. See OpenRouter provider routing docs.
 */
export type OpenRouterProviderSort = "price" | "throughput" | "latency";

export type OpenRouterThroughputPreference =
  | number
  | { p50?: number; p75?: number; p90?: number; p99?: number };

export type OpenRouterDataCollectionPolicy = "allow" | "deny";

export type OpenRouterProviderRouting = {
  sort?: OpenRouterProviderSort;
  order?: string[];
  allow_fallbacks?: boolean;
  require_parameters?: boolean;
  data_collection?: OpenRouterDataCollectionPolicy;
  zdr?: boolean;
  preferred_min_throughput?: OpenRouterThroughputPreference;
};

export type OpenRouterRoutingOptions = {
  sort?: OpenRouterProviderSort;
  preferredProvider?: string;
  allowFallbacks?: boolean;
  requireParameters?: boolean;
  dataCollection?: OpenRouterDataCollectionPolicy;
  zeroDataRetention?: boolean;
  preferredMinThroughput?: OpenRouterThroughputPreference;
  /**
   * Omit provider.sort so OpenRouter session_id sticky routing is not overridden.
   * Program generation sets this false so price/throughput preferences apply.
   */
  stickySession?: boolean;
};

/** Deprioritize sub-50 tps providers when sorting by price (OpenRouter p90 window). */
export const PROGRAM_GENERATION_PREFERRED_MIN_THROUGHPUT: OpenRouterThroughputPreference = {
  p90: 50,
};

export function buildOpenRouterProviderRouting(
  options: OpenRouterRoutingOptions = {},
): OpenRouterProviderRouting {
  const preferredProvider = options.preferredProvider?.trim();
  const hasPreferred = Boolean(preferredProvider);
  const allowFallbacks = options.allowFallbacks ?? true;
  const pinnedProvider = hasPreferred && !allowFallbacks;

  const routing: OpenRouterProviderRouting = {
    allow_fallbacks: allowFallbacks,
    require_parameters: pinnedProvider ? false : (options.requireParameters ?? false),
    data_collection: pinnedProvider ? "deny" : (options.dataCollection ?? "deny"),
    zdr: pinnedProvider ? false : (options.zeroDataRetention ?? false),
  };

  if (options.preferredMinThroughput != null) {
    routing.preferred_min_throughput = options.preferredMinThroughput;
  }

  if (hasPreferred) {
    routing.order = [preferredProvider!];
  } else if (!options.stickySession) {
    routing.sort = options.sort ?? "throughput";
  }

  return routing;
}

/** Program generation: cheapest provider first, then fastest among ties (OpenRouter Use Case 2). */
export function buildProgramGenerationProviderRouting(
  options: Pick<OpenRouterRoutingOptions, "requireParameters" | "preferredMinThroughput"> = {},
): OpenRouterProviderRouting {
  return buildOpenRouterProviderRouting({
    sort: "price",
    allowFallbacks: true,
    requireParameters: options.requireParameters ?? true,
    dataCollection: "deny",
    preferredMinThroughput:
      options.preferredMinThroughput ?? PROGRAM_GENERATION_PREFERRED_MIN_THROUGHPUT,
    stickySession: false,
  });
}
