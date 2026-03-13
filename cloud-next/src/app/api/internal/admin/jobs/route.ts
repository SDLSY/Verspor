import { getAdminJobs } from "@/lib/admin-system";
import { withAdminRoute } from "@/lib/admin-route";

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
    getAdminJobs({
      page: readNumber(searchParams.get("page")),
      pageSize: readNumber(searchParams.get("pageSize")),
      status: searchParams.get("status") ?? undefined,
    })
  );
}