#!/usr/bin/env python3
"""Writes the changelog of a release out of the commits it was made of.

One commit is one line, classified by the conventional prefix it carries: `feat(scope): summary`,
`fix!: summary`, or nothing at all. The sections and their order are changelogen's, which is what
the releases of this project's siblings are written with, with the emoji left off and a colon after
each name. A commit whose prefix is not one of them is not thrown away: it is listed under Others,
because a changelog that silently drops a change is worse than one with a section nobody expects.

Contributors are GitHub logins when they can be had, and git author names when they cannot. A login
is one person: the same person committing under two names — one set here, another on GitHub — would
otherwise be listed twice, which is what a changelog must not do to the people it thanks.

Usage:
    python3 scripts/changelog.py                          # since the base tag, up to HEAD
    python3 scripts/changelog.py --tag v0.2.1-rc.3        # a pre-release: the base is any tag
    python3 scripts/changelog.py --tag v0.2.1             # stable: the base is the last stable tag
    python3 scripts/changelog.py --from v0.2.0 --to HEAD --repo MCXCC303/HDSL
"""

import argparse
import json
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


def repository():
    url = git("remote", "get-url", "origin").strip()
    for prefix in ("https://github.com/", "git@github.com:"):
        if url.startswith(prefix):
            return url[len(prefix):].removesuffix(".git")
    return None


def is_prerelease(tag):
    """A tag with a hyphen in it names a pre-release: `v0.2.1-rc.1`, not `v0.2.1`."""
    return tag is not None and "-" in tag.removeprefix("v")


def base_tag(tag, end):
    """Returns the tag the release starts after.

    A stable release starts after the last stable one, so a series of release candidates is
    summarised by the release it led to rather than hidden behind the last candidate: somebody who
    takes `v0.2.1` is taking everything since `v0.2.0`. A pre-release starts after whatever came
    before it, candidate or not, because that is what it is a candidate for.
    """
    tags = git("tag", "--merged", end, "--sort=-creatordate").split()
    stable = not is_prerelease(tag)
    for candidate in tags:
        if candidate == tag:
            continue
        if stable and is_prerelease(candidate):
            continue
        return candidate
    return None


def from_git(revision):
    """Yields (short, full, name, email, subject) read out of this checkout."""
    out = git("log", "--no-merges", "--pretty=%h%x09%H%x09%an%x09%ae%x09%s", revision)
    for line in out.splitlines():
        parts = line.split("\t", 4)
        if len(parts) == 5:
            yield parts[0], parts[1], parts[2], parts[3], parts[4]


def from_github(repo, start, end):
    """Yields the same, with the login GitHub knows the author by, or `None`.

    The compare endpoint answers the whole range in one request and carries both halves of what is
    wanted: the commit's own message and the account GitHub matched its author to. An account is
    only there when the commit's address belongs to one, which is why the name and the address come
    back with it — a commit from an address nobody has claimed still has an author to thank.
    """
    result = subprocess.run(
        ["gh", "api", "repos/" + repo + "/compare/" + start + "..." + end + "?per_page=250"],
        capture_output=True, text=True)
    if result.returncode != 0:
        return None
    try:
        data = json.loads(result.stdout)
    except json.JSONDecodeError:
        return None
    out = []
    for entry in data.get("commits", []):
        sha = entry.get("sha", "")
        message = entry.get("commit", {}).get("message", "").split("\n")[0]
        author = entry.get("author") or {}
        person = entry.get("commit", {}).get("author") or {}
        out.append((sha[:7], sha, person.get("name") or "?", person.get("email") or "", message,
                    author.get("login")))
    return out


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


def render(rows, repo):
    found = OrderedDict((key, []) for key, _ in SECTIONS)
    found[OTHERS] = []
    people = OrderedDict()
    for short, full, name, email, subject, login in rows:
        key, text = classify(subject)
        link = "([" + short + "](https://github.com/" + repo + "/commit/" + full + "))" if repo else "(" + short + ")"
        found[key].append("- " + text + " " + link)
        # A login is one person; without one, an address is the next best thing, since a name is
        # whatever the person last typed into git config.
        who = "@" + login if login else name
        seen = login or email or name
        people[seen] = who

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
    if people:
        lines.append("### Contributors:")
        for who in sorted(people.values(), key=lambda w: w.lower()):
            lines.append("- " + who)
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def main():
    parser = argparse.ArgumentParser(description="Write a release's changelog out of its commits.")
    parser.add_argument("--tag", help="the tag being released, which decides what the base tag may be")
    parser.add_argument("--from", dest="start", help="the tag to start after (default: worked out from --tag)")
    parser.add_argument("--to", dest="end", default="HEAD", help="the commit to end at (default: HEAD)")
    parser.add_argument("--repo", help="owner/name for the links (default: read from origin)")
    parser.add_argument("--no-github", action="store_true", help="do not ask GitHub who the authors are")
    args = parser.parse_args()

    repo = args.repo or repository()
    # The tag a dry run is about does not exist yet, so the range ends at the commit named instead.
    # The tag is still what decides whether the base may be a pre-release.
    end = args.tag if _exists(args.tag) else args.end
    start = args.start or base_tag(args.tag, end)
    revision = (start + ".." + end) if start else end

    rows = None
    if repo and not args.no_github:
        rows = from_github(repo, start or "HEAD", end)
    if rows is None:
        rows = [(s, f, n, e, sub, None) for s, f, n, e, sub in from_git(revision)]

    print(render(rows, repo), end="")


def _exists(tag):
    if tag is None:
        return False
    return subprocess.run(["git", "rev-parse", "--verify", "--quiet", "refs/tags/" + tag],
                          capture_output=True).returncode == 0


if __name__ == "__main__":
    main()