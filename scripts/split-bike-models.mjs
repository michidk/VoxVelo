import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const modelRoot = join(root, "src/main/resources/assets/voxelvelo/geckolib/models/entity");
const bikeRoot = join(modelRoot, "bikes");
const frameRoot = join(modelRoot, "bike_frames");
const partRoot = join(modelRoot, "bike_parts");
const bikeNames = ["race_bike", "gravel_bike", "mountain_bike"];
const partBones = ["front_wheel", "rear_wheel", "handlebar"];

await mkdir(frameRoot, { recursive: true });
await mkdir(partRoot, { recursive: true });

for (const bikeName of bikeNames) {
    const sourcePath = join(bikeRoot, `${bikeName}.geo.json`);
    const source = JSON.parse(await readFile(sourcePath, "utf8"));
    const geometry = source["minecraft:geometry"]?.[0];
    if (!geometry || !Array.isArray(geometry.bones)) throw new Error(`Invalid GeckoLib model: ${sourcePath}`);

    const frame = structuredClone(source);
    const frameGeometry = frame["minecraft:geometry"][0];
    frameGeometry.description.identifier = `geometry.voxelvelo.${bikeName}_frame`;
    const extractedDescendants = new Set();

    for (const boneName of partBones) {
        const sourceBone = geometry.bones.find(bone => bone.name === boneName);
        const frameBone = frameGeometry.bones.find(bone => bone.name === boneName);
        if (!sourceBone || !frameBone) throw new Error(`${bikeName} is missing required bone ${boneName}`);

        const componentNames = new Set([boneName]);
        let foundChild;
        do {
            foundChild = false;
            for (const bone of geometry.bones) {
                if (bone.parent && componentNames.has(bone.parent) && !componentNames.has(bone.name)) {
                    componentNames.add(bone.name);
                    extractedDescendants.add(bone.name);
                    foundChild = true;
                }
            }
        } while (foundChild);

        // The frame keeps the authored bone transform as the stable attachment node, but no component geometry.
        delete frameBone.cubes;

        // A component model owns only its visual geometry. Its original pivot is retained so the render layer can
        // translate the geometry into the host frame's attachment coordinate system.
        const componentBones = geometry.bones.filter(bone => componentNames.has(bone.name)).map(bone => {
            const componentBone = structuredClone(bone);
            if (componentBone.name === boneName) {
                componentBone.name = "component";
                delete componentBone.parent;
            } else if (componentBone.parent === boneName) {
                componentBone.parent = "component";
            }
            return componentBone;
        });

        const component = {
            format_version: source.format_version,
            "minecraft:geometry": [{
                description: {
                    ...structuredClone(geometry.description),
                    identifier: `geometry.voxelvelo.${bikeName}_${boneName}`,
                },
                bones: componentBones,
            }],
        };
        await writeModel(join(partRoot, `${bikeName}_${boneName}.geo.json`), component);
    }

    frameGeometry.bones = frameGeometry.bones.filter(bone => !extractedDescendants.has(bone.name));
    await writeModel(join(frameRoot, `${bikeName}.geo.json`), frame);
}

async function writeModel(path, model) {
    // Generated runtime assets stay compact; the full source models above remain the human-editable form.
    await writeFile(path, `${JSON.stringify(model)}\n`);
}
