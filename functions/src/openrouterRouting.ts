/**
 * OpenRouter provider routing preferences for Stratum Firebase callables.
 *
 * Default matches pocket-crew OpenRouterRoutingConfiguration: sort by throughput,
 * allow fallbacks, deny data-collection providers. See OpenRouter provider routing docs.
 */
export type OpenRouterProviderSort = "price" | "throughput" | "latency";

export type OpenRouterDataCollectionPolicy = "allow" | "deny";

export type OpenRouterProviderRouting = {
  sort?: OpenRouterProviderSort;
  order?: string[];
  allow_fallbacks?: boolean;
  require_parameters?: boolean;
  data_collection?: OpenRouterDataCollectionPolicy;
  zdr?: boolean;
};

export type OpenRouterRoutingOptions = {
  sort?: OpenRouterProviderSort;
  preferredProvider?: string;
  allowFallbacks?: boolean;
  requireParameters?: boolean;
  dataCollection?: OpenRouterDataCollectionPolicy;
  zeroDataRetention?: boolean;
  /** Omit provider.sort so OpenRouter session_id sticky routing is not overridden. */
  stickySession?: boolean;
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

  if (hasPreferred) {
    routing.order = [preferredProvider!];
  } else if (!options.stickySession) {
    routing.sort = options.sort ?? "throughput";
  }

  return routing;
}
