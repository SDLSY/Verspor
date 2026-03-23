import ConfirmClient from "./confirm-client";

type SearchParams = Record<string, string | string[] | undefined>;

function pickFirst(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? "" : value ?? "";
}

export default async function AuthConfirmPage({
  searchParams,
}: {
  searchParams?: Promise<SearchParams>;
}) {
  const resolved = (await searchParams) ?? {};
  return (
    <ConfirmClient
      nextPath={pickFirst(resolved.next)}
      tokenHash={pickFirst(resolved.token_hash)}
      type={pickFirst(resolved.type)}
    />
  );
}
