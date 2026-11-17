/* Staged only: not served, referenced or allowed by the live Content Security Policy.
 * Activation requires a Google-certified consent flow and an explicit CSP review.
 * Never load ad code into the Android control WebView / native bridge.
 */
export function prepareAdSense(config, consent) {
  if (!config.enabled) return null;
  if (!/^ca-pub-\d{16}$/.test(config.publisher_id) || !/^\d+$/.test(config.slot_id)) {
    throw new Error('Invalid AdSense identifiers');
  }
  if (!consent || consent.canRequestAds !== true) return null;
  throw new Error('AdSense activation pending consent integration and CSP review');
}
