import type { Route } from "next";
import { redirect } from "next/navigation";

export default function AuditRedirectPage() {
  redirect("/system/audit" as Route);
}
