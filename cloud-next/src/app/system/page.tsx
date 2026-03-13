import type { Route } from "next";
import { redirect } from "next/navigation";

export default function SystemPage() {
  redirect("/system/models" as Route);
}
