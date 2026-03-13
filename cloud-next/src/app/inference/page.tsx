import type { Route } from "next";
import { redirect } from "next/navigation";

export default function InferenceRedirectPage() {
  redirect("/recommendations/profiles" as Route);
}
