/**
 * Minimal structured logger.
 *
 * Deliberately dumb: it takes a message and a flat context object, and it
 * strips anything whose key looks like a credential so a stray `{ apiKey }`
 * can never reach the logs.
 */

const SECRET_KEY_PATTERN = /(key|secret|token|password|authorization|cookie|credential)/i;

type LogContext = Record<string, unknown>;

function redact(context: LogContext | undefined): LogContext | undefined {
  if (!context) return undefined;
  const safe: LogContext = {};
  for (const [key, value] of Object.entries(context)) {
    safe[key] = SECRET_KEY_PATTERN.test(key) ? '[redacted]' : value;
  }
  return safe;
}

function emit(level: 'info' | 'warn' | 'error', message: string, context?: LogContext) {
  const payload = redact(context);
  const line = payload ? `${message} ${JSON.stringify(payload)}` : message;
  console[level](`[pricefinder] ${line}`);
}

export const logger = {
  info: (message: string, context?: LogContext) => emit('info', message, context),
  warn: (message: string, context?: LogContext) => emit('warn', message, context),
  error: (message: string, context?: LogContext) => emit('error', message, context),
};

/**
 * Turns an unknown thrown value into a message that is safe to show a user:
 * no stack traces, no URLs that might embed a key, and length-capped.
 */
export function toSafeMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (error instanceof Error && error.message) {
    return error.message.replace(/https?:\/\/\S+/g, '[url]').slice(0, 200);
  }
  return fallback;
}
