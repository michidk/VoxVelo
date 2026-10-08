# Changelogs and releases

Bikes, Fitness Library and Fitness are released together with one version and one changelog.
Use Conventional Commits (`feat`, `fix`, `refactor`, `docs`, `ci`, etc.). Add `!` or a
`BREAKING CHANGE:` footer for breaking changes. Optional scopes identify the affected mod or feature.

With git-cliff 2.14.2 installed, regenerate the tracked changelog before a release:

```sh
git-cliff --config cliff.toml --output CHANGELOG.md
```

To label the pending section with the planned release version before its tag exists:

```sh
git-cliff --config cliff.toml --tag v0.1.0 --output CHANGELOG.md
```

Review and commit the result with the matching `mod_version` in `gradle.properties`, then tag that
commit `v<mod_version>`. The `--tag` option only labels the generated changelog; it does not create a Git tag.
The detailed original feature inventory is preserved in [first-release-features.md](first-release-features.md).

The release workflow fetches complete history and uses `orhun/git-cliff-action@v4` with pinned git-cliff
2.14.2 and `--current --use-branch-tags --strip all`. `softprops/action-gh-release@v2` publishes the GitHub
release and its three JARs; `Kira-NT/mc-publish` handles Modrinth and CurseForge.
This selects the release at the checked-out tag (including the first release, with no previous tag),
not newer releases that might exist when rerunning an older workflow. The same generated Markdown file
is passed to GitHub Releases and all configured Modrinth/CurseForge projects. No extra token is needed
for changelog generation, and CI does not push changelog commits or create release tags.

Run `sh scripts/verify-changelog.sh` to test first-release notes, subsequent release ranges, rerunning an
older tag, and unreleased history in a temporary Git repository.
