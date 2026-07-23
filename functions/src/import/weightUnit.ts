/** Normalize optional client / job unit to "Lb" | "Kg". Unknown / missing → Lb. */
export function parseFallbackWeightUnit(raw: unknown): "Lb" | "Kg" {
  if (typeof raw !== "string") return "Lb";
  const t = raw.trim();
  if (t === "Kg" || t === "kg" || t === "KG") return "Kg";
  if (t === "Lb" || t === "lb" || t === "LB" || t === "lbs" || t === "Lbs") return "Lb";
  return "Lb";
}
