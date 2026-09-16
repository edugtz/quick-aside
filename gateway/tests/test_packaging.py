"""Deterministic packaging remediation gate (QAG-003 Gate F).

The live deployment install command ``pip install /opt/quickaside-gateway/gateway``
failed because setuptools flat-layout auto-discovery found multiple top-level
packages:

    Multiple top-level packages discovered in a flat-layout:
    ['deploy', 'quickaside_gateway']

``pyproject.toml`` now restricts discovery explicitly to the
``quickaside_gateway`` Python package.  This suite proves, from a clean
tree, that the distribution:

- builds and installs successfully;
- contains only the ``quickaside_gateway`` package (never ``deploy/``);
- imports after installation; and
- ships ``provider_output_schema.json`` as package data.

Dependency resolution is standard PyPI behavior already exercised by the
installed ``.[test]`` environment; this gate isolates packaging and install
mechanics deterministically (no network beyond PEP 517 build isolation).
"""

import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

GATEWAY_ROOT = Path(__file__).resolve().parents[1]

# Generated/heavyweight paths that must not influence the clean build.
_IGNORED = {
    ".venv",
    ".pytest_cache",
    "__pycache__",
    "*.egg-info",
    "build",
    "dist",
    ".DS_Store",
}


def _copy_clean_source(destination: Path) -> Path:
    """Copy the gateway tree into an isolated temp location.

    ``deploy/`` (the discovered-package culprit) is deliberately kept so the
    observed failure mode is reproduced unless the packaging config fixes it.
    """
    source = destination / "source"
    shutil.copytree(
        GATEWAY_ROOT,
        source,
        ignore=shutil.ignore_patterns(*_IGNORED),
        symlinks=False,
    )
    return source


def _build_wheel(source: Path, wheelhouse: Path) -> Path:
    subprocess.run(
        [
            sys.executable,
            "-m",
            "pip",
            "wheel",
            "--no-deps",
            "-w",
            str(wheelhouse),
            str(source),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    wheels = sorted(wheelhouse.glob("quickaside_gateway-*.whl"))
    assert len(wheels) == 1, f"expected exactly one wheel, found: {wheels}"
    return wheels[0]


def test_distribution_contains_only_quickaside_gateway_package():
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        source = _copy_clean_source(tmp_path)
        wheel = _build_wheel(source, tmp_path / "wheelhouse")

        with zipfile.ZipFile(wheel) as zf:
            names = zf.namelist()
            dist_info = next(
                name for name in names
                if name.endswith(".dist-info/METADATA")
            ).split("/", 1)[0]
            top_levels = {name.split("/", 1)[0] for name in names} - {dist_info}

            assert top_levels == {"quickaside_gateway"}
            assert "quickaside_gateway/provider_output_schema.json" in names
            assert not any(name.startswith("deploy/") for name in names)


def test_clean_install_import_and_package_data():
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        source = _copy_clean_source(tmp_path)
        wheel = _build_wheel(source, tmp_path / "wheelhouse")
        target = tmp_path / "installed"

        subprocess.run(
            [
                sys.executable,
                "-m",
                "pip",
                "install",
                "--no-deps",
                "--target",
                str(target),
                str(wheel),
            ],
            check=True,
            capture_output=True,
            text=True,
        )

        expected_schema = GATEWAY_ROOT / "quickaside_gateway" / "provider_output_schema.json"
        installed_schema = target / "quickaside_gateway" / "provider_output_schema.json"
        assert installed_schema.is_file()
        assert installed_schema.read_bytes() == expected_schema.read_bytes()

        env = dict(os.environ)
        env["PYTHONPATH"] = str(target)
        probe = (
            "import importlib.resources as res\n"
            "import quickaside_gateway\n"
            f"assert quickaside_gateway.__file__.startswith({str(target)!r}), "
            "quickaside_gateway.__file__\n"
            "schema = res.files(quickaside_gateway).joinpath('provider_output_schema.json')\n"
            "assert schema.is_file(), schema\n"
            "print('IMPORT_OK', quickaside_gateway.__file__)\n"
        )
        result = subprocess.run(
            [sys.executable, "-c", probe],
            env=env,
            cwd=tmp_path,
            check=True,
            capture_output=True,
            text=True,
        )
        assert "IMPORT_OK" in result.stdout