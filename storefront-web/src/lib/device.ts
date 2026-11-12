const KEY = 'fincore-device-id';

/** Stable per-browser device fingerprint — minted once, reused forever. */
export function getDeviceId(): string {
  let id = localStorage.getItem(KEY);
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem(KEY, id);
  }
  return id;
}
