// Next's route values retain URL escapes; malformed legacy links remain unknown destinations.
export function decodeRoute(value: string) {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}
