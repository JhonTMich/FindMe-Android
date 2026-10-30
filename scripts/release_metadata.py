"""Translate a stable Git tag into deterministic Android release metadata."""
import argparse
import json
import re
from pathlib import Path

MAX_VERSION_CODE = 2_100_000_000

def metadata(tag: str, previous_tag: str | None = None) -> dict:
    match = re.fullmatch(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:\.(0|[1-9][0-9]*))?", tag)
    if not match:
        raise ValueError("Use a stable numeric tag, for example v1.2.3 or v1.3.")
    major, minor, patch = (int(value or "0") for value in match.groups())
    if minor > 999 or patch > 999:
        raise ValueError("Minor and patch components must be at most 999.")
    code = major * 1_000_000 + minor * 1_000 + patch
    if not 1 <= code <= MAX_VERSION_CODE:
        raise ValueError("The tag exceeds Android versionCode limits.")
    if previous_tag and code <= metadata(previous_tag)["version_code"]:
        raise ValueError("The new tag must be newer than the latest published release.")
    return {"tag": tag, "version": tag[1:], "version_code": code, "apk_name": f"FindMe-{tag[1:]}.apk"}

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tag")
    parser.add_argument("--previous-tag")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()
    try:
        values = metadata(args.tag, args.previous_tag)
    except ValueError as error:
        parser.error(str(error))
    if args.output:
        args.output.write_text(json.dumps(values, indent=2) + "\n", encoding="utf-8")
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as output:
            for name in ["version", "version_code", "apk_name"]:
                output.write(f"{name}={values[name]}\n")
    print(json.dumps(values))

if __name__ == "__main__":
    main()
