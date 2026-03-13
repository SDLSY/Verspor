import { getAdminAudit } from "@/lib/admin-system";
import { withAdminRoute } from "@/lib/admin-route";

function readNumber(value: string | null): number | undefined {
  if (!value) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function readTime(value: string | null): number | undefined {
  if (!value) {
    return undefined;
  }
  const fromNumber = Number(value);
  if (Number.isFinite(fromNumber)) {
    return fromNumber;
  }
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export async function GET(req: Request) {
  const searchParams = new URL(req.url).searchParams;
  return withAdminRoute(async () =>
    getAdminAudit({
      page: readNumber(searchParams.get("page")),
      pageSize: readNumber(searchParams.get("pageSize")),
      actor: searchParams.get("actor") ?? undefined,
      action: searchParams.get("action") ?? undefined,
      resourceType: searchParams.get("resourceType") ?? undefined,
      startAt: readTime(searchParams.get("startAt")),
      endAt: readTime(searchParams.get("endAt")),
    })
  );
}