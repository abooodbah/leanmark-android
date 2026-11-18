"""Checks the "Release files" workflow for the build provenance attestation.

    python3 scripts/check-release-workflow.py [.github/workflows/release.yml]

The release job must be allowed to request an OIDC token and write
attestations, and must run actions/attest-build-provenance, pinned to its
v4.2.2 commit, on the release APK and bundle after they are built, checked
and hashed and before they are attached to the release. The workflow only
runs for a tag, so CI checks its text instead. Plain text checks: the runner
has no YAML parser for certain."""

import pathlib
import re
import sys

ACTION = "actions/attest-build-provenance@4d101475d8b20a2381f78447822ac1eab6504dd8 # v4.2.2"


def steps(text):
    """Step names in order, with the text of each step."""
    found = []
    for match in re.finditer(r"^      - name: (.+)$", text, re.M):
        found.append([match.group(1).strip(), match.start()])
    result = []
    for index, (name, start) in enumerate(found):
        end = found[index + 1][1] if index + 1 < len(found) else len(text)
        result.append((name, text[start:end]))
    return result


def problems(text):
    found = []
    job = text.split("\njobs:\n", 1)[-1]
    permissions = re.search(r"^    permissions:\n((?:      .+\n)+)", job, re.M)
    granted = dict(re.findall(r"^      ([a-z-]+): ([a-z]+)$", permissions.group(1), re.M)) if permissions else {}
    for name, level in (("contents", "write"), ("id-token", "write"), ("attestations", "write")):
        if granted.get(name) != level:
            found.append(f"the release job needs {name}: {level}")
    order = steps(job)
    names = [name for name, _ in order]
    attest = [index for index, (_, body) in enumerate(order) if "attest-build-provenance" in body]
    if len(attest) != 1:
        found.append("there must be exactly one attest-build-provenance step")
        return found
    step = order[attest[0]][1]
    if f"uses: {ACTION}" not in step:
        found.append("the attest step must use " + ACTION)
    # The release APK and bundle by name; the debug APK and the test files
    # are not what a user installs.
    for subject in ("dist/LeanMark-${{ env.VERSION }}.apk", "dist/LeanMark-${{ env.VERSION }}.aab"):
        if subject not in step:
            found.append(f"the attest step must cover {subject}")
    for before in ("Check the signer", "Name, collect and hash"):
        if before not in names or names.index(before) > attest[0]:
            found.append(f"the attest step must come after \"{before}\"")
    if "Attach to the release" not in names or names.index("Attach to the release") < attest[0]:
        found.append("the attest step must come before \"Attach to the release\"")
    return found


def main():
    path = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".github/workflows/release.yml")
    found = problems(path.read_text())
    for problem in found:
        print(f"::error file={path}::{problem}")
    if found:
        sys.exit(1)
    print(f"{path}: the release job attests the APK and the bundle before attaching them.")


if __name__ == "__main__":
    main()
