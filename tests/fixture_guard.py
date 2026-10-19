"""Keep fixture-writing regressions out of an installed reading environment."""
from pathlib import Path


def require_fixture_environment(root: Path):
    if (root / '.local/academy-content.json').exists():
        raise RuntimeError(
            'This checkout contains the installed reading curriculum. Fixture-writing tests are '
            'blocked here. Use a separate checkout, private runtime configuration and isolated '
            'test databases; do not remove the curriculum marker to bypass this protection.'
        )
