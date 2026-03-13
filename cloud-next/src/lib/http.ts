export type ApiSuccess<T> = {
  code: number;
  message: string;
  data?: T;
  traceId: string;
};

export type ApiError = {
  code: number;
  message: string;
  traceId: string;
};

export function ok<T>(
  message: string,
  traceIdOrData?: string | T,
  maybeData?: T
): ApiSuccess<T> {
  const traceId = typeof traceIdOrData === "string" ? traceIdOrData : createTraceId();
  const data = (typeof traceIdOrData === "string" ? maybeData : traceIdOrData) as T | undefined;
  return {
    code: 0,
    message,
    data,
    traceId,
  };
}

export function fail(code: number, message: string, traceId: string = createTraceId()): ApiError {
  return {
    code,
    message,
    traceId,
  };
}

export function parseJsonBody<T = Record<string, unknown>>(input: unknown): T {
  if (typeof input !== "object" || input === null) {
    return {} as T;
  }
  return input as T;
}

export function randomId(prefix: string): string {
  return `${prefix}_${Math.random().toString(36).slice(2, 10)}`;
}

export function createTraceId(): string {
  return randomId("trace");
}
