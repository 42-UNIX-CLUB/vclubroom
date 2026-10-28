/** No look-alikes (0/O, 1/l/I): the admin reads it out or types it into a message. */
const ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

/** A random temporary password from the browser's secure generator, every character equally likely. */
export function temporaryPassword(length = 14): string {
  const limit = 256 - (256 % ALPHABET.length);              // bytes past the last full round would favour some letters
  let password = "";
  while (password.length < length) {
    for (const byte of crypto.getRandomValues(new Uint8Array(length))) {
      if (byte < limit && password.length < length) password += ALPHABET[byte % ALPHABET.length];
    }
  }
  return password;
}
