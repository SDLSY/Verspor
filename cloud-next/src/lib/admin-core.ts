import type { User } from "@supabase/supabase-js";
import { createServiceClient } from "@/lib/supabase";

const DIRECTORY_FETCH_LIMIT = 200;

export type AdminPatientRiskLevel = "HIGH" | "MEDIUM" | "LOW";

export function createAdminError(message: string): Error {
  return new Error(message);
}

export function clampInteger(value: unknown, fallback: number, low: number, high: number): number {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return fallback;
  }
  return Math.max(low, Math.min(high, Math.trunc(numberValue)));
}

export function toTimestamp(value: string | null | undefined): number | null {
  if (!value) {
    return null;
  }
  const timestamp = new Date(value).getTime();
  return Number.isFinite(timestamp) ? timestamp : null;
}

export function toStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((item): item is string => typeof item === "string");
}

export function normalizeDisplayName(user: User): string {
  const metadata = (user.user_metadata ?? {}) as Record<string, unknown>;
  const candidates = [metadata.username, metadata.full_name, metadata.name];
  const found = candidates.find((value): value is string => typeof value === "string" && value.trim().length > 0);
  if (found) {
    return found.trim();
  }
  const email = user.email ?? "";
  if (email.includes("@")) {
    return email.split("@")[0];
  }
  return user.id.slice(0, 8);
}

export function normalizeRiskLevel(
  medicalRisk: string | null | undefined,
  anomalyScore: number | null,
  recoveryScore: number | null
): AdminPatientRiskLevel {
  const normalizedMedical = (medicalRisk ?? "").trim().toUpperCase();
  if (normalizedMedical === "HIGH" || normalizedMedical === "CRITICAL") {
    return "HIGH";
  }
  if (normalizedMedical === "MEDIUM" || normalizedMedical === "MODERATE") {
    return "MEDIUM";
  }
  if (normalizedMedical === "LOW") {
    return "LOW";
  }

  if ((anomalyScore ?? 0) >= 60 || (recoveryScore ?? 100) <= 55) {
    return "HIGH";
  }
  if ((anomalyScore ?? 0) >= 35 || (recoveryScore ?? 100) <= 75) {
    return "MEDIUM";
  }
  return "LOW";
}

export function mapToneFromStatus(status: string): "neutral" | "success" | "warning" | "danger" | "info" {
  const normalized = status.trim().toUpperCase();
  if (normalized === "SUCCEEDED" || normalized === "COMPLETED" || normalized === "PARSED") {
    return "success";
  }
  if (normalized === "FAILED") {
    return "danger";
  }
  if (normalized === "PENDING" || normalized === "RUNNING") {
    return "warning";
  }
  return "info";
}

export function buildMetadataObject(value: unknown): Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return {};
  }
  return value as Record<string, unknown>;
}

export function latestBy<T>(rows: T[], getKey: (row: T) => string, getTime: (row: T) => number | null): Map<string, T> {
  const output = new Map<string, T>();
  rows.forEach((row) => {
    const key = getKey(row);
    const nextTime = getTime(row) ?? -1;
    const current = output.get(key);
    const currentTime = current ? getTime(current) ?? -1 : -1;
    if (!current || nextTime >= currentTime) {
      output.set(key, row);
    }
  });
  return output;
}

export function groupCount<T>(rows: T[], getKey: (row: T) => string, predicate?: (row: T) => boolean): Map<string, number> {
  const output = new Map<string, number>();
  rows.forEach((row) => {
    if (predicate && !predicate(row)) {
      return;
    }
    const key = getKey(row);
    output.set(key, (output.get(key) ?? 0) + 1);
  });
  return output;
}

export async function listDirectoryUsers(limit = DIRECTORY_FETCH_LIMIT): Promise<User[]> {
  const client = createServiceClient();
  const users: User[] = [];
  let page = 1;
  const perPage = 100;

  while (users.length < limit) {
    const { data, error } = await client.auth.admin.listUsers({
      page,
      perPage: Math.min(perPage, limit - users.length),
    });

    if (error) {
      throw createAdminError(error.message);
    }

    const pageUsers = data.users ?? [];
    if (pageUsers.length === 0) {
      break;
    }

    users.push(...pageUsers);
    if (pageUsers.length < perPage) {
      break;
    }
    page += 1;
  }

  return users;
}

export async function getDirectoryUser(userId: string): Promise<User | null> {
  const client = createServiceClient();
  const { data, error } = await client.auth.admin.getUserById(userId);
  if (error) {
    throw createAdminError(error.message);
  }
  return data.user ?? null;
}

export function requireDirectoryUser(user: User | null, userId: string): User {
  if (!user) {
    throw createAdminError(`patient not found: ${userId}`);
  }
  return user;
}
