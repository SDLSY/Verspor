import { getAdminPatientTimeline } from "@/lib/admin-patients";
import { withAdminRoute } from "@/lib/admin-route";

type RouteContext = {
  params: Promise<{ userId: string }>;
};

export async function GET(req: Request, context: RouteContext) {
  const { userId } = await context.params;
  const searchParams = new URL(req.url).searchParams;
  const types = [
    ...searchParams.getAll("types"),
    ...(searchParams.get("type") ? [searchParams.get("type") as string] : []),
  ]
    .flatMap((value) => value.split(","))
    .map((value) => value.trim())
    .filter(Boolean);

  return withAdminRoute(async () => getAdminPatientTimeline(userId, { types }));
}
