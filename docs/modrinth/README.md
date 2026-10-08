# Modrinth project content

These files prepare the three Modrinth listings from the repository README. They have **not been applied to Modrinth**. The current listings could not be inspected because their public API endpoints returned 404 and the T3 browser host could not start its Chrome sandbox.

- `metadata.json` contains proposed summaries, icon files, source/issue/documentation links, MIT licensing and gallery images with captions.
- Each project's Markdown file is its proposed full description.
- `fitness-lib-icon-prompt.txt` records the prompt used to generate the new Fitness Library icon with the imagegen skill's bundled CLI and `gpt-image-2`, using the Bikes and Fitness icons as style references. The final 512×512 icon is `.github/images/icon_fitness_lib.png`.

The seven gallery images are the screenshots referenced by the main README. The library uses a clearly labeled bicycle integration example; Bikes and Fitness use screenshots of their own features.

Before applying, sign in and inspect each project's existing icon, summary, description, links, license and gallery. Preserve any useful project-specific information missing from these drafts. Only replace outdated gallery images after the new ones have uploaded successfully. Preserve existing Discord or donation links if they are valid; the repository README supplies no replacements for those fields.

The repository README describes an upcoming naming/dependency refactor. These drafts avoid claiming that new mod IDs or jar names have already shipped. Check each project's available releases before changing dependency information. Project descriptions do not replace required dependencies on individual Modrinth versions.

The browser reported that AppArmor blocks its sandbox and requested `sudo env "PATH=$PATH" t3 browser setup` on its host. The VibePod container cannot run sudo because it has `no new privileges` set. A Docker headless browser would have separate login state and cannot provide the shared T3 login tab.
