import { listAdminPatients } from "@/lib/admin-patients";
import { withAdminRoute } from "@/lib/admin-route";

function readBoolean(value: string | null): boolean {
  return value === "1" || value === "true";
}

function readNumber(value: string | null): number | undefined {
  if (!value) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export async function GET(req: Request) {
  const searchParams = new URL(req.url).searchParams;
  return withAdminRoute(async () =>
    listAdminPatients({
      page: readNumber(searchParams.get("page")),
      pageSize: readNumber(searchParams.get("pageSize")),
      q: searchParams.get("q") ?? undefined,
      riskLevel: searchParams.get("riskLevel") ?? undefined,
      pendingOnly: readBoolean(searchParams.get("pendingOnly")),
      failedOnly: readBoolean(searchParams.get("failedOnly")),
      recentSleepOnly: readBoolean(searchParams.get("recentSleepOnly")),
      recentSleepDays: readNumber(searchParams.get("recentSleepDays")),
    })
  );
}