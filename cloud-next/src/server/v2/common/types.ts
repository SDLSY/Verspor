export type Envelope<T> = {
  code: number;
  message: string;
  data?: T;
  traceId: string;
};

export function makeTraceId(): string {
  return `trace_${Math.random().toString(36).slice(2, 10)}`;
}

export function ok<T>(traceId: string, message: string, data?: T): Envelope<T> {
  return { code: 0, message, data, traceId };
}

export function fail(traceId: string, code: number, message: string): Envelope<never> {
  return { code, message, traceId };
}
