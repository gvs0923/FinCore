import re

_DIGITS = re.compile(r"(?<!\d)(?:\d[ -]?){12,18}\d(?!\d)")
_EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
_CONTROL = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")


def _luhn(digits: str) -> bool:
    total = 0
    for i, ch in enumerate(reversed(digits)):
        n = int(ch)
        if i % 2:
            n = n * 2 - 9 if n > 4 else n * 2
        total += n
    return total % 10 == 0


def redact(text: str) -> str:
    """Backstop before anything reaches an index or a model: PAN-like numbers, emails, control chars."""
    def pan(match: re.Match) -> str:
        digits = re.sub(r"\D", "", match.group())
        return "[REDACTED_PAN]" if 13 <= len(digits) <= 19 and _luhn(digits) else match.group()

    return _CONTROL.sub("", _EMAIL.sub("[REDACTED_EMAIL]", _DIGITS.sub(pan, text)))