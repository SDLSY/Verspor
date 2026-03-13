import type { Route } from "next";
import { redirect } from "next/navigation";

export default function AnalyticsRedirectPage() {
  redirect("/dashboard" as Route);
}
