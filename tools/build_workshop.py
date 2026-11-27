"""Build an unsigned VMCompat package from source and installed dependencies."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
OUT = Path(os.environ.get("VMCOMPAT_WORKSHOP_OUT", ROOT / "build")).expanduser().resolve()
BUILD = OUT / "compiler"
PACKAGE = OUT / "workshop"
STEAM = Path.home() / "Library/Application Support/Steam/steamapps"
GAME = Path(os.environ.get("VMCOMPAT_GAME_JAVA_DIR",
    STEAM / "common/ProjectZomboid/Project Zomboid.app/Contents/Java")).expanduser().resolve()
VP = Path(os.environ.get("VMCOMPAT_VIEWPOINT_JAR",
    STEAM / "workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar")).expanduser().resolve()
OLDPOINT = Path(os.environ.get("VMCOMPAT_OLDPOINT_JAR",
    STEAM / "workshop/content/108600/3815999052/mods/OldPoint/42/media/java/client/OldPoint.jar")).expanduser().resolve()
JDK = Path(os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@25")) / "bin"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build():
    dependencies = {
        GAME / "projectzomboid.jar": "VMCOMPAT_GAME_JAVA_DIR",
        VP: "VMCOMPAT_VIEWPOINT_JAR",
        JDK / "javac": "JAVA_HOME",
        JDK / "jar": "JAVA_HOME",
    }
    for path, variable in dependencies.items():
        if not path.is_file():
            raise SystemExit(f"Missing {path}. Set {variable} to your local installation.")

    BUILD.mkdir(parents=True, exist_ok=True)
    classpath = os.pathsep.join((str(GAME / "projectzomboid.jar"), str(VP)))
    sources = sorted((ROOT / "src/main/java").rglob("*.java"))
    with tempfile.TemporaryDirectory(prefix="compile-", dir=BUILD) as workdir:
        work = Path(workdir)
        classes = work / "classes"
        classes.mkdir()
        subprocess.run([str(JDK / "javac"), "--release", "25", "-encoding", "UTF-8",
                        "-cp", classpath, "-d", str(classes), *map(str, sources)], check=True)
        shutil.copytree(ROOT / "src/main/resources", classes, dirs_exist_ok=True)
        artifact = work / "VMCompat.jar"
        subprocess.run([str(JDK / "jar"), "--create", "--file", str(artifact),
                        "--manifest", str(HERE / "MANIFEST.MF"), "-C", str(classes), "."], check=True)

        mod = PACKAGE / "Contents/mods/VMCompat"
        jar = mod / "42/media/java/client/VMCompat.jar"
        if jar.with_suffix(".jar.zbs").exists():
            raise SystemExit("The build directory contains a signed package. Choose a fresh VMCOMPAT_WORKSHOP_OUT.")
        shutil.copytree(ROOT / "mod", mod, dirs_exist_ok=True)
        jar.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / "LICENSE", mod / "common/LICENSE.txt")
        shutil.copy2(artifact, jar)

    archive = OUT / "VMCompat-unsigned.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as output:
        for path in sorted(mod.rglob("*")):
            if path.is_file():
                output.write(path, path.relative_to(PACKAGE))
    print(json.dumps({"jar": str(jar), "sha256": sha(jar), "unsigned_zip": str(archive)}))


if __name__ == "__main__":
    build()
