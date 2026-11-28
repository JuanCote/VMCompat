"""Build the experimental, unsigned Storm loader package from source and installed dependencies.

The package is a Storm java mod: Storm loads every jar in the mod's version folder and instantiates
the class that implements ZomboidMod. It holds the same VMCompat classes as the Workshop build plus
src/storm/java, and no ZombieBuddy metadata. Install it under ~/Zomboid/mods, which Storm always loads.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

import build_workshop as base

ROOT = base.ROOT
OUT = base.OUT
STORM = os.environ.get("VMCOMPAT_STORM_JAR")


def build():
    if not STORM or not Path(STORM).expanduser().is_file():
        raise SystemExit("Set VMCOMPAT_STORM_JAR to the storm.jar your game runs "
                         "(Storm stages it under ~/Zomboid/storm/core/stage/<id>/storm.jar).")
    storm = Path(STORM).expanduser().resolve()
    for path, variable in {base.GAME / "projectzomboid.jar": "VMCOMPAT_GAME_JAVA_DIR", base.VP: "VMCOMPAT_VIEWPOINT_JAR",
                           base.JDK / "javac": "JAVA_HOME", base.JDK / "jar": "JAVA_HOME"}.items():
        if not path.is_file():
            raise SystemExit(f"Missing {path}. Set {variable} to your local installation.")

    base.BUILD.mkdir(parents=True, exist_ok=True)
    classpath = os.pathsep.join(map(str, (base.GAME / "projectzomboid.jar", base.VP, storm)))
    sources = sorted((ROOT / "src/main/java").rglob("*.java")) + sorted((ROOT / "src/storm/java").rglob("*.java"))
    package = OUT / "storm"
    mod = package / "VMCompatStorm"
    if mod.exists():
        shutil.rmtree(mod)
    with tempfile.TemporaryDirectory(prefix="compile-storm-", dir=base.BUILD) as workdir:
        work = Path(workdir)
        classes = work / "classes"
        classes.mkdir()
        subprocess.run([str(base.JDK / "javac"), "--release", "25", "-encoding", "UTF-8",
                        "-cp", classpath, "-d", str(classes), *map(str, sources)], check=True)
        shutil.copytree(ROOT / "src/main/resources", classes, dirs_exist_ok=True)
        manifest = work / "MANIFEST.MF"
        # No ZB-Preload: ZombieBuddy must not pick this jar up.
        manifest.write_text("Manifest-Version: 1.0\nImplementation-Title: VMCompat (Storm loader)\n"
                            "Implementation-Version: 1.0.20-storm\n", encoding="utf-8")
        shutil.copytree(ROOT / "src/storm/mod", mod)
        jar = mod / "42/VMCompatStorm.jar"
        subprocess.run([str(base.JDK / "jar"), "--create", "--file", str(jar),
                        "--manifest", str(manifest), "-C", str(classes), "."], check=True)
        shutil.copy2(ROOT / "LICENSE", mod / "common/LICENSE.txt")

    archive = OUT / "VMCompatStorm-unsigned.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as output:
        for path in sorted(mod.rglob("*")):
            if path.is_file():
                output.write(path, path.relative_to(package))
    print(json.dumps({"jar": str(jar), "sha256": base.sha(jar), "unsigned_zip": str(archive)}))


if __name__ == "__main__":
    build()
