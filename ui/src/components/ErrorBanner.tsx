/** An inline failure with an optional retry. Renders nothing without a message. */
export function ErrorBanner({
  message,
  onRetry,
  retryLabel = "Retry",
}: {
  message?: string | null;
  onRetry?: () => void;
  retryLabel?: string;
}) {
  if (!message) return null;
  return (
    <div className="error-banner" role="alert">
      {message}
      {onRetry && (
        <button className="text-button" onClick={onRetry}>
          {retryLabel}
        </button>
      )}
    </div>
  );
}
