#!/bin/sh
# Run with git and git-cliff 2.14.2 on PATH. Never creates tags in the real repository.
set -eu
config_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
fixture_dir=$(mktemp -d)
trap 'rm -rf -- "$fixture_dir"' EXIT HUP INT TERM
git init -q "$fixture_dir"
cd "$fixture_dir"
git config user.name 'Changelog test'
git config user.email 'changelog-test@example.invalid'
git config commit.gpgsign false
git config tag.gpgsign false
git commit -q --allow-empty -m 'feat!: initial bikes' -m 'BREAKING CHANGE: New mod IDs.'
git tag v0.1.0
git-cliff --config "$config_dir/cliff.toml" --current --use-branch-tags --strip all --output first.md
grep -q '## 0.1.0' first.md
grep -q 'BREAKING' first.md
grep -q 'New mod IDs' first.md
git commit -q --allow-empty -m 'fix(fitness): reconnect trainer'
git tag v0.2.0
git-cliff --config "$config_dir/cliff.toml" --current --use-branch-tags --strip all --output second.md
grep -q '## 0.2.0' second.md
grep -q 'Reconnect trainer' second.md
if grep -q 'Initial bikes' second.md; then exit 1; fi
git checkout -q --detach v0.1.0
git-cliff --config "$config_dir/cliff.toml" --current --use-branch-tags --strip all --output rerun.md
cmp first.md rerun.md
git checkout -q --detach v0.2.0
git commit -q --allow-empty -m 'docs: explain settings'
git-cliff --config "$config_dir/cliff.toml" --output full.md
grep -q '## Unreleased' full.md
grep -q '## 0.1.0' full.md
grep -q '## 0.2.0' full.md
grep -q 'Explain settings' full.md
echo 'Changelog checks passed: first release, release range, old-tag rerun, and unreleased history.'
