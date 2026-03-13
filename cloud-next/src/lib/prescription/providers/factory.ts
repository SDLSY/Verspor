import type { PrescriptionModelProvider } from "@/lib/prescription/providers/base";
import { DeepSeekPrescriptionProvider } from "@/lib/prescription/providers/deepseek";
import { OpenRouterPrescriptionProvider } from "@/lib/prescription/providers/openrouter";
import { VectorEnginePrescriptionProvider } from "@/lib/prescription/providers/vector-engine";

const registry: Record<string, PrescriptionModelProvider> = {
  vector_engine: new VectorEnginePrescriptionProvider(),
  openrouter: new OpenRouterPrescriptionProvider(),
  deepseek: new DeepSeekPrescriptionProvider(),
};

export function createPrescriptionProviderChain(): PrescriptionModelProvider[] {
  const order = [
    process.env.PRESCRIPTION_PROVIDER_PRIMARY?.trim().toLowerCase() || "openrouter",
    process.env.PRESCRIPTION_PROVIDER_SECONDARY?.trim().toLowerCase() || "vector_engine",
    process.env.PRESCRIPTION_PROVIDER_TERTIARY?.trim().toLowerCase() || "deepseek",
  ];
  const seen = new Set<string>();

  return order
    .map((key) => registry[key])
    .filter((provider): provider is PrescriptionModelProvider => Boolean(provider))
    .filter((provider) => {
      if (seen.has(provider.providerId) || !provider.isEnabled()) {
        return false;
      }
      seen.add(provider.providerId);
      return true;
    });
}
