import type { SupabaseClient } from "@supabase/supabase-js";

type AuditInput = {
  userId?: string | null;
  actor: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  metadata?: Record<string, unknown>;
};

export async function writeAuditEvent(
  client: SupabaseClient,
  input: AuditInput
): Promise<void> {
  await client.from("audit_events").insert({
    user_id: input.userId ?? null,
    actor: input.actor,
    action: input.action,
    resource_type: input.resourceType,
    resource_id: input.resourceId ?? null,
    metadata: input.metadata ?? {},
  });
}
