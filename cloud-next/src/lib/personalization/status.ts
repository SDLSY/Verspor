import type {
  PrescriptionPersonalizationLevel,
  PrescriptionPersonalizationMissingInput,
} from "@/lib/prescription/types";

const PERSONALIZATION_INPUT_ORDER: PrescriptionPersonalizationMissingInput[] = [
  "DEVICE_DATA",
  "BASELINE_ASSESSMENT",
  "DOCTOR_INQUIRY",
];

type ResolvePersonalizationStatusInput = {
  baseMissingInputs?: readonly string[] | null;
  hasEnoughDeviceData?: boolean | null;
  hasFreshBaseline?: boolean | null;
  hasRecentDoctorInquiry?: boolean | null;
};

type ResolvedPersonalizationStatus = {
  personalizationLevel: PrescriptionPersonalizationLevel;
  missingInputs: PrescriptionPersonalizationMissingInput[];
};

function normalizeMissingInputs(
  value: readonly string[] | null | undefined
): PrescriptionPersonalizationMissingInput[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const allowed = new Set<PrescriptionPersonalizationMissingInput>(PERSONALIZATION_INPUT_ORDER);
  return PERSONALIZATION_INPUT_ORDER.filter((key) =>
    value.some((item) => item === key && allowed.has(key))
  );
}

function applySignal(
  set: Set<PrescriptionPersonalizationMissingInput>,
  key: PrescriptionPersonalizationMissingInput,
  satisfied: boolean | null | undefined
) {
  if (satisfied == null) {
    return;
  }
  if (satisfied) {
    set.delete(key);
  } else {
    set.add(key);
  }
}

export function resolvePersonalizationStatus(
  input: ResolvePersonalizationStatusInput
): ResolvedPersonalizationStatus {
  const missingInputs = new Set<PrescriptionPersonalizationMissingInput>(
    normalizeMissingInputs(input.baseMissingInputs)
  );

  applySignal(missingInputs, "DEVICE_DATA", input.hasEnoughDeviceData);
  applySignal(missingInputs, "BASELINE_ASSESSMENT", input.hasFreshBaseline);
  applySignal(missingInputs, "DOCTOR_INQUIRY", input.hasRecentDoctorInquiry);

  const orderedMissingInputs = PERSONALIZATION_INPUT_ORDER.filter((key) => missingInputs.has(key));
  return {
    personalizationLevel: orderedMissingInputs.length === 0 ? "FULL" : "PREVIEW",
    missingInputs: orderedMissingInputs,
  };
}
