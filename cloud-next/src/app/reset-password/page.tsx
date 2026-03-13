import ResetPasswordClient from "./reset-password-client";

type SearchParams = Record<string, string | string[] | undefined>;

function pickFirst(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? "" : value ?? "";
}

export default async function ResetPasswordPage({
  searchParams,
}: {
  searchParams?: Promise<SearchParams>;
}) {
  const resolved = (await searchParams) ?? {};

  return (
    <ResetPasswordClient
      code={pickFirst(resolved.code)}
      tokenHash={pickFirst(resolved.token_hash)}
      type={pickFirst(resolved.type)}
    />
  );
}
