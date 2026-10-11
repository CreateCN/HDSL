#!/usr/bin/env python3
"""Writes the changelog of a release out of the commits it was made of.

One commit is one line, classified by the conventional prefix it carries: `feat(scope): summary`,
`fix!: summary`, or nothing at all. The sections and their order are changelogen's, which is what
the releases of this project's siblings are written with, with the emoji left off and a colon after
each name. A commit whose prefix is not one of them is not thrown away: it is listed under Others,
because a changelog that silently drops a change is worse than one with a section nobody expects.

Usage:
    python3 scripts/changelog.py                     # since the last tag, up to HEAD
    python3 scripts/changelog.py --from v0.2.0 --to HEAD
    python3 scripts/changelog.py --repo MCXCC303/harness-launcher
"""

import argparse
import re
import subprocess
import sys
from collections import OrderedDict

# changelogen's sections, in its order, without the emoji.
# https://github.com/unjs/changelogen/blob/main/src/types.ts
SECTIONS = [
    ("feat", "Enhancements"),
    ("fix", "Fixes"),
    ("perf", "Performance"),
    ("refactor", "Refactors"),
    ("docs", "Documentation"),
    ("build", "Build"),
    ("types", "Types"),
    ("chore", "Chores"),
    ("examples", "Examples"),
    ("test", "Tests"),
    ("style", "Styles"),
    ("ci", "CI"),
]

OTHERS = "Others"
COMMIT = re.compile(r"^(?P<type>[a-zA-Z]+)(?:\((?P<scope>[^)]*)\))?(?P<breaking>!)?:\s*(?P<summary>.+)$")


def git(*args):
    result = subprocess.run(["git", *args], capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit("git " + " ".join(args) + " failed: " + result.stderr.strip())
    return result.stdout


def last_tag():
    tags = git("tag", "--merged", "HEAD", "--sort=-creatordate").split()
    return tags[0] if tags else None


def repository():
    url = git("remote", "get-url", "origin").strip()
    for prefix in ("https://github.com/", "git@github.com:"):
        if url.startswith(prefix):
            return url[len(prefix):].removesuffix(".git")
    return None


def commits(revision):
    """Returns (short, full, author, subject) for every commit in a revision range."""
    out = git("log", "--no-merges", "--pretty=%h%x09%H%x09%an%x09%s", revision)
    for line in out.splitlines():
        parts = line.split("\t", 3)
        if len(parts) == 4:
            yield parts


def classify(subject):
    """Returns (section key, text) for one commit subject."""
    match = COMMIT.match(subject.strip())
    if not match:
        return OTHERS, subject.strip()
    kind = match.group("type").lower()
    if kind not in [key for key, _ in SECTIONS]:
        return OTHERS, subject.strip()
    summary = match.group("summary").strip()
    if match.group("scope"):
        summary = "**" + match.group("scope") + ":** " + summary
    if match.group("breaking"):
        summary = summary + " (BREAKING)"
    return kind, summary


def render(revision, repo):
    found = OrderedDict((key, []) for key, _ in SECTIONS)
    found[OTHERS] = []
    authors = OrderedDict()
    for short, full, author, subject in commits(revision):
        key, text = classify(subject)
        link = "([" + short + "](https://github.com/" + repo + "/commit/" + full + "))" if repo else "(" + short + ")"
        found[key].append("- " + text + " " + link)
        authors[author] = authors.get(author, 0) + 1

    lines = ["## Changelog", ""]
    for key, title in SECTIONS:
        if found[key]:
            lines.append("### " + title + ":")
            lines.extend(found[key])
            lines.append("")
    if found[OTHERS]:
        lines.append("### " + OTHERS + ":")
        lines.extend(found[OTHERS])
        lines.append("")
    if authors:
        lines.append("### Contributors:")
        for name in sorted(authors, key=lambda n: (-authors[n], n)):
            lines.append("- " + name)
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def main():
    parser = argparse.ArgumentParser(description="Write a release's changelog out of its commits.")
    parser.add_argument("--from", dest="start", help="the tag the release starts after (default: the last one)")
    parser.add_argument("--to", dest="end", default="HEAD", help="the commit the release ends at (default: HEAD)")
    parser.add_argument("--repo", help="owner/name for the links (default: read from origin)")
    args = parser.parse_args()

    start = args.start or last_tag()
    revision = (start + ".." + args.end) if start else args.end
    print(render(revision, args.repo or repository()), end="")


if __name__ == "__main__":
    main()